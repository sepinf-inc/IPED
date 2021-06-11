package dpf.ap.gpinf.telegramextractor;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import iped3.datasource.IDataSource;
import iped3.io.IItemBase;
import iped3.io.SeekableInputStream;
import iped3.search.IItemSearcher;

public class TelegramParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testTelegramParser() throws IOException, SAXException, TikaException{

        TelegramParser parser = new TelegramParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1<<20);
        InputStream stream = getStream("test-files/test_telegramCache4.db");
        parser.setExtractMessages(true);
        parser.setEnabledForUfdr(true);
        parser.getSupportedTypes(telegramContext);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-telegram-db");
        IItemSearcher itemSearcher = new IItemSearcher() {
            
            @Override
            public void close() throws IOException {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public Iterable<IItemBase> searchIterable(String luceneQuery) {
                // TODO Auto-generated method stub
                return searchIterable(luceneQuery);
            }
            
            @Override
            public List<IItemBase> search(String luceneQuery) {
                // TODO Auto-generated method stub
                return new List<IItemBase>() {
                    
                    @Override
                    public <T> T[] toArray(T[] a) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public Object[] toArray() {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public List<IItemBase> subList(int fromIndex, int toIndex) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public int size() {
                        // TODO Auto-generated method stub
                        return 0;
                    }
                    
                    @Override
                    public IItemBase set(int index, IItemBase element) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public boolean retainAll(Collection<?> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public boolean removeAll(Collection<?> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public IItemBase remove(int index) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public boolean remove(Object o) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public ListIterator<IItemBase> listIterator(int index) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public ListIterator<IItemBase> listIterator() {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public int lastIndexOf(Object o) {
                        // TODO Auto-generated method stub
                        return 0;
                    }
                    
                    @Override
                    public Iterator<IItemBase> iterator() {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public boolean isEmpty() {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public int indexOf(Object o) {
                        // TODO Auto-generated method stub
                        return 0;
                    }
                    
                    @Override
                    public IItemBase get(int index) {
                        // TODO Auto-generated method stub
                        return new IItemBase() {
                            
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
                                return true;
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
                                return new String();
                            }
                            
                            @Override
                            public byte[] getThumb() {
                                // TODO Auto-generated method stub
                                return null;
                            }
                            
                            @Override
                            public File getTempFile() throws IOException {
                                // TODO Auto-generated method stub
                                return getFile();
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
                                return getPath();
                            }
                            
                            @Override
                            public Integer getParentId() {
                                // TODO Auto-generated method stub
                                return null;
                            }
                            
                            @Override
                            public String getName() {
                                // TODO Auto-generated method stub
                                return new String();
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
                                return new String();
                            }
                            
                            @Override
                            public File getFile() {
                                // TODO Auto-generated method stub
                                return new File("src/test/resources/test-files/testFile");
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
                    }
                    
                    @Override
                    public boolean containsAll(Collection<?> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public boolean contains(Object o) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public void clear() {
                        // TODO Auto-generated method stub
                        
                    }
                    
                    @Override
                    public boolean addAll(int index, Collection<? extends IItemBase> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public boolean addAll(Collection<? extends IItemBase> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public void add(int index, IItemBase element) {
                        // TODO Auto-generated method stub
                        
                    }
                    
                    @Override
                    public boolean add(IItemBase e) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                };
            }
            
            @Override
            public String escapeQuery(String string) {
                // TODO Auto-generated method stub
                return string;
            }
        };
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, getName(), false);
        telegramContext.set(ItemInfo.class, itemInfo);
        telegramContext.set(IItemSearcher.class, itemSearcher);
        parser.parse(stream, handler, metadata, telegramContext);
        assertEquals(509, telegramtracker.title.size());
        assertEquals(513, telegramtracker.username.size());
        assertEquals(236, telegramtracker.userphone.size());
        assertEquals(261, telegramtracker.useraccount.size());
        assertEquals(118, telegramtracker.usernotes.size());
        assertEquals(32, telegramtracker.participants.size());
        assertEquals(151, telegramtracker.messagefrom.size());
        assertEquals(151, telegramtracker.messagebody.size());
        assertEquals(151, telegramtracker.messageto.size());
        assertEquals(151, telegramtracker.messagedate.size());
        
        assertEquals("Tiago", telegramtracker.title.get(0));
        assertEquals("Karol Braz", telegramtracker.title.get(1));
        assertEquals("Budi", telegramtracker.title.get(2));
        assertEquals("Nickerida", telegramtracker.title.get(3));
        assertEquals("Telegram_Chat_Marcoscachos", telegramtracker.title.get(505));
        assertEquals("Telegram_Chat_Marcoscachos_message_0", telegramtracker.title.get(506));
        assertEquals("Telegram_Group_mixirica e noronhe-se", telegramtracker.title.get(507));
        assertEquals("Telegram_Group_mixirica e noronhe-se_message_0", telegramtracker.title.get(508));

        assertEquals("Tiago", telegramtracker.username.get(0));
        assertEquals("Karol Braz", telegramtracker.username.get(1));
        assertEquals("Budi", telegramtracker.username.get(3));
        
        assertEquals("5561981124921", telegramtracker.userphone.get(0));
        assertEquals("5561992311125", telegramtracker.userphone.get(1));
        assertEquals("5561983125151", telegramtracker.userphone.get(3));
        
        assertEquals("1289498844", telegramtracker.useraccount.get(0));
        assertEquals("165119446", telegramtracker.useraccount.get(1));
        assertEquals("53985588", telegramtracker.useraccount.get(3));
        
        assertEquals("maju_chuchu", telegramtracker.usernotes.get(0));
        assertEquals("RafaelCampos", telegramtracker.usernotes.get(1));
        assertEquals("gif", telegramtracker.usernotes.get(3));
        
        assertEquals("Bruno Chaves (phone: 33667514279)", telegramtracker.participants.get(0));
        assertEquals("Nake Douglas (phone: 5561982616052)", telegramtracker.participants.get(1));
        assertEquals("Guilherme Andreúce (phone: 5561986143035)", telegramtracker.participants.get(2));

        assertEquals("Telegram (phone: 42777)", telegramtracker.messagefrom.get(0));
        assertEquals("Nickerida (phone: 5561983125151)", telegramtracker.messagefrom.get(1));
        assertEquals("Guilherme Andreúce (phone: 5561986143035)", telegramtracker.messagefrom.get(3));

        assertEquals("Telegram (phone: 42777)", telegramtracker.messageto.get(0));
        assertEquals("Nickerida (phone: 5561983125151)", telegramtracker.messageto.get(1));
        assertEquals("Guilherme Andreúce (phone: 5561986143035)", telegramtracker.messageto.get(150));
        
        assertTrue(telegramtracker.messagebody.get(0).contains("Código de login: 73632. Não envie esse código para ninguém, nem mesmo que eles digam que são do Telegram!"));
        assertTrue(telegramtracker.messagebody.get(1).contains("Sacou?"));
        assertTrue(telegramtracker.messagebody.get(150).contains("MENSAGEM DESCONHECIDA DO TIPO: TL_MESSAGEACTIONCHATMIGRATETO"));
//
//        assertEquals("2021-06-09T18:56:52Z", telegramtracker.messagedate.get(0));
//        assertEquals("2021-06-09T01:34:33Z", telegramtracker.messagedate.get(1));
//        assertEquals("2019-04-23T18:40:10Z", telegramtracker.messagedate.get(150));
       
    }
    
    @Test
    public void testTelegramParserAndroidAcc() throws IOException, SAXException, TikaException{

        TelegramParser parser = new TelegramParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1<<20);
        InputStream stream = getStream("test-files/test_telegramUserConfing.xml");
        parser.setExtractMessages(true);
        parser.setEnabledForUfdr(true);
        parser.getSupportedTypes(telegramUserContext);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-telegram-user-conf");
        IItemSearcher itemSearcher = new IItemSearcher() {
            
            @Override
            public void close() throws IOException {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public Iterable<IItemBase> searchIterable(String luceneQuery) {
                // TODO Auto-generated method stub
                return searchIterable(luceneQuery);
            }
            
            @Override
            public List<IItemBase> search(String luceneQuery) {
                // TODO Auto-generated method stub
                return new List<IItemBase>() {
                    
                    @Override
                    public <T> T[] toArray(T[] a) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public Object[] toArray() {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public List<IItemBase> subList(int fromIndex, int toIndex) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public int size() {
                        // TODO Auto-generated method stub
                        return 0;
                    }
                    
                    @Override
                    public IItemBase set(int index, IItemBase element) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public boolean retainAll(Collection<?> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public boolean removeAll(Collection<?> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public IItemBase remove(int index) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public boolean remove(Object o) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public ListIterator<IItemBase> listIterator(int index) {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public ListIterator<IItemBase> listIterator() {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public int lastIndexOf(Object o) {
                        // TODO Auto-generated method stub
                        return 0;
                    }
                    
                    @Override
                    public Iterator<IItemBase> iterator() {
                        // TODO Auto-generated method stub
                        return null;
                    }
                    
                    @Override
                    public boolean isEmpty() {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public int indexOf(Object o) {
                        // TODO Auto-generated method stub
                        return 0;
                    }
                    
                    @Override
                    public IItemBase get(int index) {
                        // TODO Auto-generated method stub
                        return new IItemBase() {
                            
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
                                return true;
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
                                return new String();
                            }
                            
                            @Override
                            public byte[] getThumb() {
                                // TODO Auto-generated method stub
                                return null;
                            }
                            
                            @Override
                            public File getTempFile() throws IOException {
                                // TODO Auto-generated method stub
                                return getFile();
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
                                return getPath();
                            }
                            
                            @Override
                            public Integer getParentId() {
                                // TODO Auto-generated method stub
                                return null;
                            }
                            
                            @Override
                            public String getName() {
                                // TODO Auto-generated method stub
                                return new String();
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
                                return new String();
                            }
                            
                            @Override
                            public File getFile() {
                                // TODO Auto-generated method stub
                                return new File("src/test/resources/test-files/testFile");
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
                    }
                    
                    @Override
                    public boolean containsAll(Collection<?> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public boolean contains(Object o) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public void clear() {
                        // TODO Auto-generated method stub
                        
                    }
                    
                    @Override
                    public boolean addAll(int index, Collection<? extends IItemBase> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public boolean addAll(Collection<? extends IItemBase> c) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                    
                    @Override
                    public void add(int index, IItemBase element) {
                        // TODO Auto-generated method stub
                        
                    }
                    
                    @Override
                    public boolean add(IItemBase e) {
                        // TODO Auto-generated method stub
                        return false;
                    }
                };
            }
            
            @Override
            public String escapeQuery(String string) {
                // TODO Auto-generated method stub
                return string;
            }
        };
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, getName(), false);
        telegramUserContext.set(ItemInfo.class, itemInfo);
        telegramUserContext.set(IItemSearcher.class, itemSearcher);
        parser.parseAndroidAccount(stream, handler, metadata, telegramUserContext);
        
        assertEquals(1, telegramusertracker.title.size());
        assertEquals(1, telegramusertracker.username.size());
        assertEquals(1, telegramusertracker.userphone.size());
        assertEquals(1, telegramusertracker.useraccount.size());

        assertEquals("Telegram - Guilherme Andreúce", telegramusertracker.title.get(0));
        assertEquals("Guilherme", telegramusertracker.username.get(0));
        assertEquals("5561986143035", telegramusertracker.userphone.get(0));
        assertEquals("guileb", telegramusertracker.useraccount.get(0));
    }
}
