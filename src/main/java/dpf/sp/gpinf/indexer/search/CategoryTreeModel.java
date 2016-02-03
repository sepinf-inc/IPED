package dpf.sp.gpinf.indexer.search;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public class CategoryTreeModel implements TreeModel{
	
	public static String rootName = "Categorias";
	private static String CONF_FILE = "conf/CategoryHierarchy.txt";
	
	public Category root = new Category(rootName, null);
	
	public CategoryTreeModel(){
		try {
			loadHierarchy();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	class Category implements Comparable<Category>{
		String name;
		Category parent;
		TreeSet<Category> children = new TreeSet<Category>();
		
		private Category(String name, Category parent){
			this.name = name;
			this.parent = parent;
		}
		
		public String toString(){
			return name;
		}

		@Override
		public int compareTo(Category o) {
			return name.compareTo(o.name);
		}
		
		@Override
		public boolean equals(Object o) {
			return name.equals(((Category)o).name);
		}
		
	}
	
	private void loadHierarchy() throws IOException{
		
	    //map from child category to parent
		HashMap<String, String> categoryMap = new HashMap<String, String>();
		for(String category : App.get().categorias)
		    categoryMap.put(category, rootName);
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(App.get().codePath + "/../" + CONF_FILE), "UTF-8"));
		
		String line = reader.readLine();
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("#"))
				continue;
			String[] keyValuePair = line.split("=");
			if (keyValuePair.length == 2) {
				String category = keyValuePair[0].trim();
				if(!categoryMap.containsKey(category))
				    categoryMap.put(category, rootName);
				String subcats = keyValuePair[1].trim();
				for (String subcat : subcats.split(";")) {
					subcat = subcat.trim();
					categoryMap.put(subcat, category);
				}
			}
		}
		reader.close();

		populateChildren(root, categoryMap);
		
		filterEmptyCategories(root);
		
	}
	
	private void populateChildren(Category category, HashMap<String, String> categoryMap){
		for(String categoryName : categoryMap.keySet())
			if(categoryMap.get(categoryName).equals(category.name)){
				Category subCat = new Category(categoryName, category);
				category.children.add(subCat);
				populateChildren(subCat, categoryMap);
			}
	}
	
	private boolean filterEmptyCategories(Category category){
	    boolean hasItems = false;
	    if(App.get().categorias.contains(category.name))
	        hasItems = true;
	    for(Category child : (TreeSet<Category>)category.children.clone()){
	        if(filterEmptyCategories(child))
	            hasItems = true;
	    }
	    if(!hasItems && category.parent != null)
	        category.parent.children.remove(category);
	    return hasItems;
	}
	

	@Override
	public Object getRoot() {
		return root;
	}

	@Override
	public Object getChild(Object parent, int index) {
		return ((Category)parent).children.toArray()[index];
	}

	@Override
	public int getChildCount(Object parent) {
		return ((Category)parent).children.size();
	}

	@Override
	public boolean isLeaf(Object node) {
		return ((Category)node).children.size() == 0;
	}

	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getIndexOfChild(Object parent, Object child) {
		int i = 0;
		for(Category cat : ((Category)parent).children){
			if(cat.equals(child))
				return i;
			i++;
		}
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
