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
package dpf.mg.udi.gpinf.shareazaparser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;

/**
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
class Util {

    public static String encodeGUID(byte[] bytes) {
        long u1 = (long) (bytes[7]) << 56 | (long) (bytes[6] & 0xff) << 48 | (long) (bytes[5] & 0xff) << 40
                | (long) (bytes[4] & 0xff) << 32 | (long) (bytes[3] & 0xff) << 24 | (long) (bytes[2] & 0xff) << 16
                | (long) (bytes[1] & 0xff) << 8 | (long) (bytes[0] & 0xff);
        long u2 = (long) (bytes[8]) << 56 | (long) (bytes[9] & 0xff) << 48 | (long) (bytes[10] & 0xff) << 40
                | (long) (bytes[11] & 0xff) << 32 | (long) (bytes[12] & 0xff) << 24 | (long) (bytes[13] & 0xff) << 16
                | (long) (bytes[14] & 0xff) << 8 | (long) (bytes[15] & 0xff);
        UUID uuid = new UUID(u1, u2);
        return uuid.toString();
    }

    public static String encodeInAddr(byte[] bytes) {
        return String.format("%d.%d.%d.%d", bytes[0] & 0xff, bytes[1] & 0xff, bytes[2] & 0xff, bytes[3] & 0xff); //$NON-NLS-1$
    }

    public static String encodeHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b & 0xff)); //$NON-NLS-1$
        }
        return builder.toString();
    }

    public static String encodeBase64(byte[] bytes) {
        Base64 encoder = new Base64();
        return encoder.encodeToString(bytes);
    }

    public static String encodeBase32(byte[] bytes) {
        Base32 encoder = new Base32();
        return encoder.encodeToString(bytes);
    }

    public static long convertToEpoch(long timestamp) {
        return timestamp / 10000L - 11644473600000L;
    }

    public static double convertToCSVTimestamp(long epoch) {
        return epoch / 86400.0 + 25569.0;
    }

    public static String decodeTriState(int state) {
        switch (state) {
            case 1:
                return "False"; //$NON-NLS-1$
            case 2:
                return "True"; //$NON-NLS-1$
            default:
        }
        return "Unknown"; //$NON-NLS-1$
    }

    // DateFormat nao é thread safe
    private static final ThreadLocal<DateFormat> threadLocal = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z"); //$NON-NLS-1$
            return df;
        }
    };

    public static String formatDatetime(long epoch) {
        Date date = new Date(epoch);
        return threadLocal.get().format(date);
    }

}
