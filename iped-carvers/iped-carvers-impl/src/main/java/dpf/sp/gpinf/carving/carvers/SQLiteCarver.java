package dpf.sp.gpinf.carving.carvers;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.commons.codec.DecoderException;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.carving.AbstractCarver;
import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Hit;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.IItem;
import iped3.io.SeekableInputStream;

public class SQLiteCarver extends AbstractCarver {

    public SQLiteCarver() throws DecoderException {
        carverTypes = new CarverType[1];
        carverTypes[0] = new CarverType();
        carverTypes[0].addHeader("SQLite format 3\\00");
        carverTypes[0].setMimeType(MediaType.parse("application/x-sqlite3"));
        carverTypes[0].setMaxLength(100000000);
        carverTypes[0].setMinLength(10000);
        carverTypes[0].setName("SQLITE");
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        SeekableInputStream is = null;
        try {
            is = parentEvidence.getStream();
            byte[] data = new byte[2];
            is.seek(header.getOffset() + 16);
            int i = 0, off = 0;
            while (i != -1 && (off += i) < data.length)
                i = is.read(data, off, data.length - off);

            int pageSize = (data[0] & 0xff) << 8 | (data[1] & 0xff);
            if (pageSize == 1)
                pageSize = 65536;

            if ((pageSize & (pageSize - 1)) != 0)
                return -1;// if not power of two

            int vers = is.read();// write version
            if ((vers != 1) && (vers != 2))
                return -1;
            vers = is.read();// read version
            if ((vers != 1) && (vers != 2))
                return -1;

            data = new byte[4];
            is.seek(header.getOffset() + 28);
            i = 0;
            off = 0;
            while (i != -1 && (off += i) < data.length)
                i = is.read(data, off, data.length - off);

            long numPages = (long)(data[0] & 0xff) << 24 | (data[1] & 0xff) << 16 | (data[2] & 0xff) << 8 | (data[3] & 0xff);

            return numPages * (long) pageSize;
            
        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            IOUtil.closeQuietly(is);
        }

        return -1;
    }

    @Override
    public boolean isValid(IItem parentEvidence, Hit headerOffset, long length) {
        // rules retrieved from "https://www.sqlite.org/fileformat.html"

        SeekableInputStream is = null;
        try {
            is = parentEvidence.getStream();
            byte[] formatWriteVersion = new byte[1];
            byte[] formatReadVersion = new byte[1];
            byte[] unusedPageBytes = new byte[1];
            byte[] maxEmbeddedPayload = new byte[1];
            byte[] minEmbeddedPayload = new byte[1];
            byte[] leafPayloadFraction = new byte[1];
            byte[] schemaFormatNumber = new byte[4];
            byte[] dbTextEncoding = new byte[4];
            byte[] reservedZero = new byte[20];
            is.seek(headerOffset.getOffset() + 18);
            is.read(formatWriteVersion);
            if (formatWriteVersion[0] != 1 && formatWriteVersion[0] != 2)
                return false;
            is.read(formatReadVersion);
            if (formatReadVersion[0] != 1 && formatWriteVersion[0] != 2)
                return false;
            is.read(unusedPageBytes);
            if (unusedPageBytes[0] != 0)
                return false;
            is.read(maxEmbeddedPayload);
            if (maxEmbeddedPayload[0] != 64)
                return false;
            is.read(minEmbeddedPayload);
            if (minEmbeddedPayload[0] != 32)
                return false;
            is.read(leafPayloadFraction);
            if (leafPayloadFraction[0] != 32)
                return false;
            is.skip(20);
            is.read(schemaFormatNumber);
            ByteBuffer wrapped = ByteBuffer.wrap(schemaFormatNumber);
            int schemaFormatNumberInt = wrapped.getInt();
            if (schemaFormatNumberInt != 1 && schemaFormatNumberInt != 2 && schemaFormatNumberInt != 3
                    && schemaFormatNumberInt != 4)
                return false;
            is.skip(8);
            is.read(dbTextEncoding);
            wrapped = ByteBuffer.wrap(dbTextEncoding);
            int dbTextEncodingInt = wrapped.getInt();
            if (dbTextEncodingInt != 1 && dbTextEncodingInt != 2 && dbTextEncodingInt != 3)
                return false;
            is.skip(12);
            is.read(reservedZero);
            for (int i = 0; i < reservedZero.length; i++) {
                if (reservedZero[i] != 0)
                    return false;
            }

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            IOUtil.closeQuietly(is);
        }

        return true;
    }
}
