package dpf.sp.gpinf.indexer.desktop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public class CategoryTreeModel implements TreeModel {

  public static String rootName = "Categorias";
  private static String CONF_FILE = "conf/CategoryHierarchy.txt";

  public Category root = new Category(rootName, null);
  private Collator collator;

  public CategoryTreeModel() {
    try {
      collator = Collator.getInstance();
      collator.setStrength(Collator.PRIMARY);
      loadHierarchy();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  class Category implements Comparable<Category> {

    String name;
    Category parent;
    TreeSet<Category> children = new TreeSet<Category>();

    private Category(String name, Category parent) {
      this.name = name;
      this.parent = parent;
    }

    public String toString() {
      return name;
    }

    @Override
    public int compareTo(Category o) {
      return collator.compare(name,o.name);
    }

    @Override
    public boolean equals(Object o) {
      return compareTo((Category)o) == 0;
    }

  }
  
  private String upperCaseChars(String cat){
	  StringBuilder str = new StringBuilder();
	  for(String s : cat.split(" "))
		  if(s.length() == 3)
			  str.append(s.toUpperCase() + " ");
		  else if(s.length() > 3)
			  str.append(s.substring(0, 1).toUpperCase() + s.substring(1) + " ");
		  else
			  str.append(s + " ");
	  return str.toString().trim();
  }

  private void loadHierarchy() throws IOException {

	ArrayList<Category> categoryList = getLeafCategories();

    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(
        new File(App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir(), CONF_FILE)), "UTF-8"));

    String line = reader.readLine();
    while ((line = reader.readLine()) != null) {
      if (line.startsWith("#")) {
        continue;
      }
      String[] keyValuePair = line.split("=");
      if (keyValuePair.length == 2) {
    	Category category = new Category(keyValuePair[0].trim(), root);
    	category = tryAddAndGet(categoryList, category);
        String subcats = keyValuePair[1].trim();
        for (String subcat : subcats.split(";")) {
          Category sub = new Category(subcat.trim(), category);
          Category cat = tryAddAndGet(categoryList, sub);
          cat.parent = category;
        }
      }
    }
    reader.close();

    populateChildren(root, categoryList);
    
    filterEmptyCategories(root, getLeafCategories());

  }
  
  private Category tryAddAndGet(ArrayList<Category> categoryList, Category category){
	  if (!categoryList.contains(category)){
          categoryList.add(category);
          return category;
      }else
	      return categoryList.get(categoryList.indexOf(category));
  }
  
  private ArrayList<Category> getLeafCategories(){
	  ArrayList<Category> categoryList = new ArrayList<Category>();
	  for (String category : App.get().appCase.getCategories()) {
		  category = upperCaseChars(category);
	      categoryList.add(new Category(category, root));
	  }
	  return categoryList;
  }

  private void populateChildren(Category category, ArrayList<Category> categoryList) {
    for (Category cat : categoryList) {
      if (cat.parent.equals(category)) {
        category.children.add(cat);
        populateChildren(cat, categoryList);
      }
    }
  }

  private boolean filterEmptyCategories(Category category, ArrayList<Category> leafCategories) {
    boolean hasItems = false;
    if (leafCategories.contains(category)) {
      hasItems = true;
    }
	for (Category child : (TreeSet<Category>) category.children.clone()) {
      if (filterEmptyCategories(child, leafCategories)) {
        hasItems = true;
      }
    }
    if (!hasItems && category.parent != null) {
    	category.parent.children.remove(category);
    }
    return hasItems;
  }

  @Override
  public Object getRoot() {
    return root;
  }

  @Override
  public Object getChild(Object parent, int index) {
    return ((Category) parent).children.toArray()[index];
  }

  @Override
  public int getChildCount(Object parent) {
    return ((Category) parent).children.size();
  }

  @Override
  public boolean isLeaf(Object node) {
    return ((Category) node).children.size() == 0;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object newValue) {
    // TODO Auto-generated method stub

  }

  @Override
  public int getIndexOfChild(Object parent, Object child) {
    int i = 0;
    for (Category cat : ((Category) parent).children) {
      if (cat.equals(child)) {
        return i;
      }
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
