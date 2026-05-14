"""Fixture for Show Logging Lines.

Covers every shape the substring matcher (`logger`, `Logger`, `logging`) must hit:
  - `import logging` (logging substring)
  - `from logging import getLogger` (Logger and logging substrings)
  - top-level `logger = logging.getLogger(__name__)` assignment
  - inline `local_logger = getLogger("…")` assignment
  - `local_logger.debug(...)` method call
  - `self.logger.x(...)` style chained call inside a class method
  - `logger.warning(...)` deeply nested under `try / except / while`
"""

import logging

logger = logging.getLogger(__name__)


def fetch(url):
    """Docstring."""
    from logging import getLogger

    local_logger = getLogger("Test")
    local_logger.debug("hello")

    while True:
        try:
            return {}
        except ValueError as exc:
            logger.warning("retry %s: %s", url, exc)


class Service:
    """Doc."""

    def __init__(self):
        self.logger = logging.getLogger("svc")

    def run(self):
        """Doc."""
        self.logger.info("running")
