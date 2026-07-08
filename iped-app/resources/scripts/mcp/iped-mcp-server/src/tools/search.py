from typing import Optional

from ..case_manager import case_manager


async def search(query: str, source_id: Optional[int] = None) -> dict:
    """Search items using a Lucene query string.

    Supports IPED's Lucene query syntax, e.g.:
    - "name:*.pdf" - files with PDF extension
    - "category:image" - image category
    - "created:[2020-01-01 TO 2020-12-31]" - date range
    - "content:\"keyword\"" - full-text search
    - "*.*" - all items

    Args:
        query: Lucene query string
        source_id: Optional source ID to restrict search to a single source
    """
    return case_manager.search(query, source_id)


async def search_by_type(file_type: str, source_id: Optional[int] = None) -> dict:
    """Search items by file type extension (e.g., pdf, jpg, docx, xls).

    Args:
        file_type: File extension to search for (without dot)
        source_id: Optional source ID to restrict search
    """
    return case_manager.search(f"type:{file_type}", source_id)


async def search_by_name(name_pattern: str, source_id: Optional[int] = None) -> dict:
    """Search items by name pattern using wildcards.

    Args:
        name_pattern: Name pattern with wildcards, e.g. "*.pdf" or "*report*"
        source_id: Optional source ID to restrict search
    """
    return case_manager.search(f"name:{name_pattern}", source_id)
