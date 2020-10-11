package dpf.sp.gpinf.indexer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import iped3.IItem;

public class IPEDCrawler {

    public static void main(String[] args) {

        // String query = "name:(\"cloud_graph.db\" \"snapshot.db\") OR
        // nome:(\"cloud_graph.db\" \"snapshot.db\")";
        String query = "name:(\"cache4.db\" \"db_sqlite\") OR nome:(\"cache4.db\" \"db_sqlite\")";

        File folderToScan = new File("Z:\\SINQ");
        File exportFolder = new File("e:\\search-export");
        exportFolder.mkdirs();

        List<File> cases = searchCasesinFolder(folderToScan);
        System.out.println("Cases found: " + cases.size());
        int exported = 0;
        for (File file : cases) {
            System.out.println("Searching in case " + file.getAbsolutePath());
            try (IPEDSource ipedCase = new IPEDSource(file)) {
                IPEDSearcher searcher = new IPEDSearcher(ipedCase, query);
                List<Integer> itemIds = searcher.search().getIds();
                System.out.println("Found " + itemIds.size() + " files.");
                if (!itemIds.isEmpty())
                    System.out.println("Exporting...");
                for (Integer id : itemIds) {
                    IItem item = ipedCase.getItemByID(id);
                    File target = new File(exportFolder, item.getName());
                    String ext = "";
                    int idx, suffix = 0;
                    if ((idx = target.getName().lastIndexOf('.')) > -1) {
                        ext = target.getName().substring(idx);
                    } else {
                        idx = target.getName().length();
                    }
                    while (target.exists()) {
                        target = new File(exportFolder, target.getName().substring(0, idx) + (++suffix) + ext);
                    }
                    try (InputStream in = item.getBufferedStream()) {
                        Files.copy(in, target.toPath());
                        exported++;
                    } catch (Exception e0) {
                        e0.printStackTrace();
                    }
                }
                System.out.println("Exported " + exported + " files.");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private static List<File> searchCasesinFolder(File folder) {
        ArrayList<File> files = new ArrayList<File>();
        if (folder.isDirectory()) {
            if (new File(folder, IPEDSource.MODULE_DIR).exists()) {
                files.add(folder);
            } else {
                File[] subFiles = folder.listFiles();
                if (subFiles != null) {
                    for (File file : subFiles) {
                        files.addAll(searchCasesinFolder(file));
                    }
                }
            }
        }
        return files;
    }

}
