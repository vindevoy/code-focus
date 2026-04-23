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

### Review process

Once a merge request is opened, the user reviews the code and leaves **one comment per remark on the GitLab issue** (not the MR). Claude handles these comments in order:

1. Read the comments one by one, from oldest to newest
2. For each comment, take the needed action on the working branch:
   - If the action produces a code change, commit it (one commit per comment is the default — combine only when the changes are trivially related)
   - If no code change is needed, explain why in the follow-up note
3. After handling a comment, add a follow-up note on the issue that references the original comment. The note carries the remarks Claude would normally write on the CLI (what was done, what was decided, what was noticed), so the trace lives next to the feedback
4. In that follow-up note, state whether Claude believes the remark is resolved — but **never mark the comment resolved**. Marking resolved is the user's privilege
5. Move on to the next comment

Once all comments are handled, push the new commits and notify the user on the CLI.


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

### Python module dependency

The plugin operates on Python code, so `plugin.xml` declares a dependency on `com.intellij.modules.python`. Without this dependency the plugin cannot be loaded into PyCharm.

### Build-time IDE target vs runtime compatibility

The plugin targets **both PyCharm Community and Professional at runtime**, but at **build time** it compiles against PyCharm Community only. The distinction:

- **Build-time dependency**: `build.gradle.kts` uses `pycharmCommunity(providers.gradleProperty("platformVersion"))`. Community is chosen because:
  - it is free and open, so CI and any contributor can fetch it without a license;
  - it ships the `com.intellij.modules.python` module the plugin needs;
  - Professional is a superset of Community, so a plugin compiled against Community's platform APIs runs unchanged on Professional.
- **Runtime compatibility**: the distributable plugin is compatible with **both** PyCharm Community and PyCharm Professional. The supported range is defined by `sinceBuild` / `untilBuild` in `gradle.properties` (`251` — `261.*`) together with the `com.intellij.modules.python` dependency declared in `plugin.xml`.

This is why the CLI and Gradle output reference "PyCharm Community" while the user-facing compatibility statement says "Community or Professional" — both are accurate, they describe different phases.
