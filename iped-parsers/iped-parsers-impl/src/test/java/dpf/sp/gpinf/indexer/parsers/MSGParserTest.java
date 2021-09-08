package dpf.sp.gpinf.indexer.parsers;



import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import iped3.util.ExtraProperties;

public class MSGParserTest extends AbstractPkgTest{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    

    @SuppressWarnings("deprecation")
    @Test
    public void testMSGParser() throws IOException, SAXException, TikaException{
        MSGParser parser = new MSGParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        InputStream stream = getStream("test-files/test_msgSample.msg");
        parser.getSupportedTypes(msgContext);
        parser.parse(stream, handler, metadata, msgContext);
        
        assertEquals("Aula 02 No Ar! Semana Javascript Expert", metadata.get(Metadata.SUBJECT));
        assertEquals("Erick Wendel", metadata.get(Metadata.MESSAGE_FROM));
        assertEquals("Guilherme Monteiro", metadata.get(Metadata.MESSAGE_TO));
        assertEquals(null, metadata.get(ExtraProperties.MESSAGE_IS_ATTACHMENT));
        assertEquals("0", metadata.get(ExtraProperties.MESSAGE_ATTACHMENT_COUNT));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void testMSGParserAttach() throws IOException, SAXException, TikaException{
        MSGParser parser = new MSGParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        InputStream stream = getStream("test-files/test_msgSampleAttach.msg");
        parser.getSupportedTypes(msgContext);
        parser.parse(stream, handler, metadata, msgContext);
        
        assertEquals("[cic-bcc-l] Passe Estudantil - Atividades em Per?odo de F?rias", metadata.get(Metadata.SUBJECT));
        assertEquals("Lista Informativa do Curso de Bacharelado em Ciência da Computação", metadata.get(Metadata.MESSAGE_FROM));
        assertEquals("cic-bcc-l@listas.unb.br; cic-lic-l@listas.unb.br; cic-mec-l@listas.unb.br; cic-eng-l@listas.unb.br", metadata.get(Metadata.MESSAGE_TO));
        assertEquals(null, metadata.get(ExtraProperties.MESSAGE_IS_ATTACHMENT));
        assertEquals("1", metadata.get(ExtraProperties.MESSAGE_ATTACHMENT_COUNT));
         
    }
    
    
}
