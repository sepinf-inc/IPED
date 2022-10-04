package iped.parsers.mft;

public class MFTAttribute {
    private int type, len, nameLen, nameOffset, dataFlags, id, dataSize, dataOffset;
    private boolean isResident;
    private String name;

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

    public int getDataSize() {
        return dataSize;
    }

    public void setDataSize(int dataSize) {
        this.dataSize = dataSize;
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
}
