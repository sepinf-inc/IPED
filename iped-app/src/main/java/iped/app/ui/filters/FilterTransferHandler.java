package iped.app.ui.filters;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;

import iped.app.ui.App;
import iped.app.ui.CombinedFilterTreeModel;
import iped.app.ui.FiltersPanel;
import iped.app.ui.Messages;
import iped.app.ui.filterdecisiontree.CombinedFilterer;
import iped.app.ui.filterdecisiontree.DecisionNode;
import iped.app.ui.filterdecisiontree.FilterNode;
import iped.app.ui.filterdecisiontree.OperandNode;
import iped.exception.QueryNodeException;
import iped.viewers.api.IFilter;
import iped.viewers.api.IFilterer;

public class FilterTransferHandler extends TransferHandler {

    static final public DataFlavor filterFlavor = new DataFlavor(IFilter.class, "iped.IFilter");
    static final public DataFlavor filterNodeFlavor = new DataFlavor(FilterNode.class, "iped.FilterNode");
    static final public DataFlavor operandNodeFlavor = new DataFlavor(OperandNode.class, "iped.OperandNode");
    static final public DataFlavor parentOperandNodeFlavor = new DataFlavor(OperandNode.class, "iped.parentOperandNode");
    private CombinedFilterer combinedFilterer;
    private FiltersPanel filtersPanel;

    public FilterTransferHandler(FiltersPanel filtersPanel, CombinedFilterer combinedFilterer) {
        this.combinedFilterer = combinedFilterer;
        this.filtersPanel = filtersPanel;
    }

    @Override
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        for (int i = 0; i < transferFlavors.length; i++) {
            if (transferFlavors[i].equals(filterFlavor) || transferFlavors[i].equals(filterNodeFlavor) || transferFlavors[i].equals(operandNodeFlavor)) {
                return true;
            }
        }
        return super.canImport(comp, transferFlavors);
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;

        TreePath tp = filtersPanel.getLastClickedPath();
        Object o = tp.getLastPathComponent();

        Object parent = (Object) tp.getParentPath().getLastPathComponent();
        if (parent instanceof CombinedFilterer) {
            parent = ((CombinedFilterer) parent).getRootNode();
        }

        DecisionNode parentDecisionNodeTmp = null;
        if (parent instanceof DecisionNode) {
            parentDecisionNodeTmp = (DecisionNode) parent;
        }

        final DecisionNode parentDecisionNode = parentDecisionNodeTmp;

        if (o != null && !(o instanceof IFilterer)) {
            Transferable result = new Transferable() {
                private DataFlavor[] filterFlavors = { filterFlavor, filterNodeFlavor, operandNodeFlavor };

                @Override
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return flavor.equals(filterFlavor) || flavor.equals(filterNodeFlavor) || flavor.equals(operandNodeFlavor) || flavor.equals(parentOperandNodeFlavor);
                }

                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return filterFlavors;
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    if (flavor.equals(filterFlavor)) {
                        if (o instanceof IFilter) {
                            return o;
                        }
                        return null;
                    }
                    if (flavor.equals(filterNodeFlavor)) {
                        if (o instanceof FilterNode) {
                            return o;
                        }
                        return null;
                    }
                    if (flavor.getHumanPresentableName().equals(operandNodeFlavor.getHumanPresentableName())) {
                        if (o instanceof OperandNode) {
                            return o;
                        }
                        return null;
                    }
                    if (flavor.getHumanPresentableName().equals(parentOperandNodeFlavor.getHumanPresentableName())) {
                        return parentDecisionNode;
                    }
                    return null;
                }
            };
            return result;
        }

        return null;
    }

    @Override
    public void exportAsDrag(JComponent comp, InputEvent e, int action) {
        super.exportAsDrag(comp, e, action);
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        if (data == null) {
            return;
        }
        try {
            JTree tree = (JTree) source;
            DecisionNode parentDecisionNode = (OperandNode) data.getTransferData(parentOperandNodeFlavor);
            if (parentDecisionNode != null && action == MOVE) {
                OperandNode operand = (OperandNode) data.getTransferData(operandNodeFlavor);
                if (operand != null) {
                    parentDecisionNode.remove((DecisionNode) operand);
                }
                FilterNode filterNode = (FilterNode) data.getTransferData(filterNodeFlavor);
                if (filterNode != null) {
                    parentDecisionNode.remove((DecisionNode) filterNode);
                }
            }
            tree.updateUI();

            combinedFilterer.startSearchResult(App.get().getResults());

            if (filtersPanel.hasFiltersApplied()) {
                App.get().getAppListener().updateFileListing();
            }
        } catch (Exception e) {
            e.printStackTrace();

        }

        super.exportDone(source, data, action);
    }

    @Override
    public boolean importData(TransferSupport support) {
        OperandNode dest = null;
        JTree tree = (JTree) support.getComponent();
        DropLocation loc = support.getDropLocation();
        TreePath destPath = tree.getPathForLocation(loc.getDropPoint().x, loc.getDropPoint().y);
        Object o = destPath.getLastPathComponent();

        Transferable data = support.getTransferable();
        if (data != null) {
            try {
                if (o instanceof CombinedFilterer) {
                    dest = ((CombinedFilterer) o).getRootNode();
                } else if (o instanceof OperandNode) {
                    dest = ((OperandNode) o);
                }
                if (dest != null) {
                    FilterNode filterNode = (FilterNode) data.getTransferData(filterNodeFlavor);
                    IFilter filter = null;
                    IFilter filterClonedSrc = null;
                    if (filterNode != null) {
                        if (support.getDropAction() == COPY) {
                            filterClonedSrc = filter;
                            filter = (IFilter) clone(((FilterNode) filterNode).getFilter());
                            if (filter != null) {
                                dest.addFilter(new FilterNode(filter, (CombinedFilterTreeModel) tree.getModel()));
                            } else {
                                return false;
                            }
                        } else {
                            if (support.getDropAction() == MOVE) {
                                dest.addFilter(filterNode);
                                tree.expandPath(destPath.pathByAddingChild(filterNode));
                            }
                        }
                    } else {
                        filter = (IFilter) data.getTransferData(filterFlavor);
                        if (filter != null) {
                            FilterNode fn = new FilterNode(filter, (CombinedFilterTreeModel) tree.getModel());
                            dest.addFilter(fn);
                            tree.expandPath(destPath.pathByAddingChild(fn));
                        }
                    }
                    if (filter != null) {
                        tree.updateUI();
                        try {
                            if (filterClonedSrc == null) {
                                combinedFilterer.preCacheFilter(filter);
                            } else {
                                if (filter != filterClonedSrc) {
                                    combinedFilterer.preCacheFilterClone(filter, filterClonedSrc);
                                }
                            }
                        } catch (Exception e) {
                            if (e.getCause() instanceof QueryNodeException) {
                                JOptionPane.showMessageDialog(tree.getRootPane(), Messages.get("FiltersPanel.addQueryFilterError"));
                                return false;
                            }
                        }
                    }

                    OperandNode operand = (OperandNode) data.getTransferData(operandNodeFlavor);
                    if (operand != null) {
                        if (support.getDropAction() == COPY) {
                            DecisionNode dn = (DecisionNode) operand.clone();
                            if (dn != null) {
                                dest.addDecisionNode(dn);
                            } else {
                                return false;
                            }
                        } else {
                            dest.addDecisionNode(operand);
                            tree.expandPath(destPath.pathByAddingChild(operand));
                        }
                    }

                    return true;
                }
            } catch (IOException | UnsupportedFlavorException e) {
                e.printStackTrace();
            }
        }
        return false;

    }

    private Object clone(Object object) {
        return object;
    }

}
