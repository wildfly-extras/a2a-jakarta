# Release Process

This document describes the process for releasing a new version of A2A Jakarta to Maven Central.

## Overview

The release process involves:
1. Updating SNAPSHOT versions to the release version
2. Opening and merging a release PR
3. Tagging the release
4. Automatic deployment to Maven Central
5. Incrementing to the next SNAPSHOT version

## Prerequisites

### Required Accounts & Access
- GitHub repository write access to `wildfly-extras/a2a-jakarta`
- Maven Central account with access to namespace: `org.wildfly.a2a`

### Required Secrets (Repository Maintainers)
The following secrets must be configured in GitHub repository settings:
- `GPG_SIGNING_KEY`: Private GPG key for artifact signing (ASCII armored)
- `GPG_SIGNING_PASSPHRASE`: Passphrase for the GPG key
- `CENTRAL_TOKEN_USERNAME`: Maven Central username token
- `CENTRAL_TOKEN_PASSWORD`: Maven Central password token

## Release Steps

The examples below use `1.0.0.Final-SNAPSHOT` and `1.0.0.Final` for demonstration. Substitute with the actual versions for your release.

### 1. Prepare Release Version

Update all POM versions from SNAPSHOT to the release version:

```bash
mvn versions:set -DnewVersion=1.0.0.Final -DgenerateBackupPoms=false
```

Verify the build works:

```bash
mvn clean install -DskipTests
```

### 2. Create Release PR

```bash
git checkout -b release/1.0.0.Final
git add -A
git commit -m "chore: release 1.0.0.Final"
git push origin release/1.0.0.Final
```

Open PR on GitHub with title: `chore: release 1.0.0.Final`

Wait for CI to pass, then merge.

### 3. Tag and Push

After the PR is merged to main:

```bash
# Pull the merged changes
git fetch upstream
git checkout main
git rebase upstream/main

# Create annotated tag
git tag v1.0.0.Final

# Push the tag (triggers deployment)
git push upstream v1.0.0.Final
```

> **Note**: The remote name for `wildfly-extras/a2a-jakarta` may differ (e.g., `upstream`, `origin`). Adjust accordingly.

### 4. Automated Deployment

Pushing the tag triggers the `release-to-maven-central.yml` workflow which:
1. Detects tag (pattern: `v?[0-9]+.[0-9]+.[0-9]+*`)
2. Checks out the tagged commit
3. Builds with `-Pcentral-release -DskipTests`
4. Signs all artifacts with GPG
5. Deploys to Maven Central with auto-publish

### 5. Increment to Next SNAPSHOT

While the release is publishing, prepare the next development version:

```bash
mvn versions:set -DnewVersion=1.0.1.Final-SNAPSHOT -DgenerateBackupPoms=false
mvn clean install -DskipTests
```

```bash
git checkout -b chore/bump-to-1.0.1.Final-SNAPSHOT
git add -A
git commit -m "chore: bump version to 1.0.1.Final-SNAPSHOT"
git push origin chore/bump-to-1.0.1.Final-SNAPSHOT
```

Open PR, wait for CI, and merge.

### 6. Verify Deployment

Check that artifacts are available on Maven Central (this can take quite some time):

```
https://central.sonatype.com/artifact/org.wildfly.a2a/a2a-jakarta-parent/1.0.0.Final
```

### 7. Publish GitHub Release

After Maven Central deployment succeeds, create or update the corresponding GitHub release:

```
https://github.com/wildfly-extras/a2a-jakarta/releases
```

Use the tag name as the release name and auto-generate the release notes.

## Troubleshooting

### GPG signing fails in workflow

**Cause**: GPG secrets are missing or incorrect

**Fix**: Repository maintainers - verify secrets in:
```
Settings > Secrets and variables > Actions
```
Check: `GPG_SIGNING_KEY`, `GPG_SIGNING_PASSPHRASE`

### Maven Central deployment times out

**Cause**: Normal Maven Central processing delays

**Fix**: Wait (up to 2 hours). Check status:
```
https://central.sonatype.com/publishing
```

### Deployment fails with authentication error

**Cause**: Maven Central tokens expired or incorrect

**Fix**: Repository maintainers:
1. Log in to [central.sonatype.com](https://central.sonatype.com)
2. Generate new tokens: Account > Generate User Token
3. Update secrets: `CENTRAL_TOKEN_USERNAME` and `CENTRAL_TOKEN_PASSWORD`

### Need to rollback a release

**Not possible** - Maven Central does not allow artifact deletion.

**Mitigation**:
1. Release a patch version with fixes
2. Document issues in GitHub release notes

## Version Numbering

Follow semantic versioning with qualifiers:

- **Major.Minor.Patch.Final** - Standard releases (e.g., `1.0.0.Final`)
- **Major.Minor.Patch.AlphaN** - Alpha releases (e.g., `1.0.0.Alpha1`)
- **Major.Minor.Patch.BetaN** - Beta releases (e.g., `1.0.0.Beta1`)
- **Major.Minor.Patch.CRN** - Candidate releases (e.g., `1.0.0.CR1`)
- **-SNAPSHOT** - Development versions (e.g., `1.0.1.Final-SNAPSHOT`)

## Workflows Reference

### build-with-release-profile.yml (Tier 1 - Trigger)
- **Triggers**: All PRs, all pushes, manual dispatch
- **Purpose**: Build with `-Pcentral-release` profile without secrets
- **Catches**: Compilation, javadoc, plugin configuration issues

### build-with-release-profile-run.yml (Tier 2 - Secrets)
- **Triggers**: `workflow_run` on Tier 1 completion
- **Purpose**: Full release profile build with GPG signing and Maven Central credential validation
- **Access**: Allow-listed maintainers (`kkhan`, `jmesnil`, `ehsavoie`, `maeste`), pushes to `main`, manual dispatch
- **Requires**: GPG and Maven Central secrets

### release-to-maven-central.yml
- **Triggers**: Tags matching `v?[0-9]+.[0-9]+.[0-9]+*`
- **Purpose**: Deploy to Maven Central
- **Requires**: GPG and Maven Central secrets
