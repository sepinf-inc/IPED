package iped.parsers.registry.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class DataCell extends CellContent {
    public DataCell(RegistryFile reg, byte[] data) {
        super(reg, data);
    }

    String getCellDescriptor() {
        return new String(Arrays.copyOf(data, 2), StandardCharsets.ISO_8859_1);
    }

}
