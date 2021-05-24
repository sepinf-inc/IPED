package dpf.inc.sepinf.UsnJrnl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped3.datasource.IDataSource;
import iped3.io.IItemBase;
import iped3.io.SeekableInputStream;
import iped3.search.IItemSearcher;
import junit.framework.TestCase;

public class UsnJrnlParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    
    @Test
    public void testUsnJrnlParsing() throws IOException, SAXException, TikaException{

        UsnJrnlParser parser = new UsnJrnlParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_UsnJrnl.bin");
        ParseContext context = new ParseContext();
        IItemSearcher searcher = new IItemSearcher() {
            
            @Override
            public void close() throws IOException {}
            @Override
            public Iterable<IItemBase> searchIterable(String luceneQuery) {return null;}
            @Override
            public List<IItemBase> search(String luceneQuery) {return null;}
            @Override
            public String escapeQuery(String string) {return null;}
            
        };
        IItemBase item = new IItemBase() {
            
            @Override
            public SeekableInputStream getStream() throws IOException {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public SeekableByteChannel getSeekableByteChannel() throws IOException {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public boolean isTimedOut() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isSubItem() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isRoot() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isDuplicate() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isDir() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isDeleted() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isCarved() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean hasFile() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean hasChildren() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public File getViewFile() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String getTypeExt() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public byte[] getThumb() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public File getTempFile() throws IOException {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Integer getSubitemId() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Date getRecordDate() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String getPath() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Integer getParentId() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String getName() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Date getModDate() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Metadata getMetadata() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public MediaType getMediaType() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Long getLength() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public byte[] getImageSimilarityFeatures() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public int getId() {
                // TODO Auto-generated method stub
                return 0;
            }
            
            @Override
            public String getHash() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public File getFile() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Map<String, Object> getExtraAttributeMap() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Object getExtraAttribute(String key) {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String getExt() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public IDataSource getDataSource() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Date getCreationDate() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public HashSet<String> getCategorySet() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public BufferedInputStream getBufferedStream() throws IOException {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Date getAccessDate() {
                // TODO Auto-generated method stub
                return null;
            }
        };
        context.set(IItemSearcher.class, searcher);
        context.set(IItemBase.class, item);
        parser.setExtractEntries(true);
        parser.getSupportedTypes(context);
        parser.findNextEntry(stream);
//        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        System.out.println(hts);
    }

}
