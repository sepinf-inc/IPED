import base64
from typing import Optional

from ..case_manager import case_manager


def _format_props(props: dict) -> str:
    lines = []
    for k, v in props.items():
        if k == "metadata":
            if v:
                lines.append("\n--- Forensic Metadata ---")
                for mk, mv in v.items():
                    lines.append(f"  {mk}: {mv}")
        elif v is not None and v != "" and v != 0:
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



async def read(source_id: int, doc_id: int) -> str:
    """Read both the metadata and text content of a document by its source ID and document ID.

    Args:
        source_id: Source ID (use 0 for single-case)
        doc_id: Document ID within the source
    """
    try:
        props = case_manager.get_item(doc_id, source_id)
        props_str = _format_props(props)
    except Exception as e:
        props_str = f"Error getting metadata: {e}"

    try:
        text = case_manager.get_item_text(doc_id, source_id)
        text_str = text if text else "[No text content extracted]"
    except Exception as e:
        text_str = f"Error getting text content: {e}"

    return f"--- METADATA ---\n{props_str}\n\n--- CONTENT ---\n{text_str}"
