"""Trivial fixture for Show Line Numbers.

The toggle flips `editor.settings.isLineNumbersShown` — file content doesn't
affect the assertion. This file exists so the test loads its own fixture in
exactly the same shape as the other Show* PSI tests.
"""

x = 1
y = 2
z = x + y
