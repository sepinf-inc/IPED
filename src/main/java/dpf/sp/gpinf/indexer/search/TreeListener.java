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
import java.util.ArrayList;
import java.util.LinkedList;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import dpf.sp.gpinf.indexer.search.TreeViewModel.Node;

public class TreeListener implements  TreeSelectionListener{
	
	Query treeQuery, recursiveTreeQuery;

	@Override
	public void valueChanged(TreeSelectionEvent evt) {
		
		Node selectedNode = (Node)evt.getPath().getLastPathComponent();
		
		if(selectedNode.docId == -1){
			treeQuery = new TermQuery(new Term("isRoot", "true"));
			recursiveTreeQuery = null;
			
		}else{
			Document doc = selectedNode.getDoc();
			
			String parentId = doc.get("ftkId");
			if (parentId == null)
				parentId = doc.get("id");
			
			treeQuery = new TermQuery(new Term("parentId", parentId));
			
			String parentSleuthId = doc.get("sleuthId");
			if(parentSleuthId != null){
				BooleanQuery boolQuery = new BooleanQuery();
				boolQuery.add(treeQuery, Occur.SHOULD);
				boolQuery.add(new TermQuery(new Term("parentSleuthId", parentSleuthId)), Occur.SHOULD);
				treeQuery = boolQuery;
			}
			
			BooleanQuery boolQuery = new BooleanQuery();
			/*String path = doc.get("caminho");
			if(path.endsWith("/"))
				boolQuery.add(new PrefixQuery(new Term("fullPath", path)), Occur.SHOULD);
			else
				boolQuery.add(new PrefixQuery(new Term("fullPath", path + "/")), Occur.SHOULD);
			boolQuery.add(new PrefixQuery(new Term("fullPath", path + ">>")), Occur.SHOULD);
			boolQuery.add(new PrefixQuery(new Term("fullPath", path + "\\")), Occur.SHOULD);
			boolQuery.add(new TermQuery(new Term("id", doc.get("id"))), Occur.MUST_NOT);*/
			
			boolQuery.add(new TermQuery(new Term("parentIds", parentId)), Occur.SHOULD);
			if(parentSleuthId != null)
				boolQuery.add(new TermQuery(new Term("parentIds", "s"+parentSleuthId)), Occur.SHOULD);
			
			recursiveTreeQuery =  boolQuery;
			
		}
		
		App.get().appletListener.updateFileListing();

	}
	
	public void navigateToParent(int docId){
		
		LinkedList<Node> path = new LinkedList<Node>(); 
		SearchResult result = new SearchResult(0);
		String textQuery = null;
		do{
			try {
				Document doc = App.get().reader.document(docId);
				
				textQuery = null;
				String parentId = doc.get("parentId");
				if(parentId != null)
					textQuery = "id:" + parentId;

				String ftkId = doc.get("ftkId");
				if (ftkId != null)
					textQuery = "ftkId:" + parentId;
				
				String parentSleuthId = doc.get("parentSleuthId");
				if(parentSleuthId != null)
					textQuery = "sleuthId:" + parentSleuthId;
				
				if(textQuery != null){
					PesquisarIndice task = new PesquisarIndice(PesquisarIndice.getQuery(textQuery));
					result = task.pesquisar();
					
					if(result.length == 1){
						docId = result.docs[0];
						path.addFirst(((TreeViewModel)App.get().tree.getModel()).new Node(docId));
					}
				}
			
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			//System.out.println("subindo");
			
		}while(result.length == 1 && textQuery != null);
		
		path.addFirst((Node)App.get().tree.getModel().getRoot());
		
		TreePath treePath = new TreePath(path.toArray());
		App.get().tree.setExpandsSelectedPaths(true);
		App.get().tree.setSelectionPath(treePath);
		App.get().tree.scrollPathToVisible(treePath);
		
		
	}

}
