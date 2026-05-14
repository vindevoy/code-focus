#!/usr/bin/env python3
"""Create (or reuse) a GitHub Release on github.com/vindevoy/code-focus and
upload the plugin zip produced by `./gradlew buildPlugin` as a release asset.

Why this exists
---------------
GitLab → GitHub mirroring (set up under issue #53) pushes git tags but does not
create GitHub Release objects or upload assets. Without this script, users who
want to install the plugin from the GitHub mirror would have no downloadable
zip — they would have to clone and build. This script closes that gap.

Usage
-----
    GH_PAT=<github-pat> python3 resources/release/upload-github-release.py

Environment
-----------
    GH_PAT     required. Fine-grained PAT scoped to vindevoy/code-focus with
               Contents: Read and write. Same token used by the mirror.

The script reads the version from gradle.properties (pluginVersion=...) and
expects build/distributions/code-focus-<version>.zip to exist. Run
`./gradlew buildPlugin` first.

If a Release already exists for the tag, the script reuses it. If the asset is
already attached, the script is a no-op. Safe to re-run.
"""

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

OWNER = "vindevoy"
REPO = "code-focus"
PROJECT_ROOT = Path(__file__).resolve().parents[2]
GRADLE_PROPS = PROJECT_ROOT / "gradle.properties"
DIST_DIR = PROJECT_ROOT / "build" / "distributions"
RELEASE_NOTES = "Code Focus release. See the GitLab project for full release notes."


def read_plugin_version() -> str:
    text = GRADLE_PROPS.read_text()
    match = re.search(r"^pluginVersion\s*=\s*(\S+)", text, re.MULTILINE)

    if not match:
        sys.exit(f"could not parse pluginVersion from {GRADLE_PROPS}")

    return match.group(1)


def github_api(
    method: str, url: str, *, json_body=None, binary_body=None, content_type=None
):
    headers = {
        "Authorization": f"Bearer {os.environ['GH_PAT']}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "code-focus-release-script",
    }

    if json_body is not None:
        data = json.dumps(json_body).encode()
        headers["Content-Type"] = "application/json"
    elif binary_body is not None:
        data = binary_body
        headers["Content-Type"] = content_type or "application/octet-stream"
        headers["Content-Length"] = str(len(binary_body))
    else:
        data = None

    req = urllib.request.Request(url, data=data, headers=headers, method=method)

    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read().decode() or "{}"
            return resp.status, json.loads(body)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main() -> int:
    if "GH_PAT" not in os.environ:
        sys.exit("GH_PAT env var is required")

    version = read_plugin_version()
    tag = f"v{version}"
    zip_path = DIST_DIR / f"code-focus-{version}.zip"

    if not zip_path.exists():
        sys.exit(f"missing {zip_path} — run ./gradlew buildPlugin first")

    status, release = github_api(
        "GET", f"https://api.github.com/repos/{OWNER}/{REPO}/releases/tags/{tag}"
    )

    if status == 200:
        print(f"release exists: id={release['id']} -> {release['html_url']}")
    elif status == 404:
        status, release = github_api(
            "POST",
            f"https://api.github.com/repos/{OWNER}/{REPO}/releases",
            json_body={
                "tag_name": tag,
                "name": f"Code Focus {version}",
                "body": RELEASE_NOTES,
                "draft": False,
                "prerelease": False,
            },
        )

        if status not in (200, 201):
            sys.exit(f"create release failed: status={status} body={release}")

        print(f"release created: id={release['id']} -> {release['html_url']}")
    else:
        sys.exit(f"unexpected status fetching release: {status} body={release}")

    asset_name = zip_path.name
    existing = next(
        (a for a in release.get("assets", []) if a["name"] == asset_name), None
    )

    if existing:
        print(
            f"asset already uploaded: {existing['name']} -> {existing['browser_download_url']}"
        )
        return 0

    upload_url = release["upload_url"].split("{")[0] + f"?name={asset_name}"
    status, asset = github_api(
        "POST",
        upload_url,
        binary_body=zip_path.read_bytes(),
        content_type="application/zip",
    )

    if status not in (200, 201):
        sys.exit(f"upload failed: status={status} body={asset}")

    print(
        f"asset uploaded: {asset['name']} ({asset['size']} bytes) -> {asset['browser_download_url']}"
    )

    return 0


if __name__ == "__main__":
    sys.exit(main())
