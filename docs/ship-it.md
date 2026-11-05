# Shipping this project

Instructions for `/ship-it` and its steps. Each `##` section is read by the step of the same
name; anything left out uses that step's defaults.

## ship-it
- Follow the PR lifecycle in `CONTRIBUTING.md`. `ship-it` does not label: add the `Kind/*` and
  `Area/*` labels as soon as `push-it` opens the PR, and `Reviewed/Fixed` to each closed issue
  after `merge-it` merges.
- A change reaches phones in two PRs: the change itself, then a version bump. `deploy-it` opens
  the bump and publishes the release.

## doc-it
- `docs/README.md` is the documentation root, with `README.md` for installing and building.
- The release process is under `docs/README.md`, Releases; CI under `docs/README.md`, CI.

## push-it
- Branch `<type>/<topic>`, with the commit type: `feat/`, `fix/`, `docs/`, `chore/`. A version
  bump is `chore/release-<version>`.
- Commit scopes in use: the screen or feature (`status`, `support`).
- Before pushing, run `./tests/run-tests.sh`. It needs JDK 21 and the Android SDK, and includes
  screenshot tests against committed goldens. The pre-push hook (`.githooks/pre-push`) refuses a
  push to `main` and, when commit signing is on, unsigned commits.
- PR body: the template's `## Summary` (ending with `Closes #N` when it resolves an issue) and
  its `## Verification` checkbox list, checked only for what was actually verified.
- Labels at creation: exactly one `Kind/*` and at least one `Area/*` for each area touched:
  `Area/App`, `Area/Build`, `Area/CI`, `Area/Docs`, `Area/Tests`.

## verify-it
- CI does not run on Gitea and reports nothing on the Gitea PR. It runs on the GitHub mirror
  (`rootiest/rootiest-status-android`) for every branch the push mirror copies there, usually
  within a minute of the push. Find the run by commit:
  `gh run list -R rootiest/rootiest-status-android --commit <sha>`, then `gh run watch <id>`.
- One workflow, `CI`: tests, then the debug APKs. It takes about three minutes.
- On a failure, screenshot diffs are in the run's `screenshot-diffs` artifact.

## merge-it
- Merge method: merge commit.
- Always ask before merging: the maintainer merges every PR. An approval covers the head commit
  CI checked; a new push needs a new approval.
- After the merge, label each issue the PR closed `Reviewed/Fixed`.

## sync-it
- No automated commits follow a merge.

## deploy-it
Deploying means publishing a signed release on Gitea, which phones pick up through Obtainium
(`docs/README.md`, Releases). Always ask before releasing.

**Nothing to deploy** when nothing in `app/`, `core/`, `wear/` or the Gradle build changed since
the last release tag.

1. **Version bump**, when the merged PR was not one. Ask whether to release now: several changes
   can share one release. If yes, bump `appVersion` in `build.gradle.kts` from the latest
   `v*` tag: minor for any `feat`, otherwise patch. Ship the bump as its own PR through
   `push-it`, `verify-it`, `merge-it` and `sync-it`: branch `chore/release-<version>`, commit
   and title `chore: version <version>`, labels `Kind/Chore` and `Area/Build`.
2. **Dry run**, on a clean `main` that matches `origin/main`: `scripts/release.sh --dry-run`.
   Show the version and the release notes it prints.
3. **Release:** `scripts/release.sh`. It runs the tests, builds both APKs, refuses any not
   signed with the release key (which only this machine holds, through `rootiest.signing` in
   `~/.gradle/gradle.properties`), and creates the Gitea release `v<version>`.
4. **Verify:** the release page it prints lists `rootiest-status-<version>.apk` and
   `wear-rootiest-status-<version>.apk`.

**Rollback:** none. A phone cannot install an older version over a newer one, so fix a bad
release with a new patch release. Never delete a published release unless the user asks.
