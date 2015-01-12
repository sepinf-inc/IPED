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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.util.PropertiesManager;

public class IcePDFViewer extends AbstractViewer {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4538119351386926692L;
	private volatile SwingController pdfController, prevController;
	private volatile JPanel viewerPanel;

	volatile int fitMode = DocumentViewController.PAGE_FIT_WINDOW_WIDTH;
	volatile int viewMode = DocumentViewControllerImpl.ONE_COLUMN_VIEW;

	@Override
	public boolean isSupportedType(String contentType) {
		return contentType.equals("application/pdf");

	}

	@Override
	public String getName() {
		return "Pdf";
	}

	public IcePDFViewer() {
		super(new BorderLayout());

		System.setProperty("org.icepdf.core.imageReference", "scaled");
		System.setProperty("org.icepdf.core.ccittfax.jai", "true");
		System.setProperty("org.icepdf.core.minMemory", "150M");
		System.setProperty("org.icepdf.core.views.page.text.highlightColor", "0xFFFF00");
		// System.setProperty("org.icepdf.core.awtFontLoading", "true");

	}

	@Override
	public void init() {

		new File(System.getProperties().getProperty("user.home"), ".icesoft/icepdf-viewer").mkdirs();

		pdfController = new SwingController();
		pdfController.setIsEmbeddedComponent(true);

		PropertiesManager propManager = new PropertiesManager(System.getProperties(), pdfController.getMessageBundle());
		propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION, "false");
		//propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_UTILITY, "false");
		propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_TOOL, "false");
		propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_ZOOM, "true");
		propManager.set(PropertiesManager.PROPERTY_SHOW_STATUSBAR, "false");
		propManager.set(PropertiesManager.PROPERTY_HIDE_UTILITYPANE, "true");
		propManager.set(PropertiesManager.PROPERTY_DEFAULT_PAGEFIT, Integer.toString(fitMode));
		// propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_PAGENAV, "true");
		// propManager.set("application.showLocalStorageDialogs", "NO");

		final SwingViewBuilder factory = new SwingViewBuilder(pdfController, propManager, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED, null, viewMode, fitMode);

		// SwingViewBuilder factory = new SwingViewBuilder(pdfController,
		// viewMode, fitMode);

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				viewerPanel = factory.buildViewerPanel();
			}
		});

		// System.out.println("Viewer PDF ok");

	}

	@Override
	public void copyScreen(Component comp) {
		super.copyScreen(pdfController.getDocumentViewController().getViewContainer());
	}

	@Override
	public void dispose() {
		if (pdfController != null)
			pdfController.dispose();

	}

	@Override
	public void loadFile(final File file, final Set<String> highlightTerms) {

		new Thread() {
			SwingController controller = prevController;

			@Override
			public void run() {
				if (controller != null) {
					if (controller.getDocumentViewController().getFitMode() != 0)
						fitMode = controller.getDocumentViewController().getFitMode();

					controller.closeDocument();
					controller.dispose();
				}
			}
		}.start();

		if (file == null)
			return;

		final JPanel panel = this.getPanel();

		new Thread() {
			@Override
			public void run() {

				init();

				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						panel.removeAll();
						panel.add(viewerPanel, BorderLayout.CENTER);
						panel.setMinimumSize(new Dimension());
					}
				});

				prevController = pdfController;
				pdfController.openDocument(file.getAbsolutePath());

				if (fitMode != pdfController.getDocumentViewController().getFitMode())
					pdfController.setPageFitMode(fitMode, true);

				if (pdfController.isUtilityPaneVisible())
					pdfController.setUtilityPaneVisible(false);

				getPanel().setSize(getPanel().getWidth() + delta, getPanel().getHeight());
				delta *= -1;

				highlightText(highlightTerms);

			}
		}.start();

	}

	private int delta = 1;
	private ArrayList<Integer> hitPages;

	private void highlightText(Set<String> highlightTerms) {
		try {
			DocumentSearchController search = pdfController.getDocumentSearchController();
			search.clearAllSearchHighlight();
			if (highlightTerms.size() == 0)
				return;
			
			//Workaround to rendering problem whith the first page with hits
			Thread.sleep(1000);

			boolean caseSensitive = false, wholeWord = true;
			for (String term : highlightTerms)
				search.addSearchTerm(term, caseSensitive, wholeWord);

			currentHit = -1;
			totalHits = 0;
			hitPages = new ArrayList<Integer>();
			for (int i = 0; i < pdfController.getDocument().getNumberOfPages(); i++) {
				int hits = search.searchHighlightPage(i);
				if (hits > 0) {
					totalHits++;
					hitPages.add(i);
					if (totalHits == 1) {
						pdfController.getDocumentViewController().setCurrentPageIndex(i);
						//pdfController.updateDocumentView();
						currentHit = 0;
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Erro/Interrupção do Highlight");
		}

	}

	@Override
	public void scrollToNextHit(boolean forward) {

		if (forward) {
			if (currentHit < totalHits - 1)
				pdfController.getDocumentViewController().setCurrentPageIndex(hitPages.get(++currentHit));

		} else {
			if (currentHit > 0)
				pdfController.getDocumentViewController().setCurrentPageIndex(hitPages.get(--currentHit));

		}
		// pdfController.updateDocumentView();

	}

}
