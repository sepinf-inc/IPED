package dpf.sp.gpinf.indexer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;

public class IPEDCrawler {

    public static void main(String[] args) {

        String query = "name:(\"logs.db\" \"contacts.db\" \"contacts2.db\") OR nome:(\"logs.db\" \"contacts.db\" \"contacts2.db\")";

        File folderToScan = new File("Z:\\SINQ");
        File exportFolder = new File("F:\\teste-files\\android-calllogs");
        exportFolder.mkdirs();

        List<File> cases = searchCasesinFolder(folderToScan);
        System.out.println("Cases found: " + cases.size());
        AtomicInteger exported = new AtomicInteger(), caseNum = new AtomicInteger();
        Executor executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        if (cases.isEmpty()) {
            System.out.println("No cases found!!!");
            System.exit(0);
        }
        // initialize sleuthkit using just this thread
        try (IPEDSource ipedCase = new IPEDSource(cases.get(0))) {
        }
        for (File file : cases) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Searching in case " + file.getAbsolutePath());
                    try (IPEDSource ipedCase = new IPEDSource(file)) {
                        IPEDSearcher searcher = new IPEDSearcher(ipedCase, query);
                        List<Integer> itemIds = searcher.search().getIds();
                        System.out.println("Found " + itemIds.size() + " files.");
                        if (!itemIds.isEmpty()) {
                            System.out.println("Exporting...");
                            caseNum.getAndIncrement();
                        }
                        for (Integer id : itemIds) {
                            IItem item = ipedCase.getItemByID(id);
                            File parentDir = new File(exportFolder, "case_" + caseNum);
                            parentDir.mkdirs();
                            File target = new File(parentDir, Util.getValidFilename(Util.getNameWithTrueExt(item)));
                            String ext = "";
                            int idx, suffix = 0;
                            if ((idx = target.getName().lastIndexOf('.')) > -1) {
                                ext = target.getName().substring(idx);
                            } else {
                                idx = target.getName().length();
                            }
                            while (target.exists()) {
                                target = new File(parentDir, target.getName().substring(0, idx) + (++suffix) + ext);
                            }
                            try (InputStream in = item.getBufferedStream()) {
                                Files.copy(in, target.toPath());
                                exported.getAndIncrement();
                            } catch (Exception e0) {
                                e0.printStackTrace();
                            }
                        }
                        System.out.println("Exported " + exported + " files.");

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

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
