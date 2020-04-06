package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import iped3.io.IStreamSource;
import iped3.util.MediaTypes;

public class CompositeViewer extends JPanel implements ChangeListener, ActionListener {

    private static final long serialVersionUID = -2751185904521769139L;
    private final static String VIEWER_CAPTION = "<html>&nbsp;" + Messages.getString("CompositeViewer.PreviewWarn") //$NON-NLS-1$ //$NON-NLS-2$
            + "</html>"; //$NON-NLS-1$
    private static String PREV_HIT_TIP = Messages.getString("CompositeViewer.PrevHit"); //$NON-NLS-1$
    private static String NEXT_HIT_TIP = Messages.getString("CompositeViewer.NextHit"); //$NON-NLS-1$
    private static String FIX_VIEWER = Messages.getString("CompositeViewer.FixViewer"); //$NON-NLS-1$

    ArrayList<Viewer> viewerList = new ArrayList<Viewer>();
    HashSet<Viewer> loadedViewers = new HashSet<Viewer>();

    volatile Viewer bestViewer, currentViewer, textViewer;
    volatile IStreamSource file, viewFile;
    volatile String contentType, viewMediaType;
    Set<String> highlightTerms;

    JTabbedPane tabbedPane;
    JCheckBox fixViewer;
    JButton prevHit, nextHit;

    Tika tika;

    public CompositeViewer() {
        super(new BorderLayout());

        tabbedPane = new JTabbedPane();
        tabbedPane.addChangeListener(this);
        JLabel viewerLabel = new JLabel(VIEWER_CAPTION);
        fixViewer = new JCheckBox(FIX_VIEWER);
        prevHit = new JButton("<"); //$NON-NLS-1$
        prevHit.setToolTipText(PREV_HIT_TIP);
        nextHit = new JButton(">"); //$NON-NLS-1$
        nextHit.setToolTipText(NEXT_HIT_TIP);
        prevHit.addActionListener(this);
        nextHit.addActionListener(this);
        JPanel navHit = new JPanel(new GridLayout());
        navHit.add(prevHit);
        navHit.add(nextHit);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(navHit, BorderLayout.WEST);
        topPanel.add(viewerLabel, BorderLayout.CENTER);
        topPanel.add(fixViewer, BorderLayout.EAST);

        this.add(topPanel, BorderLayout.NORTH);
        this.add(tabbedPane, BorderLayout.CENTER);

    }

    public void addViewer(Viewer viewer) {
        viewerList.add(viewer);
        tabbedPane.addTab(viewer.getName(), viewer.getPanel());
        if (viewer instanceof ATextViewer) {
            textViewer = viewer;
        }

    }

    public void initViewers() {
        for (Viewer viewer : viewerList) {
            viewer.init();
        }
    }

    public void clear() {
        for (Viewer viewer : viewerList) {
            viewer.loadFile(null);
        }
        file = null;
        viewFile = null;
    }

    public void dispose() {
        for (Viewer viewer : viewerList) {
            viewer.dispose();
        }
    }

    public void loadFile(IStreamSource file, String contentType, Set<String> highlightTerms) {
        loadFile(file, file, contentType, highlightTerms);
    }

    private String getViewType() {
        if (viewFile != file) {
            try {
                return tika.detect(viewFile.getFile());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return contentType;
    }

    public void loadFile(IStreamSource file, IStreamSource viewFile, String contentType, Set<String> highlightTerms) {
        this.file = file;
        this.viewFile = viewFile;
        this.contentType = contentType;
        if (tika == null)
            tika = new Tika();

        if (contentType == null) {
            try {
                this.contentType = tika.detect(file.getFile());
            } catch (IOException e) {
                this.contentType = MediaType.OCTET_STREAM.toString();
            }
        }
        this.viewMediaType = getViewType();
        this.highlightTerms = highlightTerms;

        loadedViewers.clear();
        bestViewer = getBestViewer(viewMediaType);

        if (fixViewer.isSelected() || currentViewer == bestViewer) {
            loadInViewer(currentViewer);
        } else {
            changeToViewerInEDT(bestViewer);
        }
        
        if(!fixViewer.isSelected() && bestViewer instanceof MetadataViewer)
            ((MetadataViewer)bestViewer).selectTab(2);

        if (highlightTerms != null && !highlightTerms.isEmpty()) {
            loadInViewer(textViewer);
        }

        for (Viewer viewer : viewerList) {
            if (!loadedViewers.contains(viewer)) {
                viewer.loadFile(null);
            }
        }

    }

    // Assume que os visualizadores foam adicionados em ordem crescente de
    // prioridade
    private Viewer getBestViewer(String contentType) {
        Viewer result = null;
        for (Viewer viewer : viewerList) {
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
                } else
                    result = viewer;
            }
        }
        return result;
    }

    public ATextViewer getTextViewer() {
        Viewer result = null;
        for (Viewer viewer : this.viewerList) {
            if (Messages.getString("ATextViewer.TabName").equalsIgnoreCase(viewer.getName())) { //$NON-NLS-1$
                result = viewer;
                break;
            }
        }
        return (ATextViewer) result;
    }

    private void loadInViewer(Viewer viewer) {
        if (viewer.isSupportedType(viewMediaType, true)) {
            if (!loadedViewers.contains(viewer)) {
                loadedViewers.add(viewer);
                if (viewer == textViewer && bestViewer != textViewer) {
                    viewer.loadFile(file, contentType, highlightTerms);
                } else if (viewer instanceof HexViewer || viewer instanceof MetadataViewer) {
                    viewer.loadFile(file, contentType, highlightTerms);
                } else {
                    viewer.loadFile(viewFile, viewMediaType, highlightTerms);
                }
            }
        } else {
            viewer.loadFile(null);
        }

    }

    @Override
    public void stateChanged(ChangeEvent arg0) {
        int currentTab = tabbedPane.getSelectedIndex();
        currentViewer = getViewerAtTab(currentTab);
        loadInViewer(currentViewer);
    }

    private Viewer getViewerAtTab(int tab) {
        String tabName = tabbedPane.getTitleAt(tab);
        for (Viewer viewer : viewerList) {
            if (viewer.getName().equals(tabName)) {
                return viewer;
            }
        }

        return null;

    }

    public void changeToViewer(Viewer viewer) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(viewer.getName())) {
                tabbedPane.setSelectedIndex(i);
                break;
            }
        }
    }

    private void changeToViewerInEDT(final Viewer viewer) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    changeToViewer(viewer);
                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public Viewer getCurrentViewer() {
        return currentViewer;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {

        if (evt.getSource() == prevHit) {
            currentViewer.scrollToNextHit(false);

        } else if (evt.getSource() == nextHit) {
            currentViewer.scrollToNextHit(true);
        }

    }

}
