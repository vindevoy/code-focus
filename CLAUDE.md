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

### One-time machine setup

`resources/set-java-home.fish` is a small fish script that writes `set -gx JAVA_HOME …` into `~/.config/fish/config.fish` so the inline `JAVA_HOME=…` prefix can disappear once the config is loaded. Run it once per machine:

```sh
./resources/set-java-home.fish
```

By default it points at `$HOME/.local/share/JetBrains/Toolbox/apps/pycharm/jbr`. Pass a different path as the first argument if PyCharm lives elsewhere on a given machine. The script is idempotent — re-runs are no-ops if `JAVA_HOME` is already in the fish config.

### Running Gradle without tripping the simple-expansion prompt

Claude Code also flags assignment values that contain a `$VAR` expansion (the "Contains simple expansion" prompt). The pattern that kept triggering it was the chained `export … && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew …` prefix — `$JAVA_HOME` and `$PATH` are simple expansions in assignment values.

The Gradle wrapper only needs `JAVA_HOME`; it does not need `/jbr/bin` on `PATH`. Use a **single-command var prefix** with an absolute path, no chained exports:

```sh
JAVA_HOME=/home/vindevoy/.local/share/JetBrains/Toolbox/apps/pycharm/jbr ./gradlew ktlintCheck test
```

That command has no `$VAR` and is pre-approved in `.claude/settings.json` via the `Bash(JAVA_HOME=* ./gradlew *)` pattern, so it runs without prompting. Reserve `export … && export PATH=…` only for interactive sessions where it matters.


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

### Python style for fixtures and any .py files in this repo

These rules apply to every Python file checked into the repo (today: the `resources/python/test.py` fixture, but more may follow). They sit on top of PEP 8, never against it.

**Run `ruff format` on every `.py` file before committing.** Ruff is the authoritative formatter — wherever a hand-style rule below conflicts with what `ruff format` produces, ruff wins and the rule is wrong. If `ruff` isn't on PATH, run it via `uv tool run ruff format <file>`.

**Blank lines between statement categories.** Python statements fall into three categories:

- **Sequence** — assignments, expression statements, `return`, `raise`, `pass`, `import`.
- **Iteration** — `for`, `while`.
- **Selection** — `if` / `elif` / `else`, `match`, `try` / `except` / `finally`.

When two adjacent statements are in different categories, **insert exactly one blank line between them**. Two statements in the same category sit directly next to each other with no blank line.

**Returns at the end of a function.** When a function ends with a `return` (or `raise`) and there is real code above it, **insert one blank line before that final return**. This visually separates the result from the work that produced it.

**Early-exit returns.** A `return` (or `raise`) used as a guard at the top of a function — before the main body — gets **no blank line before it**. The guard sticks to the condition that triggered it. Example:

```python
def main() -> int:
    if not sys.argv:
        return 1   # early exit — no blank above

    path = Path(sys.argv[0]).resolve()
    print(path)

    return 0   # final return — blank above
```

**Comments and the code they belong to.**

- A comment that describes the next statement **sticks to that statement** — zero blank lines between the comment and the code below it.
- For comments **inside** a function body, never put 2 blank lines after them — one blank line is the maximum, and only when the comment is genuinely orphan.
- For comments **at module top level**, ruff will keep 2 blank lines around top-level `def` / `class`, including after an orphan comment sitting between two definitions. Don't fight ruff here — the orphan comment is just a section marker and the PEP-8 separator goes both before and after it.


## Git Workflow

### Remote and issue tracking

- The repository is hosted on **GitLab** at https://gitlab.com/asynchrone/kotlin/code-focus and kept in sync with origin
- Issues are tracked on the same GitLab project and used as the primary communication channel for feedback and remarks

### Document everything on the issue

**Every meaningful action Claude takes on an issue must be reflected on that issue.** The CLI transcript is ephemeral — the GitLab issue is the durable trace and the only place the user (or a future Claude session) can review what happened, in what order, and why.

This applies to all of:

- creating or rebasing the branch (mention conflicts, what was kept, what was dropped);
- each commit's intent and the files it touches (one note can cover several related commits, but the per-commit subjects should still be listed so they can be cross-referenced with `git log`);
- any architectural decision or rule discovered along the way (the same explanation Claude would otherwise give on the CLI);
- any caveat the reviewer needs to know before testing (manual recovery steps, fixture changes, behaviour differences).

A reasonable rhythm is one consolidated note after each batch of related commits, plus a final "ready for review" note. Don't wait until the work is finished to start writing — by then, important context has already been lost from the CLI.

Notes go on the issue itself (top-level note or threaded reply inside an existing discussion when the work resolves a specific remark), **never on the merge request**, so feedback and traceability stay in one place. Every note carries the attribution line described in [GitLab note attribution](#gitlab-note-attribution).

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
9. **Wait for the user to give the explicit order to create the merge request.** Claude does **not** create the MR autonomously, even when the work looks ready and even when phrasings like "continue", "next issue", or "you know the drill" are used — none of those grant MR-creation authority. When the user says "mr" / "make MR" / "open the MR" or equivalent, Claude opens the MR with `glab api projects/<id>/merge_requests -X POST -F "description=@/tmp/file.md" … -f remove_source_branch=true` against `develop` (or `main` for hotfixes), and never approves or merges it
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

### Merge commit format

The GitLab project's `merge_commit_template` is set so the merge commit subject reuses the MR title verbatim:

```
%{title}

Merge branch '%{source_branch}' into '%{target_branch}'

See merge request %{reference}
```

Because every MR title follows the `#<issue-number> - <Message>` convention, the resulting merge commit subject does too — keeping `git log --oneline` consistent with the rest of the repo and with the `commit-msg` hook regex (even though the hook only runs on local commits, not on GitLab-side merges).

The "Merge branch '...' into '...'" detail moves to the body. GitLab cannot produce a subject of the form `#<num> - Merge branch '...'` because no placeholder yields just `#<num>` — the closest workaround would force a "Closes" prefix from `%{issues}`, which would break the hook format. Hand-editing at merge time is no longer needed.

To inspect or change the template:
```sh
# read
glab api projects/asynchrone%2Fkotlin%2Fcode-focus | python3 -c "import json,sys; print(json.load(sys.stdin)['merge_commit_template'])"

# write (template body in /tmp/template.txt)
glab api projects/asynchrone%2Fkotlin%2Fcode-focus -X PUT -F "merge_commit_template=@/tmp/template.txt"
```


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
