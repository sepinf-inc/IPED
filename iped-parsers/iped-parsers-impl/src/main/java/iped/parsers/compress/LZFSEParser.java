package iped.parsers.compress;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.github.horrorho.ragingmoose.LZFSEDecoderException;
import com.github.horrorho.ragingmoose.LZFSEInputStream;

import iped.utils.IOUtil;

public class LZFSEParser extends AbstractParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final MediaType LZFSE_MIME = MediaType.application("x-lzfse");
    private static final int MAX_MEM_SIZE = 1 << 23;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return Collections.singleton(LZFSE_MIME);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        BufferedOutputStream bos = null;
        TemporaryResources tmp = new TemporaryResources();
        try (LZFSEInputStream is = new LZFSEInputStream(new CloseShieldInputStream(stream))) {

            File tempFile = null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int read;
            byte[] buf = new byte[8192];
            while ((read = is.read(buf)) != -1) {
                if (bos == null && baos.size() + read <= MAX_MEM_SIZE) {
                    baos.write(buf, 0, read);
                } else {
                    if (bos == null) {
                        tempFile = tmp.createTemporaryFile();
                        bos = new BufferedOutputStream(new FileOutputStream(tempFile));
                    }
                    bos.write(buf, 0, read);
                }
            }
            if (bos != null) {
                bos.close();
            }
            
            Metadata subMeta = new Metadata();
            subMeta.set(TikaCoreProperties.RESOURCE_NAME_KEY, metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));

            byte[] data = baos.toByteArray();
            baos.reset();
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            InputStream subitemStream = bos == null ? bais : new SequenceInputStream(bais, new FileInputStream(tempFile));

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
            extractor.parseEmbedded(subitemStream, handler, subMeta, false);
            
        } catch (LZFSEDecoderException e) {
            throw new TikaException("Error decoding LZFSE file", e);
        } finally {
            IOUtil.closeQuietly(bos);
            tmp.close();
        }
    }

}
