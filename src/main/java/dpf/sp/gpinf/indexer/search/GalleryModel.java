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
package dpf.sp.gpinf.indexer.search;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.apache.lucene.document.Document;
import org.apache.tika.io.CloseShieldInputStream;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.ErrorIcon;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import dpf.sp.gpinf.indexer.util.UnsupportedIcon;
import dpf.sp.gpinf.indexer.util.Util;

public class GalleryModel extends AbstractTableModel {

	public int colCount = 8;
	public static int size = 150;
	public static int GALLERY_THREADS;

	public Map<Integer, GalleryValue> cache = Collections.synchronizedMap(new LinkedHashMap<Integer, GalleryValue>());
	private int maxCacheSize = 1000;
	private ErrorIcon errorIcon = new ErrorIcon();
	private UnsupportedIcon unsupportedIcon = new UnsupportedIcon();
	private ExecutorService executor;

	@Override
	public int getColumnCount() {
		return colCount;
	}

	@Override
	public int getRowCount() {
		return (int) Math.ceil((double) App.get().results.length / (double) colCount);
	}

	public boolean isCellEditable(int row, int col) {
		return true;
	}

	@Override
	public Class<?> getColumnClass(int c) {
		return GalleryCellRenderer.class;
	}

	@Override
	public Object getValueAt(final int row, final int col) {

		int idx = row * colCount + col;
		if (idx >= App.get().results.length)
			return new GalleryValue("", null, -1);

		idx = App.get().resultsTable.convertRowIndexToModel(idx);
		final int docId = App.get().results.docs[idx];
		final int id = App.get().ids[docId];

		if (cache.containsKey(id))
			return cache.get(id);

		final Document doc;
		try {
			doc = App.get().searcher.doc(docId);

		} catch (IOException e) {
			return new GalleryValue("", errorIcon, id);
		}

		final String mediaType = doc.get(IndexItem.CONTENTTYPE);
		if (!mediaType.startsWith("image") && !mediaType.endsWith("msmetafile") && !mediaType.endsWith("x-emf"))
			return new GalleryValue(doc.get(IndexItem.NAME), unsupportedIcon, id);
		
		if(executor == null)
			executor = Executors.newFixedThreadPool(GALLERY_THREADS);

		executor.execute(new Runnable() {
			public void run() {

				BufferedImage image = null;
				InputStream stream = null;
				GalleryValue value = new GalleryValue(doc.get(IndexItem.NAME), null, id);
				try {
					if (cache.containsKey(id))
						return;

					if (!App.get().gallery.getVisibleRect().intersects(App.get().gallery.getCellRect(row, col, false)))
						return;

					String export = doc.get(IndexItem.EXPORT);
					if (export != null && !export.isEmpty()) {

						image = getThumbFromReport(export);

						if (image == null) {
							File file = Util.getRelativeFile(App.get().codePath + "/../..", export);
							/*if(mediaType.equals("application/pdf")){
								PDFToImage pdfConverter = new PDFToImage();
								pdfConverter.load(file);
								file = File.createTempFile("indexador",".tmp");
								file.deleteOnExit();
								pdfConverter.convert(0, file);
								pdfConverter.close();
							}*/
							stream = Util.getStream(file, doc);
						}

					} else
						stream = Util.getSleuthStream(App.get().sleuthCase, doc);

					
					if (stream != null)
						stream.mark(1000000);
					
						
					if (image == null && doc.get(IndexItem.CONTENTTYPE).equals("image/jpeg")) {
						image = ImageUtil.getThumb(new CloseShieldInputStream(stream), value);
						stream.reset();
					}

					if (image == null) {
						image = ImageUtil.getSubSampledImage(stream, size, size, value);
						stream.reset();
					}
					
					if(image == null){
						image = new GraphicsMagicConverter().getImage(stream, size);
					}
					
					// fallBack
					/*if(image == null){
						image = ImageIO.read(stream);
						value.originalW = image.getWidth();
						value.originalH = image.getHeight();
						if(value.originalW > size || value.originalH > size) 
							image = ImageUtil.resizeImage(image, size, size);
					}*/
					 
					if (image == null)
						value.icon = errorIcon;

				} catch (Exception e) {
					//e.printStackTrace();
					value.icon = errorIcon;

				} finally {
					try {
						if (stream != null)
							stream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				value.image = image;
				cache.put(id, value);

				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						App.get().galleryModel.fireTableCellUpdated(row, col);
					}
				});

				synchronized (cache) {
					Iterator<Integer> i = cache.keySet().iterator();
					while (cache.size() > maxCacheSize) {
						i.next();
						i.remove();
					}

				}
			}
		});

		return new GalleryValue(doc.get(IndexItem.NAME), null, id);
	}

	private BufferedImage getThumbFromReport(String export) {

		BufferedImage image = null;
		try {
			int i0 = export.lastIndexOf("/");
			String nome = export.substring(i0 + 1);
			int extIdx = nome.indexOf(".");
			if (extIdx > -1)
				nome = nome.substring(0, extIdx);
			nome += ".jpg";

			// Report FTK3+
			int i1 = export.indexOf("files/");
			File file = null;
			if (i1 > -1) {
				String thumbPath = export.substring(0, i1) + "thumbnails/" + nome;
				file = Util.getRelativeFile(App.get().codePath + "/../..", thumbPath);

				// Report FTK 1.8
			} else if ((i1 = export.indexOf("Export/")) > -1) {

				if (!thumbPathCached)
					synchronized (this) {
						if (!thumbPathCached) {
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									App.get().dialogBar.setVisible(true);
								}
							});
							String thumbPath = export.substring(0, i1) + "Thumbnails";
							file = Util.getRelativeFile(App.get().codePath + "/../..", thumbPath).getCanonicalFile();
							if (file.exists())
								loadThumbPaths(file);
							thumbPathCached = true;
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									App.get().dialogBar.setVisible(false);
								}
							});
						}
					}
				file = thumbPathCache.get(nome);

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

	private HashMap<String, File> thumbPathCache = new HashMap<String, File>();
	private boolean thumbPathCached = false;

	private void loadThumbPaths(File file) {

		for (File subFile : file.listFiles())
			if (subFile.isDirectory())
				loadThumbPaths(subFile);
			else
				thumbPathCache.put(subFile.getName(), subFile);
	}

}
