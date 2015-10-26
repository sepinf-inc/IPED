package dpf.sp.gpinf.indexer.search;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public class BookmarksTreeModel implements TreeModel{
	
	public static String ROOT = "Marcadores";
	public static String NO_BOOKMARKS = "[Sem Marcadores]";
	public TreeMap<Integer, String> labelMap;
	
	static class Bookmark implements Comparator<Bookmark>{
		int id;
		String name;
		
		public Bookmark(int id, String name){
			this.id = id;
			this.name = name;
		}
		
		public String toString(){
			return name;
		}
		
		public boolean equals(Bookmark b){
			return this.id == b.id;
		}
		
		@Override
		final public int compare(Bookmark a, Bookmark b) {
			return a.name.compareTo(b.name);
		}
	}

	@Override
	public Object getRoot() {
		return ROOT;
	}

	@Override
	public Object getChild(Object parent, int index) {
		if(!ROOT.equals(parent))
			return null;
		
		if(labelMap == null)
			labelMap = (TreeMap<Integer, String>)App.get().marcadores.getLabelMap().clone();
		
		if(index == 0)
			return NO_BOOKMARKS;
		
		String[] labels = labelMap.values().toArray(new String[0]);
		Arrays.sort(labels, Collator.getInstance());
		
		return labels[index - 1];
			
	}

	@Override
	public int getChildCount(Object parent) {
		if(!ROOT.equals(parent))
			return 0;
		else{
			if(App.get().marcadores == null)
				return 0;
			else
				return App.get().marcadores.getLabelMap().size() + 1;
		}
	}

	@Override
	public boolean isLeaf(Object node) {
		if(!ROOT.equals(node))
			return true;
		else
			return false;
	}

	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		System.out.println("get index of child");
		return 0;
	}

	@Override
	public void addTreeModelListener(TreeModelListener l) {
		//treeModelListeners.addElement(l);
		
	}

	@Override
	public void removeTreeModelListener(TreeModelListener l) {
		//treeModelListeners.removeElement(l);
		
	}

}