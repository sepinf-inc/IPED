import base64
from typing import Optional

from ..case_manager import case_manager


def _format_props(props: dict) -> str:
    lines = []
    for k, v in props.items():
        if v is not None and v != "" and v != 0:
            lines.append(f"{k}: {v}")
    return "\n".join(lines)


async def get_document(source_id: int, doc_id: int) -> str:
    """Get metadata/properties of a document by its source ID and document ID.

    Returns formatted metadata including name, path, extension, size, hash,
    timestamps, and bookmark labels.

    Args:
        source_id: Source ID (use 0 for single-case)
        doc_id: Document ID within the source
    """
    props = case_manager.get_item(doc_id, source_id)
    return _format_props(props)


async def get_document_content(source_id: int, doc_id: int) -> Optional[str]:
    """Get the raw binary content of a document as base64.

    Args:
        source_id: Source ID (use 0 for single-case)
        doc_id: Document ID within the source
    """
    data = case_manager.get_item_content(doc_id, source_id)
    if data is None:
        return None
    return base64.b64encode(data).decode("utf-8")


async def get_document_text(source_id: int, doc_id: int) -> Optional[str]:
    """Get the extracted/parsed text content of a document.

    Args:
        source_id: Source ID (use 0 for single-case)
        doc_id: Document ID within the source
    """
    return case_manager.get_item_text(doc_id, source_id)


async def get_document_thumbnail(source_id: int, doc_id: int) -> Optional[str]:
    """Get the thumbnail image of a document as base64 JPEG.

    Args:
        source_id: Source ID (use 0 for single-case)
        doc_id: Document ID within the source
    """
    data = case_manager.get_item_thumbnail(doc_id, source_id)
    if data is None:
        return None
    return base64.b64encode(data).decode("utf-8")
