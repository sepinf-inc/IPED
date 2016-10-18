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

import java.io.IOException;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Vector;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.LuceneSearchResult;

public class TreeViewModel implements TreeModel {

  private Vector<TreeModelListener> treeModelListeners = new Vector<TreeModelListener>();
  private Node root;
  private static String FIRST_STRING = "Texto para agilizar primeiro acesso ao método toString, chamado para todos os filhos, inclusive fora da janela de visualização da árvore";

  private RowComparator getComparator1() {
    return new RowComparator(IndexItem.NAME) {
      @Override
      public int compare(Integer a, Integer b) {
        return sdv.getOrd(a) - sdv.getOrd(b);
      }
    };
  }
  
  private Collator collator = Collator.getInstance();
	
	private Comparator<Integer> getComparator(){
		final HashSet<String> fields = new HashSet<String>();
		fields.add(IndexItem.NAME);
		collator.setStrength(Collator.PRIMARY);
		return new Comparator<Integer>(){
			@Override
		    public int compare(Integer a, Integer b) {
				try {
					Document doc1 = App.get().appCase.getReader().document(a, fields);
					Document doc2 = App.get().appCase.getReader().document(b, fields);
			        return collator.compare(doc1.get(IndexItem.NAME),doc2.get(IndexItem.NAME));
			        
				} catch (IOException e) {
					e.printStackTrace();
				}
				return 0;
		    }
		};
	}

  public class Node {

    private Document doc;
    int docId;
    private LuceneSearchResult children;
    boolean first = true;

    public Node(int docId) {
      this.docId = docId;
    }

    public Document getDoc() {
      if (doc == null) {
        if (docId != -1) {
          try {
            this.doc = App.get().appCase.getReader().document(docId);

          } catch (IOException e) {
            //e.printStackTrace();
          }
        }
      }
      return doc;
    }

    public String toString() {
      if (first) {
        first = false;
        return FIRST_STRING;
      }

      return getDoc().get(IndexItem.NAME);
    }

    public LuceneSearchResult getChildren() {
      if (children == null) {
        listSubItens(getDoc());
      }

      return children;
    }

    private void listSubItens(Document doc) {

      String parentId = doc.get(IndexItem.FTKID);
      if (parentId == null) {
        parentId = doc.get(IndexItem.ID);
      }

      String sourceUUID = doc.get(IndexItem.EVIDENCE_UUID);
      String textQuery = IndexItem.PARENTID + ":" + parentId + " && " + IndexItem.EVIDENCE_UUID + ":" + sourceUUID;

      textQuery = "(" + textQuery + ") && (" + IndexItem.ISDIR + ":true || " + IndexItem.HASCHILD + ":true)";

      try {
		IPEDSearcher task = new IPEDSearcher(App.get().appCase, textQuery);
		task.setTreeQuery(true);
        children = task.luceneSearch();
        Integer[] array = ArrayUtils.toObject(children.getLuceneIds());
        Arrays.sort(array, getComparator());
        children = LuceneSearchResult.buildSearchResult(ArrayUtils.toPrimitive(array), null);

      } catch (Exception e) {
        children = new LuceneSearchResult(0);
        e.printStackTrace();
      }

    }
  }

  public TreeViewModel() {
    root = new Node(-1);
    root.doc = new Document();
    root.doc.add(new StoredField(IndexItem.NAME, "Evidências"));
    try {
      IPEDSearcher task = new IPEDSearcher(App.get().appCase, IndexItem.ISROOT + ":true");
	  task.setTreeQuery(true);
      root.children = task.luceneSearch();
      Integer[] array = ArrayUtils.toObject(root.children.getLuceneIds());
      Arrays.sort(array, getComparator());
      root.children = LuceneSearchResult.buildSearchResult(ArrayUtils.toPrimitive(array), null);

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  @Override
  public void addTreeModelListener(TreeModelListener l) {
    treeModelListeners.addElement(l);

  }

  @Override
  public Object getChild(Object parent, int index) {
    return new Node(((Node) parent).getChildren().getLuceneIds()[index]);
  }

  @Override
  public int getChildCount(Object parent) {
    return ((Node) parent).getChildren().getLength();
  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {

    Node childNode = (Node) child;
    for (int i = 0; i < ((Node) parent).getChildren().getLength(); i++) {
      if (childNode.docId == ((Node) parent).getChildren().getLuceneIds()[i]) {
        return i;
      }
    }

    return -1;
  }

  @Override
  public Object getRoot() {
    return root;
  }

  @Override
  public boolean isLeaf(Object node) {
    return false;
  }

  @Override
  public void removeTreeModelListener(TreeModelListener l) {
    treeModelListeners.removeElement(l);

  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    // TODO Auto-generated method stub

  }

}
