/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.app.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.controls.ErrorIcon;
import iped.data.IItemId;
import iped.engine.config.ConfigurationManager;
import iped.engine.preview.PreviewConstants;
import iped.engine.preview.PreviewKey;
import iped.engine.preview.PreviewRepositoryManager;
import iped.engine.task.ImageThumbTask;
import iped.engine.task.ThumbTask;
import iped.engine.task.index.IndexItem;
import iped.engine.task.video.VideoThumbTask;
import iped.engine.util.Util;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;
import iped.utils.ExternalImageConverter;
import iped.utils.HashValue;
import iped.utils.ImageUtil;
import iped.viewers.util.ImageMetadataUtil;

public class GalleryModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private static Logger LOGGER = LoggerFactory.getLogger(GalleryModel.class);

    /**
     * Max Sleuthkit connection pool size. Using more threads than this sometimes
     * caused deadlock in TSK if many streams are asked at the same time
     */
    private static final int MAX_TSK_POOL_SIZE = 20;

    public static final int defaultColCount = 10;

    private static final double blurIntensity = 0.02d;

    private int colCount = defaultColCount;
    private int thumbSize;
    private int galleryThreads = 1;
    private boolean logRendering = false;
    private ImageThumbTask imgThumbTask;

    private static final int maxCacheSize = 1000;
    public final Map<IItemId, GalleryValue> cache = new LinkedHashMap<IItemId, GalleryValue>() {
        private static final long serialVersionUID = 1L;

        protected boolean removeEldestEntry(Map.Entry<IItemId,GalleryValue> eldest) {
            return size() > maxCacheSize;
        }
    };

    private static final GalleryValue emptyValue = new GalleryValue("", null, null);

    private ErrorIcon errorIcon = new ErrorIcon();
    private static final BufferedImage errorImg = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
    public static final ImageIcon unsupportedIcon = new ImageIcon();
    private ExecutorService executor;
    private ExternalImageConverter externalImageConverter;

    private volatile boolean blurFilter;
    private volatile boolean grayFilter;

    public boolean getBlurFilter() {
        return blurFilter;
    }

    public boolean getGrayFilter() {
        return grayFilter;
    }

    public void setBlurFilter(boolean newBlurFilter) {
        blurFilter = newBlurFilter;
        synchronized (cache) {
            cache.clear();
        }
    }

    public void setGrayFilter(boolean newGrayFilter) {
        grayFilter = newGrayFilter;
        synchronized (cache) {
            cache.clear();
        }
    }

    @Override
    public int getColumnCount() {
        return colCount;
    }

    public void setColumnCount(int cnt) {
        colCount = cnt;
    }

    @Override
    public int getRowCount() {
        return (int) Math.ceil((double) App.get().ipedResult.getLength() / (double) colCount);
    }

    public boolean isCellEditable(int row, int col) {
        return true;
    }

    private boolean isSupportedImage(String mediaType) {
        return MetadataUtil.isImageType(mediaType);
    }

    private boolean isAnimationImage(Document doc, String mediaType) {
        return MetadataUtil.isImageSequence(mediaType) || doc.get(ExtraProperties.ANIMATION_FRAMES_PROP) != null;
    }

    private boolean isSupportedVideo(String mediaType) {
        return MetadataUtil.isVideoType(mediaType);
    }

    @Override
    public Class<?> getColumnClass(int c) {
        return GalleryCellRenderer.class;
    }

    @Override
    public Object getValueAt(final int row, final int col) {

        if (imgThumbTask == null) {
            try {
                imgThumbTask = new ImageThumbTask();
                imgThumbTask.init(ConfigurationManager.get());
                thumbSize = imgThumbTask.getImageThumbConfig().getThumbSize();
                galleryThreads = Math.min(imgThumbTask.getImageThumbConfig().getGalleryThreads(), MAX_TSK_POOL_SIZE);
                logRendering = imgThumbTask.getImageThumbConfig().isLogGalleryRendering();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int idx = row * colCount + col;
        if (idx >= App.get().ipedResult.getLength()) {
            return emptyValue;
        }

        idx = App.get().resultsTable.convertRowIndexToModel(idx);
        final IItemId id = App.get().ipedResult.getItem(idx);

        synchronized (cache) {
            GalleryValue value = cache.get(id);
            if (value != null) {
                return value;
            }
        }

        if (executor == null) {
            executor = Executors.newFixedThreadPool(galleryThreads);
            // do not use executor above in constructor below, it causes deadlock see #313
            externalImageConverter = new ExternalImageConverter();
        }

        executor.execute(new Runnable() {
            public void run() {

                BufferedImage image = null;
                InputStream stream = null;
                Document doc = null;
                GalleryValue value = new GalleryValue("", null, id);
                try {
                    synchronized (cache) {
                        if (cache.containsKey(id)) {
                            return;
                        }
                    }

                    if (!App.get().gallery.getVisibleRect().intersects(App.get().gallery.getCellRect(row, col, false))) {
                        return;
                    }

                    int docId = App.get().appCase.getLuceneId(id);
                    doc = App.get().appCase.getSearcher().doc(docId);
                    value.name = doc.get(IndexItem.NAME);

                    if (logRendering) {
                        String path = doc.get(IndexItem.PATH);
                        LOGGER.info("Gallery rendering " + path); //$NON-NLS-1$
                    }

                    final String mediaType = doc.get(IndexItem.CONTENTTYPE);

                    BytesRef bytesRef = doc.getBinaryValue(IndexItem.THUMB);
                    if (bytesRef != null && ((!isSupportedVideo(mediaType) && !isAnimationImage(doc, mediaType)) || App.get().useVideoThumbsInGallery)) {
                        byte[] thumb = bytesRef.bytes;
                        if (thumb.length > 0) {
                            image = ImageIO.read(new ByteArrayInputStream(thumb));
                        } else {
                            image = errorImg;
                        }
                    }

                    if (image == null) {
                        String hash = doc.get(IndexItem.HASH);
                        if (image == null && hash != null && !hash.isEmpty()) {
                            image = getViewImage(docId, hash,
                                    isSupportedVideo(mediaType) || isAnimationImage(doc, mediaType));
                        }

                        if (Boolean.valueOf(doc.get(IndexItem.ISDIR))) {
                            value.unsupportedType = true;
                            value.icon = IconManager.getFolderIconGallery();

                        } else if (image == null && !isSupportedImage(mediaType) && !isSupportedVideo(mediaType)) {
                            value.unsupportedType = true;
                            String type = doc.get(IndexItem.TYPE);
                            String contentType = doc.get(IndexItem.CONTENTTYPE);
                            value.icon = IconManager.getFileIconGallery(contentType, type);
                        }

                        if (image == null && value.icon == null && stream == null && isSupportedImage(mediaType)) {
                            stream = App.get().appCase.getItemByLuceneID(docId).getBufferedInputStream();
                        }

                        if (stream != null) {
                            stream.mark(10000000);
                        }

                        if (image == null && stream != null && imgThumbTask.getImageThumbConfig().isExtractThumb()
                                && mediaType.equals("image/jpeg")) { //$NON-NLS-1$
                            image = ImageMetadataUtil.getThumb(CloseShieldInputStream.wrap(stream));
                            stream.reset();
                        }

                        if (image == null && stream != null) {
                            image = ImageUtil.getSubSampledImage(stream, thumbSize);
                            stream.reset();
                        }

                        if (image == null && stream != null) {
                            String sizeStr = doc.get(IndexItem.LENGTH);
                            Long size = sizeStr == null ? null : Long.parseLong(sizeStr);
                            image = externalImageConverter.getImage(stream, thumbSize, false, size);
                        }
                    }

                    if (image == null || image == errorImg) {
                        if (value.icon == null)
                            value.icon = errorIcon;
                    } else {
                        // Resize image only if it is too large (> 2x the desired thumbSize)
                        if (image.getWidth() > thumbSize * 2 || image.getHeight() > thumbSize * 2) {
                            image = ImageUtil.resizeImage(image, thumbSize, thumbSize);
                        }

                        if (blurFilter) {
                            image = ImageUtil.blur(image, thumbSize, blurIntensity);
                        }
                        if (grayFilter) {
                            image = ImageUtil.grayscale(image);
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    value.icon = errorIcon;

                } finally {
                    try {
                        if (stream != null) {
                            stream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (image != errorImg) {
                    value.image = image;
                }

                synchronized (cache) {
                    cache.put(id, value);
                }

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        App.get().galleryModel.fireTableCellUpdated(row, col);
                    }
                });
            }
        });

        return new GalleryValue("...", null, id);
    }

    public void clearVideoThumbsInCache() {
        synchronized (cache) {
            Iterator<IItemId> it = cache.keySet().iterator();
            while (it.hasNext()) {
                IItemId id = it.next();
                int docId = App.get().appCase.getLuceneId(id);
                try {
                    Document doc = App.get().appCase.getSearcher().doc(docId);
                    String mediaType = doc.get(IndexItem.CONTENTTYPE);
                    if (isSupportedVideo(mediaType) || isAnimationImage(doc, mediaType)) {
                        it.remove();
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    private BufferedImage getViewImage(int docID, String hash, boolean isVideo) throws IOException {
        File modulesDir = App.get().appCase.getAtomicSource(docID).getModuleDir();
        File baseFolder;
        String ext;
        if (isVideo) {
            baseFolder = new File(modulesDir, PreviewConstants.VIEW_FOLDER_NAME);
            ext = VideoThumbTask.PREVIEW_EXT;
        } else {
            // for old cases, when image thumbs were not stored in index
            baseFolder = new File(modulesDir, ImageThumbTask.THUMBS_FOLDER_NAME);
            ext = ThumbTask.THUMB_EXT;
        }

        File hashFile = Util.getFileFromHash(baseFolder, hash, ext);
        if (hashFile.exists()) {
            BufferedImage image = ImageIO.read(hashFile);
            if (image == null) {
                return errorImg;
            } else {
                return image;
            }

        } else {
            try {
                PreviewKey key = new PreviewKey(new HashValue(hash).getBytes());
                AtomicReference<BufferedImage> result = new AtomicReference<>();
                PreviewRepositoryManager.get(baseFolder.getParentFile()).consumePreview(key, inputStream -> {
                    BufferedImage image = ImageIO.read(inputStream);
                    if (image == null) {
                        image = errorImg;
                    }
                    result.set(image);
                });
                return result.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        super.setValueAt(value, row, col);
        fireTableRowsUpdated(0, App.get().gallery.getRowCount() - 1);
    }
}
