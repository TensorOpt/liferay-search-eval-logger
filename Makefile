# SPDX-License-Identifier: Apache-2.0
#
# Every workflow in one place. `make` lists the targets. Gradle uses JAVA_HOME,
# which must point at JDK 17 or 21.
#
# Each branch builds for one DXP LTS line, read from gradle.properties
# (`make` prints it); each line releases separately, from its own branch.

.DEFAULT_GOAL := help
SHELL := /bin/bash
.SHELLFLAGS := -euo pipefail -c

MODULES := search-eval-logger-api search-eval-logger-service search-eval-logger-impl search-eval-logger-web

LINE := $(shell sed -nE 's/^liferay\.workspace\.product=(dxp-[0-9]{4}\.q[0-9])\..*/\1/p' gradle.properties)
BUNDLE_VERSION := $(shell sed -nE 's/^Bundle-Version: *//p' modules/search-eval-logger-api/bnd.bnd)

VERSION ?= $(BUNDLE_VERSION)
LPKG := build/e2e/liferay-search-eval-logger.lpkg

DB ?= postgres
DBS ?= postgres mysql mysql-mariadb-driver mariadb
FRESH ?= 0
VOLUMES ?= 0
PORT ?= 8080

.PHONY: help build package run stop e2e e2e-run test release

help: ## List the targets
	@grep -E '^[a-z0-9-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  make %-8s %s\n", $$1, $$2}'
	@echo
	@echo "  DB=postgres|mysql|mysql-mariadb-driver|mariadb   FRESH=1   VOLUMES=1   PORT=8080"
	@echo "  DBS=\"$(DBS)\"   VERSION=$(VERSION)   (line $(LINE))"
	@echo
	@echo "  each DB gets its own dev stack (project sel-dev-<db>). make stop DB=<db>"
	@echo "  stops just that one; make stop with no DB stops every sel-dev-* stack found."

build: ## Compile all four bundles, run the unit tests and the e2e selftest
	./gradlew build
	python3 e2e/selftest.py

package: build ## Release jars and SHA256SUMS into build/dist/<version>-<line>/, and the test .lpkg
	python3 tools/package.py --version $(VERSION)

run: build ## Start a portal with the plugin on DB, on PORT (FRESH=1 empties the database first)
	python3 tools/portal.py up --database $(DB) --http-port $(PORT) $(if $(filter 1,$(FRESH)),--fresh)

stop: ## Stop the portal started by make run. DB=<db> stops just that one; omitted stops every sel-dev-* stack found
	@if [ "$(origin DB)" = "command line" ]; then \
		python3 tools/portal.py down --database $(DB) $(if $(filter 1,$(VOLUMES)),--volumes); \
	else \
		python3 tools/portal.py down --all $(if $(filter 1,$(VOLUMES)),--volumes); \
	fi

e2e-run: ## Run the e2e suite on DB against jars/.lpkg already built (no rebuild)
	rm -rf "e2e/results/$(DB)"
	e2e/run.sh --no-build --database $(DB) --lpkg $(LPKG) --results e2e/results/$(DB)

e2e: package ## Build and package, then run the e2e suite on DB, ending with an .lpkg install
	$(MAKE) e2e-run DB=$(DB)

test: package ## Unit tests, then the e2e suite on every database in DBS
	@failed=""; \
	for db in $(DBS); do \
		echo "== e2e on $$db"; \
		$(MAKE) e2e-run DB=$$db \
			|| failed="$$failed $$db"; \
	done; \
	for db in $(DBS); do echo "$$db: $$(grep -h 'cases,' e2e/results/$$db/report.txt || echo 'no report')"; done; \
	if [ -n "$$failed" ]; then echo "e2e failed on:$$failed"; exit 1; fi

release: ## Release VERSION for this DXP line: checks, clean build, make test, publish
	@[ -n "$(VERSION)" ] \
		|| { echo "VERSION is empty. Set VERSION, e.g. make release VERSION=$(BUNDLE_VERSION)"; exit 1; }
	@[ "$(origin VERSION)" = "command line" ] || [ "$(origin VERSION)" = "environment" ] \
		|| { echo "Set VERSION, e.g. make release VERSION=$(BUNDLE_VERSION)"; exit 1; }
	@[ "$(VERSION)" = "$(BUNDLE_VERSION)" ] \
		|| { echo "VERSION $(VERSION) but the bundles are $(BUNDLE_VERSION); bump Bundle-Version in every modules/*/bnd.bnd first"; exit 1; }
	@[ -z "$$(git status --porcelain)" ] || { echo "The working tree is not clean"; exit 1; }
	@branch="$${CI_COMMIT_BRANCH:-$$(git rev-parse --abbrev-ref HEAD)}"; \
		[[ "$$branch" == main || "$$branch" == dxp-* ]] \
		|| { echo "Release from main or a dxp-* branch, not $$branch"; exit 1; }
	python3 tools/publish.py preflight --version $(VERSION)
	./gradlew $(foreach module,$(MODULES),:modules:$(module):clean)
	$(MAKE) test VERSION=$(VERSION)
	python3 tools/publish.py publish --version $(VERSION)
