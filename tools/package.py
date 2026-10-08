#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Packages the built bundles into a release directory.

    build/dist/<version>-<line>/
        ai.tensoropt.sel.*-<version>.jar   the four bundles: the release, and
                                           what is uploaded to Marketplace
        SHA256SUMS                         every jar
        release-notes.md

    build/e2e/liferay-search-eval-logger.lpkg
        the same four jars as an .lpkg, for the e2e suite only (TO-116)

<line> is the DXP line this branch builds for, from gradle.properties
(dxp-2026.q1 for liferay.workspace.product=dxp-2026.q1.13-lts).

Releases are plain jars, as Liferay's own quarterly releases are. The .lpkg is
never published: Liferay Marketplace builds its own from the uploaded jars, and
that is what Marketplace customers install. The e2e suite installs this one as
a stand-in for it, so a release is known to work when delivered that way too.
Its name never carries a version, because Liferay identifies an installed
.lpkg by its file name and replaces it only when the name matches.

The version comes from the bundles themselves (Bundle-Version in bnd.bnd). A
--version that disagrees is refused rather than papered over, so a release
never ships jars that report a different version from the one on the label.
"""

import argparse
import hashlib
import os
import re
import shutil
import sys
import zipfile

REPOSITORY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODULES = (
    "search-eval-logger-api",
    "search-eval-logger-service",
    "search-eval-logger-impl",
    "search-eval-logger-web",
)

LPKG_NAME = "liferay-search-eval-logger.lpkg"

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


def write_lpkg(path, jars, version):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        entries = [(os.path.basename(jar), open(jar, "rb").read()) for jar in jars]
        entries.append(
            (
                "liferay-marketplace.properties",
                (MARKETPLACE_PROPERTIES % {"version": version}).encode("utf-8"),
            )
        )

        for name, payload in sorted(entries):
            info = zipfile.ZipInfo(name, _ZIP_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            archive.writestr(info, payload)


def sha256(path):
    digest = hashlib.sha256()

    with open(path, "rb") as file_:
        for chunk in iter(lambda: file_.read(1 << 20), b""):
            digest.update(chunk)

    return digest.hexdigest()


def release_notes(version, line, sums):
    dxp = line.replace("dxp-", "DXP ").replace(".q", ".Q")
    checksums = "\n".join("%s  %s" % (digest, name) for name, digest in sums)

    return """# Liferay Search Eval Logger %(version)s for %(dxp)s LTS

Records what people search for on a Liferay DXP site and the results they were
shown, so search quality can be measured on your own content.

This build is for **%(dxp)s LTS** only. Each DXP LTS line has its own release.

## Install

1. Download the four `ai.tensoropt.sel.*.jar` files and `SHA256SUMS`, and
   verify them: `shasum -a 256 -c SHA256SUMS`
2. Copy the four jars to `[Liferay Home]/deploy`.
3. **Restart the portal.** The plugin only receives searches made after a
   restart.

The same version is also on Liferay Marketplace, which delivers it as an .lpkg.

The README covers enabling collection, exporting, and what is and is not
verified; SECURITY-REVIEW.md is written to be forwarded to a reviewer.

## Checksums

```
%(checksums)s
```
""" % {
        "version": version,
        "dxp": dxp,
        "checksums": checksums,
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

    files = [shutil.copy(jar, output) for jar in jars]

    sums = [(os.path.basename(path), sha256(path)) for path in files]

    with open(os.path.join(output, "SHA256SUMS"), "w") as file_:
        file_.writelines("%s  %s\n" % (digest, name) for name, digest in sums)

    with open(os.path.join(output, "release-notes.md"), "w") as file_:
        file_.write(release_notes(version, line, sums))

    print(output)


if __name__ == "__main__":
    main(sys.argv[1:])
