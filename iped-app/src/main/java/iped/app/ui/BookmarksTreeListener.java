package iped.app.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.roaringbitmap.RoaringBitmap;

import iped.data.IMultiBookmarks;
import iped.engine.data.MultiBitmapBookmarks;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IBitmapFilter;
import iped.viewers.api.IFilter;
import iped.viewers.api.IMutableFilter;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class BookmarksTreeListener implements TreeSelectionListener, TreeExpansionListener, IResultSetFilterer {
    private HashSet<Object> selection = new HashSet<>();
    private volatile boolean updatingSelection = false;
    private long collapsed = 0;
    private boolean clearing = false;

    HashMap<Object, IFilter> definedFilters = new HashMap<Object, IFilter>();

    public Set<String> getSelectedBookmarkNames() {
        return selection.stream().filter(b -> b != BookmarksTreeModel.ROOT && b != BookmarksTreeModel.NO_BOOKMARKS).map(b -> b.toString()).collect(Collectors.toSet());
    }

    public boolean isRootSelected() {
        return selection.contains(BookmarksTreeModel.ROOT);
    }

    public boolean isNoBookmarksSelected() {
        return selection.contains(BookmarksTreeModel.NO_BOOKMARKS);
    }

    @Override
    public void valueChanged(TreeSelectionEvent evt) {
        if (updatingSelection) {
            return;
        }

        if (System.currentTimeMillis() - collapsed < 500) {
            if (evt.getPath().getLastPathComponent().equals(BookmarksTreeModel.ROOT)) {
                updateModelAndSelection();
            }
            return;
        }

        for (TreePath path : evt.getPaths()) {
            if (selection.contains(path.getLastPathComponent())) {
                selection.remove(path.getLastPathComponent());
                definedFilters.remove(path.getLastPathComponent());
            } else {
                Object bookmark = path.getLastPathComponent();
                selection.add(bookmark);
                if (!bookmark.equals(BookmarksTreeModel.ROOT) && !bookmark.equals(BookmarksTreeModel.NO_BOOKMARKS)) {
                    HashSet<String> oneBookmark = new HashSet<>();
                    oneBookmark.add(bookmark.toString());
                    definedFilters.put(bookmark, new BookMarkFilter(oneBookmark));
                }
            }
        }

        if (!clearing)
            App.get().appletListener.updateFileListing();

        if (selection.contains(BookmarksTreeModel.ROOT) || selection.isEmpty()) {
            App.get().setBookmarksDefaultColor(true);
        } else {
            App.get().setBookmarksDefaultColor(false);
        }

    }

    public void updateModelAndSelection() {

        updatingSelection = true;
        Set<String> bookmarkSet = ((BookmarksTreeModel) App.get().bookmarksTree.getModel()).bookmarks;

        if (bookmarkSet != null && !selection.isEmpty()) {

            HashSet<Object> tempSel = new HashSet<>(selection);
            selection.clear();
            definedFilters.clear();

            for (Object path : tempSel) {
                if (path == BookmarksTreeModel.ROOT || path == BookmarksTreeModel.NO_BOOKMARKS || App.get().appCase.getMultiBookmarks().getBookmarkSet().contains(path)) {
                    selection.add(path);
                    HashSet<String> oneBookmark = new HashSet<>();
                    oneBookmark.add(path.toString());
                    definedFilters.put(path, new BookMarkFilter(oneBookmark));
                }
            }

            ArrayList<TreePath> selectedPaths = new ArrayList<TreePath>();
            for (Object name : selection) {
                Object[] path = name == BookmarksTreeModel.ROOT ? new Object[] { BookmarksTreeModel.ROOT } : new Object[] { BookmarksTreeModel.ROOT, name };
                selectedPaths.add(new TreePath(path));
            }

            boolean rootCollapsed = App.get().bookmarksTree.isCollapsed(0);
            App.get().bookmarksTree.setModel(new BookmarksTreeModel());
            if (rootCollapsed) {
                App.get().bookmarksTree.collapseRow(0);
            }

            App.get().bookmarksTree.setSelectionPaths(selectedPaths.toArray(new TreePath[0]));

        } else {
            boolean rootCollapsed = App.get().bookmarksTree.isCollapsed(0);
            App.get().bookmarksTree.setModel(new BookmarksTreeModel());
            if (rootCollapsed) {
                App.get().bookmarksTree.collapseRow(0);
            }
        }

        // informs combinedfilter of bookmark change so it can update its internal
        // bitset cache
        App.get().filtersPanel.getCombinedFilterer().startSearchResult(App.get().ipedResult);

        updatingSelection = false;
    }

    @Override
    public void treeExpanded(TreeExpansionEvent event) {

    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
        collapsed = System.currentTimeMillis();

    }

    @Override
    public void clearFilter() {
        clearing = true;
        App.get().bookmarksTree.clearSelection();
        clearing = false;
    }

    NoBookMarkFilter noBookMarkFilter = new NoBookMarkFilter();

    @Override
    public List getDefinedFilters() {
        ArrayList<IFilter> result = new ArrayList<IFilter>();
        BookmarksTreeListener self = this;
        Set<String> bookmarkSelection = getSelectedBookmarkNames();
        if ((!bookmarkSelection.isEmpty() || isNoBookmarksSelected()) && !isRootSelected()) {
            if ((!bookmarkSelection.isEmpty() || isNoBookmarksSelected()) && !isRootSelected()) {
                result.addAll(definedFilters.values());

                if (isNoBookmarksSelected()) {
                    result.add(noBookMarkFilter);
                }
            }
        }
        return result;
    }

    public String toString() {
        return "Bookmarks filterer";
    }

    @Override
    public IFilter getFilter() {
        BookmarksTreeListener self = this;
        Set<String> bookmarkSelection = getSelectedBookmarkNames();

        if ((!bookmarkSelection.isEmpty() || isNoBookmarksSelected()) && !isRootSelected()) {
            IFilter result;
            if (isNoBookmarksSelected()) {
                if (bookmarkSelection.isEmpty()) {
                    result = noBookMarkFilter;
                } else {
                    result = new NoBookMarkFilter(bookmarkSelection);
                }
            } else {
                result = new BookMarkFilter(bookmarkSelection);
            }
            return result;
        }
        return null;
    }

    @Override
    public boolean hasFilters() {
        return selection.size() > 0 && !isRootSelected();
    }

    @Override
    public boolean hasFiltersApplied() {
        return selection.size() > 0 && !isRootSelected();
    }
}

class BookMarkFilter implements IResultSetFilter, IMutableFilter, IBitmapFilter {
    Set<String> bookmark;

    public BookMarkFilter(Set<String> bookmark2) {
        this.bookmark = bookmark2;
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        return (MultiSearchResult) App.get().appCase.getMultiBookmarks().filterBookmarks(src, (Set<String>) bookmark);
    }

    public String toString() {
        return bookmark.toString();
    }

    @Override
    public RoaringBitmap[] getBitmap() {
        IMultiBookmarks mm = App.get().appCase.getMultiBookmarks();
        if (mm instanceof MultiBitmapBookmarks) {
            return ((MultiBitmapBookmarks) mm).getBookmarksUnions(bookmark);
        }
        return null;
    }
};

class NoBookMarkFilter implements IResultSetFilter, IMutableFilter, IBitmapFilter {
    Set<String> bookmarkSelection;

    public NoBookMarkFilter(Set<String> bookmarkSelection) {
        this.bookmarkSelection = bookmarkSelection;
    }

    public NoBookMarkFilter() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        return (MultiSearchResult) App.get().appCase.getMultiBookmarks().filterNoBookmarks(src);
    }

    public String toString() {
        return BookmarksTreeModel.NO_BOOKMARKS_NAME;
    }

    @Override
    public boolean isToFilterOut() {
        return true;
    };

    @Override
    public RoaringBitmap[] getBitmap() {
        IMultiBookmarks mm = App.get().appCase.getMultiBookmarks();
        if (mm instanceof MultiBitmapBookmarks) {
            RoaringBitmap[] result = ((MultiBitmapBookmarks) mm).getBookmarksUnions();
            RoaringBitmap[] rbSelecitons = null;
            if (bookmarkSelection != null) {
                rbSelecitons = ((MultiBitmapBookmarks) mm).getBookmarksUnions(bookmarkSelection);
            }
            if (rbSelecitons != null) {
                for (int i = 0; i < result.length; i++) {
                    result[i].andNot(rbSelecitons[i]);
                }
            }
            return result;
        }
        return null;
    }
}
