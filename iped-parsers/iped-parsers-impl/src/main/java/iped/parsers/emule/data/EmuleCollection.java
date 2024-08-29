package iped.parsers.emule.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class EmuleCollection implements Collection {

    private static final int COLLECTION_FILE_VERSION1_INITIAL = 0x01;
    private static final int COLLECTION_FILE_VERSION2_LARGEFILES = 0x02;
    private String m_sCollectionAuthorName;
    ArrayList<CollectionFile> files = new ArrayList<>();

    public static EmuleCollection loadCollectionFile(File f) throws FileNotFoundException, IOException {
        return loadCollectionFile(ByteBuffer.wrap(new FileInputStream(f).readAllBytes()));

    }

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

    public static void main(String args[]) {

        File dir = new File("/mnt/shares/10.65.6.13/pedo/mat_1535_2024/WD-WXL1A394SXJ0/emulecollections/");

        for (File f : dir.listFiles()) {
            try {
                EmuleCollection c = EmuleCollection.loadCollectionFile(f);

                System.out.println("");
                System.out.println("Collection--------------------------------------------------------");
                System.out.println(c.getName());
                System.out.println("------------------------------------------------------------------");
                for (CollectionFile cf : c.files) {
                    System.out.println("Filename:" + cf.getName());
                    System.out.println("Hash:" + cf.getHashStr());
                    System.out.println("------");
                }

            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    public String getName() {
        return m_sCollectionAuthorName;
    }

    @Override
    public List<CollectionFile> getFiles() {
        return files;
    }

}
