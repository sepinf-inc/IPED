package dpf.mt.gpinf.skype.parser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
import iped3.datasource.IDataSource;
import iped3.io.IItemBase;
import iped3.io.SeekableInputStream;
import iped3.search.IItemSearcher;

public abstract class AbstractPkgTest extends TestCase {
   protected ParseContext skypeContext;
   protected ItemInfo itemInfo;
   protected IItemSearcher itemSearcher;
   protected IItemBase itemBase;
   protected ClassLoader classLoader;
   protected File file;
   protected Metadata metadata;
   

   protected void setUp() throws Exception {
      super.setUp();
      metadata = new Metadata();
      classLoader = getClass().getClassLoader();
      file = new File(classLoader.getResource("test-files/test_skypeS4lStreeguil1.db").getFile());
      IItemSearcher itemSearcher = new IItemSearcher() {
          
          @Override
          public void close() throws IOException {}
          @Override
          public Iterable<IItemBase> searchIterable(String luceneQuery) {return searchIterable(luceneQuery);}
          @Override
          public List<IItemBase> search(String luceneQuery) {
              
              return new List<IItemBase>() {
                  
                  @Override
                  public <T> T[] toArray(T[] a) {return a;}
                  
                  @Override
                  public Object[] toArray() {return toArray();}
                  
                  @Override
                  public List<IItemBase> subList(int fromIndex, int toIndex) {return null;}
                  
                  @Override
                  public int size() {return 0;}
                  
                  @Override
                  public IItemBase set(int index, IItemBase element) {return null;}
                  
                  @Override
                  public boolean retainAll(Collection<?> c) {return false;}
                  
                  @Override
                  public boolean removeAll(Collection<?> c) {return false;}
                  
                  @Override
                  public IItemBase remove(int index) {return null;}
                  
                  @Override
                  public boolean remove(Object o) {return false;}
                  
                  @Override
                  public ListIterator<IItemBase> listIterator(int index) {return listIterator(index);}
                  
                  @Override
                  public ListIterator<IItemBase> listIterator() {return listIterator();}
                  
                  @Override
                  public int lastIndexOf(Object o) {return 0;}
                  
                  @Override
                  public Iterator<IItemBase> iterator() {return new Iterator<IItemBase>() {
                      
                      @Override
                      public IItemBase next() {return next();}
                      
                      @Override
                      public boolean hasNext() {return false;}
                  };}
                  
                  @Override
                  public boolean isEmpty() {return false;}
                  
                  @Override
                  public int indexOf(Object o) {return 0;}
                  
                  @Override
                  public IItemBase get(int index) {return get(index);}
                  
                  @Override
                  public boolean containsAll(Collection<?> c) {return false;}
                  
                  @Override
                  public boolean contains(Object o) {return false;}
                  
                  @Override
                  public void clear() {}
                  
                  @Override
                  public boolean addAll(int index, Collection<? extends IItemBase> c) {return false;}
                  
                  @Override
                  public boolean addAll(Collection<? extends IItemBase> c) {return false;}
                  
                  @Override
                  public void add(int index, IItemBase element) {}
                  
                  @Override
                  public boolean add(IItemBase e) {return false;}
              };
              }
          @Override
          public String escapeQuery(String string) {return "";}
          
      };
      ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, file.getAbsolutePath(), false);
      IItemBase itemBase = new IItemBase() {
          
          @Override
          public SeekableInputStream getStream() throws IOException {
              return new SeekableFileInputStream(file);
          }
          
          @Override
          public SeekableByteChannel getSeekableByteChannel() throws IOException {return getSeekableByteChannel();}
          
          @Override
          public boolean isTimedOut() {return false;}
          
          @Override
          public boolean isSubItem() {return false;}
          
          @Override
          public boolean isRoot() {return false;}
          
          @Override
          public boolean isDuplicate() {return false;}
          
          @Override
          public boolean isDir() {return false;}
          
          @Override
          public boolean isDeleted() {return false;}
          
          @Override
          public boolean isCarved() {return false;}
          
          @Override
          public boolean hasFile() {return false;}
          
          @Override
          public boolean hasChildren() {return false;}
          
          @Override
          public File getViewFile() {return file;}
          
          @Override
          public String getTypeExt() {return "typeExt";}
          
          @Override
          public byte[] getThumb() {return getThumb();}
          
          @Override
          public File getTempFile() throws IOException {return file;}
          
          @Override
          public Integer getSubitemId() {return getSubitemId();}
          
          
          @Override
          public String getPath() {return file.getAbsolutePath();}
          
          @Override
          public Integer getParentId() {return getParentId();}
          
          @Override
          public String getName() {return "";}
          
          @Override
          public Date getModDate() {return null;}
          
          @Override
          public Metadata getMetadata() {return metadata;}
          
          @Override
          public MediaType getMediaType() {return getMediaType();}
          
          @Override
          public Long getLength() {return getLength();}
          
          @Override
          public byte[] getImageSimilarityFeatures() {return getImageSimilarityFeatures();}
          
          @Override
          public int getId() {return 0;}
          
          @Override
          public String getHash() {return getHash();}
          
          @Override
          public File getFile() {return file;}
          
          @Override
          public Map<String, Object> getExtraAttributeMap() {
              return new Map<String, Object>() {
                  
                  @Override
                  public Collection<Object> values() {return values();}
                  
                  @Override
                  public int size() {return 0;}
                  
                  @Override
                  public Object remove(Object key) {return key;}
                  
                  @Override
                  public void putAll(Map<? extends String, ? extends Object> m) {}
                  
                  @Override
                  public Object put(String key, Object value) {return value;}
                  
                  @Override
                  public Set<String> keySet() {return keySet();}
                  
                  @Override
                  public boolean isEmpty() {return false;}
                  
                  @Override
                  public Object get(Object key) {return key;}
                  
                  @Override
                  public Set<Entry<String, Object>> entrySet() {return new Set<Map.Entry<String,Object>>() {
                      
                      @Override
                      public <T> T[] toArray(T[] a) {return a;}
                      
                      @Override
                      public Object[] toArray() {return toArray();}
                      
                      @Override
                      public int size() {return 0;}
                      
                      @Override
                      public boolean retainAll(Collection<?> c) {return false;}
                      
                      @Override
                      public boolean removeAll(Collection<?> c) {return false;}
                      
                      @Override
                      public boolean remove(Object o) {return false;}
                      
                      @Override
                      public Iterator<Entry<String, Object>> iterator() {return new Iterator<Map.Entry<String,Object>>() {
                          
                          @Override
                          public Entry<String, Object> next() {return new Entry<String, Object>() {
                              
                              @Override
                              public Object setValue(Object value) {return value;}
                              
                              @Override
                              public Object getValue() {return getValue();}
                              
                              @Override
                              public String getKey() {return getKey();}
                          };}
                          
                          @Override
                          public boolean hasNext() {return false;}
                      };}
                      
                      @Override
                      public boolean isEmpty() {return false;}
                      
                      @Override
                      public boolean containsAll(Collection<?> c) {return false;}
                      
                      @Override
                      public boolean contains(Object o) {return false;}
                      
                      @Override
                      public void clear() {}
                      
                      @Override
                      public boolean addAll(Collection<? extends Entry<String, Object>> c) {return false;}
                      
                      @Override
                      public boolean add(Entry<String, Object> e) {return false;}
                  };}
                  
                  @Override
                  public boolean containsValue(Object value) {return false;}
                  
                  @Override
                  public boolean containsKey(Object key) {return false;}
                  
                  @Override
                  public void clear() {}
              };
          }
          
          @Override
          public Object getExtraAttribute(String key) {return key;}
          
          @Override
          public String getExt() {return "";}
          
          @Override
          public IDataSource getDataSource() {
              return new IDataSource() {
                  
                  @Override
                  public void setUUID(String uuid) {}
                  
                  @Override
                  public void setName(String name) {}
                  
                  @Override
                  public String getUUID() {return "0";}
                  
                  @Override
                  public File getSourceFile() {return file;}
                  
                  @Override
                  public String getName() {return "";}
              };
          }
          
          @Override
          public Date getCreationDate() {return null;}
          
          @Override
          public HashSet<String> getCategorySet() {return null;}
          
          @Override
          public BufferedInputStream getBufferedStream() throws IOException {return null;}
          
          @Override
          public Date getAccessDate() {return null;}

        @Override
        public Date getChangeDate() {return null;}
      };


      
      skypeContext = new ParseContext();
      skypeContext.set(ItemInfo.class, itemInfo);
      skypeContext.set(IItemSearcher.class, itemSearcher);
      skypeContext.set(IItemBase.class, itemBase);
   }
   
}

