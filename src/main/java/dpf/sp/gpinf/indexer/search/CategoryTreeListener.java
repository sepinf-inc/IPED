package dpf.sp.gpinf.indexer.search;

import java.util.HashSet;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;

import dpf.sp.gpinf.indexer.analysis.FastASCIIFoldingFilter;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.CategoryTreeModel.Category;

public class CategoryTreeListener implements TreeSelectionListener, TreeExpansionListener{

	BooleanQuery query;
	private HashSet<TreePath> selection = new HashSet<TreePath>();
	private TreePath root = new TreePath(App.get().categoryTree.getModel().getRoot());
	private long collapsed = 0;

	@Override
	public void valueChanged(TreeSelectionEvent evt) {
		
		if(System.currentTimeMillis() - collapsed < 100){
			//if(evt.getPath().getLastPathComponent().equals(App.get().categoryTree.getModel().getRoot()))
				App.get().categoryTree.setSelectionPaths(selection.toArray(new TreePath[0]));
			return;
		}
		
		for(TreePath path : evt.getPaths())
			if(selection.contains(path))
				selection.remove(path);
			else
				selection.add(path);
				
		if(selection.contains(root) || selection.isEmpty()){
			query = null;
			
		}else{
			query = new BooleanQuery();
			
			for(TreePath path : selection){
				Category category = (Category)path.getLastPathComponent();
				addCategoryToQuery(category, query);
			}
		}
		
		App.get().appletListener.updateFileListing();

	}
	
	private void addCategoryToQuery(Category category, BooleanQuery query){
		String name = category.name;
		char[] input = name.toLowerCase().toCharArray();
		char[] output = new char[input.length*4];
		FastASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
		name = (new String(output)).trim();
		query.add(new TermQuery(new Term(IndexItem.CATEGORY, name)), Occur.SHOULD);
		
		for(Category subcat : category.children)
			addCategoryToQuery(subcat, query);
	}

	@Override
	public void treeExpanded(TreeExpansionEvent event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void treeCollapsed(TreeExpansionEvent event) {
		collapsed = System.currentTimeMillis();
		
	}

}
