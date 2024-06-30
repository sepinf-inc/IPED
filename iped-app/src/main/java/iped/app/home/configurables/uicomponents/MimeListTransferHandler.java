package iped.app.home.configurables.uicomponents;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;

public class MimeListTransferHandler extends TransferHandler {
    private JList<String> list;

    public MimeListTransferHandler(JList<String> list) {
        this.list = list;
    }

    public boolean canImport(TransferHandler.TransferSupport info) {
        if (!info.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return false;
        }
        return true;
    }

    public int getSourceActions(JComponent c) {
        return TransferHandler.MOVE;
    }

    public boolean importData(TransferHandler.TransferSupport info) {
        if (!info.isDrop()) {
            return false;
        }

        return true;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        return new Transferable() {
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor.equals(DataFlavor.stringFlavor);
            }

            @Override
            public DataFlavor[] getTransferDataFlavors() {
                DataFlavor[] dataFlavors = new DataFlavor[1];
                dataFlavors[0] = DataFlavor.stringFlavor;
                return dataFlavors;
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                return list.getSelectedValues();
            }
        };
    }
}
