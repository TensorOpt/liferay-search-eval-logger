#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Packages the built bundles into a release directory.

    build/dist/<version>-<line>/
        ai.tensoropt.sel.*-<version>-<line>.jar
            the four bundles, named for the DXP line they were built for:
            the zip's inputs, and what is uploaded to Marketplace
        SHA256SUMS
            every jar
        <package-name>-<version>-<line>-jars.zip
            the same jars and SHA256SUMS, one download
        release-notes.md
        package-commit.json
            internal marker (see below); never an asset, never inside the zip

    build/e2e/liferay-search-eval-logger.lpkg
        the same four jars as an .lpkg, for the e2e suite only (TO-116)

<line> is the DXP line this branch builds for, from gradle.properties
(dxp-2026.q1 for liferay.workspace.product=dxp-2026.q1.13-lts).

A release is a zip of plain jars, as Liferay's own quarterly releases are. The .lpkg is
never published: Liferay Marketplace builds its own from the uploaded jars, and
that is what Marketplace customers install. The e2e suite installs this one as
a stand-in for it, so a release is known to work when delivered that way too.
Its name never carries a version, because Liferay identifies an installed
.lpkg by its file name and replaces it only when the name matches.

The version comes from the bundles themselves (Bundle-Version in bnd.bnd). A
--version that disagrees is refused rather than papered over, so a release
never ships jars that report a different version from the one on the label.

Every packaging run also records the commit it packaged, and whether the
working tree was dirty, into package-commit.json next to the other output -
when run from a git checkout; packaging a plain source tree (for example a
GitHub "Source code" archive, which carries no .git directory) just skips the
marker. scripts/publish.py refuses to publish a dist directory recorded for
any commit other than HEAD, recorded as dirty, or missing the marker
entirely: a dirty tree is allowed here because `make e2e` and `make test`
package during ordinary development with uncommitted changes, but publishing
those jars as a release would ship something no commit can reproduce.
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

REPOSITORY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

sys.path.insert(0, os.path.join(REPOSITORY, "e2e"))

from run_e2e import MODULES  # noqa: E402 - the one list of the four modules

PACKAGE_NAME = "liferay-search-eval-logger"
LPKG_NAME = PACKAGE_NAME + ".lpkg"
MARKER_NAME = "package-commit.json"

# Read by Liferay's LPKG verifier (version) and by Marketplace, which records
# the package as an app (title, description, category).
MARKETPLACE_PROPERTIES = """\
category=Search
description=Records user searches and the results they were shown, for offline search quality evaluation.
title=Liferay Search Eval Logger
version=%(version)s
"""

# A fixed timestamp makes the archive depend on its contents alone.
_ZIP_DATE = (1980, 1, 1, 0, 0, 0)


def dxp_line():
    """dxp-2026.q1 for liferay.workspace.product=dxp-2026.q1.13-lts."""
    with open(os.path.join(REPOSITORY, "gradle.properties")) as file_:
        match = re.search(
            r"^liferay\.workspace\.product=(dxp-\d{4}\.q\d)\.\d+", file_.read(), re.M
        )

    if match is None:
        sys.exit("gradle.properties names no dxp-YYYY.qN.P product")

    return match.group(1)


def dxp_title(line):
    """DXP 2026.Q1 for dxp-2026.q1. Shared with scripts/publish.py's release title."""
    return line.replace("dxp-", "DXP ").replace(".q", ".Q")


def built_jars():
    jars = []

    for module in MODULES:
        libs = os.path.join(REPOSITORY, "modules", module, "build", "libs")

        matches = sorted(
            name
            for name in (os.listdir(libs) if os.path.isdir(libs) else [])
            if name.endswith(".jar") and not name.endswith("-sources.jar")
        )

        if len(matches) != 1:
            sys.exit(
                "Expected one jar in %s, found %s. Run make build first."
                % (libs, matches or "none")
            )

        jars.append(os.path.join(libs, matches[0]))

    return jars


def bundle_version(jar):
    with zipfile.ZipFile(jar) as archive:
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")

    match = re.search(r"^Bundle-Version: *(\S+)", manifest, re.M)

    if match is None:
        sys.exit("%s has no Bundle-Version" % jar)

    return match.group(1)


def write_deterministic_zip(path, entries):
    """entries: an iterable of (name, bytes). Sorted, and every entry stamped
    to a fixed timestamp, so the archive depends on its contents alone."""
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, payload in sorted(entries):
            info = zipfile.ZipInfo(name, _ZIP_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, payload)


def write_lpkg(path, jars, version):
    entries = [(os.path.basename(jar), open(jar, "rb").read()) for jar in jars]
    entries.append(
        (
            "liferay-marketplace.properties",
            (MARKETPLACE_PROPERTIES % {"version": version}).encode("utf-8"),
        )
    )

    write_deterministic_zip(path, entries)


def dist_jar_name(jar, line):
    """The dist copy's name: the Gradle output's own <symbolic-name>-<version>
    stem, with the DXP line appended, so a release asset says which line it
    is for. The Gradle output under modules/*/build/libs keeps its own name
    unchanged - the e2e harness and the .lpkg both read it by that name."""
    stem, ext = os.path.splitext(os.path.basename(jar))

    return "%s-%s%s" % (stem, line, ext)


def jars_zip_name(version, line):
    return "%s-%s-%s-jars.zip" % (PACKAGE_NAME, version, line)


def write_jars_zip(path, files):
    entries = [(os.path.basename(file_), open(file_, "rb").read()) for file_ in files]

    write_deterministic_zip(path, entries)


def git_commit():
    return subprocess.run(
        ["git", "-C", REPOSITORY, "rev-parse", "HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()


def git_dirty():
    status = subprocess.run(
        ["git", "-C", REPOSITORY, "status", "--porcelain"],
        check=True, capture_output=True, text=True,
    ).stdout

    return bool(status.strip())


def write_marker(output):
    """Skips the marker, rather than failing the packaging run, when there is
    no git to ask: a GitHub "Source code" archive extract carries no .git
    directory, and `git -C` then exits non-zero (or, if git itself is not
    installed, raises FileNotFoundError). verify_packaged refuses to publish
    a dist with no marker at all, so this still keeps a stale or unverifiable
    tree from being published - it just stops make package itself from
    being the thing that fails."""
    try:
        marker = {"commit": git_commit(), "dirty": git_dirty()}
    except (subprocess.CalledProcessError, FileNotFoundError):
        return

    with open(os.path.join(output, MARKER_NAME), "w") as file_:
        json.dump(marker, file_)


def read_marker(output):
    path = os.path.join(output, MARKER_NAME)

    if not os.path.isfile(path):
        return None

    with open(path) as file_:
        return json.load(file_)


def sha256(path):
    digest = hashlib.sha256()

    with open(path, "rb") as file_:
        for chunk in iter(lambda: file_.read(1 << 20), b""):
            digest.update(chunk)

    return digest.hexdigest()


def release_notes(version, line, sums, zip_name):
    dxp = dxp_title(line)
    checksums = "\n".join("%s  %s" % (digest, name) for name, digest in sums)

    return """# Liferay Search Eval Logger %(version)s for %(dxp)s LTS

Records what people search for on a Liferay DXP site and the results they were
shown, so search quality can be measured on your own content.

Built for **%(dxp)s LTS**; each DXP LTS line has its own release. The
README's Requirements section lists the other DXP releases these jars run on.

## Install

1. Download the zip and unzip it; the four jars and `SHA256SUMS` come out
   together:
   `%(zip)s`
2. Verify them: `shasum -a 256 -c SHA256SUMS`
3. Copy the four jars to `[Liferay Home]/deploy`.
4. **Restart the portal.** The plugin only receives searches made after a
   restart.

Installed from Liferay Marketplace instead, the same jars arrive as an .lpkg;
the restart is needed either way.

The README covers enabling collection, exporting, and what is and is not
verified; SECURITY-REVIEW.md is written to be forwarded to a reviewer.

## Checksums

`SHA256SUMS`, from inside the zip, for the four jars it holds; the zip itself
has no separate checksum of its own.

```
%(checksums)s
```
""" % {
        "version": version,
        "dxp": dxp,
        "checksums": checksums,
        "zip": zip_name,
    }


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--version", help="defaults to the bundles' Bundle-Version")
    parser.add_argument(
        "--dist", default=os.path.join(REPOSITORY, "build", "dist"), help="output root"
    )
    options = parser.parse_args(argv)

    jars = built_jars()
    versions = {os.path.basename(jar): bundle_version(jar) for jar in jars}

    if len(set(versions.values())) != 1:
        sys.exit("The bundles disagree on their version: %s" % versions)

    built = next(iter(versions.values()))
    version = options.version or built

    if version != built:
        sys.exit(
            "Asked to package %s, but the bundles are %s. Set Bundle-Version in "
            "every modules/*/bnd.bnd to %s and rebuild first." % (version, built, version)
        )

    line = dxp_line()
    output = os.path.join(options.dist, "%s-%s" % (version, line))

    shutil.rmtree(output, ignore_errors=True)
    os.makedirs(output)

    lpkg_directory = os.path.join(os.path.dirname(options.dist), "e2e")

    os.makedirs(lpkg_directory, exist_ok=True)

    write_lpkg(os.path.join(lpkg_directory, LPKG_NAME), jars, version)

    files = [
        shutil.copy(jar, os.path.join(output, dist_jar_name(jar, line))) for jar in jars
    ]

    sums = [(os.path.basename(path), sha256(path)) for path in files]

    sums_path = os.path.join(output, "SHA256SUMS")

    with open(sums_path, "w") as file_:
        file_.writelines("%s  %s\n" % (digest, name) for name, digest in sums)

    zip_name = jars_zip_name(version, line)

    write_jars_zip(os.path.join(output, zip_name), files + [sums_path])

    with open(os.path.join(output, "release-notes.md"), "w") as file_:
        file_.write(release_notes(version, line, sums, zip_name))

    write_marker(output)

    print(output)


if __name__ == "__main__":
    main(sys.argv[1:])
