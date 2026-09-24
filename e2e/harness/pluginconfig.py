# SPDX-License-Identifier: Apache-2.0
"""Reads the collector's .config file, so the tests do not restate it.

The file in liferay/files/osgi/configs is the only statement of what this run
configures the plugin with. Copying its values into the test code as constants
would make the manifest comparison a comparison of two copies of the same
literal: change the file alone and the assertion keeps passing against the
value it no longer sets.

The format is Felix's typed configuration syntax. Only the types this project
uses are understood, and an unrecognised one raises rather than being guessed
at, because a silently misread configuration is the failure this whole module
exists to prevent.
"""

import os
import re

from .util import HarnessError

CONFIGURATION_FILE = os.path.join(
    "liferay",
    "files",
    "osgi",
    "configs",
    "ai.tensoropt.sel.configuration.SearchEvalLoggerConfiguration.config",
)

# Attribute name in the @Meta.OCD, mapped to the key the export manifest
# reports it under. Only the settings the manifest carries are listed; the
# readiness thresholds are read from the same file but never appear there.
MANIFEST_KEYS = {
    "enabled": "enabled",
    "captureDepth": "capture_depth",
    "retentionDays": "retention_days",
    "excludeSuggestionTraffic": "exclude_suggestion_traffic",
    "admitFacetOnlySearches": "admit_facet_only_searches",
    "requireWebRequestContext": "require_web_request_context",
    "queryTextCap": "query_text_cap",
    "samplingRate": "sampling_rate",
    "cohortSaltRotationDays": "cohort_salt_rotation_days",
}

_ENTRY = re.compile(r'^(?P<name>[A-Za-z0-9_.]+)\s*=\s*(?P<value>.+)$')


def read(directory):
    """Returns {attribute name: typed value} for the collector's config file."""
    path = os.path.join(directory, CONFIGURATION_FILE)

    values = {}

    with open(path, encoding="utf-8") as file_:
        for number, raw_line in enumerate(file_, start=1):
            line = raw_line.strip()

            if not line or line.startswith("#"):
                continue

            match = _ENTRY.match(line)

            if match is None:
                raise HarnessError(
                    "%s line %d is not a configuration entry: %r"
                    % (path, number, line)
                )

            values[match.group("name")] = _value(
                match.group("value"), path, number
            )

    if not values:
        raise HarnessError("%s set nothing" % path)

    return values


def manifest_expectations(values):
    """The subset of the file the export manifest reports back."""
    expectations = {}

    for name, manifest_key in MANIFEST_KEYS.items():
        if name in values:
            expectations[manifest_key] = values[name]

    return expectations


def _value(text, path, number):
    text = text.strip()

    if text.startswith('B"') and text.endswith('"'):
        return text[2:-1] == "true"

    if text.startswith('I"') and text.endswith('"'):
        return int(text[2:-1])

    if text.startswith('L"') and text.endswith('"'):
        return int(text[2:-1])

    if text.startswith('D"') and text.endswith('"'):
        return float(text[2:-1])

    if text.startswith('"') and text.endswith('"'):
        return text[1:-1]

    raise HarnessError(
        "%s line %d uses a configuration type this reader does not know: %r"
        % (path, number, text)
    )
