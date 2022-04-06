package dpf.sp.gpinf.indexer.desktop;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.tika.Tika;

import bibliothek.extension.gui.dock.theme.eclipse.stack.EclipseTabPaneContent;
import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.CButton;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.ui.controls.CSelButton;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ATextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.AbstractViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.AttachmentSearcherImpl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.CADViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.EmailViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HexSearcherImpl;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HexViewerPlus;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlLinkViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.HtmlViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.IcePDFViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ImageViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.LibreOfficeViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.LibreOfficeViewer.NotSupported32BitPlatformExcepion;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.MetadataViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.MsgViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.MultiViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ReferencedFileViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TiffViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TikaHtmlViewer;
import dpf.sp.gpinf.indexer.ui.hitsViewer.HitsTable;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import iped3.io.IStreamSource;

/**
 * Central controller for all viewers. To add a new viewer it should be added in
 * createViewers() method.
 * 
 * @author Wladimir
 */
public class ViewerController {
    private final List<AbstractViewer> viewers = new ArrayList<>();
    private final ATextViewer textViewer;
    private final HtmlLinkViewer linkViewer;
    private final MultiViewer viewersRepository;
    private final Map<AbstractViewer, DefaultSingleCDockable> dockPerViewer = new HashMap<>();
    private final Set<AbstractViewer> updatedViewers = new HashSet<>();
    private LibreOfficeViewer officeViewer;
    private IStreamSource file;
    private IStreamSource viewFile;
    private String contentType;
    private String viewType;
    private Set<String> highlightTerms;
    private Tika tika;
    private boolean init;
    private boolean isFixed;
    private final Object lock = new Object();

    public ViewerController(final String codePath) {
        Window owner = App.get();
        
        // These viewers will have their own docking frame
        viewers.add(new HexViewerPlus(owner, new HexSearcherImpl()));
        viewers.add(textViewer = new TextViewer());
        viewers.add(new MetadataViewer() {
            @Override
            public boolean isFixed() {
                return isFixed;
            }

            @Override
            public boolean isNumeric(String field) {
                return IndexItem.isNumeric(field);
            }
        });
        viewers.add(viewersRepository = new MultiViewer());

        // These are content-specific viewers (inside a single ViewersRepository)
        viewersRepository.addViewer(new ImageViewer());
        viewersRepository.addViewer(new CADViewer());
        viewersRepository.addViewer(new HtmlViewer());
        viewersRepository.addViewer(new EmailViewer());
        viewersRepository.addViewer(new MsgViewer());
        linkViewer = new HtmlLinkViewer(new AttachmentSearcherImpl());
        viewersRepository.addViewer(linkViewer);
        viewersRepository.addViewer(new TikaHtmlViewer());
        viewersRepository.addViewer(new IcePDFViewer());
        viewersRepository.addViewer(new TiffViewer());
        viewersRepository.addViewer(new ReferencedFileViewer(viewersRepository, new AttachmentSearcherImpl()));

        new Thread() {
            public void run() {
                try {
                    for (AbstractViewer viewer : viewers) {
                        viewer.init();
                    }

                    // LibreOffice viewer initialization
                    LibreOfficeFinder loFinder = new LibreOfficeFinder(new File(codePath).getParentFile());
                    final String pathLO = loFinder.getLOPath();
                    if (pathLO != null) {
                        SwingUtilities.invokeAndWait(new Runnable() {
                            @Override
                            public void run() {
                                officeViewer = new LibreOfficeViewer(codePath + "/../lib/nativeview", pathLO); //$NON-NLS-1$
                                viewersRepository.addViewer(officeViewer);
                            }
                        });
                        officeViewer.init();
                    }

                } catch (NotSupported32BitPlatformExcepion e) {
                    JOptionPane.showMessageDialog(null, Messages.getString("ViewerController.OfficeViewerUnSupported")); //$NON-NLS-1$
                    viewersRepository.removeViewer(officeViewer);
                } catch (Throwable e) {
                    // catches NoClassDefFoundError on Linux if libreoffice-java is not installed
                    // and if debugging UI: custom class loader is not used to load libreoffice jars
                    e.printStackTrace();
                } finally {
                    synchronized (lock) {
                        init = true;
                        lock.notifyAll();
                    }
                }
            }
        }.start();
    }

    public void setFixed(boolean isFixed) {
        this.isFixed = isFixed;
    }

    public void put(AbstractViewer viewer, DefaultSingleCDockable viewerDock) {
        dockPerViewer.put(viewer, viewerDock);
    }

    public List<AbstractViewer> getViewers() {
        return Collections.unmodifiableList(viewers);
    }

    public void setHitsTableInTextViewer(HitsTable hitsTable) {
        textViewer.setHitsTable(hitsTable);
    }

    public ATextViewer getTextViewer() {
        return textViewer;
    }

    public MultiViewer getMultiViewer() {
        return viewersRepository;
    }

    public HtmlLinkViewer getHtmlLinkViewer() {
        return linkViewer;
    }

    public void dispose() {
        while (!viewers.isEmpty()) {
            AbstractViewer viewer = viewers.remove(viewers.size() - 1);
            viewer.dispose();
        }
    }

    public void clear() {
        loadFile(null, null, null, null);
    }

    public void validateViewers() {
        for (AbstractViewer viewer : viewers) {
            validateViewer(viewer);
        }
    }

    public boolean validateViewer(AbstractViewer viewer) {
        if (viewer.equals(viewersRepository)) {
            if (viewersRepository.getPanel().isShowing()) {
                if (officeViewer != null && viewersRepository.getCurrentViewer() != null
                        && viewersRepository.getCurrentViewer().equals(officeViewer)) {
                    new Thread() {
                        public void run() {
                            officeViewer.constructLOFrame();
                            officeViewer.reloadLastFile(true);
                        }
                    }.start();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInitialized() {
        synchronized (lock) {
            return init;
        }
    }

    public void reload() {
        boolean currFixed = isFixed;
        isFixed = true;
        loadFile(this.file, this.viewFile, this.contentType, this.highlightTerms);
        isFixed = currFixed; 
    }

    public void loadFile(IStreamSource file, IStreamSource viewFile, String contentType, Set<String> highlightTerms) {
        if (!isInitialized()) {
            return;
        }
        this.file = file;
        this.viewFile = viewFile;
        this.contentType = contentType;
        this.viewType = null;
        this.highlightTerms = highlightTerms;
        synchronized (updatedViewers) {
            updatedViewers.clear();
        }
        AbstractViewer requested = null;
        if (file != null && !isFixed) {
            updateViewType();
            AbstractViewer bestViewer = getBestViewer(viewType);
            if (!bestViewer.getPanel().isShowing()) {
                DefaultSingleCDockable viewerDock = dockPerViewer.get(bestViewer);
                if (!viewerDock.getExtendedMode().equals(ExtendedMode.MINIMIZED) && isContainerVisibleTab(viewerDock)) {
                    if (viewerDock != null) {
                        bestViewer.loadFile(null);
                        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                        viewerDock.toFront();
                        requested = bestViewer;
                        if (focus != null) {
                            focus.requestFocusInWindow();
                        }
                    }
                }
            }
        }
        for (AbstractViewer viewer : viewers) {
            if (!viewer.equals(requested)) {
                updateViewer(viewer, true);
            }
        }
    }

    private boolean isContainerVisibleTab(DefaultSingleCDockable dock) {
        Container cont = dock.getContentPane();
        if (cont != null) {
            Container parent;
            while ((parent = cont.getParent()) != null) {
                if (parent instanceof EclipseTabPaneContent) {
                    return parent.isShowing();
                }
                cont = parent;
            }
        }
        return false;
    }

    public void changeToViewer(AbstractViewer viewer) {
        DefaultSingleCDockable viewerDock = dockPerViewer.get(viewer);
        if (viewerDock != null) {
            Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            viewerDock.toFront();
            if (focus != null) {
                focus.requestFocusInWindow();
            }
        }
    }

    public boolean hasHits() {
        return highlightTerms != null && !highlightTerms.isEmpty();
    }

    public void updateViewer(AbstractViewer viewer, boolean clean) {
        if (viewer.getPanel().isShowing() || (viewer.equals(textViewer) && hasHits())) {
            if (isInitialized())
                loadInViewer(viewer);
            DefaultSingleCDockable dock = dockPerViewer.get(viewer);
            if (dock != null) {
                boolean hitsEnabled = viewFile != null
                        && ((hasHits() && viewer.getHitsSupported() == 0) || (viewer.getHitsSupported() == 1));

                ((CButton) dock.getAction("prevHit")).setEnabled(hitsEnabled);
                ((CButton) dock.getAction("nextHit")).setEnabled(hitsEnabled);

                int toolbarSupport = viewer.getToolbarSupported();
                if (toolbarSupport >= 0) {
                    CSelButton butToolbar = (CSelButton) dock.getAction("toolbar");
                    butToolbar.setEnabled(toolbarSupport == 1);
                    butToolbar.setSelected(toolbarSupport == 1 && viewer.isToolbarVisible());
                }
            }
        } else {
            if (clean) {
                if (isInitialized())
                    viewer.loadFile(null);
            }
        }
    }

    private void loadInViewer(AbstractViewer viewer) {
        synchronized (updatedViewers) {
            if (!updatedViewers.add(viewer))
                return;
        }
        if (viewFile == null) {
            viewer.loadFile(null);
        } else {
            if (viewer.equals(viewersRepository)) {
                updateViewType();
                if (viewer.isSupportedType(viewType, true)) {
                    viewer.loadFile(viewFile, viewType, highlightTerms);
                } else {
                    viewer.loadFile(null);
                }
            } else {
                if (viewer.isSupportedType(contentType, true)) {
                    viewer.loadFile(file, contentType, highlightTerms);
                } else {
                    viewer.loadFile(null);
                }
            }
        }
    }

    private void updateViewType() {
        if (viewType != null)
            return;
        if (!file.equals(viewFile)) {
            try {
                if (tika == null) {
                    tika = new Tika();
                }
                viewType = tika.detect(viewFile.getTempFile());
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        viewType = contentType;
    }

    private AbstractViewer getBestViewer(String contentType) {
        AbstractViewer result = null;
        for (AbstractViewer viewer : viewers) {
            if (viewer.isSupportedType(contentType, true)) {
                if (viewer instanceof MetadataViewer) {
                    if (((MetadataViewer) viewer).isMetadataEntry(contentType)) {
                        result = viewer;
                    }
                } else
                    result = viewer;
            }
        }
        return result;
    }
}