# Preface 

The Provenance repository is built on the work of many open source projects including
the [Cosmos SDK]](https://github.com/cosmos/cosmos-sdk).  The source code outside of the `/x/`
folder is largely based on a reference implementation of this SDK.  The work inside the modules
folder represents the evolution of blockchain applications started at Figure Technologies in 2018.

This project would not be possible without the dedication and support of the [Cosmos](https://cosmos.network) community.

# Contributing

- [Preface](#preface)
- [Contributing](#contributing)
  - [Architecture Decision Records (ADR)](#architecture-decision-records-adr)
  - [Pull Requests](#pull-requests)
    - [Process for reviewing PRs](#process-for-reviewing-prs)
    - [Updating Documentation](#updating-documentation)
  - [Forking](#forking)
  - [Dependencies](#dependencies)
  - [Branching Model and Release](#branching-model-and-release)
    - [PR Targeting](#pr-targeting)
    - [Development Procedure](#development-procedure)
    - [Pull Merge Procedure](#pull-merge-procedure)
    - [Release Procedure](#release-procedure)
    - [Point Release Procedure](#point-release-procedure)

Thank you for considering making contributions to the Provenance!

Contributing to this repo can mean many things such as participated in
discussion or proposing code changes. To ensure a smooth workflow for all
contributors, the general procedure for contributing has been established:

1. Either [open](https://github.com/provenance-io/explorer-service/issues/new/choose) or
   [find](https://github.com/provenance-io/explorer-service/issues) an issue you'd like to help with
2. Participate in thoughtful discussion on that issue
3. If you would like to contribute:
   1. If the issue is a proposal, ensure that the proposal has been accepted
   2. Ensure that nobody else has already begun working on this issue. If they have,
      make sure to contact them to collaborate
   3. If nobody has been assigned for the issue and you would like to work on it,
      make a comment on the issue to inform the community of your intentions
      to begin work
   4. Follow standard Github best practices: fork the repo, branch from the
      HEAD of `main`, make some commits, and submit a PR to `main`
   5. Be sure to submit the PR in `Draft` mode submit your PR early, even if
      it's incomplete as this indicates to the community you're working on
      something and allows them to provide comments early in the development process
   6. When the code is complete it can be marked `Ready for Review`
   7. Be sure to include a relevant change log entry in the `Unreleased` section
      of `CHANGELOG.md` (see file for log format)

Note that for very small or blatantly obvious problems (such as typos) it is
not required to an open issue to submit a PR, but be aware that for more complex
problems/features, if a PR is opened before an adequate design discussion has
taken place in a github issue, that PR runs a high likelihood of being rejected.

Take a peek at our [coding repo](https://github.com/tendermint/coding) for
overall information on repository workflow and standards. Note, we use `make tools` for installing the linting tools.

Other notes:

- Looking for a good place to start contributing? How about checking out some
  [good first issues](https://github.com/provenance-io/explorer-service/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)

## Architecture Decision Records (ADR)

When proposing an architecture decision for the SDK, please create an [ADR](./docs/architecture/README.md)
so further discussions can be made. We are following this process so all involved parties are in
agreement before any party begins coding the proposed implementation. If you would like to see some examples
of how these are written refer to [Tendermint ADRs](https://github.com/tendermint/tendermint/tree/master/docs/architecture)

## Pull Requests

To accommodate review process we suggest that PRs are categorically broken up.
Ideally each PR addresses only a single issue. Additionally, as much as possible
code refactoring and cleanup should be submitted as a separate PRs from bugfixes/feature-additions.

### Process for reviewing PRs

All PRs require two Reviews before merge (except docs changes, or variable name-changes which only require one). When reviewing PRs please use the following review explanations:

- `LGTM` without an explicit approval means that the changes look good, but you haven't pulled down the code, run tests locally and thoroughly reviewed it.
- `Approval` through the GH UI means that you understand the code, documentation/spec is updated in the right places, you have pulled down and tested the code locally. In addition:
  - You must also think through anything which ought to be included but is not
  - You must think through whether any added code could be partially combined (DRYed) with existing code
  - You must think through any potential security issues or incentive-compatibility flaws introduced by the changes
  - Naming must be consistent with conventions and the rest of the codebase
  - Code must live in a reasonable location, considering dependency structures (e.g. not importing testing modules in production code, or including example code modules in production code).
  - if you approve of the PR, you are responsible for fixing any of the issues mentioned here and more
- If you sat down with the PR submitter and did a pairing review please note that in the `Approval`, or your PR comments.
- If you are only making "surface level" reviews, submit any notes as `Comments` without adding a review.

### Updating Documentation

If you open a PR on Provenance Explorer Service, it is mandatory to update the relevant documentation in /docs.


## Branching Model and Release

User-facing repos should adhere to the trunk based development branching model: https://trunkbaseddevelopment.com/.

Libraries need not follow the model strictly, but would be wise to.

The SDK utilizes [semantic versioning](https://semver.org/).

### PR Targeting

Ensure that you base and target your PR on the `main` branch.

All feature additions should be targeted against `main`. Bug fixes for an outstanding release candidate
should be targeted against the release candidate branch. Release candidate branches themselves should be the
only pull requests targeted directly against main.

### Development Procedure

- the latest state of development is on `main`

### Pull Merge Procedure

- ensure pull branch is rebased on `main`
- merge pull request

### Release Procedure

If new commits are API breaking, a new major version will be released. Everything else can be considered the next
minor version to be released.

- All new commits are merged into `main`
- The `CHANGELOG` will be updated as needed

To create a new release:

- Start on `main`
- Create the release version branch `release/vx.x.x` (aka `release`)
- On the `release` branch, prepare a new version section in the `CHANGELOG.md`
  - At the top of latest changes add `## [vx.x.x](https://github.com/provenance-io/explorer-service/releases/tag/vx.x.x) - YYYY-MM-DD`
  - Beneath that add `### Release Name: xxxxxx` and choose a unique name from [this list](https://en.wikipedia.org/wiki/List_of_explorers)
  - All links must be link-ified: `python ./scripts/linkify.py CHANGELOG.md`
  - Copy the latest release entries into a `RELEASE_CHANGELOG.md`, this is needed so the bot knows which entries to add to the release page on github.
  - Commit changes to the `release` branch
  - Push `release` branch up to github
- Tag the release (use `git tag -a vx.x.x -m "vx.x.x"`)
- Push the tag up (use `git push origin vx.x.x`)
  - The release will happen automatically in github
- Create a PR from branch `release` to `main` to incorporate ONLY the `CHANGELOG.md` updates
  - Do not push `RELEASE_CHANGELOG.md` to `main`
- Delete the `release` branch

### Hotfix Procedure

If a hotfix is needed against the current release, a hotfix branch will be created from the current version tag per the
follow procedure:

- Start from tag `vx.x.x`
- Create a branch `hotfix/vx.x.(x+1)` (aka `hotfix`)
- The hotfix commit should be PR'd against both `main` and `hotfix`
  - The `CHANGELOG` should be updated accordingly for both

It is the PR's author's responsibility to fix merge conflicts, update changelog entries, and
ensure CI passes. If a PR originates from an external contributor, it may be a core team member's
responsibility to perform this process instead of the original author.
Lastly, it is core team's responsibility to ensure that the PR meets all the Hotfix criteria.

When the hotfix is ready to be released:

- Start on `hotfix/vx.x.(x+1)` (aka `hotfix`)
- On the `hotfix` branch, prepare a new version section in the `CHANGELOG.md`
  - At the top of latest changes add `## [vx.x.x](https://github.com/provenance-io/explorer-service/releases/tag/vx.x.x) - YYYY-MM-DD`
  - All links must be link-ified: `$ python ./scripts/linkify_changelog.py CHANGELOG.md`
- Copy the latest release entries into a `RELEASE_CHANGELOG.md`
- Tag the release (use `git tag -a vx.x.x -m "vx.x.x"`)
- Push the tag up (use `git push origin vx.x.x`)
  - The release will happen automatically in github
- Create a PR into `main` containing ONLY `CHANGELOG.md` updates
  -Do not push `RELEASE_CHANGELOG.md` to `main`
- Delete the `hotfix` branch

