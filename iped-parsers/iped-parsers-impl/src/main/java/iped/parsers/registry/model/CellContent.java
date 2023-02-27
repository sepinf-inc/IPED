package iped.parsers.registry.model;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class CellContent {
    byte data[];
    protected RegistryFile reg;

    public CellContent(RegistryFile reg, byte[] data) {
        this.reg = reg;
        this.data = data;
    }

    String getCellDescriptor() {
        return new String(Arrays.copyOf(data, 2));
    }

    public RegistryFile getRegistryFile() {
        return reg;
    }

    public ArrayList<Integer> getSubCellsOffsets() {
        return new ArrayList<Integer>();
    }
}
