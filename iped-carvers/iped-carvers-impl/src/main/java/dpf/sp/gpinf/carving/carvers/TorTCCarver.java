package dpf.sp.gpinf.carving.carvers;

import java.nio.charset.StandardCharsets;

import dpf.sp.gpinf.carver.api.Hit;
import dpf.sp.gpinf.carver.api.InvalidCarvedObjectException;
import dpf.sp.gpinf.carving.DefaultCarver;
import iped3.IItem;
import iped3.io.SeekableInputStream;

public class TorTCCarver extends DefaultCarver {

    private static int MAX_BUF_SIZE = 1 << 20;

    @Override
    public boolean isSpecificIgnoreCorrupted() {
        return true;
    }

    @Override
    public void validateCarvedObject(IItem parentEvidence, Hit headerOffset, long length)
            throws InvalidCarvedObjectException {

        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            is.seek(headerOffset.getOffset());
            byte[] buf = new byte[(int) Math.min(length, MAX_BUF_SIZE)];
            int i = 0, off = 0;
            while (off < buf.length && (i = is.read(buf, off, buf.length - off)) != -1) {
                off += i;
            }
            String itemText = new String(buf, 0, off, StandardCharsets.ISO_8859_1);

            if (!(itemText.contains("BUILD_FLAGS") || itemText.contains("SOCKS") || itemText.contains("HS_STATE"))) {
                throw new InvalidCarvedObjectException("Invalid or incomplete TOR TC carved fragment");
            }

        } catch (Exception e) {
            throw new InvalidCarvedObjectException(e);
        }
    }

}
