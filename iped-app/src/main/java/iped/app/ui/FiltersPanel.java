package iped.app.ui;

import java.awt.BorderLayout;
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
import java.util.List;
import java.util.function.Predicate;

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import iped.app.ui.filterdecisiontree.CombinedFilterer;
import iped.app.ui.filterdecisiontree.DecisionNode;
import iped.app.ui.filterdecisiontree.FilterNode;
import iped.app.ui.filterdecisiontree.OperandNode;
import iped.app.ui.filterdecisiontree.OperandNode.Operand;
import iped.app.ui.filterdecisiontree.OperandPopupMenu;
import iped.viewers.api.IFilter;
import iped.viewers.api.IFilterer;

public class FiltersPanel extends JPanel {
    private JTree filtersTree;
    private JScrollPane filtersTreePane;
    private JTree structuredFiltererTree;
    private JScrollPane structuredFiltererTreePane;
    private JSplitPane splitPane;

    private JTree dragSourceTree;

    private CombinedFilterer logicFilterer;
    private OperandPopupMenu operandMenu;
    FilterManager filterManager;
    private URL invertUrl;
    private ImageIcon invertIcon;
    private ImageIcon intersectionIcon;
    private ImageIcon combinationIcon;

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
                    List list = ((IFilterer) t).getDefinedFilters();
                    return list!=null && list.size()>0;
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

        
        logicFilterer = new CombinedFilterer();
        
        filterManager.addResultSetFilterer(logicFilterer);

        structuredFiltererTree = new JTree(new CombinedFilterTreeModel("Combined filterer",logicFilterer));
        structuredFiltererTree.setCellRenderer(new DefaultTreeCellRenderer() {

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if(value instanceof FilterNode) {
                    if(((FilterNode)value).isInverted()) {
                        label.setIcon(invertIcon);
                    }
                }
                if((value instanceof OperandNode)||(value instanceof CombinedFilterer)) {
                    OperandNode op = null;
                    if(value instanceof OperandNode) {
                        op = ((OperandNode)value);
                    }else {
                        op = ((CombinedFilterer)value).getRootNode();
                    }
                    if(op.getOperand()==Operand.OR) {
                        label.setIcon(combinationIcon);
                    }else {
                        label.setIcon(intersectionIcon);
                    }
                }
                return label;
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
        JCheckBox ckStructuredFilterer = new JCheckBox("Combined filterer");
        ckStructuredFilterer.setToolTipText("Apply combined filter to resultset.");
        ckStructuredFilterer.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if(ckStructuredFilterer.isSelected()) {
                    ckStructuredFilterer.setBackground(IFiltersTreeCellRenderer.ENABLED_BK_COLOR);
                    ckStructuredFilterer.setOpaque(true);
                    filterManager.setFilterEnabled(logicFilterer, true);
                    ckStructuredFilterer.updateUI();
                }else {
                    ckStructuredFilterer.setBackground(ckStructuredFilterer.getParent().getBackground());
                    ckStructuredFilterer.setOpaque(false);
                    filterManager.setFilterEnabled(logicFilterer, false);
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

        operandMenu = new OperandPopupMenu(structuredFiltererTree,logicFilterer);

        filtersTree.setDragEnabled(true);
        structuredFiltererTree.setDragEnabled(true);
        structuredFiltererTree.setRootVisible(true);

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
                                dest.addFilter(new FilterNode(((IFilter)pathObject)));
                                tree.updateUI();
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
                }
                super.drop(dtde);
            }
        };

        //filtersTree.setDropTarget(dt);
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
}