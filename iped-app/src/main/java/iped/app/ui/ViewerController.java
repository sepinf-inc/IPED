package iped.app.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import iped.app.ui.controls.CSelButton;
import iped.app.ui.viewers.AttachmentSearcherImpl;
import iped.app.ui.viewers.HexSearcherImpl;
import iped.app.ui.viewers.TextViewer;
import iped.engine.task.index.IndexItem;
import iped.io.IStreamSource;
import iped.io.URLUtil;
import iped.viewers.ATextViewer;
import iped.viewers.AudioViewer;
import iped.viewers.CADViewer;
import iped.viewers.EmailViewer;
import iped.viewers.HexViewerPlus;
import iped.viewers.HtmlLinkViewer;
import iped.viewers.HtmlViewer;
import iped.viewers.IcePDFViewer;
import iped.viewers.ImageViewer;
import iped.viewers.LibreOfficeViewer;
import iped.viewers.LibreOfficeViewer.NotSupported32BitPlatformExcepion;
import iped.viewers.MetadataViewer;
import iped.viewers.MsgViewer;
import iped.viewers.MultiViewer;
import iped.viewers.ReferencedFileViewer;
import iped.viewers.TiffViewer;
import iped.viewers.api.AbstractViewer;
import iped.viewers.components.HitsTable;
import iped.viewers.util.LibreOfficeFinder;

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

    public ViewerController() {
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
        viewersRepository.addViewer(new EmailViewer(new AttachmentSearcherImpl()));
        viewersRepository.addViewer(new MsgViewer());
        linkViewer = new HtmlLinkViewer(new AttachmentSearcherImpl());
        viewersRepository.addViewer(linkViewer);
        viewersRepository.addViewer(new IcePDFViewer());
        viewersRepository.addViewer(new TiffViewer());
        viewersRepository.addViewer(new AudioViewer(new AttachmentSearcherImpl()));
        viewersRepository.addViewer(new ReferencedFileViewer(viewersRepository, new AttachmentSearcherImpl()));

        new Thread() {
            public void run() {
                try {
                    for (AbstractViewer viewer : viewers) {
                        viewer.init();
                    }

                    // LibreOffice viewer initialization
                    URI jarUri = URLUtil.getURL(LibreOfficeViewer.class).toURI();
                    File moduledir = new File(jarUri).getParentFile().getParentFile();
                    LibreOfficeFinder loFinder = new LibreOfficeFinder(moduledir);
                    final String pathLO = loFinder.getLOPath(false);
                    if (pathLO != null) {
                        SwingUtilities.invokeAndWait(new Runnable() {
                            @Override
                            public void run() {
                                String nativeLibFolder = new File(moduledir, "lib/nativeview").getAbsolutePath();
                                officeViewer = new LibreOfficeViewer(nativeLibFolder, pathLO); // $NON-NLS-1$
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
                if (officeViewer != null && viewersRepository.getCurrentViewer() != null && viewersRepository.getCurrentViewer().equals(officeViewer)) {
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
                updateViewer(viewer, true, false);
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

    public void updateViewer(AbstractViewer viewer, boolean clean, boolean forceLoad) {
        if (viewer.getPanel().isShowing() || (viewer.equals(textViewer) && hasHits()) || forceLoad) {
            if (isInitialized())
                loadInViewer(viewer);
            DefaultSingleCDockable dock = dockPerViewer.get(viewer);
            if (dock != null) {
                boolean hitsEnabled = viewFile != null && ((hasHits() && viewer.getHitsSupported() == 0) || (viewer.getHitsSupported() == 1));

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