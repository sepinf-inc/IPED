package br.gov.pf.labld.graph.desktop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.codec.digest.DigestUtils;

import br.gov.pf.labld.graph.GraphFileWriter;
import br.gov.pf.labld.graph.GraphGenerator;
import br.gov.pf.labld.graph.GraphImportRunner.ImportListener;
import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.GraphTask;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.Messages;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.desktop.CancelableWorker;
import iped3.desktop.ProgressDialog;

class LoadGraphDatabaseWorker extends SwingWorker<Void, Void> {

    private final AppGraphAnalytics app;
    private volatile boolean loaded = false;

    LoadGraphDatabaseWorker(AppGraphAnalytics appGraphAnalytics) {
        app = appGraphAnalytics;
    }

    @Override
    protected Void doInBackground() throws Exception {
        long t = System.currentTimeMillis();
        app.setEnabled(false);
        List<IPEDSource> cases = App.get().appCase.getAtomicSources();
        if (cases.size() == 1) {
            loaded = initGraphService(new File(cases.get(0).getModuleDir(), GraphTask.DB_PATH));
        } else {
            String caseNames = cases.stream().map(c -> c.getCaseDir().getName()).sorted()
                    .collect(Collectors.joining("-"));
            String hash = DigestUtils.md5Hex(caseNames);
            File multiCaseGraphPath = new File(App.get().casesPathFile.getParentFile(),
                    "iped-graph/multicase-" + hash + "/database");
            createMultiCaseGraph(cases, multiCaseGraphPath);
            if (multiCaseGraphPath.exists()) {
                loaded = initGraphService(multiCaseGraphPath);
            }
        }
        AppGraphAnalytics.LOGGER.info("Init graph database took {}s", (System.currentTimeMillis() - t) / 1000);
        return null;
    }

    private void createMultiCaseGraph(List<IPEDSource> cases, File graphDir) {
        if (!graphDir.exists()) {
            int option = JOptionPane.showConfirmDialog(App.get(), Messages.getString("GraphAnalysis.CreateMultiGraph"));
            if (option != JOptionPane.YES_OPTION)
                return;
            try {
                if (graphDir.getParentFile().exists())
                    IOUtil.deleteDirectory(graphDir.getParentFile(), false);
                File tempDir = new File(graphDir.getAbsolutePath() + "_temp");
                ImportWorker importer = new ImportWorker(cases, tempDir);
                importer.execute();
                if (importer.get()) {
                    Files.move(tempDir.toPath(), graphDir.toPath());
                }
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        JOptionPane.showMessageDialog(App.get(), Messages.getString("GraphAnalysis.MultiGraphOk"));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(App.get(), Messages.getString("GraphAnalysis.MultiGraphError"), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class ImportWorker extends CancelableWorker<Boolean, Void> implements ImportListener {

        List<IPEDSource> cases;
        File graphOut;
        ProgressDialog progress;

        private ImportWorker(List<IPEDSource> cases, File graphOut) {
            this.cases = cases;
            this.graphOut = graphOut;
            this.progress = new ProgressDialog(App.get(), this);
            progress.setIndeterminate(true);
            progress.setExtraWidth(100);
        }

        @Override
        public void output(String line) {
            if (!line.trim().isEmpty())
                progress.setNote(line.trim());
            else
                progress.setNote("Importing...");
        }

        @Override
        protected Boolean doInBackground() throws IOException {
            try {
                List<File> csvParents = cases.stream().map(c -> new File(c.getModuleDir(), GraphTask.GENERATED_PATH))
                        .collect(Collectors.toList());
                File preparedCSVs = new File(graphOut.getParentFile(), "generated");
                GraphFileWriter.prepareMultiCaseCSVs(preparedCSVs, csvParents);
                GraphGenerator graphGenerator = new GraphGenerator();
                return graphGenerator.generate(this, graphOut, preparedCSVs.listFiles());
            } finally {
                progress.close();
            }
        }
    }

    private boolean initGraphService(File dbFile) {
        app.setStatus(Messages.getString("GraphAnalysis.Preparing"));
        app.setProgress(50);

        final ClassLoader classLoader = this.getClass().getClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
        try {
            if (!dbFile.exists()) {
                AppGraphAnalytics.LOGGER.error("Graph database not found: " + dbFile.getAbsolutePath());
                return false;
            }
            if (!dbFile.canWrite() || !IOUtil.canCreateFile(dbFile)) {
                dbFile = copyToTempFolder(dbFile);
            }
            graphService.start(dbFile);
            return true;
        } catch (Throwable e) {
            AppGraphAnalytics.LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private File copyToTempFolder(File db) throws IOException {
        File tmpDb = new File(System.getProperty("java.io.tmpdir"), "iped-graph" + db.lastModified());
        IOUtil.copiaDiretorio(db, tmpDb, true);
        return tmpDb;
    }

    @Override
    protected void done() {
        app.setStatus(Messages.getString("GraphAnalysis.Ready"));
        app.setProgress(100);
        app.setEnabled(loaded);
        app.setDatabaseLoaded(loaded);
    }
}