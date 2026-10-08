#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Publishes a packaged release to GitLab and to GitHub.

    tools/publish.py preflight --version 1.0.0
    tools/publish.py publish   --version 1.0.0 [--dry-run]

GitLab is where the code and the pipelines live; GitHub is a distribution
channel. Each release is one DXP line: tag v<version>-<line>, for example
v1.0.0-dxp-2026.q1, with the files tools/package.py wrote to
build/dist/<version>-<line>/ attached to both.

preflight runs before the build, so a release that cannot be published fails
in seconds rather than after the e2e suite. It checks everything that could
stop a release halfway: the tokens, the tag being free on both, the commit being on
GitHub already (GitLab's push mirror puts it there), and both evaluation
service pages answering, since every installed copy links to them.

Environment:
    GITLAB_TOKEN or CI_JOB_TOKEN   GitLab API access (CI provides the latter)
    CI_API_V4_URL                  default https://gitlab.com/api/v4
    GITLAB_PROJECT or CI_PROJECT_ID  numeric id or group/name path
    GITHUB_TOKEN                   a token allowed to create releases
    GITHUB_REPOSITORY              default TensorOpt/liferay-search-eval-logger
"""

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

REPOSITORY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

sys.path.insert(0, os.path.join(REPOSITORY, "tools"))

from package import dxp_line  # noqa: E402

PACKAGE = "liferay-search-eval-logger"

LINKS_SOURCE = os.path.join(
    REPOSITORY,
    "modules/search-eval-logger-web/src/main/java/ai/tensoropt/sel/web/internal/"
    "funnel/EvaluationServiceLinks.java",
)


class Release:
    def __init__(self, version, dry_run):
        self.version = version
        self.line = dxp_line()
        self.tag = "v%s-%s" % (version, self.line)
        self.directory = os.path.join(REPOSITORY, "build", "dist", "%s-%s" % (version, self.line))
        self.commit = _git("rev-parse", "HEAD")
        self.dry_run = dry_run

        self.gitlab_api = os.environ.get("CI_API_V4_URL", "https://gitlab.com/api/v4")
        self.gitlab_project = urllib.parse.quote(
            os.environ.get("GITLAB_PROJECT") or os.environ.get("CI_PROJECT_ID") or "", safe=""
        )
        self.github_repository = os.environ.get(
            "GITHUB_REPOSITORY", "TensorOpt/liferay-search-eval-logger"
        )

    @property
    def title(self):
        return "%s for %s LTS" % (
            self.version, self.line.replace("dxp-", "DXP ").replace(".q", ".Q")
        )

    def files(self):
        names = sorted(
            name for name in os.listdir(self.directory) if name.endswith(".jar")
        ) + ["SHA256SUMS"]

        return [os.path.join(self.directory, name) for name in names]

    def notes(self):
        with open(os.path.join(self.directory, "release-notes.md")) as file_:
            return file_.read()

    # GitLab

    def _gitlab_headers(self):
        if os.environ.get("GITLAB_TOKEN"):
            return {"PRIVATE-TOKEN": os.environ["GITLAB_TOKEN"]}

        return {"JOB-TOKEN": os.environ.get("CI_JOB_TOKEN", "")}

    def _gitlab(self, method, path, body=None, data=None, content_type=None, allow=()):
        return _request(
            method,
            "%s/projects/%s%s" % (self.gitlab_api, self.gitlab_project, path),
            self._gitlab_headers(),
            body=body,
            data=data,
            content_type=content_type,
            dry_run=self.dry_run and method != "GET",
            allow=allow,
        )

    def _package_url(self, name):
        return "%s/projects/%s/packages/generic/%s/%s-%s/%s" % (
            self.gitlab_api, self.gitlab_project, PACKAGE, self.version, self.line, name,
        )

    def publish_gitlab(self):
        links = []

        for path in self.files():
            name = os.path.basename(path)

            with open(path, "rb") as file_:
                _request(
                    "PUT", self._package_url(name), self._gitlab_headers(),
                    data=file_.read(), content_type="application/octet-stream",
                    dry_run=self.dry_run,
                )

            links.append({"name": name, "url": self._package_url(name), "link_type": "package"})

        self._gitlab(
            "POST",
            "/releases",
            body={
                "tag_name": self.tag,
                "ref": self.commit,
                "name": self.title,
                "description": self.notes(),
                "assets": {"links": links},
            },
        )

    # GitHub

    def _github(self, method, url, body=None, data=None, content_type=None, allow=()):
        return _request(
            method,
            url if url.startswith("https://") else "https://api.github.com" + url,
            {
                "Authorization": "Bearer %s" % os.environ.get("GITHUB_TOKEN", ""),
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
            body=body,
            data=data,
            content_type=content_type,
            dry_run=self.dry_run and method != "GET",
            allow=allow,
        )

    def publish_github(self):
        created = self._github(
            "POST",
            "/repos/%s/releases" % self.github_repository,
            body={
                "tag_name": self.tag,
                "target_commitish": self.commit,
                "name": self.title,
                "body": self.notes(),
            },
        )

        upload_url = (created or {}).get(
            "upload_url", "https://uploads.github.com/repos/%s/releases/0/assets{?name,label}"
            % self.github_repository,
        ).split("{")[0]

        for path in self.files():
            with open(path, "rb") as file_:
                self._github(
                    "POST",
                    "%s?name=%s" % (upload_url, urllib.parse.quote(os.path.basename(path))),
                    data=file_.read(),
                    content_type="application/octet-stream",
                )

    # Preflight

    def preflight(self):
        problems = []

        if not (os.environ.get("GITLAB_TOKEN") or os.environ.get("CI_JOB_TOKEN")):
            problems.append("GITLAB_TOKEN (or CI_JOB_TOKEN in CI) is not set")

        if not self.gitlab_project:
            problems.append("GITLAB_PROJECT (or CI_PROJECT_ID in CI) is not set")

        if not os.environ.get("GITHUB_TOKEN"):
            problems.append("GITHUB_TOKEN is not set")

        if problems:
            return problems

        if self._gitlab("GET", "/releases/%s" % urllib.parse.quote(self.tag, safe=""),
                        allow=(404,)) is not None:
            problems.append("GitLab already has a release %s" % self.tag)

        if self._github("GET", "/repos/%s/releases/tags/%s" % (self.github_repository, self.tag),
                        allow=(404,)) is not None:
            problems.append("GitHub already has a release %s" % self.tag)

        if self._github("GET", "/repos/%s/commits/%s" % (self.github_repository, self.commit),
                        allow=(404, 422)) is None:
            problems.append(
                "commit %s is not on GitHub yet; let GitLab's push mirror run, or push it"
                % self.commit[:12]
            )

        for url in evaluation_service_urls():
            status = _status(url)

            if status != 200:
                problems.append("%s answers %s; every installed copy links there" % (url, status))

        return problems


def evaluation_service_urls():
    with open(LINKS_SOURCE) as file_:
        return re.findall(r'_URL\s*=\s*"(https://[^"]+)"', file_.read())


def _git(*args):
    return subprocess.run(
        ["git", "-C", REPOSITORY] + list(args), check=True, capture_output=True, text=True
    ).stdout.strip()


def _status(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, method="GET"), timeout=30) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code
    except urllib.error.URLError as error:
        return "unreachable (%s)" % error.reason


def _request(method, url, headers, body=None, data=None, content_type=None, dry_run=False,
             allow=()):
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        content_type = "application/json"

    if dry_run:
        print("DRY RUN %s %s%s" % (
            method, url, " (%d bytes)" % len(data) if data is not None else ""))

        return None

    request = urllib.request.Request(url, data=data, method=method, headers=dict(headers))

    if content_type:
        request.add_header("Content-Type", content_type)

    try:
        with urllib.request.urlopen(request, timeout=600) as response:
            payload = response.read()
    except urllib.error.HTTPError as error:
        if error.code in allow:
            return None

        sys.exit("%s %s failed: HTTP %d %s" % (method, url, error.code, error.read()[:500]))

    return json.loads(payload) if payload else {}


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("command", choices=("preflight", "publish"))
    parser.add_argument("--version", required=True)
    parser.add_argument("--dry-run", action="store_true", help="publish: print, upload nothing")
    options = parser.parse_args(argv)

    release = Release(options.version, options.dry_run)

    if options.command == "preflight":
        problems = release.preflight()

        if problems:
            sys.exit("Cannot release %s:\n  - %s" % (release.tag, "\n  - ".join(problems)))

        print("Ready to release %s from %s" % (release.tag, release.commit[:12]))

        return

    if not os.path.isdir(release.directory):
        sys.exit("%s does not exist; run make package" % release.directory)

    release.publish_gitlab()
    release.publish_github()

    print("Released %s" % release.tag)


if __name__ == "__main__":
    main(sys.argv[1:])
