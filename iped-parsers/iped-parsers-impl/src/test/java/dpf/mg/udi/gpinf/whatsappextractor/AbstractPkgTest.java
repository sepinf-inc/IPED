package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import iped3.IEvidenceFileType;
import iped3.IHashValue;
import iped3.IItem;
import iped3.datasource.IDataSource;
import iped3.io.IItemBase;
import iped3.io.ISeekableInputStreamFactory;
import iped3.io.SeekableInputStream;
import iped3.search.IItemSearcher;
import iped3.util.ExtraProperties;

public abstract class AbstractPkgTest extends TestCase {
    protected ParseContext whatsappContext;
    protected EmbeddedWhatsAppParser whatsapptracker;
    protected ItemInfo itemInfo;
    protected IItemSearcher itemSearcher;
    protected IItemBase itemBase;
    protected IItem iitem;
    protected InputStream stream;

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    protected void setUp() throws Exception {
        super.setUp();
        stream = getStream("test-files/whatsapp/msgstore.db");
        iitem = new IItem(){
            
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
            public Integer getSubitemId() {
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
            public void setViewFile(File viewFile) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setType(IEvidenceFileType type) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setToIgnore(boolean toIgnore, boolean updateStats) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setToIgnore(boolean toIgnore) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setToExtract(boolean isToExtract) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setTimeOut(boolean timeOut) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setThumb(byte[] thumb) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setTempStartOffset(long tempStartOffset) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setTempFile(File tempFile) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setTempAttribute(String key, Object value) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setSumVolume(boolean sumVolume) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setSubItem(boolean isSubItem) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setRoot(boolean isRoot) {
                // TODO Auto-generated method stub
                
            }
            

            @Override
            public void setQueueEnd(boolean isQueueEnd) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setPath(String path) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setParsedTextCache(String parsedTextCache) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setParsed(boolean parsed) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setParentIdInDataSource(String string) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setParentId(Integer parentId) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setParent(IItem parent) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setName(String name) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setModificationDate(Date modificationDate) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setMetadata(Metadata metadata) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setMediaType(MediaType mediaType) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setLength(Long length) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setLabels(List<String> labels) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setIsDir(boolean isDir) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setInputStreamFactory(ISeekableInputStreamFactory inputStreamFactory) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setImageSimilarityFeatures(byte[] imageSimilarityFeatures) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setIdInDataSource(String string) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setId(int id) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setHash(String hash) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setHasChildren(boolean hasChildren) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setFtkID(Integer ftkID) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setFileOffset(long fileOffset) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setFile(File file) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setExtraAttribute(String key, Object value) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setExtension(String ext) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setExportedFile(String exportedFile) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setDuplicate(boolean duplicate) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setDeleted(boolean deleted) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setDeleteFile(boolean deleteFile) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setDataSource(IDataSource evidence) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setCreationDate(Date creationDate) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setCategory(String category) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setCarved(boolean carved) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setAddToCase(boolean addToCase) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void setAccessDate(Date accessDate) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void removeCategory(String category) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public boolean isToSumVolume() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isToIgnore() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isToExtract() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isToAddToCase() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isQueueEnd() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean isParsed() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public boolean hasTmpFile() {
                // TODO Auto-generated method stub
                return false;
            }
            
            @Override
            public IEvidenceFileType getType() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public TikaInputStream getTikaStream() throws IOException {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Reader getTextReader() throws IOException {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public File getTempFile() throws IOException {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Object getTempAttribute(String key) {
                // TODO Auto-generated method stub
                return null;
            }
            
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
            public String getParsedTextCache() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String getParentIdsString() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public List<Integer> getParentIds() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String getParentIdInDataSource() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public List<String> getLabels() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public ISeekableInputStreamFactory getInputStreamFactory() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String getIdInDataSource() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public IHashValue getHashValue() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public Integer getFtkID() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public String getFileToIndex() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public long getFileOffset() {
                // TODO Auto-generated method stub
                return 0;
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
            public String getExportedFile() {
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
            public String getCategories() {
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
            
            @Override
            public void dispose() {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public IItem createChildItem() {
                // TODO Auto-generated method stub
                return null;
            }
            
            @Override
            public void addParentIds(List<Integer> parentIds) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void addParentId(int parentId) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void addCategory(String category) {
                // TODO Auto-generated method stub
                
            }

            @Override
            public Date getChangeDate() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public void setChangeDate(Date changeDate) {
                // TODO Auto-generated method stub
                
            }
        };
        itemInfo = new ItemInfo(0, getName(), null, null, getName(), false);
        itemBase = new IItemBase() {
            
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

            @Override
            public Date getChangeDate() {
                // TODO Auto-generated method stub
                return null;
            }
        };
        itemSearcher = new IItemSearcher() {

            @Override
            public void close() throws IOException {
                // TODO Auto-generated method stub

            }

            @Override
            public Iterable<IItemBase> searchIterable(String luceneQuery) {
                // TODO Auto-generated method stub
                return null;
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
                                return new BufferedInputStream(stream);
                            }

                            @Override
                            public Date getAccessDate() {
                                // TODO Auto-generated method stub
                                return null;
                            }

                            @Override
                            public Date getChangeDate() {
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

        whatsapptracker = new EmbeddedWhatsAppParser();
        whatsappContext = new ParseContext();
        whatsappContext.set(Parser.class, whatsapptracker);
        whatsappContext.set(ItemInfo.class, itemInfo);
        whatsappContext.set(IItemSearcher.class, itemSearcher);
        whatsappContext.set(IItemBase.class, itemBase);
        whatsappContext.set(IItem.class, iitem);

    }

    @SuppressWarnings("serial")
    protected static class EmbeddedWhatsAppParser extends AbstractParser {
        protected List<String> title = new ArrayList<String>();
        protected List<String> username = new ArrayList<String>();
        protected List<String> userphone = new ArrayList<String>();
        protected List<String> useraccount = new ArrayList<String>();
        protected List<String> usernotes = new ArrayList<String>();
        protected List<String> participants = new ArrayList<String>();
        protected List<String> messagefrom = new ArrayList<String>();
        protected List<String> messagebody = new ArrayList<String>();
        protected List<String> messageto = new ArrayList<String>();
        protected List<String> messagedate = new ArrayList<String>();
        
        
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler,
                Metadata metadata, ParseContext context) throws IOException,
                SAXException, TikaException {
            if(metadata.get(TikaCoreProperties.TITLE) != null)
                title.add(metadata.get(TikaCoreProperties.TITLE));
            if(metadata.get(ExtraProperties.USER_NAME) != null);
                username.add(metadata.get(ExtraProperties.USER_NAME));
            if(metadata.get(ExtraProperties.USER_PHONE) != null)
                userphone.add(metadata.get(ExtraProperties.USER_PHONE));
            if(metadata.get(ExtraProperties.USER_ACCOUNT) != null)
                useraccount.add(metadata.get(ExtraProperties.USER_ACCOUNT));
            if(metadata.get(ExtraProperties.USER_NOTES) != null)
                usernotes.add(metadata.get(ExtraProperties.USER_NOTES));
            if(metadata.get(ExtraProperties.PARTICIPANTS) != null)
                participants.add(metadata.get(ExtraProperties.PARTICIPANTS));
            if(metadata.get(org.apache.tika.metadata.Message.MESSAGE_FROM) != null)
                messagefrom.add(metadata.get(org.apache.tika.metadata.Message.MESSAGE_FROM));
            if(metadata.get(ExtraProperties.MESSAGE_BODY) != null)
                messagebody.add(metadata.get(ExtraProperties.MESSAGE_BODY));
            if(metadata.get(org.apache.tika.metadata.Message.MESSAGE_TO) != null)
                messageto.add(metadata.get(org.apache.tika.metadata.Message.MESSAGE_TO));
            if(metadata.get(ExtraProperties.MESSAGE_DATE) != null)
                messagedate.add(metadata.get(ExtraProperties.MESSAGE_DATE));
        }
    }
}
