package iped.app.home.newcase.tabs.process;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

import iped.engine.task.AbstractTask;
import iped.engine.task.PythonTask;
import iped.engine.task.ScriptTask;

public class LineOrderTransferHandler extends TransferHandler {
    int[] indices = null;

    static final String TASK_FLAVOR_NAME = "task";
    static final DataFlavor TASK_FLAVOR = new DataFlavor(AbstractTask.class, TASK_FLAVOR_NAME);

    /**
     * We only support importing strings.
     */
    public boolean canImport(TransferHandler.TransferSupport info) {
        // Check for String flavor
        if ((!info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) && (!info.isDataFlavorSupported(TASK_FLAVOR))) {
            return false;
        }
        return true;
    }

    /**
     * We support both copy and move actions.
     */
    public int getSourceActions(JComponent c) {
        return TransferHandler.MOVE;
    }

    public boolean importTask(TransferHandler.TransferSupport info) {
        AbstractTask task;

        Transferable t = info.getTransferable();

        JTable tasksTable = (JTable) info.getComponent();
        TasksTableModel tableModel = (TasksTableModel) tasksTable.getModel();
        JTable.DropLocation dl = (JTable.DropLocation) info.getDropLocation();
        int row = dl.getRow();
        boolean insert = dl.isInsertRow();

        try {
            task = (AbstractTask) t.getTransferData(TASK_FLAVOR);
        } catch (Exception e) {
            return false;
        }

        tableModel.changeOrder(indices[0], row);

        return true;

    }

    /**
     * Perform the actual import. This demo only supports drag and drop.
     */
    public boolean importData(TransferHandler.TransferSupport info) {
        if (!info.isDrop()) {
            return false;
        }

        // Get the string that is being dropped.
        Transferable t = info.getTransferable();
        if (t.isDataFlavorSupported(TASK_FLAVOR)) {
            return importTask(info);
        }
        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            // return importTask(info);
        }
        return false;
    }

    /**
     * Bundle up the selected items in a single list for export. Each line is
     * separated by a newline.
     */
    protected Transferable createTransferable(JComponent c) {
        JTable tasksTable = (JTable) c;
        indices = tasksTable.getSelectedRows();
        TasksTableModel tableModel = (TasksTableModel) tasksTable.getModel();
        final AbstractTask task = tableModel.getTaskList().get(indices[0]);
        if ((task instanceof PythonTask) || (task instanceof ScriptTask)) {
            return new Transferable() {
                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return flavor.equals(TASK_FLAVOR);
                }

                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    DataFlavor[] dataFlavors = new DataFlavor[1];
                    dataFlavors[0] = TASK_FLAVOR;
                    return dataFlavors;
                }

                @Override
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    return task;
                }
            };
        } else {
            return null;
        }
    }

    /**
     * Remove the items moved from the list.
     */
    protected void exportDone(JComponent c, Transferable data, int action) {
        JTable tasksTable = (JTable) c;
        tasksTable.repaint();
    }

}
