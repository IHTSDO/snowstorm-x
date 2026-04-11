#!/usr/bin/env bash
# After `mvn compile jib:build`, run this to push the same tag with BuildKit
# SBOM + provenance (mode=max) attached for Docker Scout supply chain policy.
# Requires: Docker Buildx, registry auth (`docker login`), and push permissions.
set -euo pipefail

SOURCE_IMAGE="${1:?usage: $0 <source-image> [target-image]}"
TARGET_IMAGE="${2:-$SOURCE_IMAGE}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CONTEXT_DIR="$ROOT_DIR/docker/attest-buildcontext"
DOCKERFILE="$ROOT_DIR/docker/Dockerfile.attest"

docker buildx build \
	--platform "$PLATFORMS" \
	--build-arg "SOURCE_IMAGE=$SOURCE_IMAGE" \
	--sbom=true \
	--provenance=mode=max \
	-f "$DOCKERFILE" \
	-t "$TARGET_IMAGE" \
	--push \
	"$CONTEXT_DIR"
