package iped.app.home.configurables;

import java.awt.BorderLayout;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.google.common.base.Predicate;

import iped.app.home.MainFrame;
import iped.app.home.configurables.api.ConfigurableValidationException;
import iped.app.ui.CategoryMimeTreeModel;
import iped.app.ui.controls.ConfigCheckBoxTreeCellRenderer;
import iped.configuration.Configurable;
import iped.engine.config.CategoryConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.Category;

public class CategorySetConfigPanel extends ConfigurablePanel {
    private JTree categoryTree;
    Configurable<Set<String>> ceConfig;
    private JScrollPane treeScrollPanel;
    private ConfigCheckBoxTreeCellRenderer cellRenderer;
    Set<String> tempCatNames;

    protected CategorySetConfigPanel(Configurable<Set<String>> configurable, MainFrame mainFrame) {
        super(configurable, mainFrame);
        ceConfig = configurable;
        tempCatNames = new HashSet<String>();
        Set<String> configCatNames = ceConfig.getConfiguration();
        for (Iterator<String> iterator = configCatNames.iterator(); iterator.hasNext();) {
            tempCatNames.add(iterator.next());            
        }
    }

    @Override
    public void createConfigurableGUI() {
        CategoryConfig cc = ConfigurationManager.get().findObject(CategoryConfig.class);

        categoryTree = new JTree(new CategoryMimeTreeModel(cc.getRoot()));
        cellRenderer = new ConfigCheckBoxTreeCellRenderer(categoryTree, new Predicate<Object>() {
            @Override
            public boolean apply(Object input) {
                return tempCatNames.contains(((Category)input).getName());
            }
        });
        cellRenderer.getCheckbox().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Category selectedCategory = ((Category) categoryTree.getLastSelectedPathComponent());
                if(selectedCategory!=null) {
                    Set<String> cats = tempCatNames;
                    String selectedCategoryName = selectedCategory.getName();
                    if(cats.contains(selectedCategoryName)) {
                        cats.remove(selectedCategoryName);
                    }else {
                        cats.add(selectedCategoryName);
                    }
                    changed=true;
                }
            }
        });
        categoryTree.setCellRenderer(cellRenderer);

        treeScrollPanel = new JScrollPane();
        treeScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        treeScrollPanel.setViewportView(categoryTree);
        treeScrollPanel.setAutoscrolls(true);

        this.setLayout(new BorderLayout());
        this.add(treeScrollPanel, BorderLayout.CENTER);
    }

    @Override
    public void applyChanges() throws ConfigurableValidationException {
        ceConfig.setConfiguration(tempCatNames);
    }

}
