/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3;

import java.io.Serializable;

/**
 *
 * @author Nassif
 */
public abstract class IHashValue implements Comparable<IHashValue>, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public abstract byte[] getBytes();

    public String toString() {
        StringBuilder result = new StringBuilder();
        for (byte b : getBytes()) {
            result.append(String.format("%1$02X", b)); //$NON-NLS-1$
        }
        return result.toString();
    }

    @Override
    public int compareTo(IHashValue hash) {
        byte[] compBytes = hash.getBytes();
        byte[] bytes = getBytes();
        for (int i = 0; i < bytes.length; i++) {
            int cmp = Integer.compare(bytes[i] & 0xFF, compBytes[i] & 0xFF);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Override
    public boolean equals(Object hash) {
        if (hash == this) return true;
        if (hash == null) return false;
        return compareTo((IHashValue) hash) == 0;
    }

    @Override
    public int hashCode() {
        byte[] bytes = getBytes();
        return bytes[3] & 0xFF | (bytes[2] & 0xFF) << 8 | (bytes[1] & 0xFF) << 16 | (bytes[0] & 0xFF) << 24;
    }

}
