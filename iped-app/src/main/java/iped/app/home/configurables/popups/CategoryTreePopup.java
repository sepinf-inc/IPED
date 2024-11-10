package iped.app.home.configurables.popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.SortedSet;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;

import iped.app.home.configurables.SetCategoryConfigurablePanel;
import iped.engine.data.Category;

public class CategoryTreePopup extends JPopupMenu implements ActionListener {

    JMenuItem createCategory;
    SetCategoryConfigurablePanel configPanel;
    JTree categoryTree;

    public CategoryTreePopup(SetCategoryConfigurablePanel configPanel) {
        this.configPanel = configPanel;
        this.categoryTree = configPanel.getCategoryTree();
        createCategory = new JMenuItem("Create category");
        createCategory.addActionListener(this);
        add(createCategory);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == createCategory) {
            int row = categoryTree.getRowForPath(categoryTree.getLeadSelectionPath());
            // Category cat = (Category) categoryTree.getPathForLocation(this.getX(),
            // this.getY()).getLastPathComponent();
            Category cat = (Category) categoryTree.getLeadSelectionPath().getLastPathComponent();
            Category newcat = new Category();
            newcat.setName("New category");
            newcat.setParent(cat);
            cat.getChildren().add(newcat);
            configPanel.refreshModel();

            if (row == 0) {
                SortedSet<Category> children = cat.getChildren();
                int index = 0;
                for (Iterator iterator = children.iterator(); iterator.hasNext();) {
                    Category category = (Category) iterator.next();
                    index++;
                    if (category.equals(newcat)) {
                        break;
                    }
                }
                categoryTree.startEditingAtPath(categoryTree.getPathForRow(index));
            } else {
                categoryTree.startEditingAtPath(categoryTree.getPathForRow(row + 1));
            }
        }
    }
}
