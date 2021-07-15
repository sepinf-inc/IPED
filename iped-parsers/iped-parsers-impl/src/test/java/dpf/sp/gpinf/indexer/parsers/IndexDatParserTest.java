package dpf.sp.gpinf.indexer.parsers;


import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import junit.framework.TestCase;

public class IndexDatParserTest  extends TestCase{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

//  Using external program:
//  private String TOOL_PATH = "C:\\Users\\guilh\\Downloads\\libpff-main\\libmsiecf-main\\vs2019\\Release\\Win32\\"; //$NON-NLS-1$

    @Test
    public void testIndexDatParser() throws IOException, SAXException, TikaException{

        IndexDatParser parser = new IndexDatParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_index.dat");
        parser.getSupportedTypes(context);
        try {
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("msiecfexport 20210506"));
        assertTrue(hts.contains("Record type"));
        assertTrue(hts.contains("URL"));
        assertTrue(hts.contains("Offset range"));
        assertTrue(hts.contains("20480 - 20736 (256)"));
        assertTrue(hts.contains("Location"));
        assertTrue(hts.contains("Cookie:guileb@google.com.br/"));
        assertTrue(hts.contains("Filename"));
        assertTrue(hts.contains("guileb@google.com[2].txt"));
        assertTrue(hts.contains("Cookie:guileb@incredimail.com/"));
        assertTrue(hts.contains("Cookie:guileb@google.com.br/search"));
        assertTrue(hts.contains("Cookie:guileb@incredibarvuz1.com/"));
        assertTrue(hts.contains("Cookie:guileb@google.com.br/complete/search"));
        assertTrue(hts.contains("Cookie:guileb@www.incredibarvuz1.com/"));
        assertTrue(hts.contains("Export completed."));
        }catch (Exception e) {
            System.out.println(e);
        }
        
    }
}
