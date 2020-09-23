package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.CardLayout;
import java.awt.GridLayout;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import iped3.io.IStreamSource;

//TODO: Perhaps renaming this class to MultiViewer (or CompositeViewer or something more meaningful) would be a good idea 
public class ViewersRepository extends Viewer {

    private JPanel cardViewer = new JPanel(new CardLayout());
    private ArrayList<Viewer> viewerList = new ArrayList<Viewer>();
    private Viewer currentViewer;

    public ViewersRepository() {
        super(new GridLayout());
        this.getPanel().add(cardViewer);
    }

    @Override
    public String getName() {
        return Messages.getString("ViewersRepository.TabName"); //$NON-NLS-1$
    }

    public void addViewer(final Viewer viewer) {
        viewerList.add(viewer);
        cardViewer.add(viewer.getPanel(), viewer.getName());
    }

    public void removeViewer(final Viewer viewer) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    viewerList.remove(viewer);
                    cardViewer.remove(viewer.getPanel());
                }
            });
        } catch (InvocationTargetException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return getSupportedViewer(contentType) != null;
    }

    private Viewer getSupportedViewer(String contentType) {
        Viewer result = null;
        for (Viewer viewer : viewerList) {
            if (viewer.isSupportedType(contentType, true)) {
                result = viewer;
            }
        }
        return result;
    }

    private void clear() {
        for (Viewer viewer : viewerList) {
            viewer.loadFile(null);
        }
    }

    @Override
    public void init() {
        for (Viewer viewer : viewerList) {
            viewer.init();
        }
    }

    @Override
    public void dispose() {
        for (Viewer viewer : viewerList) {
            viewer.dispose();
        }
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {
        loadFile(content, null, highlightTerms);
    }

    @Override
    public void loadFile(IStreamSource content, String contentType, Set<String> highlightTerms) {

        if (content == null) {
            clear();
            return;
        }

        currentViewer = getSupportedViewer(contentType);
        if (currentViewer != null) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    CardLayout layout = (CardLayout) cardViewer.getLayout();
                    layout.show(cardViewer, currentViewer.getName());
                }
            });
            currentViewer.loadFile(content, contentType, highlightTerms);
        }

        for (Viewer viewer : viewerList) {
            if (viewer != currentViewer) {
                viewer.loadFile(null);
            }
        }

    }

    @Override
    public void scrollToNextHit(boolean forward) {
        if (currentViewer != null) {
            currentViewer.scrollToNextHit(forward);
        }
    }

    @Override
    public int getHitsSupported() {
        if (currentViewer != null) {
            return currentViewer.getHitsSupported();
        }
        return -1;
    }

    @Override
    public int getToolbarSupported() {
        int ret = 0;
        if (currentViewer != null) {
            int val = currentViewer.getToolbarSupported();
            if (val > ret) {
                ret = val;
            }
        }
        return ret;
    }

    @Override
    public boolean isToolbarVisible() {
        if (currentViewer != null) {
            return currentViewer.isToolbarVisible();
        }
        return false;
    }

    @Override
    public void setToolbarVisible(boolean isVisible) {
        if (currentViewer != null) {
            currentViewer.setToolbarVisible(isVisible);
        }
    }

    @Override
    public void copyScreen() {
        if (currentViewer != null) {
            currentViewer.copyScreen();
        }
    }

    public Viewer getCurrentViewer() {
        return currentViewer;
    }
}
