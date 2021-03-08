package dpf.mt.gpinf.registro.model;

import java.util.Arrays;

public class DataCell extends CellContent {
    public DataCell(RegistryFile reg, byte[] data) {
        super(reg, data);
    }

    String getCellDescriptor() {
        return new String(Arrays.copyOf(data, 2));
    }

}
