# CLAUDE.md — Code Focus Plugin

## Project Overview

Code Focus is a PyCharm plugin that hides comments and logging lines from Python code (.py files), allowing developers to focus on the code itself without visual clutter.

- **Target audience**: Python developers using PyCharm (Community or Professional edition)
- **License**: MIT (open source)
- **Language scope**: Python only (for now)
- **Author**: Yves Vindevogel (yves.vindevogel@asynchrone.com)
- **Development**: Entirely built with the assistance of Claude CLI


## Tech Stack

- **Language**: Kotlin 2.1.20
- **Build tool**: Gradle (Kotlin DSL), Gradle 9.4.1
- **JDK**: JDK 21 (bundled via JetBrains Runtime)
- **Plugin framework**: IntelliJ Platform Gradle Plugin 2.14.0 (`org.jetbrains.intellij.platform`)
- **Compatibility**: PyCharm 2025.1 through 2026.1 (sinceBuild `251`, untilBuild `261.*`)
- **Template**: Official JetBrains IntelliJ Platform Plugin Template


## Project Structure

Standard JetBrains plugin template layout. No custom deviations.


## Build & Run

Standard Gradle commands from the JetBrains plugin template:

- `./gradlew build` — Build the plugin
- `./gradlew runIde` — Run a sandboxed PyCharm instance with the plugin loaded
- `./gradlew test` — Run all tests
- `./gradlew buildPlugin` — Package the plugin as a .zip for distribution
- `./gradlew verifyPlugin` — Verify plugin structure and compatibility


## Code Style & Conventions

Follow the official Kotlin coding conventions: https://kotlinlang.org/docs/coding-conventions.html

No custom overrides.


## Git Workflow

### Remote and issue tracking

- The repository is hosted on **GitLab** at https://gitlab.com/asynchrone/kotlin/code-focus and kept in sync with origin
- Issues are tracked on the same GitLab project and used as the primary communication channel for feedback and remarks

### Branching strategy

- **main**: Production-ready code. Merges come from release branches only
- **develop**: Integration branch. All feature/bugfix/hotfix branches merge here
- **release/x.y.z**: Created from develop when a version is ready. Merged into main by the author

Feature and bugfix branches are created from `develop`:

- `feature/<issue-number>-<title>` — for new features
- `bugfix/<issue-number>-<title>` — for bug fixes

Hotfix branches follow Gitflow conventions:

- `hotfix/<issue-number>-<title>` — branched from `main` for urgent production fixes, merged back into both `main` and `develop`

Branch names use **lowercase, numbers, and dashes only**.

### Workflow for each issue

1. Receive the issue number from the user on the CLI
2. Checkout `develop` (or `main` for hotfixes), fetch and pull from origin
3. Create the appropriate branch based on issue type and title
4. Push the new branch to origin immediately
5. Implement the changes, committing as you go
6. Tests run automatically before each commit (pre-commit hook) and full suite before each push (pre-push hook)
7. Push commits to origin
8. Notify the user on the CLI that the work is ready for review
9. Claude creates a merge request on GitLab — Claude never approves or merges it
10. The user reviews and tests — feedback goes into the GitLab issue
11. Once accepted, the user approves and merges into `develop` (or `main` and `develop` for hotfixes)
12. The user closes the issue manually on GitLab
13. After confirming the MR is merged, delete the local working branch and run `git fetch --prune` to clean up the remote-tracking reference

### Commit message format

Format: `#<issue-number> - <Message>`

- The issue number must match the GitLab issue being worked on
- The message must start with a capital letter
- This format is enforced by the `hooks/commit-msg` hook

Examples:
- `#2 - Add comment folding provider`
- `#15 - Fix regex pattern for multiline comments`


## Testing

### Framework

- **JUnit 5** for unit and integration tests
- Tests follow the standard `src/test/kotlin/` directory structure

### Strategy

- Extensive test coverage is expected
- All public functionality must have corresponding tests
- Fast unit tests must pass before every commit (pre-commit hook in `hooks/pre-commit`)
- Full test suite must pass before every push (pre-push hook in `hooks/pre-push`)

### Git hooks

- **Pre-commit hook** (`hooks/pre-commit`): Runs fast unit tests before each commit. If any test fails, the commit is blocked.
- **Pre-push hook** (`hooks/pre-push`): Runs the full test suite before each push. If any test fails, the push is blocked.

As the test suite grows, the pre-commit hook should be kept fast (unit tests only). Integration and slower tests run in the pre-push hook.


## Common Tasks

### Debug the plugin

Run `./gradlew runIde` to launch a sandboxed PyCharm instance. Breakpoints and the debugger work from IntelliJ when running this task in debug mode.

### Package for distribution

Run `./gradlew buildPlugin` to produce a .zip file in `build/distributions/`. This file can be installed manually in PyCharm via Settings > Plugins > Install Plugin from Disk.

### Verify plugin compatibility

Run `./gradlew verifyPlugin` to check the plugin structure and compatibility with the declared PyCharm version range.


## Notes

- The plugin operates on Python code, so `plugin.xml` must declare a dependency on `com.intellij.modules.python`. The scaffold does not add this automatically — it must be configured when implementing the first feature.
- The plugin template's `build.gradle.kts` targets IntelliJ IDEA Community by default. This must be switched to PyCharm Community (e.g., `pycharmCommunity("2025.1")`) as part of the first code issue, since the plugin depends on Python-specific platform APIs.
