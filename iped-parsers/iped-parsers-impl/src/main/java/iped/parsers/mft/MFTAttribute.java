package iped.parsers.mft;

import java.util.List;

public class MFTAttribute {
    private int type, len, nameLen, nameOffset, dataFlags, id, dataOffset;
    private long dataLength;
    private boolean isResident;
    private String name;
    private List<Long> dataruns;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getLen() {
        return len;
    }

    public void setLen(int len) {
        this.len = len;
    }

    public int getNameLen() {
        return nameLen;
    }

    public void setNameLen(int nameLen) {
        this.nameLen = nameLen;
    }

    public int getNameOffset() {
        return nameOffset;
    }

    public void setNameOffset(int nameOffset) {
        this.nameOffset = nameOffset;
    }

    public int getDataFlags() {
        return dataFlags;
    }

    public void setDataFlags(int dataFlags) {
        this.dataFlags = dataFlags;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getDataLength() {
        return dataLength;
    }

    public void setDataLength(long dataLength) {
        this.dataLength = dataLength;
    }

    public int getDataOffset() {
        return dataOffset;
    }

    public void setDataOffset(int dataOffset) {
        this.dataOffset = dataOffset;
    }

    public boolean isResident() {
        return isResident;
    }

    public void setResident(boolean isResident) {
        this.isResident = isResident;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDataruns(List<Long> dataruns) {
        this.dataruns = dataruns;
    }

    public List<Long> getDataruns() {
        return dataruns;
    }
}
