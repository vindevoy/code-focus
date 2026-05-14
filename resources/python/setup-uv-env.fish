#!/usr/bin/env fish
# Create / refresh the project's uv-managed virtual environment.
#
# The env lives at <project-root>/.venv and is gitignored. PyCharm uses it as
# the Python interpreter for any .py file in the project so the Format button
# (issue #39) can find ruff and so the test.py fixture in resources/python
# resolves its imports against the same env the IDE shows.
#
# Re-run this script whenever resources/python/requirements.txt changes or a
# fresh checkout needs the env. Idempotent: uv venv is a no-op if .venv
# already exists and matches the requested interpreter; uv pip install --upgrade
# pulls newer versions when the constraint allows.

set script_dir (dirname (realpath (status filename)))
set project_root (realpath "$script_dir/../..")

cd "$project_root"; or exit 1

if not command -v uv >/dev/null
    echo "uv not found on PATH — install it from https://docs.astral.sh/uv/"
    exit 1
end

if test -d "$project_root/.venv"
    echo ".venv already exists at $project_root/.venv — reusing it"
else
    echo "Creating .venv at $project_root/.venv"
    uv venv .venv; or exit 1
end

echo "Installing requirements from resources/python/requirements.txt"
set -x VIRTUAL_ENV "$project_root/.venv"
uv pip install --upgrade -r resources/python/requirements.txt; or exit 1

echo
echo "Done. Point PyCharm at:"
echo "  $project_root/.venv/bin/python"
echo "via Settings → Project: code-focus → Python Interpreter → Add Interpreter →"
echo "    Add Local Interpreter → Existing → Python path."
