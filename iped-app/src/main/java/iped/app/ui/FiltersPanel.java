package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.net.URL;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.DropMode;
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

import org.apache.lucene.search.Query;

import iped.app.ui.controls.CheckBoxTreeCellRenderer;
import iped.app.ui.filterdecisiontree.CombinedFilterer;
import iped.app.ui.filterdecisiontree.DecisionNode;
import iped.app.ui.filterdecisiontree.FilterNode;
import iped.app.ui.filterdecisiontree.OperandNode;
import iped.app.ui.filterdecisiontree.OperandNode.Operand;
import iped.app.ui.filterdecisiontree.OperandPopupMenu;
import iped.app.ui.filters.FilterTransferHandler;
import iped.viewers.api.ClearFilterListener;
import iped.viewers.api.IFilter;
import iped.viewers.api.IFilterer;
import iped.viewers.api.IMiniaturizable;
import iped.viewers.api.IQueryFilterer;

public class FiltersPanel extends JPanel implements ClearFilterListener
        , IQueryFilterer//internal combinedfilterer wrapper to reflect on panel color 
        {
    private JTree filtersTree;
    private JScrollPane filtersTreePane;
    private JTree structuredFiltererTree;
    private JScrollPane structuredFiltererTreePane;
    private JSplitPane splitPane;

    private CombinedFilterer combinedFilterer;
    private OperandPopupMenu operandMenu;
    FilterManager filterManager;
    private URL invertUrl;
    private ImageIcon invertIcon;
    private ImageIcon intersectionIcon;
    private ImageIcon combinationIcon;
    private JCheckBox ckStructuredFilterer;
    private FiltererMenu filtererMenu;

    private volatile TreePath lastClickedPath;

    public FiltersPanel() {
        invertUrl = this.getClass().getResource("negative.png");
        invertIcon = new ImageIcon(invertUrl);
        intersectionIcon = new ImageIcon(this.getClass().getResource("intersection.png"));
        combinationIcon = new ImageIcon(this.getClass().getResource("combination.png"));
    }

    public TreePath getLastClickedPath() {
        return lastClickedPath;
    }


    public void install(FilterManager filterManager) {
        this.filterManager = filterManager;
        filtersTree = new JTree();
        CheckBoxTreeCellRenderer treeCellRenderer = new CheckBoxTreeCellRenderer(filtersTree, new Predicate<Object>() {
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
        });
        filtersTree.setCellRenderer(treeCellRenderer);
        filtersTree.setCellEditor(treeCellRenderer);
        filtersTree.setEditable(true);
        filtersTree.setSelectionModel(null);
        filtersTree.setRootVisible(false);
        filtersTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastClickedPath = filtersTree.getPathForLocation(e.getX(), e.getY());
            }
        });
        filtersTree.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                TreePath tp = filtersTree.getPathForLocation(e.getX(), e.getY());
                if (tp != null && tp.getLastPathComponent() instanceof IFilter) {
                    filtersTree.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else {
                    filtersTree.setCursor(Cursor.getDefaultCursor());
                }
            }
        });

        filtersTree.setModel(new FiltersTreeModel(filterManager.getFilterers()));

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
                if(value instanceof FilterNode) {
                    IFilter filter = ((FilterNode)value).getFilter();
                    if(filter instanceof IMiniaturizable) {
                        Image newimg = ((IMiniaturizable)filter).getThumb().getScaledInstance(16, 16,  java.awt.Image.SCALE_SMOOTH);
                        label.setIcon(new ImageIcon(newimg));
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
        ckStructuredFilterer = new JCheckBox(combinedFilterer.getFilterName());
        ckStructuredFilterer.setToolTipText(Messages.get(combinedFilterer.getClass().getName()+Messages.TOOLTIP_NAME_SUFFIX));
        ckStructuredFilterer.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if(ckStructuredFilterer.isSelected()) {
                    ckStructuredFilterer.setBackground(CheckBoxTreeCellRenderer.ENABLED_BK_COLOR);
                    ckStructuredFilterer.setOpaque(true);
                    filterManager.setFilterEnabled(combinedFilterer, true);
                    ckStructuredFilterer.updateUI();
                }else {
                    ckStructuredFilterer.setBackground(ckStructuredFilterer.getParent().getBackground());
                    ckStructuredFilterer.setOpaque(false);
                    filterManager.setFilterEnabled(combinedFilterer, false);
                    ckStructuredFilterer.updateUI();
                }
                App.get().setDockablesColors();
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

        FilterTransferHandler fth = new FilterTransferHandler(this, combinedFilterer);
        structuredFiltererTree.setTransferHandler(fth);
        structuredFiltererTree.setDropMode(DropMode.ON);
        filtersTree.setTransferHandler(fth);

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
            @Override
            public void mouseDragged(MouseEvent e) {
                super.mouseDragged(e);
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                super.mouseMoved(e);
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


    @Override
    public List<IFilter> getDefinedFilters() {
        //does not expose filters as it is not actually registered as result set filterer 
        return null;
    }


    @Override
    public boolean hasFilters() {
        //does not expose filters as it is not actually registered as result set filterer 
        return false;
    }


    @Override
    public boolean hasFiltersApplied() {
        //Wraps combofilterer 
        return ckStructuredFilterer!=null && ckStructuredFilterer.isSelected();
    }


    @Override
    public Query getQuery() {
        //does not expose filters as it is not actually registered as result set filterer 
        return null;
    }
}