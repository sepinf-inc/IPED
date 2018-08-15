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
package dpf.sp.gpinf.indexer.desktop;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashSet;
import java.util.LinkedList;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
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

import dpf.sp.gpinf.indexer.desktop.TreeViewModel.Node;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSearcherImpl;
import dpf.sp.gpinf.indexer.search.IPEDSourceImpl;
import dpf.sp.gpinf.indexer.search.QueryBuilderImpl;
import dpf.sp.gpinf.indexer.util.SwingUtil;
import iped3.exception.ParseException;
import iped3.exception.QueryNodeException;
import iped3.search.LuceneSearchResult;

public class TreeListener implements TreeSelectionListener, ActionListener, TreeExpansionListener {

  Query treeQuery, recursiveTreeQuery;
  boolean rootSelected = false;
  HashSet<TreePath> selection = new HashSet<TreePath>();
  private long collapsedTime = 0;

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

    if (rootSelected || selection.isEmpty()) {
      treeQuery = new TermQuery(new Term(IndexItem.ISROOT, "true")); //$NON-NLS-1$
      recursiveTreeQuery = null;

    } else {
      String treeQueryStr = ""; //$NON-NLS-1$
      recursiveTreeQuery = new BooleanQuery();

      for (TreePath path : selection) {
        Document doc = ((Node) path.getLastPathComponent()).getDoc();

        String parentId = doc.get(IndexItem.FTKID);
        if (parentId == null)
          parentId = doc.get(IndexItem.ID);
        
        String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
        treeQueryStr += "(" + IndexItem.PARENTID + ":" + parentId + " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID + ") "; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

        BooleanQuery subQuery = new BooleanQuery();
        subQuery.add(new TermQuery(new Term(IndexItem.PARENTIDs, parentId)), Occur.MUST);
        subQuery.add(new TermQuery(new Term(IndexItem.EVIDENCE_UUID, sourceUUID)), Occur.MUST);
        ((BooleanQuery) recursiveTreeQuery).add(subQuery, Occur.SHOULD);
      }

      try {
        treeQuery = new QueryBuilderImpl(App.get().appCase).getQuery(treeQueryStr);
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
        if (parentId != null){
            String ftkId = doc.get(IndexItem.FTKID);
            if (ftkId == null){
                IPEDSourceImpl src = (IPEDSourceImpl) App.get().appCase.getAtomicSource(docId);
                docId = App.get().appCase.getBaseLuceneId(src) + src.getLuceneId(Integer.parseInt(parentId));
                path.addFirst(((TreeViewModel) App.get().tree.getModel()).new Node(docId));
            }else{
                String textQuery = IndexItem.FTKID + ":" + parentId; //$NON-NLS-1$
                if (textQuery != null) {
                  String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
                  textQuery += " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID;  //$NON-NLS-1$ //$NON-NLS-2$
                    
                  IPEDSearcherImpl task = new IPEDSearcherImpl(App.get().appCase, textQuery);
                  task.setTreeQuery(true);
                  result = task.luceneSearch();

                  if (result.getLength() == 1) {
                    docId = result.getLuceneIds()[0];
                    path.addFirst(((TreeViewModel) App.get().tree.getModel()).new Node(docId));
                  }else
                    parentId = null;
                }
            }
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
    
    if(selection.size() == 1 && !rootSelected){
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

}
