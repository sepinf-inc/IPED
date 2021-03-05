package dpf.sp.gpinf.indexer.desktop;

import java.awt.Color;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.filechooser.FileFilter;

import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.PythonTask;
import dpf.sp.gpinf.indexer.process.task.TaskInstaller;
import dpf.sp.gpinf.indexer.search.SimilarFacesSearch;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import gpinf.dev.data.CaseData;
import gpinf.dev.data.Item;
import iped3.IItem;
import iped3.IItemId;
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
                app.similarFacesRefItem.setFile(file);
                app.similarFacesRefItem.setMediaType(MediaType.image("unknown"));
                try (InputStream is = new FileInputStream(file)) {
                    BufferedImage img = ImageUtil.getSubSampledImage(is, 100, 100);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpg", baos);
                    app.similarFacesRefItem.setThumb(baos.toByteArray());
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                WaitDialog wait = new WaitDialog(app, Messages.getString("FaceSimilarity.LoadingFace"));

                FaceFeatureExtractor callable = new FaceFeatureExtractor(app.similarFacesRefItem,
                        app.appCase.getModuleDir()) {
                    @Override
                    protected void onFinish() {
                        wait.setVisible(false);
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

        private static final String SCRIPT_PATH = TaskInstaller.SCRIPT_BASE + "/FaceRecognitionTask.py";

        private static volatile PythonTask task;
        private IItem item;
        private File moduleDir;

        private FaceFeatureExtractor(IItem item, File moduleDir) {
            this.item = item;
            this.moduleDir = moduleDir;
        }

        protected abstract void onFinish();

        @Override
        public byte[] call() throws Exception {

            try {
                if (task == null) {
                    task = new PythonTask(new File(moduleDir, SCRIPT_PATH));
                    task.setCaseData(new CaseData(0));
                    task.init(Configuration.getInstance().properties, new File(moduleDir, "conf"));
                }

                task.process(item);
                // TODO enable when queue end is handled
                // Item queueEnd = new Item();
                // queueEnd.setQueueEnd(true);
                // task.process(queueEnd);

                List<NDArray> faces = (List<NDArray>) item.getExtraAttribute(SimilarFacesSearch.FACE_FEATURES);

                if (faces != null && faces.size() > 0) {
                    return IndexItem.convNDArrayToByteArray(faces.get(0));
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
