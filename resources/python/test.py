"""Sample module exercising every comment / blank-line shape the Code Focus toggles must handle.

Used as a fixture for the "Show Comments" and "Show Blank Lines" pills in PyCharm.
"""

# Top-of-file standalone comment, after the module docstring and before imports.
# A second consecutive standalone comment so PyCharm's grouped-comment fold has a chance to kick in.

import json  # inline comment after a stdlib import
import os
import sys

from collections import defaultdict, deque
from dataclasses import dataclass
from pathlib import Path  # inline comment on a from-import


# Standalone comment between imports and the first top-level constant.
MAX_RETRIES = 3  # inline comment on a constant assignment
DEFAULT_NAME = "code-focus"


# Standalone comment between two top-level constants.
# Followed by a second one to form a 2-line group.
TIMEOUT_SECONDS = 30


def fetch(url: str, retries: int = MAX_RETRIES) -> dict:
    """Fetch a URL and return the decoded JSON payload.

    Multi-line docstring with a blank line in the middle so the docstring
    fold spans more than one line. Used to verify that hiding comments also
    hides multi-line docstrings.
    """
    # Standalone comment inside a function body, before any code.
    attempt = 0  # inline comment on the first statement
    last_error: Exception | None = None

    while attempt < retries:
        # Standalone comment inside a while loop.
        attempt += 1
        try:
            # Standalone comment immediately before a return.
            return json.loads(os.environ.get("FAKE_RESPONSE", "{}"))
        except ValueError as exc:  # inline comment on an except clause
            last_error = exc

            # Two consecutive standalone comments deep inside a function,
            # to verify that grouped-comment folding works at depth.
            print(f"retry {attempt}: {exc}")

    raise RuntimeError(f"giving up after {retries} attempts") from last_error


@dataclass
class Recording:
    """Minimal dataclass with a docstring and decorated methods."""

    name: str
    samples: list[int]

    # Class-level standalone comment between the fields and the first method.
    def duration(self) -> int:
        """Return the number of samples — single-line docstring."""
        return len(self.samples)  # inline comment on a return

    @staticmethod
    def empty() -> "Recording":
        # Standalone comment immediately under a decorator + def line.
        return Recording(name=DEFAULT_NAME, samples=[])

    def summary(self) -> str:
        """Build a human-readable summary."""
        # First standalone comment in a 3-line group, to exercise PyCharm's
        # grouped-comment fold (it usually only kicks in for 3+).
        # Second comment in the group — describes the buffer below.
        # Third comment in the group — closes the trio.
        buffer = deque(self.samples, maxlen=10)

        counts: defaultdict[int, int] = defaultdict(int)
        for sample in buffer:  # inline comment on a for-loop header
            counts[sample] += 1

        return f"{self.name}: {len(buffer)} samples, {len(counts)} distinct"


# Standalone comment that sits between two top-level definitions.


def main() -> int:
    """Entry point used when the module is run as a script."""
    path = Path(sys.argv[0]).resolve()
    print(path)  # inline comment on the only print
    return 0


# Trailing standalone comment at the very bottom of the file.
if __name__ == "__main__":
    sys.exit(main())
