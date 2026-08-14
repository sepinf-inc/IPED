from ..case_manager import case_manager


async def list_bookmarks() -> list[str]:
    """List all bookmark names available in the currently open case."""
    return case_manager.list_bookmarks()


async def get_bookmark(name: str) -> str:
    """Get all documents in a bookmark by bookmark name.

    Returns a formatted list of source_id and doc_id pairs for each
    document in the bookmark.

    Args:
        name: The bookmark name to look up
    """
    items = case_manager.get_bookmark(name)
    if not items:
        return f"Bookmark '{name}' is empty."
    lines = [f"Bookmark: {name}", f"Total items: {len(items)}", ""]
    for item in items:
        lines.append(f"  source_id={item['source_id']}, doc_id={item['id']}")
    return "\n".join(lines)
