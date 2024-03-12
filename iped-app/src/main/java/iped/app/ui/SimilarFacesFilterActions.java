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
import iped.engine.config.TaskInstallerConfig;
import iped.engine.data.CaseData;
import iped.engine.data.Item;
import iped.engine.search.SimilarFacesSearch;
import iped.engine.task.ImageThumbTask;
import iped.engine.task.PythonTask;
import iped.engine.task.index.IndexItem;
import iped.parsers.util.IgnoreContentHandler;
import iped.utils.FileInputStreamFactory;
import iped.utils.ImageUtil;
import jep.NDArray;

public class SimilarFacesFilterActions {

    private static ExecutorService executor = Executors.newSingleThreadExecutor();

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
                ((ResultTableRowSorter) app.resultsTable.getRowSorter())
                        .setSortKeysSuper(app.similarFacesPrevSortKeys);
            app.similarFacesPrevSortKeys = null;
            if (updateResults)
                app.appletListener.updateFileListing();
        }
    }

    public static void searchSimilarImages(boolean external) {
        App app = App.get();

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
            app.similarFacesRefItem = null;
            File file = fileChooser.getSelectedFile();
            if (file != null) {
                app.similarFacesRefItem = new Item();
                app.similarFacesRefItem.setName(file.getName());
                app.similarFacesRefItem.setIdInDataSource("");
                app.similarFacesRefItem.setInputStreamFactory(new FileInputStreamFactory(file.toPath()));

                // populates tif orientation if rotated
                try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                    App.get().getAutoParser().parse(bis, new IgnoreContentHandler(),
                            app.similarFacesRefItem.getMetadata(), new ParseContext());
                } catch (IOException | SAXException | TikaException e2) {
                    e2.printStackTrace();
                }

                try (InputStream is = new FileInputStream(file)) {
                    BufferedImage img = ImageUtil.getSubSampledImage(is, 100);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpg", baos);
                    app.similarFacesRefItem.setThumb(baos.toByteArray());
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                final WaitDialog wait = new WaitDialog(app, Messages.getString("FaceSimilarity.LoadingFace"));

                FaceFeatureExtractor callable = new FaceFeatureExtractor(app.similarFacesRefItem,
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
                Future<byte[]> future = executor.submit(callable);
                wait.setVisible(true);

                try {
                    byte[] features = future.get();
                    app.similarFacesRefItem.setExtraAttribute(SimilarFacesSearch.FACE_FEATURES, features);

                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(App.get(), e.getMessage(),
                            Messages.getString("FaceSimilarity.ExternalTitle"), JOptionPane.ERROR_MESSAGE);
                    app.similarFacesRefItem = null;
                }
            }
        } else {
            app.similarFacesRefItem = null;
            int selIdx = app.resultsTable.getSelectedRow();
            if (selIdx != -1) {
                IItemId itemId = app.ipedResult.getItem(app.resultsTable.convertRowIndexToModel(selIdx));
                if (itemId != null) {
                    app.similarFacesRefItem = app.appCase.getItemByItemId(itemId);
                    if (app.similarFacesRefItem.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES) == null) {
                        app.similarFacesRefItem = null;
                    }
                }
            }
        }

        if (app.similarFacesRefItem != null) {
            int minScore = 0;
            while (minScore == 0) {
                try {
                    String input = JOptionPane.showInputDialog(app, Messages.getString("FaceSimilarity.MinScore"),
                            SimilarFacesSearch.getMinScore());
                    minScore = Integer.parseInt(input.trim());
                    if (minScore < 1 || minScore > 100) {
                        minScore = 0;
                        continue;
                    }
                    SimilarFacesSearch.setMinScore(minScore);

                } catch (NumberFormatException e) {
                }
            }
        }

        if (app.similarFacesRefItem != null) {
            List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
            if (sortKeys == null || sortKeys.isEmpty() || sortKeys.get(0).getColumn() != 2) {
                app.similarFacesPrevSortKeys = sortKeys;
                ArrayList<RowSorter.SortKey> sortScore = new ArrayList<RowSorter.SortKey>();
                sortScore.add(new RowSorter.SortKey(2, SortOrder.DESCENDING));
                ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(sortScore);
            }
            app.appletListener.updateFileListing();
        }
        app.similarFacesFilterPanel.setCurrentItem(app.similarFacesRefItem, external);
        app.similarFacesFilterPanel.setVisible(app.similarFacesRefItem != null);
    }

    private abstract static class FaceFeatureExtractor implements Callable<byte[]> {

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
        public byte[] call() throws Exception {

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

                List<NDArray> faces = (List<NDArray>) item.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES);

                if (faces != null && faces.size() > 0) {
                    float[] array = IndexItem.convNDArrayToFloatArray(faces.get(0));
                    return IndexItem.convFloatArrayToByteArray(array);
                } else {
                    throw new Exception(Messages.getString("FaceSimilarity.ExternalFaceNotFound"));
                }
            } finally {
                onFinish();
            }

        }

    }

    public static boolean isFeatureEnabled() {
        return true;
    }

    private static class WaitDialog extends JDialog {

        /**
         * 
         */
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
