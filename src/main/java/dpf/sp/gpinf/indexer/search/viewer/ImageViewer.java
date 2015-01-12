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
package dpf.sp.gpinf.indexer.search.viewer;

import gpinf.led.ImageUtil;
import gpinf.led.ImageViewPanel;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;

public class ImageViewer extends AbstractViewer {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6992477374808107778L;

	private ImageViewPanel imagePanel;

	public ImageViewer() {
		super(new GridLayout());
		imagePanel = new ImageViewPanel();
		this.getPanel().add(imagePanel);

	}

	@Override
	public String getName() {
		return "Imagem";
	}

	@Override
	public boolean isSupportedType(String contentType) {
		return 	/*contentType.equals("image/gif") || 
				contentType.equals("image/jpeg") || 
				contentType.equals("image/x-ms-bmp") ||
				contentType.equals("image/png");
				*/
				contentType.startsWith("image");
				//contentType.endsWith("msmetafile") || 
				//contentType.endsWith("x-emf"); 
	}

	@Override
	public void loadFile(File file, Set<String> highlightTerms) {

		BufferedImage image = null;
		if(file != null)
			try {
				image = ImageUtil.read(file);
				
			} catch (IOException e) {
				
				try {
					BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
					image = new GraphicsMagicConverter().getImage(in, 0);
					
				} catch (FileNotFoundException e1) {
					//e1.printStackTrace();
				}
				
			}
		
		final BufferedImage img = image;

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				imagePanel.setImage(img);
			}
		});

	}

	@Override
	public void init() {
		// TODO Auto-generated method stub

	}

	@Override
	public void copyScreen(Component comp) {
		BufferedImage image = imagePanel.getImage();

		TransferableImage trans = new TransferableImage(image);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(trans, trans);
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public void scrollToNextHit(boolean forward) {
		// TODO Auto-generated method stub

	}

}
