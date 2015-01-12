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
package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;

public class TreeViewModel implements TreeModel{
	
	private Vector<TreeModelListener> treeModelListeners = new Vector<TreeModelListener>();
	private Node root;
	private static String FIRST_STRING = "Texto para agilizar primeiro acesso ao método toString, chamado para todos os filhos, inclusive fora da janela de visualização da árvore";
	private RowComparator comparator = new RowComparator(4);
	
	public class Node{
		private Document doc;
		int docId;
		private SearchResult children;
		boolean first = true;
		
		public Node(int docId){
			this.docId = docId;
		}
		
		public Document getDoc(){
			if(doc == null)
				if(docId != -1)
					try {
						this.doc = App.get().reader.document(docId);
						
					} catch (IOException e) {
						//e.printStackTrace();
					}
			return doc;
		}
		
		public String toString(){
			if(first){
				first = false;
				return FIRST_STRING;
			}
			
			return getDoc().get("nome");
		}
		
		public SearchResult getChildren(){
			if(children == null)
				listSubItens(getDoc());
			
			return children;
		}
		
		private void listSubItens(Document doc) {
			
			String parentId = doc.get("ftkId");
			if (parentId == null)
				parentId = doc.get("id");
			
			String textQuery = "parentId:" + parentId;
			
			String parentSleuthId = doc.get("sleuthId");
			if(parentSleuthId != null)
				textQuery += " parentSleuthId:" + parentSleuthId;
			
			textQuery = "(" + textQuery + ") && (isDir:true || hasChildren:true)";

			try {
				PesquisarIndice task = new PesquisarIndice(PesquisarIndice.getQuery(textQuery));
				children = task.pesquisar();
				Integer[] array = ArrayUtils.toObject(children.docs);
				Arrays.sort(array, comparator);
				children.docs = ArrayUtils.toPrimitive(array);
				children.scores = null;

			} catch (Exception e) {
				children = new SearchResult(0);
				e.printStackTrace();
			}

		}
	}
	
	public TreeViewModel(){
		root = new Node(-1);
		root.doc = new Document();
		root.doc.add(new StoredField("nome", "Evidências"));
		PesquisarIndice pesquisa;
		try {
			pesquisa = new PesquisarIndice(PesquisarIndice.getQuery("isRoot:true"));
			root.children = pesquisa.pesquisar();
			Integer[] array = ArrayUtils.toObject(root.children.docs);
			Arrays.sort(array, comparator);
			root.children.docs = ArrayUtils.toPrimitive(array);
			root.children.scores = null;
			
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
		return new Node(((Node)parent).getChildren().docs[index]);
	}

	@Override
	public int getChildCount(Object parent) {
		return ((Node)parent).getChildren().length;
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		
		Node childNode = (Node)child;
		for(int i = 0; i < ((Node)parent).getChildren().docs.length; i++)
			if(childNode.docId == ((Node)parent).getChildren().docs[i])
				return i;
		
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
