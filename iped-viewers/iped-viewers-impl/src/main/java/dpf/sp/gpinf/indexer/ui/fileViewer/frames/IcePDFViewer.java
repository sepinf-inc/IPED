package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.search.DocumentSearchController;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.SwingViewBuilder;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.common.views.DocumentViewModelImpl;
import org.icepdf.ri.util.PropertiesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import iped3.io.IStreamSource;

public class IcePDFViewer extends Viewer {

    private static Logger LOGGER = LoggerFactory.getLogger(IcePDFViewer.class);
    /**
     *
     */
    private static final long serialVersionUID = -4538119351386926692L;
    private volatile SwingController pdfController;
    private volatile JPanel viewerPanel;
    private volatile JLabel labelMsg;

    volatile int fitMode = DocumentViewController.PAGE_FIT_WINDOW_WIDTH;
    volatile int viewMode = DocumentViewControllerImpl.ONE_COLUMN_VIEW;

    private final Object lock = new Object();
    private volatile IStreamSource lastContent;
    
    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("application/pdf"); //$NON-NLS-1$
    }

    @Override
    public String getName() {
        return "Pdf"; //$NON-NLS-1$
    }

    public IcePDFViewer() {
        super(new BorderLayout());
        isToolbarVisible = true;

        // System.setProperty("org.icepdf.core.imageReference", "scaled"); //$NON-NLS-1$
        // //$NON-NLS-2$
        System.setProperty("org.icepdf.core.ccittfax.jai", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        System.setProperty("org.icepdf.core.minMemory", "150M"); //$NON-NLS-1$ //$NON-NLS-2$
        System.setProperty("org.icepdf.core.views.page.text.highlightColor", "0xFFFF00"); //$NON-NLS-1$ //$NON-NLS-2$
        // pode provocar crash da jvm
        // System.setProperty("org.icepdf.core.awtFontLoading", "true");

    }

    @Override
    public void init() {

        new File(System.getProperties().getProperty("user.home"), ".icesoft/icepdf-viewer").mkdirs(); //$NON-NLS-1$ //$NON-NLS-2$

        pdfController = new SwingController() {
            //Override openDocument, to avoid opening dialog messages
            public void openDocument(String pathname) {
                if (pathname != null && pathname.length() > 0) {
                    try {
                        closeDocument();
                        setDisplayTool(DocumentViewModelImpl.DISPLAY_TOOL_WAIT);
                        document = new Document();
                        setupSecurityHandler(document, documentViewController.getSecurityCallback());
                        document.setFile(pathname);
                        commonNewDocumentHandling(pathname);
                    } catch (Exception e) {
                    }
                }
            }
            
            public void closeDocument() {
                try {
                    if (document != null) {
                        super.closeDocument();
                    }
                } catch (Exception e) {
                }
            }

            public void setToolBarVisible(boolean isVisible) {
                if (document != null) {
                    try {
                        super.setToolBarVisible(isVisible);
                    } catch (Exception e) {
                    }
                }
            }
        };
        pdfController.setIsEmbeddedComponent(true);
        pdfController.getDocumentViewController().getViewContainer().setFocusable(false);

        PropertiesManager propManager = PropertiesManager.getInstance();
        propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_ANNOTATION, "false"); //$NON-NLS-1$
        propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_TOOL, "false"); //$NON-NLS-1$
        propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_ZOOM, "true"); //$NON-NLS-1$
        propManager.set(PropertiesManager.PROPERTY_SHOW_STATUSBAR, "false"); //$NON-NLS-1$
        propManager.set(PropertiesManager.PROPERTY_HIDE_UTILITYPANE, "true"); //$NON-NLS-1$
        propManager.set(PropertiesManager.PROPERTY_DEFAULT_PAGEFIT, Integer.toString(fitMode));
        // propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_UTILITY, "false");
        // propManager.set(PropertiesManager.PROPERTY_SHOW_TOOLBAR_PAGENAV, "true");
        // propManager.set("application.showLocalStorageDialogs", "NO");

        // final SwingViewBuilder factory = new SwingViewBuilder(pdfController,
        // propManager, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED, null,
        // viewMode, fitMode);
        final SwingViewBuilder factory = new SwingViewBuilder(pdfController, viewMode, fitMode);

        pdfController.getDocumentViewController().setAnnotationCallback(
                new org.icepdf.ri.common.MyAnnotationCallback(pdfController.getDocumentViewController()));

        final JPanel panel = this.getPanel();
        labelMsg = new JLabel(Messages.getString("PDFViewer.OpenError"), JLabel.CENTER);
        labelMsg.setVisible(false);
        labelMsg.setPreferredSize(new Dimension(0, 50));

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                viewerPanel = factory.buildViewerPanel();
                panel.add(labelMsg, BorderLayout.NORTH);
                panel.add(viewerPanel, BorderLayout.CENTER);
                panel.setMinimumSize(new Dimension());
            }
        });

    }

    @Override
    public void copyScreen(Component comp) {
        super.copyScreen(pdfController.getDocumentViewController().getViewContainer());
    }

    @Override
    public void dispose() {
        if (pdfController != null) {
            pdfController.dispose();
        }

    }

    @Override
    public void loadFile(final IStreamSource content, final Set<String> highlightTerms) {
        
        lastContent = content;
        
        if (content == null) {
            pdfController.closeDocument();
            viewerPanel.setVisible(false);
            return;
        }

        // some docs hang and block loading new docs
        try {
            Library.shutdownThreadPool();
        } catch (Exception e) {
        }

        new Thread() {
            @Override
            public void run() {
                synchronized (lock) {
                    if (!content.equals(lastContent)) return;
                    
                    labelMsg.setVisible(false);
                    viewerPanel.setVisible(false);

                    try {
                        pdfController.openDocument(content.getFile().getAbsolutePath());
                        if (!content.equals(lastContent)) return;
                        if (!isDocumentValid()) {
                            labelMsg.setVisible(true);
                            getPanel().revalidate();
                            return;
                        }
                        pdfController.setToolBarVisible(isToolbarVisible());
    
                        if (fitMode != pdfController.getDocumentViewController().getFitMode()) {
                            pdfController.setPageFitMode(fitMode, true);
                        }
    
                        if (pdfController.isUtilityPaneVisible()) {
                            pdfController.setUtilityPaneVisible(false);
                        }
    
                        viewerPanel.setVisible(true);
                        viewerPanel.revalidate();
                        getPanel().revalidate();
    
                        //TODO:Remove before final commit, if revalidate works as expected
                        // resize to force redraw
                        //getPanel().setSize(getPanel().getWidth() + delta, getPanel().getHeight());
                        //delta *= -1;
    
                        highlightText(highlightTerms, content);
                    } catch (Exception e) {
                        if (content == null || !content.equals(lastContent)) return;
                        labelMsg.setVisible(true);
                        getPanel().revalidate();
                    }
                }
            }
        }.start();

    }

    private boolean isDocumentValid() {
        try {
            Document document = pdfController.getDocument();
            if (document == null) return false;
            Catalog catalog = document.getCatalog();
            if (catalog == null) return false;
            return catalog.getPageTree() != null;
        } catch (Exception e) {
        }
        return false;
    }
    
    //TODO:Remove before final commit, if revalidate works as expected
    //private int delta = 1;
    private ArrayList<Integer> hitPages;

    private void highlightText(Set<String> highlightTerms, IStreamSource content) {
        try {
            DocumentSearchController search = pdfController.getDocumentSearchController();
            search.clearAllSearchHighlight();
            if (highlightTerms.size() == 0) {
                return;
            }

            //TODO: Review this! In rare (random) cases it doesn't work. 
            // Workaround to rendering problem whith the first page with hits
            Thread.sleep(500);

            boolean caseSensitive = false, wholeWord = true;
            for (String term : highlightTerms) {
                search.addSearchTerm(term, caseSensitive, wholeWord);
            }

            currentHit = -1;
            totalHits = 0;
            hitPages = new ArrayList<Integer>();
            for (int i = 0; i < pdfController.getDocument().getNumberOfPages(); i++) {
                if(content != lastContent) {
                    break;
                }
                int hits = search.searchHighlightPage(i);
                if (hits > 0) {
                    totalHits++;
                    hitPages.add(i);
                    if (totalHits == 1) {
                        pdfController.getDocumentViewController().setCurrentPageIndex(i);
                        // pdfController.updateDocumentView();
                        currentHit = 0;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.info("Error/Highlight interrupted"); //$NON-NLS-1$
        }

    }

    @Override
    public void scrollToNextHit(boolean forward) {

        if (forward) {
            if (currentHit < totalHits - 1) {
                pdfController.getDocumentViewController().setCurrentPageIndex(hitPages.get(++currentHit));
            }

        } else {
            if (currentHit > 0) {
                pdfController.getDocumentViewController().setCurrentPageIndex(hitPages.get(--currentHit));
            }

        }
        // pdfController.updateDocumentView();

    }

    @Override
    public void setToolbarVisible(boolean isVisible) {
        super.setToolbarVisible(isVisible);
        pdfController.setToolBarVisible(isVisible);
    }

    @Override
    public int getToolbarSupported() {
        return 1;
    }
}