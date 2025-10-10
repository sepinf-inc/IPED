package iped.viewers;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import iped.io.IStreamSource;
import iped.viewers.api.AbstractViewer;
import iped.viewers.localization.Messages;
import iped.viewers.search.CenterTopLayoutManager;
import iped.viewers.search.HitsUpdater;
import iped.viewers.search.SearchEvent;
import iped.viewers.search.SearchListener;
import iped.viewers.search.SearchPanel;

public class MultiViewer extends AbstractViewer {

    private JPanel cardViewer = new JPanel(new CardLayout());
    private ArrayList<AbstractViewer> viewerList = new ArrayList<AbstractViewer>();
    private AbstractViewer currentViewer;
    private SearchPanel searchPanel;

    public MultiViewer() {
        super(new CenterTopLayoutManager());
        searchPanel = new SearchPanel();
        searchPanel.setPreferredSize(new Dimension(240, 28));
        searchPanel.setVisible(false);
        getPanel().add(searchPanel, CenterTopLayoutManager.TOP);
        getPanel().add(cardViewer, CenterTopLayoutManager.CENTER);
        HitsUpdater hitsUpdater = new HitsUpdater() {
            @Override
            public void updateHits(int currHit, int totHits) {
                searchPanel.setHits(currHit, totHits);
            }
        };
        searchPanel.addSearchListener(new SearchListener() {
            @Override
            public void stateChanged(SearchEvent e) {
                if (currentViewer != null) {
                    if (e.getType() == SearchEvent.termChange) {
                        currentViewer.searchInViewer(searchPanel.getText(), hitsUpdater);
                    } else if (e.getType() == SearchEvent.nextHit) {
                        currentViewer.scrollToNextHit(true, true, hitsUpdater);
                    } else if (e.getType() == SearchEvent.prevHit) {
                        currentViewer.scrollToNextHit(false, true, hitsUpdater);
                    }
                }
            }
        });
    }

    @Override
    public String getName() {
        return Messages.getString("MultiViewer.TabName");
    }

    public void addViewer(final AbstractViewer viewer) {
        viewerList.add(viewer);
        cardViewer.add(viewer.getPanel(), viewer.getName());
    }

    public void removeViewer(final AbstractViewer viewer) {
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

    private AbstractViewer getSupportedViewer(String contentType) {
        AbstractViewer result = null;
        for (AbstractViewer viewer : viewerList) {
            if (viewer.isSupportedType(contentType, true)) {
                result = viewer;
            }
        }
        return result;
    }

    private void clear() {
        for (AbstractViewer viewer : viewerList) {
            viewer.loadFile(null);
        }
    }

    @Override
    public void init() {
        for (AbstractViewer viewer : viewerList) {
            viewer.init();
        }
    }

    @Override
    public void dispose() {
        for (AbstractViewer viewer : viewerList) {
            viewer.dispose();
        }
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {
        loadFile(content, null, highlightTerms);
    }

    @Override
    public void loadFile(IStreamSource content, String contentType, Set<String> highlightTerms) {

        searchPanel.setVisible(false);
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

        for (AbstractViewer viewer : viewerList) {
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
    public boolean isSearchSupported() {
        if (currentViewer != null) {
            return currentViewer.isSearchSupported();
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

    public AbstractViewer getCurrentViewer() {
        return currentViewer;
    }

    public void setBlurFilter(boolean enableBlur) {
        for (AbstractViewer viewer : viewerList) {
            if (viewer instanceof ImageViewer) {
                ((ImageViewer) viewer).setBlurFilter(enableBlur);
            }
        }
    }

    public void setGrayFilter(boolean enableGray) {
        for (AbstractViewer viewer : viewerList) {
            if (viewer instanceof ImageViewer) {
                ((ImageViewer) viewer).setGrayFilter(enableGray);
            }
        }
    }

    public void searchInViewer() {
        searchPanel.setVisible(!searchPanel.isVisible());
    }

    public List<AbstractViewer> getViewerList() {
        return Collections.unmodifiableList(viewerList);
    }
}
