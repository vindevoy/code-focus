# CLAUDE.md — Code Focus Plugin

## Working with Claude

- **Autonomous execution**: the user trusts Claude to run commands on their local machine without asking for permission first. Claude may execute Gradle tasks, shell commands, git operations, `glab` calls, `sudo apt` installs, and any other local tooling required to complete a task. Do not prompt the user for permission before a tool call.
- **Ask questions about the project, not about permissions**: if Claude is uncertain, ask about project direction, design trade-offs, or intent — not about whether it is allowed to execute something.

### Claude Code permissions

The project's allow list lives in **`.claude/settings.json`** and is committed to the repo so it travels across machines. It pre-approves the commands Claude needs to drive this project — Gradle tasks, `git`, `glab`, `python3 …`, common read-only shell utilities, the IDE-bundled JBRs, and reads under the project, `~/.gradle`, `~/.claude`, `~/.config`, and `~/.local/share/JetBrains`.

If Claude needs a new command shape that isn't covered:

1. Prefer the **broadest sensible prefix** (e.g. `Bash(python3 *)`, not one entry per script). This is the whole point of committing the file — a narrow allow that has to be re-typed on every variation defeats the goal.
2. Add the entry to `.claude/settings.json` (committed, shared across machines), **not** `.claude/settings.local.json` (per-machine, gitignored). Only use the local file for genuinely machine-specific paths or sensitive entries.
3. Avoid broad allows for destructive verbs that this project doesn't need (`rm -rf`, `sudo *`, network fetchers like `curl */wget *`). Keep those prompted.

`.gitignore` is set up so only `.claude/settings.json` is tracked; all other files under `.claude/` (including `settings.local.json` and any runtime state) stay local.

### Avoid `~` in Bash assignments

Claude Code flags `~` on the right-hand side of a bash assignment with a "Tilde in assignment value — bash may expand at assignment time" prompt, which interrupts every invocation. Use **absolute paths** (e.g. `/home/vindevoy/.local/share/JetBrains/Toolbox/apps/pycharm/jbr`) or `$HOME/...` instead of `~/...` when writing bash commands — both for `export VAR=...` and for direct binary invocations. The allow patterns in `.claude/settings.json` are already written with absolute paths to match.

Better still: set `JAVA_HOME` (and similar) persistently in `~/.profile` so it never needs to be exported inline. The fish shell side is already covered in `~/.config/fish/config.fish`, but bash sub-shells used by tooling don't inherit fish vars.


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

### Branch protection

Both `main` and `develop` are **protected** on GitLab:

- Only **maintainers** can push to or merge into them. Developer-level accounts cannot push directly, so every change lands through a merge request.
- **Force pushes** are disabled.
- The branches **cannot be deleted**, so GitLab does not offer to delete them when a merge request is merged.

Release, feature, bugfix, and hotfix branches are **not** protected — they are short-lived and owned by whoever is working on them.

### Workflow for each issue

1. Receive the issue number from the user on the CLI
2. Checkout `develop` (or `main` for hotfixes), fetch and pull from origin
3. Create the appropriate branch based on issue type and title
4. Push the new branch to origin immediately
5. Implement the changes, committing as you go
6. Tests run automatically before each commit (pre-commit hook) and full suite before each push (pre-push hook)
7. Push commits to origin
8. Notify the user on the CLI **and** post a status note on the GitLab issue that the work is ready for review. **All status updates and progress notes go on the issue, not on a merge request** — keep the conversation in one place so feedback does not scatter
9. **Wait for the user to give the explicit order to create the merge request.** Claude does **not** create the MR autonomously, even when the work is ready. When the user says so, Claude opens the MR with `glab mr create` against `develop` (or `main` for hotfixes) — and never approves or merges it
10. The user reviews and tests — feedback goes into the GitLab issue
11. Once accepted, the user approves and merges into `develop` (or `main` and `develop` for hotfixes)
12. The user closes the issue manually on GitLab
13. After confirming the MR is merged, **and before starting the next issue**, clean up the local branch in the same response:
    ```sh
    git checkout develop && git pull --ff-only origin develop \
      && git branch -d <merged-branch> \
      && git fetch --prune
    ```
    Use `git branch -d` (lowercase) so it refuses to delete an unmerged branch — that is the safety we want. If multiple merged branches piled up across rounds, run `git branch --merged develop | grep -v develop` to find them and delete them in one batch. Don't carry stale local branches into the next round

### Review process

Once a merge request is opened, the user reviews the code and leaves **one comment per remark on the GitLab issue** (not the MR). Claude handles these comments in order:

1. Read the comments one by one, from oldest to newest
2. For each comment, take the needed action on the working branch:
   - If the action produces a code change, commit it (one commit per comment is the default — combine only when the changes are trivially related)
   - If no code change is needed, explain why in the follow-up note
3. After handling a comment, post the follow-up note as a **reply inside the same GitLab discussion thread** as the original comment, not as a new top-level note. Threading both entries under one discussion means the user resolves a single thread per remark instead of two. The follow-up carries the remarks Claude would normally write on the CLI (what was done, what was decided, what was noticed), so the trace lives next to the feedback
4. In that follow-up note, state whether Claude believes the remark is resolved — but **never mark the comment resolved**. Marking resolved is the user's privilege
5. Move on to the next comment

Once all comments are handled, push the new commits and notify the user on the CLI.


### Creating MRs and posting GitLab notes

For anything that takes a multi-line markdown body — opening a merge request, posting a note on an issue or discussion thread — **write the body to a temp file with the `Write` tool, then upload it via `glab api` with `-F field=@/tmp/file.md`**. Do **not** use `--description "$(cat /tmp/file.md)"` style command substitution: Claude Code's bash parser fails with "Unhandled node type: string" on multi-line `$()` substitutions, which interrupts the call.

Recipes:

- **Create an MR**: always pass `remove_source_branch=true` so GitLab proposes deleting the source branch when the MR is merged. Feature/bugfix/hotfix branches are short-lived; leaving them around clutters the remote.
  ```sh
  glab api projects/asynchrone%2Fkotlin%2Fcode-focus/merge_requests -X POST \
    -F "description=@/tmp/mr-body.md" \
    -f source_branch=<branch> \
    -f target_branch=develop \
    -f title="<title>" \
    -f remove_source_branch=true
  ```
- **Post a note on an issue**:
  ```sh
  glab api projects/asynchrone%2Fkotlin%2Fcode-focus/issues/<n>/notes -X POST \
    -F "body=@/tmp/note.md"
  ```
- **Post a threaded reply in a discussion**:
  ```sh
  glab api projects/asynchrone%2Fkotlin%2Fcode-focus/issues/<n>/discussions/<full-discussion-id>/notes -X POST \
    -F "body=@/tmp/reply.md"
  ```

The full discussion ID is the SHA-style string returned by the discussions endpoint, not a truncated prefix.

For one-line notes that don't need markdown formatting, the inline `-F "body=…"` form is fine and doesn't trip the parser.

### GitLab note attribution

Notes that Claude posts on GitLab from the user's machine are authored under the user's GitLab username, because Claude posts via the user's personal access token. To keep authorship unambiguous, every note Claude posts **must begin with an attribution line** on its own, followed by a blank line and then the body:

> _Authored by Claude — running on @vindevoy's local machine._

This applies to trace replies in discussion threads and any other note Claude creates on the project (comments on issues, merge requests, anywhere).


### Handling comments in a loop

When the user asks Claude to "loop" on an issue's comments (or invokes `/loop`), Claude polls the issue autonomously until every discussion is resolved, instead of waiting for an explicit CLI "continue" between each round. One iteration does:

1. Fetch all discussions on the issue
2. For any open discussion with a top-level comment that has no trace reply from Claude yet, handle it per the Review process above — make changes, commit, push, and post a threaded trace reply with the attribution line
3. For any discussion where the user has added a new reply since Claude's last note, re-engage: read the reply, take the requested action, commit and push if needed, post a further threaded reply
4. Sleep approximately 1 minute between iterations so the user has time to work on or reply to comments without Claude racing them
5. Exit the loop only when every discussion is resolved **and** the previous iteration produced no new activity — a full idle cycle confirms nothing is in flight

Until the loop exits, Claude does not need the user to type "continue" on the CLI; it polls and reacts on its own.


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
