# CODE FOCUS - A PyCharm plugin


## Author

- Name:               Yves Vindevogel
- GitLab handle:      vindevoy
- E-mail:             yves.vindevogel@asynchrone.com


## Purpose

<!-- Plugin description -->
Code Focus hides the comments and logging lines from Python code (`.py` files) in PyCharm (Community or Professional), allowing developers to focus on the code itself without the clutter of those extra, and very useful, lines.
<!-- Plugin description end -->


## Compatibility

- **IDE**: PyCharm Community or Professional
- **Supported versions**: 2025.1 through 2026.1 (build range `251` — `261.*`)
- **Language scope**: Python (`.py`) only


## Installation

Until the plugin is published on the JetBrains Marketplace, install it manually from a locally built zip:

1. Run `./gradlew buildPlugin` — the distributable is produced in `build/distributions/`.
2. In PyCharm: `Settings` → `Plugins` → gear icon → `Install Plugin from Disk…` → pick the zip.
3. Restart the IDE.


## Build & run

Standard Gradle commands from the JetBrains plugin template:

- `./gradlew build` — compile, run tests, and package
- `./gradlew runIde` — launch a sandboxed PyCharm instance with the plugin loaded
- `./gradlew test` — run all tests
- `./gradlew buildPlugin` — produce the distributable zip in `build/distributions/`
- `./gradlew verifyPlugin` — verify plugin structure and compatibility

Development uses **JDK 21** and **Kotlin 2.1.20**.


## Architecture

This plugin is written in Kotlin, as this is the preferred language for JetBrains plugins. The editor used for this, is IntelliJ Professional.


## AI

This plugin is completely written with the assistance of Claude CLI.


## Feedback

Issues and feature requests live on GitLab: https://gitlab.com/asynchrone/kotlin/code-focus/-/issues


## License

Released under the [MIT License](LICENSE).
