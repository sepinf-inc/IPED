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
package dpf.sp.gpinf.indexer.desktop;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;
import org.apache.lucene.util.BytesRef;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.HTMLReportTask;
import dpf.sp.gpinf.indexer.process.task.ImageThumbTask;
import dpf.sp.gpinf.indexer.process.task.VideoThumbTask;
import dpf.sp.gpinf.indexer.util.ErrorIcon;
import dpf.sp.gpinf.indexer.util.GalleryValue;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.ImageMetadataUtil;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItemId;

public class GalleryModel extends AbstractTableModel {

    private static Logger LOGGER = LoggerFactory.getLogger(GalleryModel.class);

    /**
     * Max Sleuthkit connection pool size. Using more threads than this sometimes
     * caused deadlock in TSK if many streams are asked at the same time
     */
    private static final int MAX_TSK_POOL_SIZE = 20;

    public int colCount = 10;
    private int thumbSize = 160;
    private int galleryThreads = 1;
    private boolean logRendering = false;
    ImageThumbTask imgThumbTask;

    public Map<IItemId, GalleryValue> cache = Collections.synchronizedMap(new LinkedHashMap<IItemId, GalleryValue>());
    private int maxCacheSize = 1000;
    private ErrorIcon errorIcon = new ErrorIcon();
    private BufferedImage errorImg = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY);
    private UnsupportedIcon unsupportedIcon = new UnsupportedIcon();
    private ExecutorService executor;
    private GraphicsMagicConverter magickConverter;

    @Override
    public int getColumnCount() {
        return colCount;
    }

    @Override
    public int getRowCount() {
        return (int) Math.ceil((double) App.get().ipedResult.getLength() / (double) colCount);
    }

    public boolean isCellEditable(int row, int col) {
        return true;
    }

    private boolean isSupportedImage(String mediaType) {
        return ImageThumbTask.isImageType(MediaType.parse(mediaType));
    }

    private boolean isSupportedVideo(String mediaType) {
        return VideoThumbTask.isVideoType(MediaType.parse(mediaType));
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
                imgThumbTask.init(Configuration.getInstance().properties,
                        new File(Configuration.getInstance().configPath + "/conf")); //$NON-NLS-1$
                thumbSize = imgThumbTask.thumbSize;
                galleryThreads = Math.min(imgThumbTask.galleryThreads, MAX_TSK_POOL_SIZE);
                logRendering = imgThumbTask.logGalleryRendering;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        int idx = row * colCount + col;
        if (idx >= App.get().ipedResult.getLength()) {
            return new GalleryValue("", null, null); //$NON-NLS-1$
        }

        idx = App.get().resultsTable.convertRowIndexToModel(idx);
        final IItemId id = App.get().ipedResult.getItem(idx);
        final int docId = App.get().appCase.getLuceneId(id);

        synchronized (cache) {
            if (cache.containsKey(id)) {
                return cache.get(id);
            }
        }

        final Document doc;
        try {
            doc = App.get().appCase.getSearcher().doc(docId);

        } catch (IOException e) {
            return new GalleryValue("", errorIcon, id); //$NON-NLS-1$
        }

        final String mediaType = doc.get(IndexItem.CONTENTTYPE);

        if (executor == null) {
            executor = Executors.newFixedThreadPool(galleryThreads);
            // do not use executor above in constructor below, it causes deadlock see #313
            magickConverter = new GraphicsMagicConverter();
        }

        executor.execute(new Runnable() {
            public void run() {

                BufferedImage image = null;
                InputStream stream = null;
                GalleryValue value = new GalleryValue(doc.get(IndexItem.NAME), null, id);
                boolean getDimension = true;
                try {
                    if (cache.containsKey(id)) {
                        return;
                    }

                    if (!App.get().gallery.getVisibleRect()
                            .intersects(App.get().gallery.getCellRect(row, col, false))) {
                        return;
                    }

                    if (logRendering) {
                        String path = doc.get(IndexItem.PATH);
                        LOGGER.info("Gallery rendering " + path); //$NON-NLS-1$
                    }

                    BytesRef bytesRef = doc.getBinaryValue(IndexItem.THUMB);
                    if (bytesRef != null && (!isSupportedVideo(mediaType) || App.get().useVideoThumbsInGallery)) {
                        byte[] thumb = bytesRef.bytes;
                        if (thumb.length > 0) {
                            image = ImageIO.read(new ByteArrayInputStream(thumb));
                        } else {
                            image = errorImg;
                        }
                    }

                    String hash = doc.get(IndexItem.HASH);
                    if (image == null && hash != null && !hash.isEmpty()) {
                        image = getViewImage(docId, hash, !isSupportedImage(mediaType));
                        int resizeTolerance = 4;
                        if (image != null) {
                            if (image.getWidth() < thumbSize - resizeTolerance
                                    && image.getHeight() < thumbSize - resizeTolerance) {
                                value.originalW = image.getWidth();
                                value.originalH = image.getHeight();
                                getDimension = false;
                            }
                        }
                    }

                    String export = doc.get(IndexItem.EXPORT);
                    if (image == null && export != null && !export.isEmpty() && isSupportedImage(mediaType)) {
                        image = getThumbFromFTKReport(
                                App.get().appCase.getAtomicSource(docId).getCaseDir().getAbsolutePath(), export);
                        getDimension = false;
                    }

                    if (image == null && !isSupportedImage(mediaType) && !isSupportedVideo(mediaType)) {
                        image = errorImg;
                        value.icon = unsupportedIcon;
                    }

                    if (image == null && stream == null && isSupportedImage(mediaType)) {
                        stream = App.get().appCase.getItemByLuceneID(docId).getBufferedStream();
                    }

                    if (stream != null) {
                        stream.mark(10000000);
                    }

                    if (stream != null && getDimension) {
                        Dimension d = ImageUtil.getImageFileDimension(stream);
                        if (d != null) {
                            value.originalW = d.width;
                            value.originalH = d.height;
                        }
                        stream.reset();
                    }

                    if (image == null && stream != null && ImageThumbTask.extractThumb
                            && mediaType.equals("image/jpeg")) { //$NON-NLS-1$
                        image = ImageMetadataUtil.getThumb(new CloseShieldInputStream(stream));
                        stream.reset();
                    }

                    if (image == null && stream != null) {
                        image = ImageUtil.getSubSampledImage(stream, thumbSize, thumbSize);
                        stream.reset();
                    }

                    if (image == null && stream != null) {
                        String sizeStr = doc.get(IndexItem.LENGTH);
                        Long size = sizeStr == null ? null : Long.parseLong(sizeStr);
                        image = magickConverter.getImage(stream, thumbSize, false, size);
                    }

                    if (image == null || image == errorImg) {
                        if (value.icon == null)
                            value.icon = errorIcon;
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

                cache.put(id, value);

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        App.get().galleryModel.fireTableCellUpdated(row, col);
                    }
                });

                synchronized (cache) {
                    Iterator<IItemId> i = cache.keySet().iterator();
                    while (cache.size() > maxCacheSize) {
                        i.next();
                        i.remove();
                    }

                }
            }
        });

        return new GalleryValue(doc.get(IndexItem.NAME), null, id);
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
                    if (isSupportedVideo(mediaType)) {
                        it.remove();
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    private BufferedImage getViewImage(int docID, String hash, boolean isVideo) throws IOException {
        File baseFolder = App.get().appCase.getAtomicSource(docID).getModuleDir();
        if (isVideo) {
            baseFolder = new File(baseFolder, HTMLReportTask.viewFolder);
        } else {
            baseFolder = new File(baseFolder, ImageThumbTask.thumbsFolder);
        }

        File hashFile = Util.getFileFromHash(baseFolder, hash, "jpg"); //$NON-NLS-1$
        if (hashFile.exists()) {
            BufferedImage image = ImageIO.read(hashFile);
            if (image == null) {
                return errorImg;
            } else {
                return image;
            }

        } else {
            return null;
        }
    }

    private BufferedImage getThumbFromFTKReport(String basePath, String export) {

        BufferedImage image = null;
        try {
            int i0 = export.lastIndexOf("/"); //$NON-NLS-1$
            String nome = export.substring(i0 + 1);
            int extIdx = nome.indexOf("."); //$NON-NLS-1$
            if (extIdx > -1) {
                nome = nome.substring(0, extIdx);
            }
            nome += ".jpg"; //$NON-NLS-1$

            // Report FTK3+
            int i1 = export.indexOf("files/"); //$NON-NLS-1$
            File file = null;
            if (i1 > -1) {
                String thumbPath = export.substring(0, i1) + "thumbnails/" + nome; //$NON-NLS-1$
                file = Util.getResolvedFile(basePath, thumbPath);
            }
            if (file != null && file.exists()) {
                image = ImageIO.read(file);
                image = ImageUtil.trim(image);
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }

        return image;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        super.setValueAt(value, row, col);
        fireTableRowsUpdated(0, App.get().gallery.getRowCount() - 1);
    }

}
