import logging
import sys
import os

# --- NOVO BLOCO DE REDIRECIONAMENTO ---
# Salva o file descriptor original do stdout para o MCP usar
original_stdout_fd = os.dup(1)

# Redireciona o stdout nativo (FD 1) para o stderr (FD 2)
# Qualquer biblioteca C/C++ ou Java escreverá no stderr agora
os.dup2(2, 1)

# Restaura o sys.stdout do Python para apontar para o FD original salvo,
# garantindo que o FastMCP consiga enviar o JSON-RPC limpo.
sys.stdout = os.fdopen(original_stdout_fd, 'w', buffering=1)
# --------------------------------------

from fastmcp import FastMCP

from .config import settings
from .jvm_bridge import ensure_jvm
from .case_manager import case_manager
from .tools import sources, search, documents, bookmarks

logger = logging.getLogger(__name__)

mcp = FastMCP(
    "IPED MCP Server",
    instructions="Direct Java access to IPED Digital Forensic Tool via PyJnius",
)


def register_tools():
    mcp.tool(name="list_sources", description="List all open case sources with their IDs, paths, and item counts.")(sources.list_sources)

    mcp.tool(name="search", description="Search items using a Lucene query string. Supports IPED's Lucene query syntax.")(search.search)
    mcp.tool(name="search_by_type", description="Search items by file type extension (e.g., pdf, jpg, docx).")(search.search_by_type)
    mcp.tool(name="search_by_name", description="Search items by name pattern using wildcards.")(search.search_by_name)
    mcp.tool(name="get_searchable_fields", description="Get all searchable metadata field names in the open case and their data types (e.g. String, Date, Integer).")(search.get_searchable_fields)

    mcp.tool(name="get_document", description="Get metadata/properties of a document by source ID and document ID.")(documents.get_document)
    mcp.tool(name="get_document_content", description="Get the raw binary content of a document as base64.")(documents.get_document_content)
    mcp.tool(name="get_document_text", description="Get the extracted/parsed text content of a document.")(documents.get_document_text)
    mcp.tool(name="read", description="Read both the metadata and text content of a document/message by source ID and document ID.")(documents.read)
    mcp.tool(name="read_batch", description="Read both the metadata and text content of multiple documents/messages in a single batch call. Safety limit: max 50 items.")(documents.read_batch)

    mcp.tool(name="list_bookmarks", description="List all bookmark names available in the currently open case.")(bookmarks.list_bookmarks)
    mcp.tool(name="get_bookmark", description="Get all documents in a bookmark by bookmark name.")(bookmarks.get_bookmark)


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(levelname)s - %(name)s - %(message)s",
        stream=sys.stderr,
    )

    errors = settings.validate()
    if errors:
        for err in errors:
            logger.error("Configuration error: %s", err)
        logger.error(
            "Fix the above errors in .env or set the corresponding environment variables, then restart."
        )
        sys.exit(1)

    logger.info("Initializing JVM with classpath from %s", settings.iped_home)
    try:
        ensure_jvm()
    except RuntimeError as e:
        logger.error("Failed to initialize JVM: %s", e)
        sys.exit(1)

    logger.info("Opening case at %s", settings.case_path)
    try:
        info = case_manager.open_case()
        logger.info("Case opened: %s", info)
    except Exception as e:
        logger.error("Failed to open case: %s", e)
        sys.exit(1)

    register_tools()

    logger.info("Starting MCP server (stdio)")
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
