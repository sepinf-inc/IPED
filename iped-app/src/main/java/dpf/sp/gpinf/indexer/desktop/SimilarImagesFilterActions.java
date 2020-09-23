package dpf.sp.gpinf.indexer.desktop;

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
import javax.swing.SortOrder;
import javax.swing.RowSorter.SortKey;
import javax.swing.filechooser.FileFilter;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.IPEDConfig;
import dpf.sp.gpinf.indexer.process.task.ImageSimilarityTask;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import gpinf.dev.data.Item;
import gpinf.similarity.ImageSimilarity;
import iped3.IItemId;

public class SimilarImagesFilterActions {
    private static final int sampleFactor = 3;
    private static final GraphicsMagicConverter graphicsMagicConverter = new GraphicsMagicConverter();

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
                    img = ImageUtil.getSubSampledImage(is, ImageSimilarity.maxDim * sampleFactor,
                            ImageSimilarity.maxDim * sampleFactor);
                } catch (Exception e) {
                } finally {
                    IOUtil.closeQuietly(is);
                }
                if (img == null) {
                    try {
                        is = new BufferedInputStream(new FileInputStream(file));
                        img = graphicsMagicConverter.getImage(is, ImageSimilarity.maxDim * sampleFactor);
                    } catch (Exception e) {
                    } finally {
                        IOUtil.closeQuietly(is);
                    }
                }
                if (img != null) {
                    img = ImageUtil.resizeImage(img, ImageSimilarity.maxDim, ImageSimilarity.maxDim);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try {
                        ImageIO.write(img, "jpg", baos);
                    } catch (Exception e) {
                    }
                    app.similarImagesQueryRefItem = new Item();
                    app.similarImagesQueryRefItem.setName(file.getName());
                    app.similarImagesQueryRefItem.setThumb(baos.toByteArray());
                    app.similarImagesQueryRefItem
                            .setImageSimilarityFeatures(new ImageSimilarity().extractFeatures(img));
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
                    if (app.similarImagesQueryRefItem.getImageSimilarityFeatures() == null) {
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
        IPEDConfig ipedConfig = (IPEDConfig) ConfigurationManager.getInstance().findObjects(IPEDConfig.class).iterator()
                .next();
        String enabled = ipedConfig.getApplicationConfiguration().getProperty(ImageSimilarityTask.enableParam);
        if (enabled != null && enabled.trim().equalsIgnoreCase(Boolean.TRUE.toString())) {
            return true;
        }
        return false;
    }
}
