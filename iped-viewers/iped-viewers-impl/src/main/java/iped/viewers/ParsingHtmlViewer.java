package iped.viewers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.SAXException;

import iped.io.IStreamSource;
import iped.parsers.mft.MFTEntry;
import iped.parsers.mft.MFTEntryParser;
import iped.parsers.util.ToXMLContentHandler;
import iped.utils.IOUtil;

/**
 * Calls a parser to get the HTML view of the item content. It can be used with
 * small files, which parsing is very fast and do NOT depend on any context
 * information.
 */
public class ParsingHtmlViewer extends HtmlViewer {
    private String contentType;

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals(MFTEntry.MIME_TYPE);
    }
    
    @Override
    public void loadFile(IStreamSource content, String contentType, Set<String> highlightTerms) {
        this.contentType = contentType;
        super.loadFile(content, contentType, highlightTerms);
    }

    @Override
    protected File getTempFile(IStreamSource content) {
        AbstractParser parser = null;
        if (contentType.equalsIgnoreCase(MFTEntry.MIME_TYPE)) {
            parser = new MFTEntryParser();
        }
        if (parser == null) {
            return null;
        }
        InputStream in = null;
        BufferedWriter writer = null;
        try {
            in = content.getSeekableInputStream();
            ToXMLContentHandler handler = new ToXMLContentHandler();
            parser.parse(in, handler, new Metadata(), new ParseContext());
            File tmpFile = File.createTempFile("iped", ".html");
            tmpFile.deleteOnExit();
            writer = new BufferedWriter(new FileWriter(tmpFile));
            writer.write(handler.toString());
            return tmpFile;
        } catch (IOException | SAXException | TikaException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtil.closeQuietly(in);
            IOUtil.closeQuietly(writer);
        }
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

}
