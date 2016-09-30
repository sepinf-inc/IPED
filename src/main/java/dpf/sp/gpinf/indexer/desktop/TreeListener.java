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
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import dpf.sp.gpinf.indexer.desktop.TreeViewModel.Node;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.QueryBuilder;
import dpf.sp.gpinf.indexer.search.SearchResult;

public class TreeListener implements TreeSelectionListener, ActionListener, TreeExpansionListener, MouseListener {

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
      treeQuery = new TermQuery(new Term(IndexItem.ISROOT, "true"));
      recursiveTreeQuery = null;

    } else {
      String treeQueryStr = "";
      recursiveTreeQuery = new BooleanQuery();

      for (TreePath path : selection) {
        Document doc = ((Node) path.getLastPathComponent()).getDoc();

        String parentId = doc.get(IndexItem.FTKID);
        if (parentId == null)
          parentId = doc.get(IndexItem.ID);
        
        String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
        treeQueryStr += "(" + IndexItem.PARENTID + ":" + parentId + " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID + ") ";

        BooleanQuery subQuery = new BooleanQuery();
        subQuery.add(new TermQuery(new Term(IndexItem.PARENTIDs, parentId)), Occur.MUST);
        subQuery.add(new TermQuery(new Term(IndexItem.EVIDENCE_UUID, sourceUUID)), Occur.MUST);
        ((BooleanQuery) recursiveTreeQuery).add(subQuery, Occur.SHOULD);
      }

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
    SearchResult result = new SearchResult(0);
    String textQuery = null;
    do {
      try {
        Document doc = App.get().appCase.getReader().document(docId);

        textQuery = null;
        String parentId = doc.get(IndexItem.PARENTID);
        if (parentId != null)
          textQuery = IndexItem.ID + ":" + parentId;

        String ftkId = doc.get(IndexItem.FTKID);
        if (ftkId != null)
          textQuery = IndexItem.FTKID + ":" + parentId;
        
        String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
        textQuery += " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID;

        if (textQuery != null) {
		  IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery);
		  task.setTreeQuery(true);
          result = task.pesquisar();

          if (result.getLength() == 1) {
            docId = result.getLuceneIds()[0];
            path.addFirst(((TreeViewModel) App.get().tree.getModel()).new Node(docId));
          }
        }

      } catch (Exception e) {
        e.printStackTrace();
      }

    } while (result.getLength() == 1 && textQuery != null);

    path.addFirst((Node) App.get().tree.getModel().getRoot());

    TreePath treePath = new TreePath(path.toArray());
    App.get().tree.setExpandsSelectedPaths(true);
    App.get().tree.setSelectionPath(treePath);
    App.get().tree.scrollPathToVisible(treePath);

    App.get().treeTab.setSelectedIndex(2);
  }

  @Override
  public void actionPerformed(ActionEvent e) {

    if ((App.get().recursiveTreeList.isSelected() && rootSelected) || selection.isEmpty()) {
      App.get().treeTab.setBackgroundAt(2, App.get().defaultTabColor);
    } else {
      App.get().treeTab.setBackgroundAt(2, App.get().alertColor);
    }

    App.get().appletListener.updateFileListing();

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
  public void mouseClicked(MouseEvent e) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.isPopupTrigger()) {
      showPopupMenu(e);
    }

  }

  private void showPopupMenu(MouseEvent e) {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem exportTree = new JMenuItem("Exportar árvore de diretórios");
    exportTree.addActionListener(new TreeMenuListener(false));
    menu.add(exportTree);

    JMenuItem exportTreeChecked = new JMenuItem("Exportar árvore de diretórios (itens selecionados)");
    exportTreeChecked.addActionListener(new TreeMenuListener(true));
    menu.add(exportTreeChecked);

    menu.show((Component) e.getSource(), e.getX(), e.getY());
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (e.isPopupTrigger()) {
      showPopupMenu(e);
    }

  }

  class TreeMenuListener implements ActionListener {

    boolean onlyChecked = false;

    TreeMenuListener(boolean onlyChecked) {
      this.onlyChecked = onlyChecked;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

      TreePath[] paths = App.get().tree.getSelectionPaths();
      if (paths == null || paths.length != 1) {
        JOptionPane.showMessageDialog(null, "Selecione 01 (um) nó na árvore de diretórios como base de exportação!");
      } else {
        Node treeNode = (Node) paths[0].getLastPathComponent();
        ExportFileTree.salvarArquivo(treeNode.docId, onlyChecked);
      }

    }

  }

  @Override
  public void mouseEntered(MouseEvent e) {
    // TODO Auto-generated method stub

  }

  @Override
  public void mouseExited(MouseEvent e) {
    // TODO Auto-generated method stub

  }

}
