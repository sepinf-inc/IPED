package dpf.mt.gpinf.registro.model;

import java.util.Arrays;

public class SubKeysList extends CellContent {

    public SubKeysList(RegistryFile reg, byte[] data) {
        super(reg, data);
    }

    public int getSubKeysCount() {
        byte buffer[] = Arrays.copyOfRange(data, 2, 4);
        return (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8;
    }

    public int[] getOffsets() {
        int count = getSubKeysCount();
        if (count <= 0)
            return null;
        int[] result = new int[count];

        int tamEntrada = 4;
        if ((data[0] == 'l') && ((data[1] == 'f') || (data[1] == 'h'))) {
            tamEntrada = 8;
        }

        for (int i = 0; i < getSubKeysCount(); i++) {
            int pos = 4 + (i * tamEntrada);
            result[i] = (data[pos] & 0xFF) | (data[pos + 1] & 0xFF) << 8 | (data[pos + 2] & 0xFF) << 16
                    | (data[pos + 3] & 0xFF) << 24;
        }

        return result;
    }

}
