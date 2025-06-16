package iped.parsers.emule.data;

import java.net.URLDecoder;

public class ED2KURLCollectionFile implements ECollectionFile {
    private String name;
    private String hashStr;
    private long size;

    public ED2KURLCollectionFile(String name, String hashStr, String size) {
        this.name = name;
        this.hashStr = hashStr;
        try {
            this.size = Long.parseLong(size);
        } catch (Exception e) {
            this.size = 0;
        }
    }

    @Override
    public String getName() {
        String result;
        try {
            result = URLDecoder.decode(name);// removes any URL escape character from file name
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return name;
    }

    @Override
    public String getHashStr() {
        return hashStr;
    }

    public long getSize() {
        return size;
    }

}
