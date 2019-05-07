package dpf.mt.gpinf.registro.model;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class KeyNode extends CellContent {

    private KeyValue[] values = null;

    public KeyNode(RegistryFile reg, byte[] data) {
        super(reg, data);
    }

    public Date getLastWrittenAsDate() {
        long milis = getLastWrittenTimestamp().toMillis();
        milis = milis - 11644473600000l; // diminui numero de milis segundos entre 01/01/1601 (filetime) e 01/01/1970
                                         // (javadate)
        return new Date(milis);
    }

    public FileTime getLastWrittenTimestamp() {
        byte buffer[] = Arrays.copyOfRange(data, 4, 12);
        DataInputStream di = new DataInputStream(new ByteArrayInputStream(buffer));
        long value = 0;
        try {
            long pow = 1;
            for (int i = 0; i < 8; i++) {
                value += di.readUnsignedByte() * pow;
                pow = pow * 256;
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // long value2 = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] &
        // 0xFF) << 16 | (buffer[3] & 0xFF) << 24 | (buffer[4] & 0xFF) << 32 |
        // (buffer[5] & 0xFF) << 40 | (buffer[6] & 0xFF) << 48 | (buffer[7] & 0xFF) <<
        // 56;
        FileTime f = FileTime.from(value / (10l), TimeUnit.MICROSECONDS);
        return f;
    }

    public int getSubKeysCount() {
        byte buffer[] = Arrays.copyOfRange(data, 20, 24);
        return (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
    }

    public int getValuesCount() {
        byte buffer[] = Arrays.copyOfRange(data, 36, 40);
        return (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
    }

    public String getKeyName() {
        byte buffer[] = Arrays.copyOfRange(data, 72, 74);
        int nameLength = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8;
        return new String(Arrays.copyOfRange(data, 76, 76 + nameLength));
    }

    public KeyNode getSubKey(String name) {
        ArrayList<KeyNode> resultado = getSubKeys();
        for (int i = 0; i < resultado.size(); i++) {
            if (resultado.get(i).getKeyName().equals(name)) {
                return resultado.get(i);
            }
        }
        return null;
    }

    public ArrayList<KeyNode> getSubKeys() {
        if (getSubKeysCount() > 0) {
            byte[] buffer = Arrays.copyOfRange(data, 28, 32);
            int listOffset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16
                    | (buffer[3] & 0xFF) << 24;
            buffer = null;

            ArrayList<KeyNode> resultado = new ArrayList<KeyNode>();

            addSubkeys(listOffset, resultado);

            return resultado;
        } else {
            return null;
        }
    }

    private void addSubkeys(int listOffset, ArrayList resultado) {
        HiveCell cell = reg.getCell(listOffset);
        if (cell.getCellContent() instanceof SubKeysList) {
            SubKeysList listOffsets = (SubKeysList) cell.getCellContent();
            int offsets[] = listOffsets.getOffsets();
            for (int i = 0; i < offsets.length; i++) {
                int offset = offsets[i];
                HiveCell keyCell = reg.getCell(offset);
                resultado.add((KeyNode) keyCell.getCellContent());
            }
        }
        if (cell.getCellContent() instanceof IndexRoot) {
            IndexRoot ri = (IndexRoot) cell.getCellContent();
            int[] offsets = ri.getOffsets();
            for (int i = 0; i < offsets.length; i++) {
                addSubkeys(offsets[i], resultado);
            }
        }
    }

    private void loadValues() {
        int count = getValuesCount();
        if (count <= 0) {
            values = new KeyValue[0];
            return;
        }
        values = new KeyValue[count];

        byte[] buffer = Arrays.copyOfRange(data, 40, 44);
        int listOffset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16
                | (buffer[3] & 0xFF) << 24;
        buffer = null;

        int pos = 0;
        HiveCell cellList = reg.getCell(listOffset);
        if (cellList.getCellContent() instanceof DataCell) {
            DataCell listSegments = (DataCell) cellList.getCellContent();
            int offset = 0;
            for (int i = 0; i < count; i++) {
                buffer = Arrays.copyOfRange(listSegments.data, i * 4, (i * 4) + 4);
                offset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16
                        | (buffer[3] & 0xFF) << 24;
                buffer = null;
                HiveCell valueCell = reg.getCell(offset);
                values[pos] = (KeyValue) valueCell.getCellContent();
                pos++;
            }
        }
    }

    public KeyValue getValue(String name) {
        if (values == null) {
            loadValues();
        }
        KeyValue result = null;
        for (int i = 0; i < values.length; i++) {
            if (values[i].getValueName().equals(name)) {
                result = values[i];
                break;
            }
        }
        return result;
    }

    public String toString() {
        return getKeyName();
    }

    public KeyValue[] getValues() {
        if (values == null) {
            loadValues();
        }

        return values;
        /*
         * int count = getValuesCount(); if (count<=0) return null; KeyValue[] result =
         * new KeyValue[count];
         * 
         * byte[] buffer = Arrays.copyOfRange(data, 40, 44); int listOffset = (buffer[0]
         * & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) << 16 | (buffer[3] &
         * 0xFF) << 24 ; buffer = null;
         * 
         * int pos = 0; HiveCell cellList = reg.getCell(listOffset);
         * if(cellList.getCellContent() instanceof DataCell){ DataCell listSegments =
         * (DataCell) cellList.getCellContent(); int offset = 0; for (int i = 0; i <
         * count; i++) { buffer = Arrays.copyOfRange(listSegments.data, i*4, (i*4) + 4);
         * offset = (buffer[0] & 0xFF) | (buffer[1] & 0xFF) << 8 | (buffer[2] & 0xFF) <<
         * 16 | (buffer[3] & 0xFF) << 24; buffer = null; HiveCell valueCell =
         * reg.getCell(offset); result[pos] = (KeyValue) valueCell.getCellContent();
         * pos++; } }
         * 
         * return result;
         */ }

}
