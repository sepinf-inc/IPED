package iped.parsers.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

import iped.data.IHashValue;
import iped.data.IItem;
import iped.data.IItemReader;
import iped.datasource.IDataSource;
import iped.io.ISeekableInputStreamFactory;
import iped.io.SeekableInputStream;
import iped.search.IItemSearcher;
import iped.utils.SeekableFileInputStream;
import junit.framework.TestCase;

public abstract class BaseItemSearchContext extends TestCase {

    protected File getFile(String name) throws IOException {
        try {
            return new File(BaseItemSearchContext.class.getClassLoader().getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    protected InputStream getStream(String name) throws IOException {
        return Files.newInputStream(getFile(name).toPath());
    }

    protected ParseContext getContext(String testFilePath) throws IOException {

        File file = getFile(testFilePath);
        IItem item = new IItem() {

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
            public boolean hasPreview() {
                return false;
            }

            @Override
            public File getPreviewBaseFolder() {
                return null;
            }

            @Override
            public String getPreviewExt() {
                return null;
            }

            @Override
            public String getType() {
                return this.getExt();
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
                return file.getPath();
            }

            @Override
            public Integer getParentId() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public String getName() {
                return file.getName();
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
                return file.length();
            }

            @Override
            public int getId() {
                // TODO Auto-generated method stub
                return 0;
            }

            @Override
            public String getHash() {
                try {
                    return DigestUtils.md5Hex(Files.readAllBytes(file.toPath()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            public String getExt() {
                int dotIdx = file.getName().lastIndexOf('.');
                if (dotIdx == -1)
                    return "";
                else
                    return file.getName().substring(dotIdx);
            }

            @Override
            public IDataSource getDataSource() {
                return new IDataSource() {

                    @Override
                    public void setUUID(String uuid) {
                    }

                    @Override
                    public void setName(String name) {
                    }

                    @Override
                    public String getUUID() {
                        return "0";
                    }

                    @Override
                    public File getSourceFile() {
                        return null;
                    }

                    @Override
                    public String getName() {
                        return "";
                    }
                };
            }

            @Override
            public void setViewFile(File viewFile) {
                // TODO Auto-generated method stub
            }

            @Override
            public void setHasPreview(boolean b) {
            }

            @Override
            public void setPreviewExt(String viewExt) {
            }

            @Override
            public void setType(String type) {
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
            public void setFileOffset(long fileOffset) {
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
            public void setDeleted(boolean deleted) {
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
            public TikaInputStream getTikaStream() throws IOException {
                return TikaInputStream.get(file.toPath());
            }

            @Override
            public Reader getTextReader() throws IOException {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public File getTempFile() throws IOException {
                return file;
            }

            @Override
            public Object getTempAttribute(String key) {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public SeekableInputStream getSeekableInputStream() throws IOException {
                return new SeekableFileInputStream(file);
            }

            @Override
            public SeekableByteChannel getSeekableByteChannel() throws IOException {
                return FileChannel.open(file.toPath());
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
            public long getFileOffset() {
                return -1;
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
            public BufferedInputStream getBufferedInputStream() throws IOException {
                return new BufferedInputStream(Files.newInputStream(file.toPath()));
            }

            @Override
            public ImageInputStream getImageInputStream() throws IOException {
                return ImageIO.createImageInputStream(file);
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

        ItemInfo itemInfo = new ItemInfo(0, item.getHash(), Collections.emptySet(), Collections.emptySet(),
                file.getPath(), false);

        IItemSearcher itemSearcher = new IItemSearcher() {

            @Override
            public void close() throws IOException {
                // no op
            }

            @Override
            public Iterable<IItemReader> searchIterable(String luceneQuery) {
                return Collections.emptyList();
            }

            @Override
            public List<IItemReader> search(String luceneQuery) {
                return Collections.emptyList();
            }

            @Override
            public String escapeQuery(String string) {
                return string;
            }
        };

        ParseContext context = new ParseContext();
        context.set(ItemInfo.class, itemInfo);
        context.set(IItemSearcher.class, itemSearcher);
        context.set(IItemReader.class, item);
        context.set(IItem.class, item);
        return context;

    }

}
