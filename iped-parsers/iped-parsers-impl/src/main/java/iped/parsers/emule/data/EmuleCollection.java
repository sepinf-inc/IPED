package iped.parsers.emule.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class EmuleCollection implements ECollection {

    private static final int COLLECTION_FILE_VERSION1_INITIAL = 0x01;
    private static final int COLLECTION_FILE_VERSION2_LARGEFILES = 0x02;
    private String m_sCollectionAuthorName;
    ArrayList<ECollectionFile> files = new ArrayList<>();

    public static EmuleCollection loadCollectionFile(ByteBuffer data) throws IOException {
        EmuleCollection result = new EmuleCollection();

        data.order(ByteOrder.LITTLE_ENDIAN);

        int version = data.getInt();
        if (version == COLLECTION_FILE_VERSION1_INITIAL || version == COLLECTION_FILE_VERSION2_LARGEFILES) {
            int headerTagCount = data.getInt();
            while (headerTagCount > 0) {
                Tag t = Tag.createTag(data, true);
                switch (t.getNameID()) {
                    case Tag.FT_FILENAME:
                        result.m_sCollectionAuthorName = t.getStr();
                        break;
                    case Tag.FT_COLLECTIONAUTHOR: {
                        result.m_sCollectionAuthorName = t.getStr();
                        break;
                    }
                    case Tag.FT_COLLECTIONAUTHORKEY: {
                        break;
                    }
                }

                headerTagCount--;
            }
            int fileCount = data.getInt();
            while (fileCount > 0) {

                result.files.add(EmuleCollectionFile.loadCollectionFile(data));

                fileCount--;
            }
        }

        return result;

    }

    public String getName() {
        return m_sCollectionAuthorName;
    }

    @Override
    public List<ECollectionFile> getFiles() {
        return files;
    }

}
