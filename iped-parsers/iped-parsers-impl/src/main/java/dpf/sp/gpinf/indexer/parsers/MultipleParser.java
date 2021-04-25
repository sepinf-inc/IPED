package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.utils.ParserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import iped3.io.IStreamSource;

/**
 * Runs a list of parsers sequentially on a file.
 * 
 * @author Nassif
 *
 */
public class MultipleParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(MultipleParser.class);

    private List<Parser> parsers = new ArrayList<>();

    private Set<MediaType> supportedTypes = new HashSet<>();

    @Field
    private boolean stopAfterSomeParserWorks = false;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return supportedTypes;
    }

    public void addParser(Parser parser) {
        parsers.add(parser);
    }

    public void addSupportedTypes(Set<MediaType> mimes) {
        supportedTypes.addAll(mimes);
    }

    @Field
    public void setParsers(String value) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String[] parsers = value.split(";");
        for (String p : parsers) {
            if (!(p = p.trim()).isEmpty()) {
                this.parsers.add((Parser) Class.forName(p).newInstance());
            }
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        
        ItemInfo itemInfo = context.get(ItemInfo.class);
        IStreamSource source = context.get(IStreamSource.class);
        TemporaryResources tmp = new TemporaryResources();
        TikaException tikaException = null;
        EmbeddedContentHandler embeddedHandler = new EmbeddedContentHandler(handler);
        embeddedHandler.startDocument();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            boolean firstTis = true;
            Path tempPath = null;
            for (Parser parser : parsers) {
                if (tis != null && (tis.hasFile() || source == null)) {
                    tempPath = tis.getPath();
                }
                if (tis == null) {
                    if (tempPath != null) {
                        tis = TikaInputStream.get(tempPath);
                    } else {
                        tis = TikaInputStream.get(source.getStream());
                    }
                    firstTis = false;
                }
                Metadata newMetadata = getNewMetadata(metadata);
                try {
                    parser.parse(tis, embeddedHandler, newMetadata, context);

                    if (stopAfterSomeParserWorks) {
                        break;
                    }

                    // if this parser works, clear exception to not throw it
                    tikaException = null;

                } catch (Throwable e) {
                    ParserUtils.recordParserFailure(parser, e, newMetadata);
                    String filepath = itemInfo != null ? itemInfo.getPath() : "file";
                    logger.warn("Exception from {} on {}: {}", parser.getClass().getName(), filepath, e.toString());
                    if (tikaException == null) {
                        tikaException = new TikaException("Exception from " + parser.getClass().getName());
                    }
                    tikaException.addSuppressed(e);

                } finally {
                    ParserUtils.recordParserDetails(parser, newMetadata);
                    // merge even if parser fails, some meta could be extracted
                    mergeMetadata(metadata, newMetadata);
                    if (!firstTis) {
                        IOUtils.closeQuietly(tis);
                    }
                    tis = null;
                }
            }

        } finally {
            embeddedHandler.endDocument();
            tmp.close();
            if (tikaException != null) {
                throw tikaException;
            }
        }
    }

    private Metadata getNewMetadata(Metadata metadata) {
        Metadata newMetadata = new Metadata();
        newMetadata.set(Metadata.RESOURCE_NAME_KEY, metadata.get(Metadata.RESOURCE_NAME_KEY));
        newMetadata.set(Metadata.CONTENT_LENGTH, metadata.get(Metadata.CONTENT_LENGTH));
        newMetadata.set(Metadata.CONTENT_TYPE, metadata.get(Metadata.CONTENT_TYPE));
        newMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE));
        return metadata;
    }

    private void mergeMetadata(Metadata metadata, Metadata newMetadata) {
        for (String name : newMetadata.names()) {
            Set<String> oldValues = new TreeSet<>(Arrays.asList(metadata.getValues(name)));
            for (String newVal : newMetadata.getValues(name)) {
                if (!oldValues.contains(newVal)) {
                    metadata.add(name, newVal);
                }
            }
        }
    }

}
