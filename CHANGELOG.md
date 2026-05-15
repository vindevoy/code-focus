# Code Focus Changelog

## [Unreleased]

## [1.0.1]

### Fixed

- Fold regions created by the toggles (Show Comments, Show Logging Lines, Show Imports) now absorb the blank line(s) immediately above and below the folded range, so collapsed `>` placeholders no longer leave orphaned blank lines around them. ([#52](https://gitlab.com/asynchrone/kotlin/code-focus/-/issues/52))
- Toggle and button labels now use `JBFont.label()` instead of `JBFont.small()`, matching the editor tab font and the **Fix** button. ([#57](https://gitlab.com/asynchrone/kotlin/code-focus/-/issues/57))
- Toggle pill background and slider knob radius bumped from `JBUI.scale(2)` to `JBUI.scale(6)`, matching the buttons for a softer, less amateur look. ([#59](https://gitlab.com/asynchrone/kotlin/code-focus/-/issues/59))
- Removed leftover diagnostic `LOG.warn(...)` calls from `ShowLoggingLinesToggle` that flooded `idea.log` with `[CodeFocus] applyToEditor: ...` lines on every editor open / toggle flip. ([#60](https://gitlab.com/asynchrone/kotlin/code-focus/-/issues/60))

## [1.0.0]

### Added

- First user-installable release. See the [v1.0.0 GitLab release page](https://gitlab.com/asynchrone/kotlin/code-focus/-/releases/v1.0.0) for the full feature list (5 toggles + Re-Apply / Format / Fix buttons + the **Settings → Tools → Code Focus** screen).
- Project scaffold based on the official JetBrains IntelliJ Platform Plugin Template, targeting PyCharm Community 2025.1.
