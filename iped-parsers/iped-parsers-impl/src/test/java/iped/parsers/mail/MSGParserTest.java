package iped.parsers.mail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import iped.parsers.util.AbstractPkgTest;
import iped.properties.ExtraProperties;

public class MSGParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    private class EmbeddedDocumentMetadataTracker implements EmbeddedDocumentExtractor {

        private List<Metadata> attachmentsMeta = new ArrayList<>();

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
                throws SAXException, IOException {
            attachmentsMeta.add(metadata);
        }
    };

    @Test
    public void testMSGParser() throws IOException, SAXException, TikaException {
        MSGParser parser = new MSGParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext msgContext = new ParseContext();
        EmbeddedDocumentMetadataTracker embeddedTracker = new EmbeddedDocumentMetadataTracker();
        msgContext.set(EmbeddedDocumentExtractor.class, embeddedTracker);
        try (InputStream stream = getStream("test-files/test_msgSample.msg")) {
            parser.parse(stream, handler, metadata, msgContext);
            assertEquals("Aula 02 No Ar! Semana Javascript Expert", metadata.get(TikaCoreProperties.SUBJECT));
            assertEquals("Erick Wendel", metadata.get(Metadata.MESSAGE_FROM));
            assertEquals("Guilherme Monteiro", metadata.get(Metadata.MESSAGE_TO));
            assertEquals(0, embeddedTracker.attachmentsMeta.size());
            assertEquals(0, (int) metadata.getInt(ExtraProperties.MESSAGE_ATTACHMENT_COUNT));

        }
    }

    @Test
    public void testMSGParserAttach() throws IOException, SAXException, TikaException {
        MSGParser parser = new MSGParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext msgContext = new ParseContext();
        EmbeddedDocumentMetadataTracker embeddedTracker = new EmbeddedDocumentMetadataTracker();
        msgContext.set(EmbeddedDocumentExtractor.class, embeddedTracker);
        try (InputStream stream = getStream("test-files/test_msgSampleAttach.msg")) {
            parser.getSupportedTypes(msgContext);
            parser.parse(stream, handler, metadata, msgContext);

            assertEquals("[cic-bcc-l] Passe Estudantil - Atividades em Per?odo de F?rias",
                    metadata.get(TikaCoreProperties.SUBJECT));
            assertEquals("Lista Informativa do Curso de Bacharelado em Ciência da Computação",
                    metadata.get(Metadata.MESSAGE_FROM));
            assertEquals(
                    "cic-bcc-l@listas.unb.br; cic-lic-l@listas.unb.br; cic-mec-l@listas.unb.br; cic-eng-l@listas.unb.br",
                    metadata.get(Metadata.MESSAGE_TO));
            assertEquals(1, embeddedTracker.attachmentsMeta.size());
            for (Metadata attachMeta : embeddedTracker.attachmentsMeta) {
                assertEquals("true", attachMeta.get(ExtraProperties.MESSAGE_IS_ATTACHMENT));
            }
            assertEquals(1, (int) metadata.getInt(ExtraProperties.MESSAGE_ATTACHMENT_COUNT));

        }

    }

}
