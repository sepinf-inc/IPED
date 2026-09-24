import logging
from pathlib import Path
from typing import Optional

from .jvm_bridge import get_class, cast_to
from .config import settings

logger = logging.getLogger(__name__)


class IPEDCaseManager:
    def __init__(self):
        self._source = None
        self._multi_source = None
        self._is_multi = False

    @property
    def source(self):
        if self._source is None:
            raise RuntimeError("Case not opened. Call open_case() first.")
        return self._source

    @property
    def multi_source(self):
        if self._multi_source is None:
            raise RuntimeError("Multi-source not opened. Call open_case() first.")
        return self._multi_source

    @property
    def is_multi(self) -> bool:
        return self._is_multi

    def open_case(self, case_path: Optional[Path] = None) -> dict:
        case_path = case_path or settings.case_path
        case_file = get_class("java.io.File")(str(case_path))

        IPEDSource = get_class("iped.engine.data.IPEDSource")
        IPEDMultiSource = get_class("iped.engine.data.IPEDMultiSource")

        if IPEDSource.checkIfIsCaseFolder(case_file):
            logger.info("Opening single case: %s", case_path)
            self._source = IPEDSource(case_file)
            self._is_multi = False
            return {"type": "single", "path": str(case_path), "total_items": self._source.getTotalItems()}
        else:
            logger.info("Opening multi-source: %s", case_path)
            self._multi_source = IPEDMultiSource(case_file)
            self._is_multi = True
            atomic_sources = self._multi_source.getAtomicSources()
            return {
                "type": "multi",
                "path": str(case_path),
                "source_count": atomic_sources.size(),
                "total_items": self._multi_source.getTotalItems(),
            }

    def close_case(self):
        if self._source is not None:
            try:
                self._source.close()
            except Exception as e:
                logger.warning("Error closing source: %s", e)
            self._source = None
        if self._multi_source is not None:
            try:
                self._multi_source.close()
            except Exception as e:
                logger.warning("Error closing multi-source: %s", e)
            self._multi_source = None
        self._is_multi = False

    def list_sources(self) -> list[dict]:
        if self._is_multi:
            sources = self._multi_source.getAtomicSources()
            result = []
            for i in range(sources.size()):
                s = sources.get(i)
                result.append({
                    "source_id": i,
                    "path": str(s.getCaseDir().getAbsolutePath()),
                    "total_items": s.getTotalItems(),
                })
            return result
        else:
            return [{
                "source_id": 0,
                "path": str(self._source.getCaseDir().getAbsolutePath()),
                "total_items": self._source.getTotalItems(),
            }]

    def search(self, query: str, source_id: Optional[int] = None) -> dict:
        import re
        
        # Auto-escape unescaped colons in known IPED fields (e.g. Communication:From -> Communication\:From)
        fields = [
            "Communication:From", 
            "Communication:To", 
            "Communication:Direction", 
            "Communication:Date", 
            "Communication:Participants"
        ]
        escaped_query = query
        for f in fields:
            pattern = r"(?<!\\)" + re.escape(f)
            escaped_query = re.sub(pattern, f.replace(":", "\\:"), escaped_query)

        IPEDSearcher = get_class("iped.engine.search.IPEDSearcher")

        try:
            if source_id is not None and self._is_multi:
                atomic = self._multi_source.getAtomicSourceBySourceId(source_id)
                searcher = IPEDSearcher(atomic, escaped_query)
                searcher.setNoScoring(True)
                java_result = searcher.search()
                length = java_result.getLength()
                ids = [java_result.getId(i) for i in range(length)]
                return {
                    "source_id": source_id,
                    "total": length,
                    "ids": ids,
                }
            elif self._is_multi:
                searcher = IPEDSearcher(self._multi_source, escaped_query)
                searcher.setNoScoring(True)
                java_result = searcher.multiSearch()
                length = java_result.getLength()
                items = []
                for i in range(length):
                    item_id = java_result.getItem(i)
                    items.append({
                        "source_id": item_id.getSourceId(),
                        "id": item_id.getId(),
                    })
                return {
                    "total": length,
                    "items": items,
                }
            else:
                searcher = IPEDSearcher(self._source, escaped_query)
                searcher.setNoScoring(True)
                java_result = searcher.search()
                length = java_result.getLength()
                ids = [java_result.getId(i) for i in range(length)]
                return {
                    "source_id": 0,
                    "total": length,
                    "ids": ids,
                }
        except Exception as e:
            logger.error("Error executing Lucene search for query '%s' (escaped: '%s'): %s", query, escaped_query, e)
            return {
                "total": 0,
                "error": f"Search failed (invalid syntax or field name): {str(e)}",
                "ids": [],
                "items": []
            }

    def get_item(self, item_id: int, source_id: int = 0) -> dict:
        if self._is_multi:
            ItemId = get_class("iped.engine.data.ItemId")
            iid = ItemId(source_id, item_id)
            item = self._multi_source.getItemByItemId(iid)
        else:
            item = self._source.getItemByID(item_id)

        if item is None:
            raise ValueError(f"Item not found: source={source_id}, id={item_id}")

        if self._is_multi:
            lucene_id = self._multi_source.getLuceneId(iid)
        else:
            lucene_id = self._source.getLuceneId(item_id)

        props = {
            "id": item.getId(),
            "source_id": source_id,
            "lucene_id": lucene_id,
            "name": item.getName() or "",
            "path": item.getPath() or "",
            "extension": item.getExt() or "",
            "type_extension": item.getType() or "",
            "length": item.getLength() or 0,
            "hash": item.getHash() or "",
            "is_dir": item.isDir(),
            "is_deleted": item.isDeleted(),
            "is_carved": item.isCarved(),
            "is_subitem": item.isSubItem(),
            "has_children": item.hasChildren(),
            "has_preview": item.hasPreview(),
        }

        try:
            props["media_type"] = str(item.getMediaType()) if item.getMediaType() else ""
        except Exception:
            props["media_type"] = ""

        for date_field, method_name in [
            ("created", "getCreationDate"),
            ("modified", "getModDate"),
            ("accessed", "getAccessDate"),
            ("changed", "getChangeDate"),
        ]:
            try:
                method = getattr(item, method_name)
                dt = method()
                props[date_field] = str(dt) if dt else None
            except Exception:
                props[date_field] = None

        try:
            labels = item.getLabels()
            props["bookmarks"] = list(labels) if labels else []
        except Exception:
            props["bookmarks"] = []

        try:
            metadata = item.getMetadata()
            if metadata is not None:
                meta_dict = {}
                for name in metadata.names():
                    values = metadata.getValues(name)
                    if values:
                        val_list = [str(v) for v in values]
                        meta_dict[str(name)] = val_list if len(val_list) > 1 else val_list[0]
                props["metadata"] = meta_dict
        except Exception as e:
            logger.warning("Error getting item metadata: %s", e)

        return props

    def get_item_content(self, item_id: int, source_id: int = 0) -> Optional[bytes]:
        if self._is_multi:
            ItemId = get_class("iped.engine.data.ItemId")
            iid = ItemId(source_id, item_id)
            item = self._multi_source.getItemByItemId(iid)
        else:
            item = self._source.getItemByID(item_id)

        if item is None or item.isDir():
            return None

        stream = item.getBufferedInputStream()
        if stream is None:
            return None

        try:
            IOUtils = get_class("org.apache.commons.io.IOUtils")
            data = IOUtils.toByteArray(cast_to("java.io.InputStream", stream))
            return bytes(data)
        finally:
            stream.close()

    def get_item_text(self, item_id: int, source_id: int = 0) -> Optional[str]:
        if self._is_multi:
            ItemId = get_class("iped.engine.data.ItemId")
            iid = ItemId(source_id, item_id)
            item = self._multi_source.getItemByItemId(iid)
        else:
            item = self._source.getItemByID(item_id)

        if item is None:
            return None

        reader = item.getTextReader()
        if reader is None:
            return None

        try:
            IOUtils = get_class("org.apache.commons.io.IOUtils")
            return IOUtils.toString(cast_to("java.io.Reader", reader))
        finally:
            reader.close()

    def get_item_thumbnail(self, item_id: int, source_id: int = 0) -> Optional[bytes]:
        if self._is_multi:
            ItemId = get_class("iped.engine.data.ItemId")
            iid = ItemId(source_id, item_id)
            item = self._multi_source.getItemByItemId(iid)
        else:
            item = self._source.getItemByID(item_id)

        if item is None or not item.hasPreview():
            return None

        thumb = item.getThumb()
        return bytes(thumb) if thumb else None

    def list_bookmarks(self) -> list[str]:
        if self._is_multi:
            bm = self._multi_source.getMultiBookmarks()
            bm_set = bm.getBookmarkSet()
            return list(bm_set) if bm_set else []
        else:
            bm = self._source.getBookmarks()
            bm_map = bm.getBookmarkMap()
            return list(bm_map.values()) if bm_map else []

    def get_bookmark(self, name: str) -> list[dict]:
        if self._is_multi:
            bm = self._multi_source.getMultiBookmarks()
            bm_set = bm.getBookmarkSet()
            if name not in (list(bm_set) if bm_set else []):
                raise ValueError(f"Bookmark not found: {name}")

            IPEDSearcher = get_class("iped.engine.search.IPEDSearcher")
            searcher = IPEDSearcher(self._multi_source, "")
            searcher.setNoScoring(True)
            all_results = searcher.multiSearch()

            result = []
            for i in range(all_results.getLength()):
                item_id = all_results.getItem(i)
                if bm.hasBookmark(item_id, name):
                    result.append({
                        "source_id": item_id.getSourceId(),
                        "id": item_id.getId(),
                    })
            return result
        else:
            bm = self._source.getBookmarks()
            bm_id = bm.getBookmarkId(name)
            if bm_id < 0:
                raise ValueError(f"Bookmark not found: {name}")

            IPEDSearcher = get_class("iped.engine.search.IPEDSearcher")
            searcher = IPEDSearcher(self._source, "")
            searcher.setNoScoring(True)
            all_results = searcher.search()

            result = []
            for i in range(all_results.getLength()):
                item_id = all_results.getId(i)
                if bm.hasBookmark(item_id, bm_id):
                    result.append({"source_id": 0, "id": item_id})
            return result

    def get_searchable_fields(self) -> dict:
        MetadataUtil = get_class("iped.parsers.util.MetadataUtil")
        types_map = MetadataUtil.getMetadataTypes()
        result = {}
        for entry in types_map.entrySet():
            class_name = str(entry.getValue().getName()).split(".")[-1]
            result[str(entry.getKey())] = class_name
        return result


case_manager = IPEDCaseManager()
