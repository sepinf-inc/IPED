package iped.engine.util;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import iped.data.IItem;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;

public class IPEDCrawler {

    private static final boolean SKIP_KNOWN_FOLDERS = true;

    private static ConcurrentLinkedQueue<File> cases = new ConcurrentLinkedQueue<>();
    private static AtomicInteger numCases = new AtomicInteger();

    public static void main(String[] args) {

        if (args.length != 3) {
            System.err.println("Please provide exactly 3 parameters: input_folder export_folder search_query");
            System.exit(1);
        }

        File folderToScan = new File(args[0]);
        File exportFolder = new File(args[1]);
        String query = args[2];

        if (!folderToScan.exists() || !folderToScan.isDirectory()) {
            System.err.println("Input cases folder doesn't exist or is not a directory!");
            System.exit(2);
        }

        exportFolder.mkdirs();

        if (!exportFolder.exists() || !exportFolder.isDirectory()) {
            System.err.println("Export folder couldn't be created or is not a directory!");
            System.exit(3);
        }

        Thread folderScan = searchCasesinFolder(folderToScan);

        AtomicInteger counter = new AtomicInteger();
        AtomicInteger exported = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        boolean first = true;
        while (folderScan.isAlive() || !cases.isEmpty()) {
            File file = cases.poll();
            if (file == null) {
                continue;
            }
            if (first) {
                // initialize sleuthkit using just one thread
                System.out.println("Initializing from case " + file.getAbsolutePath());
                IPEDSource ipedCase = new IPEDSource(file, null, false);
                ipedCase.close();
                first = false;
            }
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    int caseNum = counter.incrementAndGet();
                    System.out.println("Searching for files into case " + caseNum + ": " + file.getAbsolutePath());
                    try (IPEDSource ipedCase = new IPEDSource(file, null, false)) {
                        IPEDSearcher searcher = new IPEDSearcher(ipedCase, query);
                        int[] itemIds = searcher.search().getIds();
                        System.out.println("Found " + itemIds.length + " files.");
                        if (itemIds.length == 0) {
                            return;
                        }
                        System.out.println("Exporting...");
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
                            try (InputStream in = item.getBufferedInputStream()) {
                                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                exported.getAndIncrement();
                            } catch (Exception e0) {
                                e0.printStackTrace();
                            }
                        }
                        System.out.println("Exported " + exported + " files.");

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        finished.incrementAndGet();
                    }
                }
            });

        }
        while (finished.get() < numCases.get()) {
            continue;
        }
        System.out.println("Exported " + exported + " files.");
        System.exit(0);

    }

    private static Thread searchCasesinFolder(File folder) {
        Thread t = new Thread() {
            public void run() {
                recurse(folder);
                System.out.println("Cases found: " + numCases.get());
            }
        };
        t.start();
        return t;
    }

    private static void recurse(File folder) {
        if (SKIP_KNOWN_FOLDERS) {
            String name = folder.getName();
            if (((name.equals("Exportados") || name.equals("Exported")) && (new File(folder.getParentFile(), IPEDSource.MODULE_DIR).exists() || new File(folder.getParentFile(), "indexador").exists())) ||
                    ((name.equals("report") || name.equals("relatorio")) && new File(folder, "thumbs").exists()) ||
                    (name.equals("indexador") && new File(folder, IPEDSource.INDEX_DIR).exists())) {
                return;
            }
        }
        if (new File(folder, IPEDSource.MODULE_DIR + "/" + IPEDSource.INDEX_DIR).exists()) {
            System.out.println("Case found in " + folder.getAbsolutePath());
            cases.add(folder);
            numCases.incrementAndGet();
        } else {
            System.out.println("Searching for cases in " + folder.getAbsolutePath());
            File[] subFiles = folder.listFiles();
            if (subFiles != null) {
                for (File file : subFiles) {
                    if (file.isDirectory()) {
                        recurse(file);
                    }
                }
            }
        }
    }

}

