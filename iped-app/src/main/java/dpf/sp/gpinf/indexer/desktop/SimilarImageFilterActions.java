package dpf.sp.gpinf.indexer.desktop;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.filechooser.FileFilter;

import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import gpinf.dev.data.Item;
import gpinf.similarity.ImageSimilarity;
import iped3.IItemId;

public class SimilarImageFilterActions {
    private static final int sampleFactor = 3;
    private static final GraphicsMagicConverter graphicsMagicConverter = new GraphicsMagicConverter();

    public static void clear() {
        App app = App.get();
        app.similarImagesQueryRefItem = null;
        app.similarImageFilterPanel.setVisible(false);
        app.appletListener.updateFileListing();
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
                    img = ImageUtil.getSubSampledImage(is, ImageSimilarity.maxDim * sampleFactor, ImageSimilarity.maxDim
                            * sampleFactor);
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
                    app.similarImagesQueryRefItem.setImageSimilarityFeatures(new ImageSimilarity().extractFeatures(
                            img));
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
            ArrayList<RowSorter.SortKey> sortScore = new ArrayList<RowSorter.SortKey>();
            sortScore.add(new RowSorter.SortKey(2, SortOrder.DESCENDING));
            ((ResultTableRowSorter) app.resultsTable.getRowSorter()).setSortKeysSuper(sortScore);
            app.appletListener.updateFileListing();
        }
        app.similarImageFilterPanel.setCurrentItem(app.similarImagesQueryRefItem);
        app.similarImageFilterPanel.setVisible(app.similarImagesQueryRefItem != null);
    }
}
