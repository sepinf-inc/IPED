package iped.parsers.browsers.chrome;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.discord.cache.CacheAddr.InputStreamNotAvailable;
import iped.parsers.discord.cache.CacheEntry;
import iped.parsers.discord.cache.Index;
import iped.parsers.util.MetadataUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

public class CacheIndexParser extends AbstractParser {

    // TODO
    private static final long serialVersionUID = 1L;

    public static final MediaType CHROME_INDEX_MIME_TYPE = MediaType.application("x-chrome-cache-index");
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(CHROME_INDEX_MIME_TYPE);

    private static Logger LOGGER = LoggerFactory.getLogger(CacheIndexParser.class);

    public static final String METADATA_PREFIX = "chromeCache";
    public static final String IS_CACHE_INDEX_ENTRY = METADATA_PREFIX + ":isChromeCacheEntry";
    public static final String CACHE_URL = METADATA_PREFIX + ":chromeCacheUrl";
    private static final String CACHE_ENTRY_NAME = METADATA_PREFIX + ":cacheEntryName";

    public static final String CACHE_ENTRY_CREATED = METADATA_PREFIX + ":created";
    public static final String CACHE_ENTRY_COUNT = METADATA_PREFIX + ":numEntries";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream indexFile, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        IItemSearcher searcher = context.get(IItemSearcher.class);
        IItemReader item = context.get(IItemReader.class);

        context.set(CacheIndexParser.class, this);

        if (searcher != null && item != null) {
            String commonQuery = BasicProps.EVIDENCE_UUID + ":" + item.getDataSource().getUUID() + " AND "
                    + BasicProps.PARENTID + ":" + item.getParentId() + " AND " + BasicProps.CARVED + ":false AND NOT "
                    + BasicProps.TYPE + ":slack AND NOT " + BasicProps.TYPE + ":fileslack AND NOT " + BasicProps.NAME
                    + ":slack AND NOT " + BasicProps.LENGTH + ":0 AND NOT " + BasicProps.ISDIR + ":true AND NOT "
                    + BasicProps.PATH + ":gpucache";

            List<IItemReader> externalFiles = searcher.search(commonQuery + " AND " + BasicProps.NAME + ":f");
            List<IItemReader> dataFiles = searcher.search(commonQuery + " AND " + BasicProps.NAME
                    + ":(\"data_0\"  OR \"data_1\" OR \"data_2\" OR \"data_3\" OR \"data_4\" OR \"data_5\")");

            MetadataUtil.addCustomMetadataPrefix(METADATA_PREFIX);

            Index index;
            try {
                index = new Index(indexFile, item.getPath(), dataFiles, externalFiles);

                metadata.set(CACHE_ENTRY_COUNT, Integer.toString(index.getEntriesCont()));

                TikaException exception = null;

                List<CacheEntry> lce = index.getLst();

                for (CacheEntry ce : lce) {

                    Map<String, String> httpResponse = ce.getHttpResponse();
                    String requestUrl = ce.getRequestURL();

                    try {

                        String contentEncoding = httpResponse.get("content-encoding");
                        if (contentEncoding == null) {
                            contentEncoding = httpResponse.get("Content-Encoding");
                        }
                        if (contentEncoding != null) {
                            contentEncoding = contentEncoding.trim();
                        }
                        InputStream is;
                        try {
                            is = ce.getResponseDataSize() > 0
                                    ? ce.getResponseDataStream(contentEncoding)
                                    : new ByteArrayInputStream(new byte[] {});
                        } catch (InputStreamNotAvailable e) {
                            LOGGER.warn(
                                    "Input Stream for entry not found:" + requestUrl + " in item "
                                    + item.getPath());

                            is = new ByteArrayInputStream(new byte[] {});
                        }

                        Metadata entryMeta = new Metadata();
                        entryMeta.set("URL", requestUrl);
                        entryMeta.set(TikaCoreProperties.TITLE,
                                requestUrl.substring(requestUrl.lastIndexOf('/') + 1));
                        entryMeta.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                                requestUrl.substring(requestUrl.lastIndexOf('/') + 1));
                        entryMeta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                        entryMeta.set(IS_CACHE_INDEX_ENTRY, Boolean.TRUE.toString());
                        entryMeta.set(CACHE_ENTRY_NAME, ce.getName());
                        entryMeta.set(CACHE_URL, requestUrl);
                        entryMeta.set(TikaCoreProperties.CREATED, ce.getCreationTime());

                        for (Map.Entry<String, String> entry : httpResponse.entrySet()) {
                            entryMeta.set(entry.getKey(), entry.getValue());
                        }

                        extractor.parseEmbedded(is, handler, entryMeta, true);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        if (exception == null) {
                            exception = new TikaException("ChromeCacheParser parsing error.", ex);
                        }
                        exception.addSuppressed(ex);
                        continue;
                    }
                }

                if (exception != null) {
                    throw exception;
                }
            } catch (IOException e1) {
            } catch (ChromeCacheException e) {
            }
        }
    }
}
