package iped.app.ui;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.SAXException;

import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.config.AbstractTaskPropertiesConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FaceRecognitionConfig;
import iped.engine.config.TaskInstallerConfig;
import iped.engine.data.CaseData;
import iped.engine.data.Item;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.task.ImageThumbTask;
import iped.engine.task.PythonTask;
import iped.engine.task.index.IndexItem;
import iped.engine.task.index.IndexItem.KnnVector;
import iped.parsers.util.IgnoreContentHandler;
import iped.utils.FileInputStreamFactory;
import iped.utils.ImageUtil;

public class SimilarFacesFilterActions {

    private static ExecutorService executor = Executors.newSingleThreadExecutor();

    private static int minScore = 50;
    private static int mode = 0;

    public static void clear() {
        clear(true);
    }

    public static void clear(boolean updateResults) {
        App app = App.get();
        if (app.similarFacesRefItem != null) {
            app.similarFacesRefItem = null;
            app.similarFacesFilterPanel.setVisible(false);
            List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
            if (sortKeys != null && !sortKeys.isEmpty() && sortKeys.get(0).getColumn() == 2
                    && app.similarFacesPrevSortKeys != null)
                ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(app.similarFacesPrevSortKeys);
            app.similarFacesPrevSortKeys = null;
            if (updateResults)
                app.appletListener.updateFileListing();
        }
    }

    public static void searchSimilarFaces(boolean external) {
        App app = App.get();

        IItemId itemId = null;
        IItem newSimilarFacesRefItem = null;
        if (external) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle(Messages.getString("FaceSimilarity.ExternalTitle"));
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.setFileFilter(new FileFilter() {
                public String getDescription() {
                    return Messages.getString("FaceSimilarity.Image");
                }

                public boolean accept(File f) {
                    return true;
                }
            });
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (fileChooser.showOpenDialog(App.get()) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File file = fileChooser.getSelectedFile();
            if (file != null) {
                newSimilarFacesRefItem = new Item();
                newSimilarFacesRefItem.setName(file.getName());
                newSimilarFacesRefItem.setIdInDataSource("");
                newSimilarFacesRefItem.setInputStreamFactory(new FileInputStreamFactory(file.toPath()));

                // populates tif orientation if rotated
                try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                    App.get().getAutoParser().parse(bis, new IgnoreContentHandler(),
                            newSimilarFacesRefItem.getMetadata(), new ParseContext());
                } catch (IOException | SAXException | TikaException e2) {
                    e2.printStackTrace();
                }

                try (InputStream is = new FileInputStream(file)) {
                    BufferedImage img = ImageUtil.getSubSampledImage(is, 100);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpg", baos);
                    newSimilarFacesRefItem.setThumb(baos.toByteArray());
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                final WaitDialog wait = new WaitDialog(app, Messages.getString("FaceSimilarity.LoadingFace"));

                FaceFeatureExtractor callable = new FaceFeatureExtractor(newSimilarFacesRefItem,
                        app.appCase.getModuleDir()) {
                    @Override
                    protected void onFinish() {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                wait.setVisible(false);
                            }
                        });
                    }
                };
                Future<Void> future = executor.submit(callable);
                wait.setVisible(true);

                try {
                    future.get();

                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(App.get(), e.getMessage(),
                            Messages.getString("FaceSimilarity.ExternalTitle"), JOptionPane.ERROR_MESSAGE);
                    newSimilarFacesRefItem = null;
                }
            }
        } else {
            int selIdx = app.resultsTable.getSelectedRow();
            if (selIdx != -1) {
                itemId = app.ipedResult.getItem(app.resultsTable.convertRowIndexToModel(selIdx));
                if (itemId != null) {
                    newSimilarFacesRefItem = app.appCase.getItemByItemId(itemId);
                    if (newSimilarFacesRefItem.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES) == null) {
                        newSimilarFacesRefItem = null;
                    }
                }
            }
        }

        if (newSimilarFacesRefItem != null) {
            SimilarFacesOptionsDialog opt = new SimilarFacesOptionsDialog(app, newSimilarFacesRefItem, minScore, mode);
            opt.setVisible(true);
            if (opt.isOk()) {
                SimilarFacesSearch.setMinScore(minScore = opt.getMinScore());
                SimilarFacesSearch.setMode(mode = opt.getMode());
                SimilarFacesSearch.setSelectedIdxs(opt.getSelectedIdxs());
            } else {
                newSimilarFacesRefItem = null;
            }
            opt.dispose();
        }

        if (newSimilarFacesRefItem != null) {
            app.similarFacesRefItem = newSimilarFacesRefItem;
            app.similarFacesSearchFilterer.setItem(itemId, app.similarFacesRefItem);
    
            List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
            if (sortKeys == null || sortKeys.isEmpty() || sortKeys.get(0).getColumn() != 2) {
                app.similarFacesPrevSortKeys = sortKeys;
                ArrayList<RowSorter.SortKey> sortScore = new ArrayList<RowSorter.SortKey>();
                sortScore.add(new RowSorter.SortKey(2, SortOrder.DESCENDING));
                ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(sortScore);
            }
            app.appletListener.updateFileListing();

            app.similarFacesFilterPanel.setCurrentItem(app.similarFacesRefItem, external);
            app.similarFacesFilterPanel.setVisible(true);
        }
    }

    private abstract static class FaceFeatureExtractor implements Callable<Void> {

        private static final String SCRIPT_PATH = TaskInstallerConfig.SCRIPT_BASE + "/FaceRecognitionTask.py";
        private static final String CONF_FILE = "FaceRecognitionConfig.txt";
        private static final String NUM_PROCESSES = "numFaceRecognitionProcesses";

        private static volatile PythonTask task;
        private IItem item;
        private File moduleDir;

        private FaceFeatureExtractor(IItem item, File moduleDir) {
            this.item = item;
            this.moduleDir = moduleDir;
        }

        protected abstract void onFinish();

        private static void dispose() {
            if (task != null) {
                Future<Void> future = executor.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        // must be called on executor thread
                        task.finish();
                        return null;
                    }
                });
                try {
                    future.get(5, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public Void call() throws Exception {

            try {
                if (FaceFeatureExtractor.task == null) {
                    File script = new File(moduleDir, SCRIPT_PATH);
                    PythonTask task = new PythonTask(script);
                    task.setCaseData(new CaseData());
                    AbstractTaskPropertiesConfig taskConfig = (AbstractTaskPropertiesConfig) ConfigurationManager.get()
                            .getTaskConfigurable(CONF_FILE);

                    // if jep is not found, no config is returned for python tasks
                    if (taskConfig != null) {
                        taskConfig.getConfiguration().setProperty(NUM_PROCESSES, "1");
                    }
                    task.setThrowExceptionInsteadOfLogging(true);
                    task.init(ConfigurationManager.get());
                    Runtime.getRuntime().addShutdownHook(new Thread() {
                        public void run() {
                            dispose();
                        }
                    });
                    FaceFeatureExtractor.task = task;
                }

                // populate info used by task
                item.setMediaType(MediaType.image("unknown"));
                item.setExtraAttribute(ImageThumbTask.HAS_THUMB, true);
                item.setHash(DigestUtils.md5Hex(Files.readAllBytes(item.getTempFile().toPath())));

                FaceFeatureExtractor.task.process(item);
                // TODO enable when queue end is handled
                // Item queueEnd = new Item();
                // queueEnd.setQueueEnd(true);
                // task.process(queueEnd);

                @SuppressWarnings("unchecked")
                List<KnnVector> faces = (List<KnnVector>) item.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES);
                if (faces != null && !faces.isEmpty()) {
                    List<byte[]> faceEncodings = new ArrayList<byte[]>();
                    for (KnnVector face : faces) {
                        float[] array = IndexItem.convDoubleToFloatArray(face.getArray());
                        byte[] bytes = IndexItem.convFloatArrayToByteArray(array);
                        faceEncodings.add(bytes);
                    }
                    item.setExtraAttribute(SimilarFacesSearch.FACE_FEATURES, faceEncodings);
                } else {
                    throw new Exception(Messages.getString("FaceSimilarity.ExternalFaceNotFound"));
                }
            } finally {
                onFinish();
            }
            return null;
        }
    }

    public static boolean isFeatureEnabled() {
        return ConfigurationManager.get().getEnableTaskProperty(FaceRecognitionConfig.enableParam);
    }

    public static boolean isExternalSearchEnabled() {
        if (System.getProperty("os.name").startsWith("Windows")) {
            String ipedRoot = System.getProperty("iped.root");
            if (ipedRoot != null && new File(ipedRoot).exists()) {
                return true;
            }
            return false;
        }
        return true;
    }

    private static class WaitDialog extends JDialog {

        private static final long serialVersionUID = 1L;

        private JProgressBar progressBar;

        public WaitDialog(Frame frame, String msg) {
            super(frame, Dialog.ModalityType.APPLICATION_MODAL);

            progressBar = new JProgressBar(0, 1);
            progressBar.setValue(0);
            progressBar.setString(msg); // $NON-NLS-1$
            progressBar.setForeground(Color.WHITE);
            progressBar.setStringPainted(true);
            progressBar.setIndeterminate(true);

            this.setBounds(0, 0, 150, 30);
            this.setUndecorated(true);
            this.getContentPane().add(progressBar);
            this.setLocationRelativeTo(frame);
        }
    }
}
