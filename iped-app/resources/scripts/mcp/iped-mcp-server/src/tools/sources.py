from ..case_manager import case_manager


async def list_sources() -> list[dict]:
    """List all open case sources with their IDs, paths, and item counts."""
    return case_manager.list_sources()
