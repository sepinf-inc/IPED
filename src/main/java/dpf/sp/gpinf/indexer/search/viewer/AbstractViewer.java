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

import java.awt.Component;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractViewer {

	private static Logger LOGGER = LoggerFactory.getLogger(AbstractViewer.class);
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private JPanel panel;

	protected int currentHit, totalHits;

	/*
	 * Construtor
	 */
	public AbstractViewer() {
		panel = new JPanel();
	}

	public AbstractViewer(LayoutManager layout) {
		panel = new JPanel(layout);
	}

	public JPanel getPanel() {
		return panel;
	}

	abstract public String getName();

	/*
	 * Retorna se o visualizador suporta o tipo de arquivo informado
	 */
	abstract public boolean isSupportedType(String contentType);

	/*
	 * Método de inicialização do visualizador, possivelmente lenta, para ser
	 * chamado fora da thread de eventos.
	 */
	abstract public void init();

	/*
	 * Libera os recursos utilizados pelo visualizador
	 */
	abstract public void dispose();

	/*
	 * Renderiza o arquivo. Valor nulo deve indicar limpeza da visualização
	 */
	public void loadFile(File file, String contentType, Set<String> highlightTerms) {
		loadFile(file, highlightTerms);
	}

	public void loadFile(File file) {
		loadFile(file, null);
	}

	abstract public void loadFile(File file, Set<String> highlightTerms);

	abstract public void scrollToNextHit(boolean forward);

	public void copyScreen(Component comp) {
		BufferedImage image = new BufferedImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_RGB);

		comp.paint(image.getGraphics());
		TransferableImage trans = new TransferableImage(image);
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		clipboard.setContents(trans, trans);
	}

	public class TransferableImage implements Transferable, ClipboardOwner {

		Image i;

		public TransferableImage(Image i) {
			this.i = i;
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			if (flavor.equals(DataFlavor.imageFlavor) && i != null) {
				return i;
			} else {
				throw new UnsupportedFlavorException(flavor);
			}
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			DataFlavor[] flavors = new DataFlavor[1];
			flavors[0] = DataFlavor.imageFlavor;
			return flavors;
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			DataFlavor[] flavors = getTransferDataFlavors();
			for (int i = 0; i < flavors.length; i++) {
				if (flavor.equals(flavors[i])) {
					return true;
				}
			}

			return false;
		}

		@Override
		public void lostOwnership(Clipboard arg0, Transferable arg1) {
			LOGGER.info("Lost Clipboard Ownership");

		}
	}

}
