#!/usr/bin/env fish
#
# Sets JAVA_HOME in ~/.config/fish/config.fish so Gradle commands on this
# machine run without an inline `JAVA_HOME=…` prefix and without firing
# Claude Code's "Tilde in assignment" or "Contains simple expansion" prompts.
#
# Default JBR path: $HOME/.local/share/JetBrains/Toolbox/apps/pycharm/jbr
# Pass a different path as the first argument if PyCharm lives elsewhere.
#
# Usage:
#   ./resources/set-java-home.fish
#   ./resources/set-java-home.fish /opt/jdks/jbr-25
#
# Idempotent: if JAVA_HOME is already configured in config.fish, the script
# leaves the file alone and exits 0.

if test (count $argv) -ge 1
    set jbr $argv[1]
else
    set jbr $HOME/.local/share/JetBrains/Toolbox/apps/pycharm/jbr
end

if not test -x "$jbr/bin/java"
    echo "[set-java-home] No JBR found at $jbr." >&2
    echo "[set-java-home] Pass a different path as the first argument, e.g.:" >&2
    echo "[set-java-home]   $argv[0] /opt/jdks/jbr-25" >&2
    exit 1
end

set config $HOME/.config/fish/config.fish

if test -f $config; and grep -q 'set -gx JAVA_HOME' $config
    echo "[set-java-home] JAVA_HOME already configured in $config — leaving it alone."
    exit 0
end

mkdir -p (dirname $config)
echo "" >> $config
echo "# Code Focus: bundled JBR for Gradle (set by resources/set-java-home.fish)" >> $config
echo "set -gx JAVA_HOME $jbr" >> $config
echo 'fish_add_path $JAVA_HOME/bin' >> $config

echo "[set-java-home] JAVA_HOME=$jbr written to $config"
echo "[set-java-home] Open a new fish session, or run: source $config"
