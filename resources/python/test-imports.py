"""Fixture for Show Imports.

A top-level import group of four `import` and three `from … import …` lines, plus
one inline `from … import …` inside a function body.
"""

import json
import os
import sys

from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


def fetch():
    """Docstring."""
    from logging import getLogger

    return getLogger("X")
