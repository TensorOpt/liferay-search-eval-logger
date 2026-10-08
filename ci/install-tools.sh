#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Installs what the CI jobs need on top of eclipse-temurin:21-jdk: python3,
# make and git from the distribution, and the Docker CLI, Compose and Buildx at
# pinned versions, each checked against a pinned SHA-256 before it is used.

set -euo pipefail

apt-get update -qq
apt-get install -y -qq --no-install-recommends python3 make git curl ca-certificates > /dev/null

fetch() {
	local url="$1" sha256="$2" target="$3"

	curl -sSfL -o "$target" "$url"
	echo "$sha256  $target" | sha256sum -c --quiet -
}

fetch https://download.docker.com/linux/static/stable/x86_64/docker-27.3.1.tgz \
	9b4f6fe406e50f9085ee474c451e2bb5adb119a03591f467922d3b4e2ddf31d3 /tmp/docker.tgz
tar -xzf /tmp/docker.tgz -C /usr/local/bin --strip-components=1 docker/docker

mkdir -p /usr/local/lib/docker/cli-plugins

fetch https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
	383ce6698cd5d5bbf958d2c8489ed75094e34a77d340404d9f32c4ae9e12baf0 \
	/usr/local/lib/docker/cli-plugins/docker-compose

fetch https://github.com/docker/buildx/releases/download/v0.17.1/buildx-v0.17.1.linux-amd64 \
	aa7a9778349e1a8ace685e4c51a1d33e7a9b0aa6925d1c625b09cb3800eba696 \
	/usr/local/lib/docker/cli-plugins/docker-buildx

chmod +x /usr/local/lib/docker/cli-plugins/*

docker version --format 'Docker client {{.Client.Version}}'
docker compose version
