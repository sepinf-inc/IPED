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
package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import iped.app.ui.TreeViewModel.Node;
import iped.app.ui.filters.QueryFilter;
import iped.engine.data.IPEDSource;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.QueryBuilder;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilterer;

public class TreeListener extends MouseAdapter implements TreeSelectionListener, ActionListener, TreeExpansionListener, IQueryFilterer {

    private Query treeQuery, recursiveTreeQuery;
    PathFilter currentFilter;
    private PathFilter currentRecursiveFilter;

    boolean rootSelected = false;
    HashSet<TreePath> selection = new HashSet<TreePath>();
    private long collapsedTime = 0;
    private ArrayList<IFilter> definedFilters;

    @Override
    public void valueChanged(TreeSelectionEvent evt) {

        for (TreePath path : evt.getPaths()) {
            if (selection.contains(path)) {
                selection.remove(path);
            } else {
                selection.add(path);
            }
        }

        if (System.currentTimeMillis() - collapsedTime < 200) {
            collapsedTime = 0;
            return;
        }

        rootSelected = false;
        for (TreePath path : selection) {
            if (((Node) path.getLastPathComponent()).docId == -1) {
                rootSelected = true;
                break;
            }
        }

        definedFilters = new ArrayList<IFilter>();

        if (rootSelected || selection.isEmpty()) {
            currentFilter = null;

            treeQuery = new TermQuery(new Term(IndexItem.ISROOT, "true")); //$NON-NLS-1$
            recursiveTreeQuery = null;
        } else {
            String treeQueryStr = ""; //$NON-NLS-1$
            BooleanQuery.Builder recursiveQueryBuilder = new BooleanQuery.Builder();

            currentFilter = new PathFilter();
            currentRecursiveFilter = new PathFilter();
            currentRecursiveFilter.setRecursive(true);

            for (TreePath path : selection) {
                Document doc = ((Node) path.getLastPathComponent()).getDoc();

                String parentId = doc.get(IndexItem.ID);

                String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
                treeQueryStr += "(" + IndexItem.PARENTID + ":" + parentId + " && " + IndexItem.EVIDENCE_UUID + ":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        + sourceUUID + ") "; //$NON-NLS-1$

                BooleanQuery.Builder subQuery = new BooleanQuery.Builder();
                subQuery.add(new TermQuery(new Term(IndexItem.PARENTIDs, parentId)), Occur.MUST);
                subQuery.add(new TermQuery(new Term(IndexItem.EVIDENCE_UUID, sourceUUID)), Occur.MUST);
                recursiveQueryBuilder.add(subQuery.build(), Occur.SHOULD);

                currentFilter.addParentId(sourceUUID, parentId);
                currentRecursiveFilter.addParentId(sourceUUID, parentId);
            }
            recursiveTreeQuery = recursiveQueryBuilder.build();
            currentRecursiveFilter.setQuery(recursiveTreeQuery);

            if (selection.size() > 0) {
                if (App.get().recursiveTreeList.isSelected()) {
                    definedFilters.add(currentRecursiveFilter);
                } else {
                    definedFilters.add(currentFilter);
                }
            }

            try {
                treeQuery = new QueryBuilder(App.get().appCase).getQuery(treeQueryStr);
                currentFilter.setQuery(recursiveTreeQuery);

            } catch (ParseException | QueryNodeException e) {
                e.printStackTrace();
            }
        }
        actionPerformed(null);

    }

    public void navigateToParent(int docId) {

        LinkedList<Node> path = new LinkedList<Node>();
        LuceneSearchResult result = new LuceneSearchResult(0);
        String parentId = null;
        do {
            try {
                Document doc = App.get().appCase.getReader().document(docId);

                parentId = doc.get(IndexItem.PARENTID);
                if (parentId != null) {
                    IPEDSource src = (IPEDSource) App.get().appCase.getAtomicSource(docId);
                    docId = App.get().appCase.getBaseLuceneId(src) + src.getLuceneId(Integer.parseInt(parentId));
                    path.addFirst(((TreeViewModel) App.get().tree.getModel()).new Node(docId));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        } while (parentId != null);

        path.addFirst((Node) App.get().tree.getModel().getRoot());

        App.get().moveEvidenveTabToFront();

        TreePath treePath = new TreePath(path.toArray());
        App.get().tree.setExpandsSelectedPaths(true);
        App.get().tree.setSelectionPath(treePath);
        App.get().tree.scrollPathToVisible(treePath);

    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if ((App.get().recursiveTreeList.isSelected() && rootSelected) || selection.isEmpty()) {
            App.get().setEvidenceDefaultColor(true);
        } else {
            App.get().setEvidenceDefaultColor(false);
        }

        App.get().appletListener.updateFileListing();

        if (selection.size() == 1 && selection.iterator().next().getPathCount() > 2) {
            int luceneId = ((Node) selection.iterator().next().getLastPathComponent()).docId;
            FileProcessor parsingTask = new FileProcessor(luceneId, false);
            parsingTask.execute();
        }

    }

    @Override
    public void treeExpanded(TreeExpansionEvent event) {
        // TODO Auto-generated method stub

    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
        collapsedTime = System.currentTimeMillis();

    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showTreeMenu(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showTreeMenu(e);
        }
    }

    private void showTreeMenu(MouseEvent e) {
        MenuClass menu = new MenuClass(true);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    @Override
    public void clearFilter() {
        TreeSelectionListener[] listeners = App.get().tree.getTreeSelectionListeners();
        for (TreeSelectionListener lis : listeners) {
            App.get().tree.removeTreeSelectionListener(lis);
        }

        try {
            definedFilters = null;
            selection.clear();
            App.get().tree.clearSelection();
        } finally {
            for (TreeSelectionListener lis : listeners) {
                App.get().tree.addTreeSelectionListener(lis);
            }
        }
    }

    class PathFilter extends QueryFilter {
        boolean recursive = false;
        HashMap<String, List<String>> evidenceParentIdMap = new HashMap<String, List<String>>();

        public void setRecursive(boolean recursive) {
            this.recursive = recursive;
        }

        public void setQuery(Query q) {
            this.query = q;
        }

        public PathFilter(Query query) {
            super(query);
        }

        public PathFilter() {
            super(null);
        }

        public boolean isRecursive() {
            return recursive;
        }

        public void addParentId(String evidenceUUID, String parentId) {
            List<String> parentIds = evidenceParentIdMap.get(evidenceUUID);
            if (parentIds == null) {
                parentIds = new ArrayList<String>();
                evidenceParentIdMap.put(evidenceUUID, parentIds);
            }
            parentIds.add(parentId);
        }
    }

    @Override
    public List getDefinedFilters() {
        return definedFilters;
    }

    @Override
    public boolean hasFiltersApplied() {
        return definedFilters != null && definedFilters.size() > 0
                && ((PathFilter) definedFilters.get(0)).evidenceParentIdMap.size() > 0;
    }

    @Override
    public Query getQuery() {
        if (definedFilters != null) {
            if (App.get().recursiveTreeList.isSelected())
                return recursiveTreeQuery;
            else
                return treeQuery;
        }
        return null;
    }

    public String toString() {
        return "Evidence panel";
    }

    @Override
    public boolean hasFilters() {
        return definedFilters != null && definedFilters.size() > 0
                && ((PathFilter) definedFilters.get(0)).evidenceParentIdMap.size() > 0;
    }

    @Override
    public void restoreDefinedFilters(List<IFilter> filtersToRestore) {
        TreeSelectionListener[] listeners = App.get().tree.getTreeSelectionListeners();
        for (TreeSelectionListener lis : listeners) {
            App.get().tree.removeTreeSelectionListener(lis);
        }

        try {
            definedFilters = new ArrayList<IFilter>();
            App.get().tree.clearSelection();
            for (IFilter filter : filtersToRestore) {
                if (filter instanceof PathFilter) {
                    PathFilter pathFilter = (PathFilter) filter;
                    App.get().recursiveTreeList.setSelected(pathFilter.isRecursive());

                    for (int i = 0; i < App.get().tree.getRowCount(); i++) {
                        TreePath tp = App.get().tree.getPathForRow(i);
                        Node node = (Node) tp.getLastPathComponent();
                        if (checkSelection(node, pathFilter)) {
                            App.get().tree.addSelectionPath(tp);
                        }

                    }

                    definedFilters.add(pathFilter);

                }
            }
        } finally {
            for (TreeSelectionListener lis : listeners) {
                App.get().tree.addTreeSelectionListener(lis);
            }
        }
    }

    private boolean checkSelection(Node node, PathFilter pathFilter) {
        Document doc = node.getDoc();
        String evidenceUUID = doc.get(IndexItem.EVIDENCE_UUID);
        List<String> parentIds = pathFilter.evidenceParentIdMap.get(evidenceUUID);
        if (parentIds != null) {
            String parentId = doc.get(IndexItem.ID);
            return parentIds.contains(parentId);
        }
        return false;
    }

}
