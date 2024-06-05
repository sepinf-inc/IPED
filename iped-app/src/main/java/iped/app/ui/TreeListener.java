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
    boolean rootSelected = false;
    HashSet<TreePath> selection = new HashSet<TreePath>();
    private long collapsedTime = 0;
    private boolean clearing = false;
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

        definedFilters = null;
        if (rootSelected || selection.isEmpty()) {
            treeQuery = new TermQuery(new Term(IndexItem.ISROOT, "true")); //$NON-NLS-1$
            recursiveTreeQuery = null;
        } else {
            String treeQueryStr = ""; //$NON-NLS-1$
            BooleanQuery.Builder recursiveQueryBuilder = new BooleanQuery.Builder();

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
            }
            recursiveTreeQuery = recursiveQueryBuilder.build();

            try {
                treeQuery = new QueryBuilder(App.get().appCase).getQuery(treeQueryStr);
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

        if (!clearing)
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
        clearing = true;
        App.get().tree.clearSelection();
        clearing = false;
    }

    @Override
    public List getDefinedFilters() {
        TreeListener self = this;
        if (definedFilters == null) {
            definedFilters = new ArrayList<IFilter>();
            if (selection.size() >= 1) {
                if (App.get().recursiveTreeList.isSelected()) {
                    definedFilters.add(new QueryFilter(self.recursiveTreeQuery));
                } else {
                    definedFilters.add(new QueryFilter(self.treeQuery));
                }
            }
        }
        return definedFilters;
    }

    @Override
    public boolean hasFiltersApplied() {
        return recursiveTreeQuery != null;
    }

    @Override
    public Query getQuery() {
        if (App.get().recursiveTreeList.isSelected())
            return recursiveTreeQuery;
        else
            return treeQuery;
    }

    public String toString() {
        return "Evidence panel";
    }

    @Override
    public boolean hasFilters() {
        return recursiveTreeQuery != null;
    }

}
