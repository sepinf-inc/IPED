package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.function.Predicate;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import iped.app.ui.filterdecisiontree.CombinedFilterer;
import iped.app.ui.filterdecisiontree.DecisionNode;
import iped.app.ui.filterdecisiontree.FilterNode;
import iped.app.ui.filterdecisiontree.OperandNode;
import iped.app.ui.filterdecisiontree.OperandNode.Operand;
import iped.app.ui.filterdecisiontree.OperandPopupMenu;
import iped.exception.QueryNodeException;
import iped.viewers.api.ClearFilterListener;
import iped.viewers.api.IFilter;
import iped.viewers.api.IFilterer;

public class FiltersPanel extends JPanel implements ClearFilterListener {
    private JTree filtersTree;
    private JScrollPane filtersTreePane;
    private JTree structuredFiltererTree;
    private JScrollPane structuredFiltererTreePane;
    private JSplitPane splitPane;

    private JTree dragSourceTree;

    private CombinedFilterer combinedFilterer;
    private OperandPopupMenu operandMenu;
    FilterManager filterManager;
    private URL invertUrl;
    private ImageIcon invertIcon;
    private ImageIcon intersectionIcon;
    private ImageIcon combinationIcon;
    private JCheckBox ckStructuredFilterer;
    private FiltererMenu filtererMenu;

    public FiltersPanel() {
        invertUrl = this.getClass().getResource("negative.png");
        invertIcon = new ImageIcon(invertUrl);
        intersectionIcon = new ImageIcon(this.getClass().getResource("intersection.png"));
        combinationIcon = new ImageIcon(this.getClass().getResource("combination.png"));
    }


    public void install(FilterManager filterManager) {
        this.filterManager = filterManager;
        filtersTree = new JTree(new FiltersTreeModel(filterManager.getFilterers()));
        filtersTree.setEditable(true);

        filtersTree.setRootVisible(false);
        filtersTree.setCellRenderer(new IFiltersTreeCellRenderer(filtersTree, new Predicate<Object>() {
            @Override
            public boolean test(Object t) {
                if(t instanceof IFilterer) {
                    return filterManager.isFiltererEnabled((IFilterer) t);
                }
                return false;
            }
        }, new Predicate<Object>() {
            @Override
            public boolean test(Object t) {
                if(t instanceof IFilterer) {
                    return ((IFilterer) t).hasFilters();
                }
                return false;
            }            
        }));
        
        filtersTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                TreePath p = filtersTree.getPathForLocation(e.getX(), e.getY());
                Object c = p.getLastPathComponent();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragSourceTree = filtersTree;
                super.mousePressed(e);
            }
        });

        
        combinedFilterer = new CombinedFilterer();
        
        filterManager.addResultSetFilterer(combinedFilterer);
        filterManager.setFilterEnabled(combinedFilterer, false);

        structuredFiltererTree = new JTree(new CombinedFilterTreeModel(combinedFilterer));        
        structuredFiltererTree.setCellRenderer(new DefaultTreeCellRenderer() {

            JLabel nlabel = new JLabel(invertIcon);
            JPanel p = new JPanel(new BorderLayout());
                    
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                p.removeAll();
                JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                p.setOpaque(false);
                p.add(label,BorderLayout.CENTER);
                if((value instanceof CombinedFilterer)) {
                    value = ((CombinedFilterer)value).getRootNode();
                }
                if((value instanceof DecisionNode)&& (((DecisionNode)value).isInverted())) {
                    p.add(nlabel,BorderLayout.WEST);
                }
                if((value instanceof OperandNode)) {
                    OperandNode op = ((OperandNode)value);
                    if(op.getOperand()==Operand.OR) {
                        label.setIcon(combinationIcon);
                    }else {
                        label.setIcon(intersectionIcon);
                    }
                }
                return p;
            }
            
        });

        splitPane=new JSplitPane();
        
        splitPane.setDividerSize(2);
        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(.4);
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        filtersTreePane = new JScrollPane(filtersTree);
        splitPane.setTopComponent(filtersTreePane);
        
        JPanel structuredFiltererTreePanel = new JPanel(new BorderLayout());
        ckStructuredFilterer = new JCheckBox(combinedFilterer.getName());
        ckStructuredFilterer.setToolTipText(Messages.get(combinedFilterer.getClass().getName()+Messages.TOOLTIP_NAME_SUFFIX));
        ckStructuredFilterer.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if(ckStructuredFilterer.isSelected()) {
                    ckStructuredFilterer.setBackground(IFiltersTreeCellRenderer.ENABLED_BK_COLOR);
                    ckStructuredFilterer.setOpaque(true);
                    filterManager.setFilterEnabled(combinedFilterer, true);
                    ckStructuredFilterer.updateUI();
                }else {
                    ckStructuredFilterer.setBackground(ckStructuredFilterer.getParent().getBackground());
                    ckStructuredFilterer.setOpaque(false);
                    filterManager.setFilterEnabled(combinedFilterer, false);
                    ckStructuredFilterer.updateUI();
                }
                App.get().getAppListener().updateFileListing();
            }
        });
        structuredFiltererTreePanel.add(ckStructuredFilterer, BorderLayout.NORTH);
        structuredFiltererTreePane = new JScrollPane(structuredFiltererTree);
        structuredFiltererTreePanel.add(structuredFiltererTreePane, BorderLayout.CENTER);
        splitPane.setBottomComponent(structuredFiltererTreePanel);
        this.setLayout(new BorderLayout());
        this.add(splitPane, BorderLayout.CENTER);

        operandMenu = new OperandPopupMenu(structuredFiltererTree,combinedFilterer);

        filtererMenu = new FiltererMenu();

        filtersTree.setDragEnabled(true);

        filtersTree.addMouseListener(new MouseAdapter() {
            public void showPopupMenu(MouseEvent e) {
                Object o = filtersTree.getPathForLocation(e.getX(), e.getY()).getLastPathComponent();
                filtererMenu.setContext(o);
                filtererMenu.show((JComponent) e.getSource(), e.getX(), e.getY());
            }
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }
        });
        
        
        structuredFiltererTree.setDragEnabled(true);
        structuredFiltererTree.setRootVisible(true);
        
        FiltersPanel self = this;

        DropTarget dt = new DropTarget() {
            @Override
            public synchronized void dragEnter(DropTargetDragEvent dtde) {
                JTree tree = structuredFiltererTree;
                Object o = tree.getPathForLocation(dtde.getLocation().x, dtde.getLocation().y).getLastPathComponent();
                if(o instanceof CombinedFilterer || o instanceof OperandNode) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }else {
                    dtde.rejectDrag();
                }
                super.dragEnter(dtde);
            }

            @Override
            public synchronized void dragOver(DropTargetDragEvent dtde) {
                JTree tree = structuredFiltererTree;
                Object o = tree.getPathForLocation(dtde.getLocation().x, dtde.getLocation().y).getLastPathComponent();
                if(o instanceof CombinedFilterer || o instanceof OperandNode) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }else {
                    dtde.rejectDrag();
                }
                super.dragOver(dtde);
            }

            @Override
            public synchronized void drop(DropTargetDropEvent dtde) {
                OperandNode dest = null;
                JTree tree = structuredFiltererTree;
                TreePath destPath = tree.getPathForLocation(dtde.getLocation().x, dtde.getLocation().y);
                Object o = destPath.getLastPathComponent();
                if(o instanceof CombinedFilterer) {
                    dest=((CombinedFilterer)o).getRootNode();
                }else if(o instanceof OperandNode) {
                    dest=((OperandNode)o);
                }
                if(dest!=null) {
                    structuredFiltererTree.expandPath(destPath);
                    if(dragSourceTree==filtersTree) {
                        for(TreePath path: filtersTree.getSelectionPaths()) {
                            Object pathObject = path.getLastPathComponent();
                            if(pathObject instanceof IFilter) {
                                try {
                                    combinedFilterer.preCacheFilter(((IFilter)pathObject));
                                    dest.addFilter(new FilterNode(((IFilter)pathObject)));
                                    tree.updateUI();
                                }catch(Exception e) {
                                    if(e.getCause() instanceof QueryNodeException) {
                                        JOptionPane.showMessageDialog(self.getRootPane(), Messages.get("FiltersPanel.addQueryFilterError"));
                                        return;
                                    }
                                }
                            }
                        }
                    } else {
                        for(TreePath path: structuredFiltererTree.getSelectionPaths()) {
                            Object pathObject = path.getLastPathComponent();
                            if(pathObject instanceof DecisionNode) {
                                Object parent = (Object) path.getParentPath().getLastPathComponent();
                                if(parent instanceof CombinedFilterer) {
                                    parent = ((CombinedFilterer)parent).getRootNode();
                                }
                                ((DecisionNode)parent).remove((DecisionNode) path.getLastPathComponent());
                                dest.addDecisionNode((DecisionNode) path.getLastPathComponent());
                                tree.updateUI();
                            }
                        }
                    }
                    combinedFilterer.startSearchResult(App.get().ipedResult);
                }
                super.drop(dtde);
            }
        };

        structuredFiltererTree.setDropTarget(dt);

        structuredFiltererTree.addMouseListener(new MouseAdapter() {
            public void showPopupMenu(MouseEvent e) {
                Object o = structuredFiltererTree.getPathForLocation(e.getX(), e.getY()).getLastPathComponent();
                if(o instanceof CombinedFilterer || o instanceof DecisionNode) {
                    if(o instanceof CombinedFilterer) {
                        operandMenu.setDecisionNode(((CombinedFilterer)o).getRootNode());
                        operandMenu.disableRemove();
                    }else {
                        operandMenu.setDecisionNode((DecisionNode)o);
                        operandMenu.enableRemove();
                    }
                    operandMenu.show((JComponent) e.getSource(), e.getX(), e.getY());
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                dragSourceTree = structuredFiltererTree;
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragSourceTree = null;

                super.mouseReleased(e);
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }
        });
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if(filtersTree!=null) {
            filtersTree.updateUI();
        }
        
    }

    @Override
    public void clearFilter() {
        ckStructuredFilterer.setSelected(false);
        ckStructuredFilterer.setBackground(ckStructuredFilterer.getParent().getBackground());
        ckStructuredFilterer.setOpaque(false);
        filterManager.setFilterEnabled(combinedFilterer, false);
        ckStructuredFilterer.updateUI();
    }


    public CombinedFilterer getCombinedFilterer() {
        return combinedFilterer;
    }
}