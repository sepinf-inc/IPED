package org.apache.tika.fork;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped3.util.ExtraProperties;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.parsers.util.Messages;

public class EmbeddedDocumentParser implements EmbeddedDocumentExtractor, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    // Utilizado para restringir tamanho máximo do nome de subitens de zips
    // corrompidos
    private static int NAME_MAX_LEN = 256;

    private static Logger LOGGER = LoggerFactory.getLogger(EmbeddedDocumentParser.class);

    private transient ParseContext context;

    public static class NameTitle {

        public String name;
        public boolean hasTitle;

        public NameTitle(String name, boolean hasTitle) {
            this.name = name;
            this.hasTitle = hasTitle;
        }
    }

    public EmbeddedDocumentParser(ParseContext context) {
        this.context = context;
    }

    public void setContext(ParseContext context) {
        this.context = context;
    }

    @Override
    public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
            throws SAXException, IOException {

        ItemInfo itemInfo = context.get(ItemInfo.class);
        itemInfo.incChild();

        String name = getNameTitle(metadata, itemInfo.getChild()).name;
        char[] nameChars = (name + "\n\n").toCharArray(); //$NON-NLS-1$

        handler.characters(nameChars, 0, nameChars.length);

        String subitemPath = itemInfo.getPath() + ">>" + name; //$NON-NLS-1$
        ParsingEmbeddedDocumentExtractor embeddedParser = new ParsingEmbeddedDocumentExtractor(context);
        try {
            embeddedParser.parseEmbedded(stream, handler, metadata, false);

        } catch (Exception e) {
            // do not interrupt parsing of parent doc if parsing of child doc fails
            LOGGER.warn("{} Error while parsing subitem {}\t\t{}", Thread.currentThread().getName(), subitemPath, //$NON-NLS-1$
                    e.toString());
        }

    }

    public static NameTitle getNameTitle(Metadata metadata, int child) {
        boolean hasTitle = false;
        String name = metadata.get(TikaMetadataKeys.RESOURCE_NAME_KEY);
        if (name == null || name.isEmpty()) {
            name = metadata.get(ExtraProperties.MESSAGE_SUBJECT);
            if (name == null || name.isEmpty()) {
                name = metadata.get(TikaCoreProperties.TITLE);
            }
            if (name != null) {
                hasTitle = true;
            }
        }
        if (name == null || name.isEmpty()) {
            name = metadata.get(TikaMetadataKeys.EMBEDDED_RELATIONSHIP_ID);
        }

        if (name == null || name.isEmpty()) {
            name = Messages.getString("EmbeddedDocumentParser.UnNamed") + child; //$NON-NLS-1$
        }

        if (name.length() > NAME_MAX_LEN) {
            name = name.substring(0, NAME_MAX_LEN);
        }
        return new NameTitle(name, hasTitle);
    }

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        return true;
    }

}
