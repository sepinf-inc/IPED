package iped.engine.datasource.ad1;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 *
 * @author guilherme.dutra
 */
public class FileHeader {

    // private static final int FILESIZE_CODE = 3;
    private static final int ACCESSED_DATE_CODE = 7;
    private static final int CREATED_DATE_CODE = 8;
    private static final int MODIFIED_DATE_CODE = 9;
    private static final int RECORD_DATE_CODE = 40962;

    private AD1Extractor extractor;

    public FileHeader parent;

    public long object_address = 0L;
    public long nextObjAddress = 0L;
    public long childAddress = 0L;
    public long object_PC_partial_end = 0L;
    public long object_PC_partial_start = 0L;
    private long objectSizeBytes = 0L;
    public int objectType = 0;
    public long objectNameSize = 0L;
    public String objectName = "";
    public long objectChunkSize = 0L;
    public String path = "";
    public List<Chunk> chunkList = null;
    public Map<Integer, Property> propertiesMap = new HashMap<>();

    private SimpleDateFormat simpleDateFormat = null;

    public void setObjectSizeBytes(long bytes) {
        this.objectSizeBytes = bytes;
    }

    public long getFileSize() {
        return objectSizeBytes;
    }

    public FileHeader(AD1Extractor extractor, FileHeader parent) {
        this.extractor = extractor;
        this.parent = parent;
        chunkList = new ArrayList<>();
        simpleDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public FileHeader getNextHeader() throws IOException {
        if (nextObjAddress == 0)
            return null;

        return extractor.readObject(nextObjAddress, parent);
    }

    public FileHeader getChildHeader() throws IOException {
        if (childAddress == 0)
            return null;

        return extractor.readObject(childAddress, this);
    }

    public Date getATime() {

        Property prop = propertiesMap.get(ACCESSED_DATE_CODE);
        if (prop != null) {
            try {
                return simpleDateFormat.parse(prop.getValue());

            } catch (ParseException e) {
                System.out.println("Error reading ATime from " + getFilePath() + " " + e.toString());
            }
        }

        return null;
    }

    public Date getCTime() {

        Property prop = propertiesMap.get(CREATED_DATE_CODE);
        if (prop != null) {
            try {
                return simpleDateFormat.parse(prop.getValue());

            } catch (ParseException e) {
                System.out.println("Error reading CTime from " + getFilePath() + " " + e.toString());
            }
        }

        return null;
    }

    public Date getMTime() {

        Property prop = propertiesMap.get(MODIFIED_DATE_CODE);
        if (prop != null) {
            try {
                return simpleDateFormat.parse(prop.getValue());

            } catch (ParseException e) {
                System.out.println("Error reading MTime from " + getFilePath() + " " + e.toString());
            }
        }

        return null;
    }

    public Date getRTime() {

        Property prop = propertiesMap.get(RECORD_DATE_CODE);
        if (prop != null) {
            try {
                return simpleDateFormat.parse(prop.getValue());

            } catch (ParseException e) {
                System.out.println("Error reading RTime from " + getFilePath() + " " + e.toString());
            }
        }

        return null;
    }

    public boolean hasChildren() {
        return childAddress != 0;
    }

    public boolean isDirectory() {
        return (objectType & 0x04) == 4;
    }

    public boolean isDeleted() {
        return (objectType & 0x02) == 2;
    }

    public String getFilePath() {
        return path;
    }

    public String getFileName() {
        return objectName;
    }

    public boolean isEncrypted() {

        return false;

    }

    public void addChunk(long ini, long fim) {

        chunkList.add(new Chunk(ini, fim));

    }

}
