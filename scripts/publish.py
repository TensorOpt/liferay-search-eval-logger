#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Publishes a packaged release to GitLab and to GitHub.

    scripts/publish.py preflight --version 1.0.0
    scripts/publish.py publish   --version 1.0.0 [--dry-run]

GitLab is where the code and the pipelines live; GitHub is a distribution
channel. Each release is one DXP line: tag v<version>-<line>, for example
v1.0.0-dxp-2026.q1, with the files scripts/package.py wrote to
build/dist/<version>-<line>/ attached to both.

preflight runs before the build, so a release that cannot be published fails
in seconds rather than after the e2e suite. It checks everything that could
stop a release halfway: the tokens, the tag not already fully released on
both GitLab and GitHub, that tag not already pointing somewhere other than
HEAD on either side, the commit being on GitHub already (GitLab's push
mirror puts it there), and both evaluation service pages answering, since
every installed copy links to them.

publish is retry-safe: each side skips itself once its own release already
exists and is complete (see publish_gitlab/publish_github), so a run that
failed partway - say, GitLab succeeded and GitHub did not - can be run again
unchanged rather than requiring a new version or manual cleanup. Because
jars are not byte-reproducible (bnd stamps a build time into each one), a
retry's rebuild cannot be assumed to match what an earlier, already-published
side holds: when GitLab's release already exists, publish_github fetches
*its* files back from GitLab's generic package registry and uploads those,
rather than whatever this retry's build/dist happens to contain, so both
sides always ship byte-identical jars under one tag. The GitHub side itself
is created as a draft, filled with its assets, and only then published,
because a GitHub release (unlike GitLab's) is visible before its assets are
attached; a draft found on a later run is a previous run's unfinished side
and is deleted and rebuilt rather than resumed, since there is no reliable
way to tell which of its assets, if any, already uploaded. (GitHub's
/releases/tags/{tag} endpoint never returns a draft, so finding one to
delete is a separate, paged /releases listing.)

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

sys.path.insert(0, os.path.join(REPOSITORY, "scripts"))

from package import dxp_line, dxp_title  # noqa: E402

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
        return "%s for %s LTS" % (self.version, dxp_title(self.line))

    def _asset_names(self):
        return sorted(
            name for name in os.listdir(self.directory) if name.endswith(".jar")
        ) + ["SHA256SUMS"]

    def files(self):
        return [os.path.join(self.directory, name) for name in self._asset_names()]

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

    def _gitlab_release(self):
        return self._gitlab(
            "GET", "/releases/%s" % urllib.parse.quote(self.tag, safe=""), allow=(404,)
        )

    def _download_gitlab_asset(self, name):
        return _download(self._package_url(name), self._gitlab_headers())

    def publish_gitlab(self):
        # A GitLab release is created in one call, after every asset is
        # uploaded, so its existence already means this side is done: a retry
        # after a failure that happened later (on GitHub) must not re-upload
        # assets or fail on a release that is already there. Skipped outright
        # under --dry-run: the existence check is a real, authenticated GET,
        # which would otherwise make "print what a run would do" depend on
        # having working credentials and network access.
        if not self.dry_run and self._gitlab_release() is not None:
            print("GitLab already has a release %s; skipping" % self.tag)

            return

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

    def _github_release(self):
        """The published release for this tag, if any. GitHub's own
        /releases/tags/{tag} resolves a tag to its release but never returns
        a draft (a draft has no tag ref yet), so a hit here is always a
        complete, already-published release - never the other run's
        half-built state _github_draft looks for below."""
        return self._github(
            "GET", "/repos/%s/releases/tags/%s" % (self.github_repository, self.tag),
            allow=(404,),
        )

    def _github_draft(self):
        """A previous run's unfinished draft for this tag. Has to be found
        by listing (see _github_release) and filtering, since GitHub does
        not offer a "give me the draft for this tag" lookup. Capped at the
        newest 100 releases, comfortably more than this repository will ever
        have sitting unpublished at once."""
        releases = self._github(
            "GET", "/repos/%s/releases?per_page=100" % self.github_repository
        ) or []

        return next(
            (r for r in releases if r.get("draft") and r.get("tag_name") == self.tag), None
        )

    def _github_tag_commit(self):
        """The commit this tag currently resolves to on GitHub, if it exists
        there at all - via /commits/{ref}, which resolves any ref (a tag
        included) to its commit regardless of whether a release was ever
        made from it."""
        resolved = self._github(
            "GET", "/repos/%s/commits/%s" % (self.github_repository, self.tag),
            allow=(404, 422),
        )

        return (resolved or {}).get("sha")

    def publish_github(self):
        # Unlike GitLab's, a GitHub release exists (and is visible to anyone
        # watching the repo) before its assets are attached, so a retry needs
        # to tell a finished release from one a previous run left half built.
        # It is created as a draft, filled, and only then flipped to
        # published. A found draft is the other run's half-built state:
        # there is no reliable way to tell which assets it already has, so
        # it is deleted and rebuilt rather than patched, which also means
        # every upload below always starts from an empty release and can
        # never collide with a stale asset of the same name. Both existence
        # checks are skipped outright under --dry-run, for the same offline
        # reason publish_gitlab skips its own.
        if not self.dry_run and self._github_release() is not None:
            print("GitHub already has a release %s; skipping" % self.tag)

            return

        draft = None if self.dry_run else self._github_draft()

        if draft is not None:
            self._github("DELETE", "/repos/%s/releases/%s" % (self.github_repository, draft["id"]))

        # Jars are not byte-reproducible across builds (bnd stamps a build
        # time into each one), so a retry's local build/dist cannot be
        # assumed to match what GitLab already published under this tag.
        # When GitLab's side is already done, its notes and exact bytes are
        # used rather than the local, possibly different, rebuild's.
        gitlab_release = None if self.dry_run else self._gitlab_release()

        created = self._github(
            "POST",
            "/repos/%s/releases" % self.github_repository,
            body={
                "tag_name": self.tag,
                "target_commitish": self.commit,
                "name": self.title,
                "body": self.notes() if gitlab_release is None else gitlab_release["description"],
                "draft": True,
            },
        )

        upload_url = (created or {}).get(
            "upload_url", "https://uploads.github.com/repos/%s/releases/0/assets{?name,label}"
            % self.github_repository,
        ).split("{")[0]

        for name in self._asset_names():
            if gitlab_release is not None:
                data = self._download_gitlab_asset(name)
            else:
                with open(os.path.join(self.directory, name), "rb") as file_:
                    data = file_.read()

            self._github(
                "POST",
                "%s?name=%s" % (upload_url, urllib.parse.quote(name)),
                data=data,
                content_type="application/octet-stream",
            )

        self._github(
            "PATCH",
            "/repos/%s/releases/%s" % (self.github_repository, (created or {}).get("id", 0)),
            body={"draft": False},
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

        # Each side is skipped by publish() once it already has a complete
        # release (see publish_gitlab/publish_github), so only refuse here
        # when BOTH are already done - there is then truly nothing left for
        # a run to do, which is the one case worth stopping before the build.
        # A lone GitLab release, or a GitHub release still in draft (a
        # previous run's unfinished side), is left for publish() to finish.
        # _github_release() never returns a draft (see its docstring), so a
        # hit there is always the complete, done state.
        gitlab_release = self._gitlab_release()
        github_release = self._github_release()

        if gitlab_release is not None and github_release is not None:
            problems.append("GitLab and GitHub already have a release %s" % self.tag)

        # A release that exists for the wrong commit is worse than a missing
        # one: publish() would skip the existing side and ship this run's
        # freshly built jars under a tag already pinned elsewhere, silently
        # mixing two commits' artifacts under one version. Checked for both
        # sides, and independent of whether a release was ever made from the
        # tag on GitHub - GitLab's push mirror pushes tags on their own, so
        # the tag itself can exist there with no release yet.

        gitlab_commit = ((gitlab_release or {}).get("commit") or {}).get("id")

        if gitlab_release is not None and not gitlab_commit:
            problems.append("GitLab's release %s does not say which commit it is for" % self.tag)
        elif gitlab_commit and gitlab_commit != self.commit:
            problems.append(
                "GitLab's release %s is for commit %s, not HEAD %s. Bump "
                "the version instead of retrying."
                % (self.tag, gitlab_commit[:12], self.commit[:12])
            )

        github_tag_commit = self._github_tag_commit()

        if github_tag_commit and github_tag_commit != self.commit:
            problems.append(
                "GitHub's tag %s already points at commit %s, not HEAD %s. "
                "Bump the version instead of retrying."
                % (self.tag, github_tag_commit[:12], self.commit[:12])
            )

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


def _download(url, headers):
    """Raw bytes from an authenticated GET, for re-uploading an asset GitLab
    already holds rather than re-reading _request's JSON-decoded result."""
    try:
        with urllib.request.urlopen(
            urllib.request.Request(url, headers=dict(headers)), timeout=600
        ) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        sys.exit("GET %s failed: HTTP %d %s" % (url, error.code, error.read()[:500]))


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
