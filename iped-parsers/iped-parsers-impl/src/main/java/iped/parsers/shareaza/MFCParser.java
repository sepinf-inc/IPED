/*
 * Copyright 2015-2015, Fabio Melo Pfeifer
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.parsers.shareaza;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.util.LittleEndianInputStream;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
class MFCParser {

    private final LittleEndianInputStream is;

    public MFCParser(InputStream is) {
        this.is = new LittleEndianInputStream(is);
    }

    public byte[] readBytes(int n) throws IOException {
        byte[] buff = new byte[n];
        is.readFully(buff);
        return buff;
    }

    public int readInt() throws IOException {
        return is.readInt();
    }

    public long readUInt() throws IOException {
        return is.readUInt();
    }

    public long readLong() throws IOException {
        return is.readLong();
    }

    public short readShort() throws IOException {
        return is.readShort();
    }

    public int readUShort() throws IOException {
        return is.readUShort();
    }

    public boolean readBool() throws IOException {
        int n = readInt();
        return n != 0;
    }

    public byte readByte() throws IOException {
        return is.readByte();
    }

    public int readUByte() throws IOException {
        return is.readUByte();
    }

    public long readFileTime() throws IOException {
        return is.readLong();
    }

    public String readHash(int n) throws IOException {
        return readHash(n, "hex"); //$NON-NLS-1$
    }

    public String readEpochDateTime() throws IOException {
        return Util.formatDatetime(Util.convertToEpoch(readLong()));
    }

    public String readHash(int n, String encoder) throws IOException {
        return readHash(n, encoder, true);
    }
    
    public String readHash(int n, String encoder, boolean readValid) throws IOException {
        String ret = "0"; //$NON-NLS-1$
        boolean valid = readValid ? readBool() : true;
        if (valid) {
            byte[] bytes = readBytes(n);
            switch (encoder) {
                case "base32": //$NON-NLS-1$
                    ret = Util.encodeBase32(bytes);
                    break;

                case "base64": //$NON-NLS-1$
                    ret = Util.encodeBase64(bytes);
                    break;

                case "guid": //$NON-NLS-1$
                    ret = Util.encodeGUID(bytes);
                    break;

                case "hex": //$NON-NLS-1$
                default:
                    ret = Util.encodeHex(bytes);
                    break;
            }
        }

        return ret;
    }

    // Returns int[] {stringLen, isUnicode (0 or 1)}
    private int[] readStringLen() throws IOException {
        int blen = readUByte();
        int isUnicode = 0;
        if (blen < 0xff) {
            return new int[] { blen, isUnicode };
        }

        int wlen = readUShort();
        if (wlen == 0xfffe) {
            isUnicode = 1;
            blen = readUByte();
            if (blen < 0xff) {
                return new int[] { blen, isUnicode };
            }
            wlen = readUShort();
        }

        if (wlen < 0xffff) {
            return new int[] { wlen, isUnicode };
        }

        long dLen = readUInt();
        if (dLen < 0xFFFFFFFF) {
            return new int[] { (int) dLen, isUnicode };
        }

        long qLen = readUInt();
        return new int[] { (int) qLen, isUnicode };
    }

    public int readCount() throws IOException {
        int ret = readUShort();
        if (ret == 0xffff) {
            return readInt();
        }
        return ret;
    }

    public String readString() throws IOException {
        int[] r = readStringLen();
        int len = r[0];
        boolean isUnicode = r[1] == 1;

        String ret = ""; //$NON-NLS-1$
        if (len > 0) {
            if (isUnicode) {
                ret = new String(readBytes(len * 2), "UTF-16LE"); //$NON-NLS-1$
            } else {
                ret = new String(readBytes(len), "UTF-8"); //$NON-NLS-1$
            }
        }
        return ret;
    }
}
