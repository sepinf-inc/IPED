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

import dpf.sp.gpinf.indexer.util.StreamSource;

public class CompositeViewer extends JPanel implements ChangeListener, ActionListener {

    private static final long serialVersionUID = -2751185904521769139L;
    private final static String VIEWER_CAPTION = "<html>&nbsp;O visualizador pode conter erros. Clique 2 vezes sobre o arquivo para abri-lo.</html>";
    private static String PREV_HIT_TIP = "Ocorrência anterior";
    private static String NEXT_HIT_TIP = "Próxima ocorrência";
    private static String FIX_VIEWER = "Fixar Visualizador";

    ArrayList<Viewer> viewerList = new ArrayList<Viewer>();
    HashSet<Viewer> loadedViewers = new HashSet<Viewer>();

    volatile Viewer bestViewer, currentViewer, textViewer;
    volatile StreamSource file, viewFile;
    volatile String contentType, viewMediaType;
    Set<String> highlightTerms;

    JTabbedPane tabbedPane;
    JCheckBox fixViewer;
    JButton prevHit, nextHit;

    Tika tika = new Tika();

    public CompositeViewer() {
        super(new BorderLayout());

        tabbedPane = new JTabbedPane();
        tabbedPane.addChangeListener(this);
        JLabel viewerLabel = new JLabel(VIEWER_CAPTION);
        fixViewer = new JCheckBox(FIX_VIEWER);
        prevHit = new JButton("<");
        prevHit.setToolTipText(PREV_HIT_TIP);
        nextHit = new JButton(">");
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
        if (viewer instanceof TextViewer) {
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

    public void loadFile(StreamSource file, String contentType, Set<String> highlightTerms) {
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

    public void loadFile(StreamSource file, StreamSource viewFile, String contentType, Set<String> highlightTerms) {
        this.file = file;
        this.viewFile = viewFile;
        this.contentType = contentType;
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

        if (highlightTerms != null && !highlightTerms.isEmpty()) {
            loadInViewer(textViewer);
        }

        for (Viewer viewer : viewerList) {
            if (!loadedViewers.contains(viewer)) {
                viewer.loadFile(null);
            }
        }

    }

    //Assume que os visualizadores foam adicionados em ordem crescente de prioridade
    private Viewer getBestViewer(String contentType) {
        Viewer result = null;
        for (Viewer viewer : viewerList) {
            if (viewer.isSupportedType(contentType)) {
                result = viewer;
            }
        }
        return result;
    }
    
    public TextViewer getTextViewer() {
        Viewer result = null;
        for (Viewer viewer : this.viewerList) {
            if ("Texto".equalsIgnoreCase(viewer.getName())) {
                result = viewer;
                break;
            }
        }
        return (TextViewer) result;
    }

    private void loadInViewer(Viewer viewer) {
        if (viewer.isSupportedType(viewMediaType)) {
            if (!loadedViewers.contains(viewer)) {
                loadedViewers.add(viewer);
                if (viewer == textViewer && bestViewer != textViewer) {
                    viewer.loadFile(file, contentType, highlightTerms);
                } else if (viewer instanceof HexViewer) {
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
