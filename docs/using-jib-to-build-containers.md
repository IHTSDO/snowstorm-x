# Using Jib to build containers

## Introduction

This project uses [Google Jib](https://github.com/GoogleContainerTools/jib) to build optimised Docker/OCI images, without the need for a Dockerfile and Docker Engine.

The Maven plugin supports the definition of the container in `pom.xml` and provides new goals to build a container. The full documentation for the Maven Plugin can be found [here](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin).


## Multiple Platform Support

Jib supports building container images for multiple platform architectures e.g. `linux/amd64`, `linux/arm64`.

Note: in order to achieve this the base image must support the desired platforms.

### Amazon Corretto

The [Amazon Corretto](https://hub.docker.com/_/amazoncorretto) OpenJDK 17 base image supports both `linux/amd64` and `linux/arm64`. The build uses the fully qualified reference `docker.io/library/amazoncorretto:17`.


## Build to remote container registry

Jib supports build and pushing of images to a cloud container registry including:

- DockerHub
- Google Container Registry (GCR)
- AWS Elastic Container Registry (ECR)
- Azure Container Registry (ACR)
- JFrog Container Registry (JCR)

The following command can be used to build and push images to a registry.

```
mvn compile jib:build
```

The configuration of the destination registry and authentication methods are documented here:

- [Registry](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#configuration)
- [Authentication](https://github.com/GoogleContainerTools/jib/tree/master/jib-maven-plugin#authentication-methods)

## Build to a local docker registry

The following steps describe how to build to a local registry

1. Launch a local docker registry

```
docker run -d -p 5001:5000 --name registry registry:2
```

2. Set `docker.registry` property in pom.xml

```
<docker.registry>localhost:5001/</docker.registry>
```

3. Set `docker.allowInsecureRegistries` property in `pom.xml`

```
<docker.allowInsecureRegistries>true<docker.allowInsecureRegistries>
```

4. Run the build

```
mvn compile jib:build
```


## Build to a local docker daemon

Jib supports building an image directly to a local docker daemon. However it is not possible to build for multiple platforms with this approach.

In order to build an image for a specific platform (either linux/amd64 or linux/arm64) two maven profiles have been configured:

### Profile: docker-amd64

```
mvn compile jib:dockerBuild -P docker-amd64
```

### Profile: docker-arm64

```
mvn compile jib:dockerBuild -P docker-arm64
```

## Supply chain attestations (Docker Scout)

[Jib](https://github.com/GoogleContainerTools/jib) does not attach [BuildKit attestations](https://docs.docker.com/build/metadata/attestations/) (SBOM and SLSA provenance). Docker Scout’s default **Supply Chain Attestations** policy expects both an SBOM and **provenance with `mode=max`**, which are normally produced by `docker buildx build`.

After pushing an image with Jib, re-wrap and push the same reference with BuildKit so attestations are attached to the manifest index:
Replace "VERSION" with the actual pom version.
```
mvn compile jib:build
scripts/push-image-with-attestations.sh docker.io/snomedinternational/snowstorm-x:VERSION
```

Use the same registry path and tag you passed to Jib (`docker.registry`, `docker.image.prefix`, and `docker.image.tag` in `pom.xml`). Override architectures if needed, for example:

```
PLATFORMS=linux/amd64 scripts/push-image-with-attestations.sh my-registry/snowstorm-x:tag
```

You must push to a registry (not only `docker load`); BuildKit stores attestations on the registry manifest. See Docker’s [attestations](https://docs.docker.com/build/metadata/attestations/) and [Scout remediation](https://docs.docker.com/scout/policy/remediation/#supply-chain-attestations-remediation) for details.

## Docker Scout: up-to-date and approved base images

Docker Scout’s **Up-to-Date Base Images** and **Approved Base Images** policies only get reliable data when [SLSA provenance](https://docs.docker.com/build/metadata/attestations/slsa-provenance/) is present (see [Supply chain attestations](#supply-chain-attestations-docker-scout) above). Without provenance, Scout often reports **no data** for those checks. Use the same `scripts/push-image-with-attestations.sh` step after `jib:build` so provenance is attached.

**Up-to-date base images:** Scout compares the base layers in your image with the current digest for the tag you built from. Rebuild and push when you want to pick up updates to `amazoncorretto:17`. If you ever pin the base to a digest in `pom.xml`, bump that digest when refreshing the JDK base.

**Approved base images:** This is configured in the **Docker Scout** org (or team) [policy settings](https://docs.docker.com/scout/policy/#approved-base-images), not in this repository. Add an allowed pattern that matches this project’s base, for example `docker.io/library/amazoncorretto:*` or a broader `docker.io/library/*`, depending on your organisation’s rules. Options such as “only supported tags” for Docker Official Images apply to how strictly Scout evaluates official images.
