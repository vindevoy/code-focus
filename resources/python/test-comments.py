"""Module docstring on a single line — exercises Show Comments folding the docstring."""

# Top-of-file standalone comment.
# Second consecutive standalone comment (grouped pair).

import os  # inline comment after an import


def fetch():
    """Single-line function docstring."""
    # Standalone comment inside a function body.
    x = 1  # inline comment on an assignment

    return x


def explain():
    """Multi-line docstring.

    Second paragraph after a blank line — exercises a multi-line docstring fold.
    """
    # First of three grouped comments inside a function.
    # Second grouped comment.
    # Third grouped comment, closes the trio.
    return None
