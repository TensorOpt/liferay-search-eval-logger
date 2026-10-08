# SPDX-License-Identifier: Apache-2.0
#
# The portal the harness tests: Liferay's own image with the harness's files
# baked in rather than bind mounted. A bind mount is resolved by whichever
# Docker daemon runs the container, which under Docker-in-Docker (GitLab's
# shared runners) is not the machine holding this checkout; a build context is
# sent over the Docker API and arrives the same everywhere.

ARG LIFERAY_IMAGE
FROM ${LIFERAY_IMAGE}

# Copied into Liferay Home by the image's entrypoint before the portal starts.
COPY --chown=liferay:liferay files/ /mnt/liferay/files/

# A JDBC driver the image does not ship, for the databases that need one
# (MySQL Connector/J). The harness fills this directory per run; it is empty
# for every other database.
COPY --chown=liferay:liferay drivers/ /opt/liferay/tomcat/webapps/ROOT/WEB-INF/shielded-container-lib/
