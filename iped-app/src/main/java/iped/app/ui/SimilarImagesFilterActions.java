package iped.app.ui;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.filechooser.FileFilter;

import iped.data.IItemId;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.Item;
import iped.engine.task.similarity.ImageSimilarity;
import iped.engine.task.similarity.ImageSimilarityTask;
import iped.utils.ExternalImageConverter;
import iped.utils.IOUtil;
import iped.utils.ImageUtil;

public class SimilarImagesFilterActions {
    private static final int sampleFactor = 3;

    // do not instantiate here, makes external command adjustment fail, see #740
    private static ExternalImageConverter externalImageConverter;

    public static void clear() {
        clear(true);
    }

    public static void clear(boolean updateResults) {
        App app = App.get();
        if (app.similarImagesQueryRefItem != null) {
            app.similarImagesQueryRefItem = null;
            app.similarImageFilterPanel.setVisible(false);
            List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
            if (sortKeys != null && !sortKeys.isEmpty() && sortKeys.get(0).getColumn() == 2 && app.similarImagesPrevSortKeys != null)
                ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(app.similarImagesPrevSortKeys);
            app.similarImagesPrevSortKeys = null;
            if (updateResults)
                app.appletListener.updateFileListing();
        }
    }

    public static void searchSimilarImages(boolean external) {
        App app = App.get();
        if (external) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle(Messages.getString("ImageSimilarity.ExternalTitle"));
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.setFileFilter(new FileFilter() {
                public String getDescription() {
                    return Messages.getString("ImageSimilarity.Image");
                }

                public boolean accept(File f) {
                    return true;
                }
            });
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (fileChooser.showOpenDialog(App.get()) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            app.similarImagesQueryRefItem = null;
            File file = fileChooser.getSelectedFile();
            if (file != null) {
                BufferedImage img = null;
                BufferedInputStream is = null;
                try {
                    is = new BufferedInputStream(new FileInputStream(file));
                    img = ImageUtil.getSubSampledImage(is, ImageSimilarity.maxDim * sampleFactor);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    IOUtil.closeQuietly(is);
                }
                if (img == null) {
                    try {
                        is = new BufferedInputStream(new FileInputStream(file));
                        if (externalImageConverter == null) {
                            externalImageConverter = new ExternalImageConverter();
                        }
                        img = externalImageConverter.getImage(is, ImageSimilarity.maxDim, false, file.length());
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        IOUtil.closeQuietly(is);
                    }
                }
                if (img != null) {
                    img = ImageUtil.resizeImage(img, ImageSimilarity.maxDim, ImageSimilarity.maxDim, BufferedImage.TYPE_INT_RGB);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try {
                        ImageIO.write(img, "jpg", baos);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    app.similarImagesQueryRefItem = new Item();
                    app.similarImagesQueryRefItem.setName(file.getName());
                    app.similarImagesQueryRefItem.setThumb(baos.toByteArray());
                    app.similarImagesQueryRefItem.setExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES,
                            new ImageSimilarity().extractFeatures(img));
                } else {
                    JOptionPane.showMessageDialog(App.get(), Messages.getString("ImageSimilarity.ExternalError"),
                            Messages.getString("ImageSimilarity.ExternalTitle"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        } else {
            app.similarImagesQueryRefItem = null;
            int selIdx = app.resultsTable.getSelectedRow();
            if (selIdx != -1) {
                IItemId itemId = app.ipedResult.getItem(app.resultsTable.convertRowIndexToModel(selIdx));
                if (itemId != null) {
                    app.similarImagesQueryRefItem = app.appCase.getItemByItemId(itemId);
                    if (app.similarImagesQueryRefItem
                            .getExtraAttribute(ImageSimilarityTask.IMAGE_FEATURES) == null) {
                        app.similarImagesQueryRefItem = null;
                    }
                }
            }
        }

        if (app.similarImagesQueryRefItem != null) {
            List<? extends SortKey> sortKeys = app.resultsTable.getRowSorter().getSortKeys();
            if (sortKeys == null || sortKeys.isEmpty() || sortKeys.get(0).getColumn() != 2) {
                app.similarImagesPrevSortKeys = sortKeys;
                ArrayList<RowSorter.SortKey> sortScore = new ArrayList<RowSorter.SortKey>();
                sortScore.add(new RowSorter.SortKey(2, SortOrder.DESCENDING));
                ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(sortScore);
            }
            app.appletListener.updateFileListing();
        }
        app.similarImageFilterPanel.setCurrentItem(app.similarImagesQueryRefItem, external);
        app.similarImageFilterPanel.setVisible(app.similarImagesQueryRefItem != null);
    }

    public static boolean isFeatureEnabled() {
        return ConfigurationManager.get().getEnableTaskProperty(ImageSimilarityTask.enableParam);
    }
}
