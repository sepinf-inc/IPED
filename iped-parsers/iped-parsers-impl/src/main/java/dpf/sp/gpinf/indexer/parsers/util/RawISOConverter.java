package dpf.sp.gpinf.indexer.parsers.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.tika.io.TemporaryResources;

import dpf.sp.gpinf.indexer.util.IOUtil;

public class RawISOConverter {

    private static int getISODataOffset(File file) {

        FileInputStream fis = null;
        try {
            String magicISO9660Str = "CD001"; //$NON-NLS-1$
            String magicUDFStr = "NSR0"; //$NON-NLS-1$
            byte[] magicISO9660 = magicISO9660Str.getBytes("UTF-8"); //$NON-NLS-1$
            byte[] magicUDF = magicUDFStr.getBytes("UTF-8"); //$NON-NLS-1$

            byte[] header = new byte[64 * 1024];
            fis = new FileInputStream(file);
            int read = 0, off = 0;
            while (read != -1 && (off += read) < header.length) {
                read = fis.read(header, off, header.length - off);
            }

            // CDROM sector 2048
            if (matchMagic(magicISO9660, header, 32769) || matchMagic(magicISO9660, header, 34817)
                    || matchMagic(magicUDF, header, 32769) || matchMagic(magicUDF, header, 34817))
                return 0;

            // CDROM RAW sector 2352
            if (matchMagic(magicISO9660, header, 37649) || matchMagic(magicISO9660, header, 40001)
                    || matchMagic(magicUDF, header, 37649) || matchMagic(magicUDF, header, 40001))
                return 16;

            // CDROM RAW XA sector 2352
            if (matchMagic(magicISO9660, header, 37657) || matchMagic(magicISO9660, header, 40009)
                    || matchMagic(magicUDF, header, 37657) || matchMagic(magicUDF, header, 40009))
                return 24;

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            IOUtil.closeQuietly(fis);
        }
        return 0;
    }

    private static boolean matchMagic(byte[] magic, byte[] header, int off) {
        for (int i = 0; i < magic.length; i++) {
            if (magic[i] != header[off + i]) {
                return false;
            }
        }
        return true;
    }

    public static File convertTo2048SectorISO(File iso, TemporaryResources tmp) {

        int dataOffset = getISODataOffset(iso);
        if (dataOffset == 0)
            // not a raw iso, return
            return iso;

        File tmpFile;
        InputStream fis = null;
        OutputStream fos = null;
        try {
            tmpFile = tmp.createTemporaryFile();
            fos = new BufferedOutputStream(new FileOutputStream(tmpFile));
            fis = new BufferedInputStream(new FileInputStream(iso));
            int read = 0, off = 0;
            byte[] data = new byte[2352];
            while (read != -1) {
                read = 0;
                off = 0;
                while (read != -1 && (off += read) < data.length)
                    read = fis.read(data, off, data.length - off);

                if (dataOffset == 16) {
                    if (data[15] == 0x01)
                        fos.write(data, dataOffset, 2048);
                    else
                        fos.write(data, dataOffset, 2336);
                }
                if (dataOffset == 24) {
                    if (data[23] != 0x01)
                        fos.write(data, dataOffset, 2048);
                    else
                        fos.write(data, dataOffset, 2324);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            tmpFile = iso;

        } finally {
            IOUtil.closeQuietly(fis);
            IOUtil.closeQuietly(fos);
        }

        return tmpFile;

    }

}
