package dpf.sp.gpinf.indexer.desktop;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
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

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;

import bibliothek.gui.dock.common.DefaultSingleCDockable;
import bibliothek.gui.dock.common.action.CButton;
import bibliothek.gui.dock.common.action.CCheckBox;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ATextViewer;
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
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.NoJavaFXViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TextViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TiffViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.TikaHtmlViewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.Viewer;
import dpf.sp.gpinf.indexer.ui.fileViewer.frames.ViewersRepository;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;
import dpf.sp.gpinf.indexer.util.JarLoader;
import dpf.sp.gpinf.indexer.util.LibreOfficeFinder;
import iped3.io.IStreamSource;
import iped3.util.MediaTypes;

/**
 * Central controller for all viewers. 
 * To add a new viewer it should be added in createViewers() method.
 * 
 * @author Wladimir
 */
public class ViewerController {
    private final List<Viewer> viewers = new ArrayList<Viewer>();
    private final ATextViewer textViewer;
    private final ViewersRepository viewersRepository;
    private final Map<Viewer, DefaultSingleCDockable> dockPerViewer = new HashMap<Viewer, DefaultSingleCDockable>();
    private final Set<Viewer> updatedViewers = new HashSet<Viewer>();
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

    public ViewerController(AppSearchParams params) {
        //These viewers will have their own docking frame
        viewers.add(new HexViewerPlus(new HexSearcherImpl(), Configuration.getInstance().configPath));
        viewers.add(textViewer = new TextViewer(params));
        viewers.add(new MetadataViewer());
        viewers.add(viewersRepository = new ViewersRepository());

        new Thread() {
            public void run() {
                boolean javaFX = new JarLoader().loadJavaFX();

                //These are content-specific viewers (inside a single ViewersRepository)
                viewersRepository.addViewer(new ImageViewer());
                viewersRepository.addViewer(new CADViewer());
                if (javaFX) {
                    viewersRepository.addViewer(new HtmlViewer());
                    viewersRepository.addViewer(new EmailViewer());
                    viewersRepository.addViewer(new HtmlLinkViewer(new AttachmentSearcherImpl()));
                    viewersRepository.addViewer(new TikaHtmlViewer());
                } else {
                    viewersRepository.addViewer(new NoJavaFXViewer());
                }
                viewersRepository.addViewer(new IcePDFViewer());
                viewersRepository.addViewer(new TiffViewer());
                for (Viewer viewer : viewers) {
                    viewer.init();
                }
                tika = new Tika();

                //LibreOffice viewer initialization
                LibreOfficeFinder loFinder = new LibreOfficeFinder(new File(params.codePath).getParentFile());
                final String pathLO = loFinder.getLOPath();
                if (pathLO != null) {
                    try {
                        officeViewer = new LibreOfficeViewer(params.codePath + "/../lib/nativeview", pathLO); //$NON-NLS-1$
                        viewersRepository.addViewer(officeViewer);
                        officeViewer.init();
                    } catch (NotSupported32BitPlatformExcepion e) {
                        JOptionPane.showMessageDialog(null, Messages.getString("ViewerController.OfficeViewerUnSupported")); //$NON-NLS-1$
                        viewersRepository.removeViewer(officeViewer);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                params.dialogBar.setVisible(false);
                synchronized (lock) {
                    init = true;
                    lock.notifyAll();
                }
            }
        }.start();
    }

    public void setFixed(boolean isFixed) {
        this.isFixed = isFixed;
    }

    public void put(Viewer viewer, DefaultSingleCDockable viewerDock) {
        dockPerViewer.put(viewer, viewerDock);
    }

    public List<Viewer> getViewers() {
        return Collections.unmodifiableList(viewers);
    }

    public ATextViewer getTextViewer() {
        return textViewer;
    }

    public ViewersRepository getMultiViewer() {
        return viewersRepository;
    }

    public void dispose() {
        while (!viewers.isEmpty()) {
            Viewer viewer = viewers.remove(viewers.size() - 1);
            viewer.dispose();
        }
    }

    public void clear() {
        loadFile(null, null, null, null);
    }

    public void validateViewers() {
        for (Viewer viewer : viewers) {
            validateViewer(viewer);
        }
    }

    public boolean validateViewer(Viewer viewer) {
        //TODO: Remove before final commit
        //System.err.println("VALIDATE : " + viewer.getName());
        if (viewer.equals(viewersRepository)) {
            if (viewersRepository.getPanel().isShowing()) {
                if (officeViewer != null && viewersRepository.getCurrentViewer().equals(officeViewer)) {
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

    private void checkInit() {
        if (!init) {
            synchronized (lock) {
                while (!init) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }
    
    private boolean isInitialized() {
        synchronized(lock) {
            return init;
        }
    }

    public void loadFile(IStreamSource file, IStreamSource viewFile, String contentType, Set<String> highlightTerms) {
        checkInit();
        this.file = file;
        this.viewFile = viewFile;
        this.contentType = contentType;
        this.viewType = null;
        this.highlightTerms = highlightTerms;
        synchronized (updatedViewers) {
            updatedViewers.clear();
        }
        Viewer requested = null;
        if (file != null && !isFixed) {
            updateViewType();
            Viewer bestViewer = getBestViewer(viewType);
            if (!bestViewer.getPanel().isShowing()) {
                DefaultSingleCDockable viewerDock = dockPerViewer.get(bestViewer);
                if (viewerDock != null) {
                    Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    viewerDock.toFront();
                    requested = bestViewer;
                    if (focus != null) {
                        focus.requestFocusInWindow();
                    }
                }
            }
        }
        for (Viewer viewer : viewers) {
            if (!viewer.equals(requested)) {
                updateViewer(viewer, true);
            }
        }
    }

    public void changeToViewer(Viewer viewer) {
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

    public void updateViewer(Viewer viewer, boolean clean) {
        //TODO: Remove before final commit
        //System.err.println("UPDATE : " + viewer.getName() + " : " + clean);
        if (viewer.getPanel().isShowing() || (viewer.equals(textViewer) && hasHits())) {
            if (isInitialized()) loadInViewer(viewer);
            DefaultSingleCDockable dock = dockPerViewer.get(viewer);
            if (dock != null) {
                boolean hitsEnabled = viewFile != null && hasHits() && viewer.getHitsSupported();

                ((CButton) dock.getAction("prevHit")).setEnabled(hitsEnabled);
                ((CButton) dock.getAction("nextHit")).setEnabled(hitsEnabled);

                int toolbarSupport = viewer.getToolbarSupported();
                if (toolbarSupport >= 0) {
                    CCheckBox chkToolbar = (CCheckBox) dock.getAction("toolbar");
                    chkToolbar.setEnabled(toolbarSupport == 1);
                    chkToolbar.setSelected(toolbarSupport == 1 && viewer.isToolbarVisible());
                }
            }
        } else {
            if (clean) {
                if (isInitialized()) viewer.loadFile(null);
            }
        }
    }

    private void loadInViewer(Viewer viewer) {
        synchronized (updatedViewers) {
            if (!updatedViewers.add(viewer)) return;
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
        if (viewType != null) return;
        if (!file.equals(viewFile)) {
            try {
                viewType = tika.detect(viewFile.getFile());
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        viewType = contentType;
    }

    private Viewer getBestViewer(String contentType) {
        Viewer result = null;
        for (Viewer viewer : viewers) {
            if (viewer.isSupportedType(contentType, true)) {
                if (viewer instanceof MetadataViewer) {
                    MediaType type = MediaType.parse(contentType);
                    while (type != null && !type.equals(MediaType.OCTET_STREAM)) {
                        if (MediaTypes.METADATA_ENTRY.equals(type)) {
                            result = viewer;
                            break;
                        }
                        type = viewer.getParentType(type);
                    }
                } else result = viewer;
            }
        }
        return result;
    }
}