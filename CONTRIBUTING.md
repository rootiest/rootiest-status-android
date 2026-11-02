# Contributing to rootiest-status-android

## Workflow

1. Branch off `main` before the first edit (`feat/...`, `fix/...`, `docs/...`, `chore/...`).
   Never commit to or push `main`.
2. Commit continuously with Conventional Commits subjects: `type(scope): description`.
3. Keep unrelated fixes on their own branch and commit.
4. Run `./tests/run-tests.sh` before pushing. The pre-push hook (`.githooks/pre-push`)
   is enabled with `git config core.hooksPath .githooks`.
5. Push the branch and open a pull request. The maintainer merges; do not merge your own PR.

## Labels

Every pull request and issue gets exactly one `Kind/*` label (Bug, Feature, Enhancement,
Documentation, Testing, Refactor, Chore, Performance, Security) and at least one `Area/*`
label for each area it touches. Apply them when you create it.

Closed issues carry their close reason as one `Reviewed/*` label: `Reviewed/Fixed` when a
merged pull request resolved it, otherwise `Reviewed/Won't Fix`, `Reviewed/Duplicate` or
`Reviewed/Invalid`. Maintainers apply these.

## Commit messages and pull requests

- Describe what changed and why; link the issue it closes.

## Documentation

Docs under `docs/` are the single source of truth. Update them in the same pull request
as the change. Generated files are never edited by hand; edit the source and regenerate.

## License

By contributing you agree your work is released under the GPL-3.0-or-later license.
