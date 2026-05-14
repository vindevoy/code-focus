"""Fixture for Show Blank Lines.

Layout chosen so every blank line has a known classification:
  - decorative blank between top-level constants → fold
  - PEP 8 separator before / after top-level def → keep
  - PEP 8 separator after the last import → keep
  - decorative blank inside a function body → fold
"""

import os
import sys


# PEP 8 separator above (lines 11-12 are the two blank lines after the imports).
MAX = 1

# Decorative blank between top-level constants (line 16) → must fold.
DEFAULT = "x"


def fetch():
    """Docstring."""
    a = 1

    # Decorative blank above (line 23) is in a function body → must fold.
    b = 2

    return a + b


def main():
    """Entry."""
    return 0
