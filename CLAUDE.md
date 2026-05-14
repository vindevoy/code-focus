# CLAUDE.md — Code Focus Plugin

## Working with Claude

- **Autonomous execution**: the user trusts Claude to run commands on their local machine without asking for permission first. Claude may execute Gradle tasks, shell commands, git operations, `glab` calls, `sudo apt` installs, and any other local tooling required to complete a task. Do not prompt the user for permission before a tool call.
- **Ask questions about the project, not about permissions**: if Claude is uncertain, ask about project direction, design trade-offs, or intent — not about whether it is allowed to execute something.
- **No "want me to proceed?" — just proceed.** Do not end a turn with "want me to commit?", "should I push?", "ready to MR?", "shall I run the tests?". Drive the task to completion: fix → build → test → commit → push, then summarise what landed. The exceptions are the durable workflow gates already documented elsewhere in this file: do NOT open a merge request until the user adds the `Merge` label or types `mr`, and do NOT switch branches in live mode without an explicit instruction. Everything else is on Claude.
- **Ship working code, not "you can run this if you want" code.** Always run `./gradlew ktlintCheck test` (and any other verification the change calls for) before declaring done. If the build fails, fix it; if a test fails, fix it. Do not hand the user code that hasn't been built and tested.

### Claude Code permissions

The project's allow list is split between two files:

- **`.claude/settings.json`** — committed, project-wide. Contains entries that apply to **anyone working on this project**: verb-only prefixes for tools the project uses (`git *`, `glab *`, `python3 *`, `uv *`, `./gradlew *`, etc.), read-only shell utilities, env probes, and `/tmp` scratch ops. No path under `/home/<someone>` belongs here — it would only work on that one person's machine.
- **`.claude/settings.local.json`** — gitignored, per-person. Holds anything that is genuinely machine-specific: absolute `Read` / `Edit` paths under the user's home, the user's IDE Toolbox JBR binary location, and any individual experimentation. Never put a project-wide rule here, or other contributors won't have it.

If Claude needs a new command shape that isn't covered:

1. Prefer the **broadest sensible prefix** (`Bash(git *)`, not `Bash(git status*)` + `Bash(git diff*)` + …). The whole point of committing the project list is that we don't re-type a new entry every time the verb gets a different sub-command. Verb-level allows are the default; only narrow when the verb genuinely contains an unsafe sub-command we want to keep prompting (see "Security risks" below).
2. Decide the right file: project-wide → `settings.json`, machine-specific → `settings.local.json`. When in doubt, project-wide unless the entry literally references a path under `/home/<user>`.
3. JSON has no comments — keep both files lean and readable, and document the **why** behind any non-obvious entry in this CLAUDE.md section instead of in the JSON itself.

**Security risks — do NOT broadly allow.** Each of these would let Claude do something destructive or smuggle in untrusted code; they stay prompted on every invocation, which is the design.

| Pattern                                  | Why it's risky                                                                                              |
|------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `Bash(rm *)` or `Bash(rm -rf *)`         | Deletes anything. Allowed only inside `/tmp` (`Bash(rm /tmp/*)`).                                            |
| `Bash(sudo *)`                           | System-wide modification with elevated privileges. Always confirm.                                          |
| `Bash(curl *)` / `Bash(wget *)`          | Network fetchers — can pull untrusted code or exfiltrate secrets (e.g. tokens). Use `glab api` for GitLab. |
| `Bash(bash -c *)` / `Bash(sh -c *)`      | Arbitrary code through a quoted command — defeats prefix matching entirely.                                 |
| `Bash(eval *)` / `Bash(exec *)`          | Same problem as `bash -c`.                                                                                  |
| Any pattern with `*` as the **whole** value | Universal allow — never useful, always dangerous.                                                       |

Note that even with the broad allows below (`git *`, `uv *`, etc.), Claude Code's **safety hooks** still surface a separate prompt for things like `git push --force` to a protected branch, `git reset --hard`, simple-expansion arguments, etc. Allowing the verb does not bypass the safety check on a destructive flag — that is intentional layering.

**`.gitignore` is set up so only `.claude/settings.json` is tracked**; all other files under `.claude/` (including `settings.local.json` and any runtime state) stay local.

**Editing `.claude/settings.json` itself prompts by design.** The dialog reads "Yes / Yes, and allow Claude to edit its own settings for this session / No". Even with `Edit(//.../code-focus/**)` and `Edit(//.../code-focus/.claude/**)` in the allow list, Claude Code surfaces a separate self-config-edit prompt to keep allowlist mutations explicit. There is no `.claude/settings.json` entry that suppresses it (by design — the prompt protects against silent self-elevation). When you know you'll be doing several allowlist additions in one session, pick option 2 ("Yes, and allow Claude to edit its own settings for this session") on the first prompt; subsequent edits within that session land without further prompts.

### Avoid `~` in Bash assignments

Claude Code flags `~` on the right-hand side of a bash assignment with a "Tilde in assignment value — bash may expand at assignment time" prompt, which interrupts every invocation. Use **absolute paths** (e.g. `/home/vindevoy/.local/share/JetBrains/Toolbox/apps/pycharm/jbr`) or `$HOME/...` instead of `~/...` when writing bash commands — both for `export VAR=...` and for direct binary invocations. The allow patterns in `.claude/settings.json` are already written with absolute paths to match.

Better still: set `JAVA_HOME` (and similar) persistently in `~/.profile` so it never needs to be exported inline. The fish shell side is already covered in `~/.config/fish/config.fish`, but bash sub-shells used by tooling don't inherit fish vars.

### One-time machine setup

`resources/set-java-home.fish` is a small fish script that writes `set -gx JAVA_HOME …` into `~/.config/fish/config.fish` so the inline `JAVA_HOME=…` prefix can disappear once the config is loaded. Run it once per machine:

```sh
./resources/set-java-home.fish
```

By default it points at `$HOME/.local/share/JetBrains/Toolbox/apps/pycharm/jbr`. Pass a different path as the first argument if PyCharm lives elsewhere on a given machine. The script is idempotent — re-runs are no-ops if `JAVA_HOME` is already in the fish config.

### Python interpreter for PyCharm: `.venv` via `uv`

The plugin is Kotlin, but the test fixtures in `resources/python/` are Python — and the **Format** button (issue #39) shells out to `ruff` against the file in the active editor. To exercise either, PyCharm needs a Python interpreter that has `ruff` on it, and the simplest way is a uv-managed virtual env at the project root:

```sh
./resources/python/setup-uv-env.fish
```

The script creates `.venv` at the project root (gitignored), installs the packages pinned in `resources/python/requirements.txt` (just `ruff` today; add more there as needed), and prints the path to plug into PyCharm. Re-runs are idempotent — existing `.venv` is reused, requirements are upgraded if newer versions match the constraint.

Once the env is up, point PyCharm at `<project>/.venv/bin/python` via **Settings → Project: code-focus → Python Interpreter → Add Interpreter → Add Local Interpreter → Existing → Python path**. After that, the Format button on a `.py` editor finds `ruff` automatically.

### Running Gradle without tripping the simple-expansion prompt

Claude Code also flags assignment values that contain a `$VAR` expansion (the "Contains simple expansion" prompt). The pattern that kept triggering it was the chained `export … && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew …` prefix — `$JAVA_HOME` and `$PATH` are simple expansions in assignment values.

The Gradle wrapper only needs `JAVA_HOME`; it does not need `/jbr/bin` on `PATH`. Use a **single-command var prefix** with an absolute path, no chained exports:

```sh
JAVA_HOME=/home/vindevoy/.local/share/JetBrains/Toolbox/apps/pycharm/jbr ./gradlew ktlintCheck test
```

That command has no `$VAR` and is pre-approved in `.claude/settings.json` via the `Bash(JAVA_HOME=* ./gradlew *)` pattern, so it runs without prompting. Reserve `export … && export PATH=…` only for interactive sessions where it matters.

The same "simple expansion" prompt fires on **any** bash argument containing `$VAR` — not just gradle invocations. The most common case is `echo "JAVA_HOME=$JAVA_HOME"` or similar status-checking lines. Don't write those: use `printenv VAR` (or `env | grep VAR`) instead — both read the variable directly without quoted expansion in an argument, neither trips the prompt, and `printenv*` / `env *` are pre-approved. Same for any other env probe — `printenv` first, never `echo "$VAR"`.

The same prompt also fires when a `for` loop interpolates the loop variable into command arguments, e.g. `for f in a b c; do curl … "$f.png"; done`. Even though `$f` is a shell variable rather than an env var, Claude Code flags it as a "Contains expansion" risk. For short batches (a handful of items) **expand the loop into N straight-line commands** — no loop, no `$f`, no prompt:

```sh
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/uploads/<sha-1>/image.png > /tmp/34.png
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/uploads/<sha-2>/image.png > /tmp/35.png
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/uploads/<sha-3>/image.png > /tmp/36.png
```

Verbose, but each call is a single pre-approved `glab` invocation. Reserve real for-loops for cases where the iteration count is unknown at write time and you genuinely need the dynamism — and even then, do it inside a script file you call with `python3 /tmp/script.py`, not inline `bash -c`.

### No multi-line `python3 -c "…"` — use a heredoc or a temp file

Claude Code flags any quoted bash argument that contains a `\n#` sequence with the "Newline followed by `#` inside a quoted argument can hide arguments from path validation" prompt. The pattern that triggered the issue was a multi-line `python3 -c "..."` whose script body included a Python `# comment` line — the `# ` inside the quoted argument is what matched the rule, even though the `#` is meant as a Python comment, not a bash one.

There is no permission entry that bypasses this safety check. The fix is to never write multi-line `python3 -c "..."` in the first place. Use a heredoc:

```sh
python3 << 'EOF'
import json, sys
# this comment is fine — it's heredoc content, not a quoted argument
data = json.load(sys.stdin)
print(data["title"])
EOF
```

Or stage the script in `/tmp` and run it as a file:

```sh
# Write the script via the Write tool to /tmp/script.py, then:
python3 /tmp/script.py
```

Both forms are pre-approved (`Bash(python3 *)`) and don't trip the safety check. Reserve inline `python3 -c "..."` for genuine one-liners with no `#` characters and no newlines.

### One verb per Bash call — never chain different verbs

The allow patterns in `.claude/settings.json` are matched against the **whole** bash command line, not against each piped/chained piece. So a compound like:

```sh
git rebase --abort && git branch foo origin/foo && git status -uno
```

does not match `Bash(git rebase*)` *or* `Bash(git status*)` cleanly — Claude Code surfaces a fresh permission prompt even though every individual verb is in the allow list. Same trap with `;` separators and pipes when the segments are different verbs.

The rule: **one logical command per Bash tool call**. If you need to run several things in sequence, issue them as separate Bash calls. The runtime is fast enough that the overhead is negligible, and each call lands cleanly against the matching allow pattern with zero prompts.

The one exception is multiple invocations of the **same verb** chained together, where the compound still matches the verb's prefix pattern — e.g. `git fetch && git checkout develop && git pull --ff-only origin develop` matches `Bash(git *)` and is fine. Mixing verbs (e.g. `git rebase --abort && git status`) is what trips the prompt.

### Editing files: use the Edit tool, never `sed -i`

Claude Code's safety hook flags `sed -i` (and similar in-place editors) with "sed command contains operations that require explicit approval (e.g., write commands, execute commands)" because the `-i` flag silently rewrites files on disk — same risk class as raw write. There's no permission entry that bypasses this; in-place file rewrite is by design something the user has to confirm.

The fix is **don't use `sed -i` at all**. The Edit tool is the right hammer for changing file content — it's deterministic, shows up in the conversation as a diff, and Claude Code's standard Edit allow (project tree) covers it without prompting. For multi-file changes, repeat the Edit call rather than reach for `sed`.

`sed` without `-i` (read-only stream editing — e.g. `sed -n '5,10p' file` to print lines, or piping into something else) is fine and stays prompted only by general Bash policy.

### Python tooling: `uv tool`, never pip / pipx / sudo apt

Whenever you need a Python developer tool (today: `ruff`; tomorrow probably `mypy`, `pytest`, etc.), reach for **`uv`** — `uv` is on PATH at `/home/vindevoy/.local/bin/uv` and `uv tool *` / `uvx *` are pre-approved in `.claude/settings.json`. The two recipes you actually need:

```sh
# one-shot run, no install side effects
uv tool run ruff format <file>      # equivalent: uvx ruff format <file>

# persistent install of a tool, kept in uv's tool-env
uv tool install ruff
```

Do **not** reach for `pip3 install --user`, `pipx install`, or `sudo apt-get install` to satisfy a missing tool. All three trip permission prompts, two of them touch system-wide state, and none of them are needed when `uv` is available. The tool-existence check before invoking is fine via `command -v <tool>` (also pre-approved); skip the install attempt entirely when the tool is already present.

To install something into the **project venv** (e.g. adding a tool that the test fixture needs), do **not** prefix with `VIRTUAL_ENV=$PWD/.venv ...`. The `$PWD` there is a `$VAR` expansion in an argument and trips the "Contains simple expansion" prompt. Pass the interpreter explicitly instead:

```sh
uv pip install --python .venv/bin/python <package>
```

`uv` resolves the venv from the interpreter path; no env-var prefix needed, no expansion, no prompt. This is what `resources/python/setup-uv-env.fish` already does — re-running that script is the one-shot way to refresh the project venv from `requirements.txt`.


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

### GitHub mirror

A read-only public mirror of this project lives at **https://github.com/vindevoy/code-focus**. GitLab pushes to it automatically — there is no manual `git push` to GitHub from a developer machine, and contributors should never commit on the GitHub side (anything they push there will be force-overwritten on the next mirror cycle).

The mirror is configured in **GitLab → Settings → Repository → Mirroring repositories** as a *push* mirror with `only_protected_branches = true`. Because `main` and `develop` are the only protected branches on GitLab, the mirror sends exactly:

- `refs/heads/main`
- `refs/heads/develop`
- every tag (e.g. `v1.0.0`) — tags are mirrored unconditionally

Feature/bugfix/hotfix branches are **not** mirrored; they stay on GitLab.

Authentication uses a fine-grained GitHub Personal Access Token scoped to the `vindevoy/code-focus` repo with `Contents: Read and write` (and the mandatory `Metadata: Read-only`). The token is embedded in the mirror URL and stored encrypted by GitLab — `glab api projects/.../remote_mirrors` returns it masked as `https://*****:*****@github.com/...`.

To rotate the token:

```sh
# Replace mirror id 4047800 if it changes; new URL embeds the new token.
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/remote_mirrors/4047800 -X PUT \
  -f url='https://vindevoy:<new-pat>@github.com/vindevoy/code-focus.git'

# Force a fresh sync after rotation:
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/remote_mirrors/4047800/sync -X POST
```

To inspect mirror state (status, last sync, last error):

```sh
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/remote_mirrors/4047800
```

### Publishing the plugin zip to GitHub Releases

The mirror pushes git tags (e.g. `v1.0.0`) to GitHub but **does not** create GitHub Release objects or upload the `code-focus-<version>.zip` artifact. Without an attached asset, anyone who lands on the GitHub Releases page has no downloadable plugin — they would have to clone and build. To close that gap, run the helper script after each new tag is mirrored:

```sh
./gradlew buildPlugin
GH_PAT=<github-pat> python3 resources/release/upload-github-release.py
```

The script reads `pluginVersion` from `gradle.properties`, expects `build/distributions/code-focus-<version>.zip` to exist, then either creates the GitHub Release for tag `v<version>` or reuses the existing one, and uploads the zip as a release asset. It is **idempotent** — re-runs are no-ops if the release and asset already exist, so it is safe to wire into a release ritual.

The PAT is the same fine-grained token used by the GitLab mirror (`Contents: Read and write` on `vindevoy/code-focus`). Do not commit it; pass it inline as `GH_PAT=...` for the single invocation.

The release body the script writes is intentionally minimal ("Code Focus release. See the GitLab project for full release notes."), because the authoritative release notes live on the GitLab Release page (which has the rich markdown produced from the MR description). If a richer GitHub-side body is wanted for a specific release, edit it directly on github.com after the upload — the script will not overwrite it on subsequent runs.

### Document everything on the issue

**Every meaningful action Claude takes on an issue must be reflected on that issue.** The CLI transcript is ephemeral — the GitLab issue is the durable trace and the only place the user (or a future Claude session) can review what happened, in what order, and why.

This applies to all of:

- creating or rebasing the branch (mention conflicts, what was kept, what was dropped);
- each commit's intent and the files it touches (one note can cover several related commits, but the per-commit subjects should still be listed so they can be cross-referenced with `git log`);
- any architectural decision or rule discovered along the way (the same explanation Claude would otherwise give on the CLI);
- any caveat the reviewer needs to know before testing (manual recovery steps, fixture changes, behaviour differences).

A reasonable rhythm is one consolidated note after each batch of related commits, plus a final "ready for review" note. Don't wait until the work is finished to start writing — by then, important context has already been lost from the CLI.

Notes go on the issue itself (top-level note or threaded reply inside an existing discussion when the work resolves a specific remark), **never on the merge request**, so feedback and traceability stay in one place. Every note carries the attribution line described in [GitLab note attribution](#gitlab-note-attribution).

### Issue state labels

The project uses four GitLab labels as a state machine between the user and Claude. They are the unambiguous, glanceable signal of "whose turn is it" — preferred over scrolling the CLI for the most recent message. The labels exist on the project already; never invent new variants.

- **`Waiting`** — Claude is waiting for the user. Claude **adds** this whenever it has just posted a status note that needs a human reply (work ready for review, follow-up on a remark, anything that ends with "your turn"). The user **removes** it when they respond with a new comment, mark a discussion resolved, or otherwise re-engage. Until the label is gone, Claude should not assume new feedback exists — re-poll, but don't push speculative changes.
- **`Merge`** — The user is signalling that no remarks are open and Claude should now create the merge request. Claude treats this label as equivalent to the verbal "mr" / "make MR" order (either path is valid). On detecting `Merge`, Claude opens the MR and then **removes `Merge`** in the same step.
- **`Merge Request`** — Claude **adds** this immediately after creating the MR. The user takes it from there: review, approve, merge. **Claude never performs the merge itself** — only creates the MR.
- **`On Hold`** — The user has parked the issue. **Claude must not work on it** until the label is removed: no new branch, no commits to an existing branch, no MR. When picking the next issue from the queue (especially in AFK mode), skip any issue carrying `On Hold`. If an issue Claude is mid-flight on gets `On Hold` added, finish the current commit + push if already in progress, post a status note acknowledging the hold, and stop — the branch stays where it is for later resumption.

Setting / removing a label via glab:

```sh
# Add a label
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/issues/<n> -X PUT -f "add_labels=Waiting"

# Remove a label
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/issues/<n> -X PUT -f "remove_labels=Merge"

# Swap one for another in a single call (used after creating the MR):
glab api projects/asynchrone%2Fkotlin%2Fcode-focus/issues/<n> -X PUT -f "remove_labels=Merge" -f "add_labels=Merge Request"
```

The whole-life sequence on a typical issue:

1. User opens issue → no state label.
2. Claude implements + posts status note → adds `Waiting`.
3. User reviews, leaves remark → user removes `Waiting`.
4. Claude addresses remark → re-adds `Waiting`. Repeat 3–4 until accepted.
5. User adds `Merge` → Claude opens MR, removes `Merge`, adds `Merge Request`.
6. User merges → user closes the issue.

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

### Switching branches: always rebase, clean up after merges

Two rules whenever Claude moves between branches:

1. **Switching to an existing feature/bugfix/hotfix branch always starts with `git rebase develop`**. The target branch may have been opened days ago and develop has typically moved since (other MRs landed). Working on a stale base hides conflicts until MR time and silently re-introduces issues that were already fixed on develop. Pull develop first, then `git checkout <branch> && git rebase develop`. If the rebase rewrites the original commit subject (a known mangle when the rebase hits a conflict), restore it via `git commit --amend -m "…"` before pushing. Force-push with `--force-with-lease`.
2. **Switching away because an issue was just closed/merged means the local branch is dead — delete it before starting the next one.** Run the standard cleanup before checking out the next branch:

   ```sh
   git checkout develop
   git pull --ff-only origin develop
   git branch -d <merged-branch>
   git fetch --prune
   ```

   `git branch -d` (lowercase) refuses to delete an unmerged branch — that's the safety we want. If multiple merged branches piled up across rounds, run `git branch --merged develop | grep -v develop` to find them and delete them in one batch.

The two rules compose for the common path "merged X, now start on Y": cleanup after X, then `git checkout Y && git rebase develop` even if Y already exists.

### Milestone scope: only work on the active milestone

The project ships in milestones. **Claude works only on issues assigned to the currently active (open, not closed) milestone.** Future-milestone issues — even when they look small or self-contained — are out of scope until the active milestone closes.

When picking work from the queue (especially in AFK mode):

1. List the active milestones (`glab api projects/.../milestones`); usually exactly one is `state=active`.
2. Filter open issues to those whose `milestone.title` matches the active one.
3. Skip everything else.

If no milestone is active (rare — between releases), do nothing autonomously and ask before starting work.

### Working modes: live vs AFK

The user has two explicit modes, and Claude's freedom to act differs between them.

- **Live mode** (default). The user is at the keyboard, possibly running `./gradlew runIde` against whichever branch is currently checked out. Claude **must not switch branches** without an explicit instruction — flipping the working tree out from under a `runIde` session swaps the code the user is actively testing. Claude also waits for commands rather than running ahead through the issue queue: do the requested step, post the note, and wait. Phrasings that put Claude in live mode include "I'm back", "I'm testing X", "I'm working", or just resuming after a quiet stretch.

- **AFK mode**. The user has explicitly announced they're leaving — "I'm off for a couple of hours", "going to bed", "AFK", or equivalent. While AFK, Claude is free to: switch branches at will (still per the rebase + cleanup rules above), pick the next open issue from the queue without waiting for a per-issue command, and chain feature / bugfix work end-to-end. The queue is the **active-milestone** open issues (see [Milestone scope](#milestone-scope-only-work-on-the-active-milestone)) **minus anything carrying `On Hold` or already in `Waiting`**. When the queue empties (every active-milestone issue is `Waiting`, `On Hold`, or otherwise blocked on the user), stop and sleep — re-poll occasionally but don't spin work for its own sake.

The mode flips back to live the moment the user sends any message after returning. Until then, treat unknown state as live (the safer default).

### Workflow for each issue

1. Receive the issue number from the user on the CLI
2. Checkout `develop` (or `main` for hotfixes), fetch and pull from origin
3. Create the appropriate branch based on issue type and title
4. Push the new branch to origin immediately
5. Implement the changes, committing as you go
6. Tests run automatically before each commit (pre-commit hook) and full suite before each push (pre-push hook)
7. Push commits to origin
8. Notify the user on the CLI **and** post a status note on the GitLab issue that the work is ready for review. **All status updates and progress notes go on the issue, not on a merge request** — keep the conversation in one place so feedback does not scatter. **Then add the `Waiting` label to the issue** so the user can see at a glance which issues are blocked on them — see [Issue state labels](#issue-state-labels)
9. **Wait for the user to add the `Merge` label** before opening the merge request. Verbal phrasings like "mr" / "make MR" still work as an explicit instruction (the label and the CLI order are interchangeable), but **do not infer MR authority from "continue", "next issue", "you know the drill" etc.** When `Merge` is on the issue, Claude opens the MR with `glab api projects/<id>/merge_requests -X POST -F "description=@/tmp/file.md" … -f remove_source_branch=true` against `develop` (or `main` for hotfixes), then **adds the `Merge Request` label and removes `Merge`** so the user sees the MR is ready for them. **Claude never approves or merges the MR — only creates it**
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
- **Download an upload (e.g. an image attached to an issue)**:
  ```sh
  glab api projects/asynchrone%2Fkotlin%2Fcode-focus/uploads/<sha>/image.png > /tmp/<n>.png
  ```
  The `<sha>` is the path segment shown in the markdown image link of the issue (`/uploads/<sha>/image.png`). `glab api` is already authenticated via `~/.config/glab-cli/config.yml`, so the request goes through without exposing the token.

  **Never** download GitLab uploads by piping `curl` together with the token from the glab config — patterns like `curl … -H "PRIVATE-TOKEN: $(grep -oP 'token: \S+' ~/.config/glab-cli/config.yml … | awk …)"` mix the token into a `$(...)` command substitution that Claude Code's bash parser flags ("This command requires approval"), and they leak the token into the bash trace. `glab api` is the only sanctioned path.

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
