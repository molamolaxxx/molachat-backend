# Validation matrix

## Baseline rule

The root `pom.xml` sets `skipTests=true`. A test claim must use `-DskipTests=false`. Do not infer a fresh result from existing Surefire reports.

## Focused backend tests

Run a specific class first:

```bash
./mvnw -q -DskipTests=false -Dtest=ClassName test
```

Run several related classes:

```bash
./mvnw -q -DskipTests=false -Dtest='ClassOne,ClassTwo' test
```

Run the full MolaChat suite when risk and environment permit:

```bash
./mvnw -q -DskipTests=false test
```

Some older integration-style tests may require external state. If they fail, inspect the fresh report and distinguish environmental setup from product regression; do not hide or overwrite the result.

## Package and static checks

```bash
./mvnw -q -DskipTests package
git diff --check
node --check src/main/resources/static/js/path/to/changed.js
```

Packaging is a build check, not a test run. For inline or extracted JavaScript, syntax-check the actual executable script content where practical.

## Change-specific coverage

| Change | Minimum useful evidence |
| --- | --- |
| Controller/DTO | validation, auth/identity, success and failure response tests |
| Storage/factory | selected backend behavior and serialization/compatibility |
| WebSocket/session | action routing, stream lifecycle, reconnect/session switch |
| ACP callback | instance+group routing, duplicates, stale session, reason handling |
| Fast Team | focused Team tests plus async ordering, admission, compensation, lifecycle |
| Frontend state | JS syntax plus actual browser interaction and cleanup |
| Popup/layout | desktop, narrow and short viewport, scrolled page, internal vs outer scrolling |

## Fast Team dual-repository checks

When a change crosses MolaChat and cmd-proxy, validate both repositories with their own current commands. Known cmd-proxy Maven coverage commonly uses:

```bash
mvn -pl cmd-proxy-app test
```

Also package the relevant cmd-proxy modules and confirm expected normal and dependency-bundled jars if deployment needs them. Do not run this command from MolaChat or claim it ran when the companion repository is unavailable.

## Deployment and E2E

- Source files in this repository are authoritative for frontend assets.
- Sync source → Nginx/static deployment only when requested and authorized; verify each modified file with `cmp` or SHA-256 afterward.
- Restart/deploy only when explicitly in scope.
- Real Fast Team acceptance needs deployed processes and at least two actual placements/devices for cross-instance behavior.
- Verify old Team recovery, creation, message streaming, TalkTo, session rotation, offline/reconnect, deletion, and cleanup according to the changed surface.

## Completion report

Report exact commands, current pass/fail/error/skip counts, package/static results, and skipped checks. Keep unit, integration, browser, deployment, and production evidence in distinct categories.
