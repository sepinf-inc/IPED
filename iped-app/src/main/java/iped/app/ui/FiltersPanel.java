package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.net.URL;
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
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import iped.app.ui.controls.CheckBoxTreeCellRenderer;
import iped.app.ui.filterdecisiontree.CombinedFilterer;
import iped.app.ui.filterdecisiontree.DecisionNode;
import iped.app.ui.filterdecisiontree.FilterNode;
import iped.app.ui.filterdecisiontree.OperandNode;
import iped.app.ui.filterdecisiontree.OperandNode.Operand;
import iped.app.ui.filterdecisiontree.OperandPopupMenu;
import iped.app.ui.filters.FilterTransferHandler;
import iped.viewers.api.IFilter;
import iped.viewers.api.IFilterer;
import iped.viewers.api.IMiniaturizable;

public class FiltersPanel extends JPanel
{
    private JTree filtersTree;
    private JScrollPane filtersTreePane;
    private JTree combinedFiltererTree;
    private JScrollPane combinedFiltererTreePane;
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

        Predicate filtererEnabledPredicate = new Predicate<Object>() {
            @Override
            public boolean test(Object t) {
                if (t instanceof IFilterer) {
                    return filterManager.isFiltererEnabled((IFilterer) t) && (((IFilterer) t).hasFiltersApplied());
                }
                return false;
            };
        };

        Predicate filtererVisiblePredicate = new Predicate<Object>() {
            @Override
            public boolean test(Object t) {
                if (t instanceof IFilterer) {
                    return true;
                }
                return false;
            };
        };
        CheckBoxTreeCellRenderer treeCellRenderer = new CheckBoxTreeCellRenderer(filtersTree,
                filtererEnabledPredicate, filtererVisiblePredicate) {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                if (value instanceof IFilter) {
                    IFilter filter = (IFilter) value;
                    String toolTip = filter.getTextualDetails();
                    ((JComponent)result).setToolTipText(toolTip);
                }

                if (value instanceof IFilterer) {
                    result.setBackground(
                            (((IFilterer) value).hasFilters() && filterManager.isFiltererEnabled((IFilterer) value))
                                    ? ENABLED_BK_COLOR
                                    : Color.white);
                }

                return result;
            }
        };
        treeCellRenderer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                IFilterer filterer = (IFilterer) e.getSource();
                if (filterer.hasFilters()) {
                    filterManager.setFilterEnabled(filterer, !filterManager.isFiltererEnabled(filterer));
                    App.get().filtersPanel.updateUI();
                    App.get().getAppListener().updateFileListing();
                } else {
                    App.get().filtersPanel.updateUI();
                }
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
        combinedFilterer.setFiltersPanel(this);
        combinedFilterer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                App.get().setDockablesColors();
            }
        });

        filterManager.addResultSetFilterer(combinedFilterer);
        filterManager.setFilterEnabled(combinedFilterer, false);

        combinedFiltererTree = new JTree(new CombinedFilterTreeModel(combinedFilterer)) {
            String dragHereMsg = Messages.get("iped.app.ui.filterdecisiontree.CombinedFilterer.dragAndDropTooltip");

            @Override
            protected void paintComponent(Graphics g) {
                // TODO Auto-generated method stub
                super.paintComponent(g);
                if (((CombinedFilterTreeModel) getModel()).getFiltersToNodeMap().size() == 0) {
                    drawCenteredString(g, dragHereMsg, this.getBounds(), g.getFont());
                }
            }

            public void drawCenteredString(Graphics g, String text, Rectangle rect, Font font) {
                FontMetrics metrics = g.getFontMetrics(font);
                int x = rect.x + (rect.width - metrics.stringWidth(text)) / 2;
                int y = rect.y + ((rect.height - metrics.getHeight()) / 2) + metrics.getAscent();
                g.setFont(font);
                g.drawString(text, x, y);
            }
        };
        combinedFiltererTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        combinedFiltererTree.setCellRenderer(new DefaultTreeCellRenderer() {

            JLabel nlabel = new JLabel(invertIcon);
            JPanel p = new JPanel(new BorderLayout());

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                p.removeAll();
                JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                p.setOpaque(false);
                p.add(label, BorderLayout.CENTER);
                if ((value instanceof CombinedFilterer)) {
                    value = ((CombinedFilterer) value).getRootNode();
                }
                if ((value instanceof DecisionNode) && (((DecisionNode) value).isInverted())) {
                    p.add(nlabel, BorderLayout.WEST);
                }
                if ((value instanceof OperandNode)) {
                    OperandNode op = ((OperandNode) value);
                    if (op.getOperand() == Operand.OR) {
                        label.setIcon(combinationIcon);
                    } else {
                        label.setIcon(intersectionIcon);
                    }
                }
                if (value instanceof FilterNode) {
                    IFilter filter = ((FilterNode) value).getFilter();
                    if (filter instanceof IMiniaturizable) {
                        Image newimg = ((IMiniaturizable) filter).getThumb().getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH);
                        label.setIcon(new ImageIcon(newimg));
                    }
                    String toolTip = filter.getTextualDetails();
                    p.setToolTipText(toolTip);
                } else {
                    p.setToolTipText(null);
                }
                return p;
            }

        });
        ToolTipManager.sharedInstance().registerComponent(combinedFiltererTree);

        splitPane = new JSplitPane();

        splitPane.setDividerSize(2);
        splitPane.setOneTouchExpandable(true);
        splitPane.setResizeWeight(.4);
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        filtersTreePane = new JScrollPane(filtersTree);
        splitPane.setTopComponent(filtersTreePane);

        JPanel structuredFiltererTreePanel = new JPanel(new BorderLayout());
        ckStructuredFilterer = new JCheckBox(combinedFilterer.getFilterName());
        ckStructuredFilterer.setToolTipText(Messages.get(combinedFilterer.getClass().getName() + Messages.TOOLTIP_NAME_SUFFIX));
        ckStructuredFilterer.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (ckStructuredFilterer.isSelected()) {
                    ckStructuredFilterer.setBackground(CheckBoxTreeCellRenderer.ENABLED_BK_COLOR);
                    ckStructuredFilterer.setOpaque(true);
                    filterManager.setFilterEnabled(combinedFilterer, true);
                    ckStructuredFilterer.updateUI();
                } else {
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
        combinedFiltererTreePane = new JScrollPane(combinedFiltererTree);
        structuredFiltererTreePanel.add(combinedFiltererTreePane, BorderLayout.CENTER);
        splitPane.setBottomComponent(structuredFiltererTreePanel);
        this.setLayout(new BorderLayout());
        this.add(splitPane, BorderLayout.CENTER);

        operandMenu = new OperandPopupMenu(combinedFiltererTree, combinedFilterer);

        filtererMenu = new FiltererMenu();

        filtersTree.setDragEnabled(true);

        filtersTree.addMouseListener(new MouseAdapter() {
            public void showPopupMenu(MouseEvent e) {
                TreePath tp = filtersTree.getPathForLocation(e.getX(), e.getY());
                if (tp != null) {
                    Object o = tp.getLastPathComponent();
                    filtererMenu.setContext(o);
                    filtererMenu.show((JComponent) e.getSource(), e.getX(), e.getY());
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
        });

        combinedFiltererTree.setDragEnabled(true);
        combinedFiltererTree.setRootVisible(true);

        FiltersPanel self = this;

        FilterTransferHandler fth = new FilterTransferHandler(this, combinedFilterer);
        combinedFiltererTree.setTransferHandler(fth);
        combinedFiltererTree.setDropMode(DropMode.ON);
        filtersTree.setTransferHandler(fth);

        combinedFiltererTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastClickedPath = combinedFiltererTree.getPathForLocation(e.getX(), e.getY());
            }
        });

        combinedFiltererTree.addMouseListener(new MouseAdapter() {
            public void showPopupMenu(MouseEvent e) {
                Object o = combinedFiltererTree.getPathForLocation(e.getX(), e.getY()).getLastPathComponent();
                if (o instanceof CombinedFilterer || o instanceof DecisionNode) {
                    if (o instanceof CombinedFilterer) {
                        operandMenu.setDecisionNode(((CombinedFilterer) o).getRootNode());
                        operandMenu.disableRemove();
                    } else {
                        operandMenu.setDecisionNode((DecisionNode) o);
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
        if (filtersTree != null) {
            filtersTree.updateUI();
        }

    }

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

    public boolean isCombinedFiltererApplied() {
        return ckStructuredFilterer.isSelected();
    }
}