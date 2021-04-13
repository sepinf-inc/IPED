package dpf.sp.gpinf.indexer;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.mp4.MP4Parser;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;

public class IPEDCrawler {

    public static void main(String[] args) {

        String queryStr = "contentType:(";
        MediaType[] mimes = { 
                MediaType.application("pkcs7-mime"), 
                MediaType.application("pkcs7-signature"), 
                MediaType.application("timestamped-data"),
                MediaType.application("x-hwp-v5"), 
                MediaType.image("heif"), 
                MediaType.image("heif-sequence"),
                MediaType.image("heic"), 
                MediaType.image("heic-sequence"),
                MediaType.image("emf"), 
                MediaType.application("onenote"),
                MediaType.application("vnd.mif"),
                MediaType.application("x-maker"),
                MediaType.application("x-mif"),
                MediaType.application("vnd.oasis.opendocument.tika.flat.document"),
                MediaType.application("vnd.oasis.opendocument.flat.text"),
                MediaType.application("vnd.oasis.opendocument.flat.presentation"),
                MediaType.application("vnd.oasis.opendocument.flat.spreadsheet"),
                MediaType.application("x-sas-data"),
                MediaType.application("vnd.adobe.indesign-idml-package") };
        for (MediaType mime : new MP4Parser().getSupportedTypes(null)) {
            queryStr += "\"" + mime.toString() + "\" ";
        }
        queryStr += ")";
        final String query = queryStr;

        File folderToScan = new File("Z:\\SINQ");
        File exportFolder = new File("F:\\teste-files\\mp4-crawling");
        exportFolder.mkdirs();

        List<File> cases = searchCasesinFolder(folderToScan);
        System.out.println("Cases found: " + cases.size());
        AtomicInteger exported = new AtomicInteger(), counter = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
                    int caseNum = counter.incrementAndGet();
                    System.out.println("Searching in case "  + caseNum + ": " + file.getAbsolutePath());
                    try (IPEDSource ipedCase = new IPEDSource(file)) {
                        IPEDSearcher searcher = new IPEDSearcher(ipedCase, query);
                        List<Integer> itemIds = searcher.search().getIds();
                        System.out.println("Found " + itemIds.size() + " files.");
                        if (!itemIds.isEmpty()) {
                            System.out.println("Exporting...");
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
                            synchronized (exported) {
                                while (target.exists()) {
                                    target = new File(parentDir, target.getName().substring(0, idx) + (++suffix) + ext);
                                }
                                target.createNewFile();
                            }
                            try (InputStream in = item.getBufferedStream()) {
                                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
        try {
            executor.shutdown();
            executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            e.printStackTrace();
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
