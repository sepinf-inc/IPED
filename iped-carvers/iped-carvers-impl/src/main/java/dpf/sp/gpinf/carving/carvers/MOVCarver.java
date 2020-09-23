package dpf.sp.gpinf.carving.carvers;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.commons.codec.DecoderException;
import org.apache.tika.mime.MediaType;

import dpf.sp.gpinf.carving.AbstractCarver;
import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Hit;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.IItem;
import iped3.io.SeekableInputStream;

public class MOVCarver extends AbstractCarver {

    private static final String[] mp4AtomTypes = { "ftyp", "moov", "mdat", "skip", "free", "wide", "pnot", "uuid", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
            "meta", "pict", "PICT", "pdin", "junk" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    private static HashSet<String> atomSet = new HashSet<String>();

    static {
        atomSet.addAll(Arrays.asList(mp4AtomTypes));
    }

    private int defaultMinLength = 100000;
    private int defaultMaxLength = 500000000;

    public MOVCarver() throws DecoderException {
        carverTypes = new CarverType[5];

        carverTypes[0] = new CarverType();
        carverTypes[0].addHeader("????ftypmp41");
        carverTypes[0].addHeader("????ftypmp42");
        carverTypes[0].addHeader("????ftypmmp4");
        carverTypes[0].addHeader("????ftypMSNV");
        carverTypes[0].addHeader("????ftypFACE");
        carverTypes[0].addHeader("????ftypdash");
        carverTypes[0].addHeader("????ftypisom");
        carverTypes[0].setMimeType(MediaType.parse("video/mp4"));
        carverTypes[0].setMaxLength(defaultMaxLength);
        carverTypes[0].setMinLength(defaultMinLength);
        carverTypes[0].setName("MOV");

        carverTypes[1] = new CarverType();
        carverTypes[1].addHeader("????ftypmjp2");
        carverTypes[1].setMimeType(MediaType.parse("video/mj2"));
        carverTypes[1].setMaxLength(defaultMaxLength);
        carverTypes[1].setMinLength(defaultMinLength);
        carverTypes[1].setName("MOV");

        carverTypes[2] = new CarverType();
        carverTypes[2].addHeader("????ftypM4V");
        carverTypes[2].setMimeType(MediaType.parse("video/x-m4v"));
        carverTypes[2].setMaxLength(defaultMaxLength);
        carverTypes[2].setMinLength(defaultMinLength);
        carverTypes[2].setName("MOV");

        carverTypes[3] = new CarverType();
        carverTypes[3].addHeader("????ftyp3g");
        carverTypes[3].setMimeType(MediaType.parse("video/3gpp"));
        carverTypes[3].setMaxLength(defaultMaxLength);
        carverTypes[3].setMinLength(defaultMinLength);
        carverTypes[3].setName("MOV");

        carverTypes[4] = new CarverType();
        carverTypes[4].addHeader("????ftypqt\\20\\20");
        carverTypes[4].setMimeType(MediaType.parse("video/quicktime"));
        carverTypes[4].setMaxLength(defaultMaxLength);
        carverTypes[4].setMinLength(defaultMinLength);
        carverTypes[4].setName("MOV");

        for (int i = 0; i < carverTypes.length; i++) {
            carverTypes[i].setCarverClass(this.getClass().getName());
        }
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        long atomStart = header.getOffset();
        SeekableInputStream is = null;
        try {
            is = parentEvidence.getStream();
            byte[] data = new byte[4];
            while (true) {
                is.seek(atomStart + 4);
                int i = 0, off = 0;
                while (i != -1 && (off += i) < data.length)
                    i = is.read(data, off, data.length - off);

                String atomType = new String(data, "windows-1252"); //$NON-NLS-1$
                if (!atomSet.contains(atomType))
                    // EOF
                    break;

                is.seek(atomStart);
                i = 0;
                off = 0;
                while (i != -1 && (off += i) < data.length)
                    i = is.read(data, off, data.length - off);

                long atomSize = (long) (data[0] & 0xff) << 24 | (data[1] & 0xff) << 16 | (data[2] & 0xff) << 8
                        | (data[3] & 0xff);

                if (atomSize <= 0)
                    break;

                if (atomSize == 1) {
                    byte[] extendedSize = new byte[8];
                    is.seek(atomStart + 8);
                    i = 0;
                    off = 0;
                    while (i != -1 && (off += i) < extendedSize.length)
                        i = is.read(extendedSize, off, extendedSize.length - off);

                    atomSize = (long) (extendedSize[0] & 0xff) << 56 | (long) (extendedSize[1] & 0xff) << 48
                            | (long) (extendedSize[2] & 0xff) << 40 | (long) (extendedSize[3] & 0xff) << 32
                            | (long) (extendedSize[4] & 0xff) << 24 | (extendedSize[5] & 0xff) << 16
                            | (extendedSize[6] & 0xff) << 8 | (extendedSize[7] & 0xff);
                }

                if (atomSize <= 0)
                    break;

                atomStart += atomSize;

                if (atomStart >= parentEvidence.getLength())
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            IOUtil.closeQuietly(is);
        }

        return atomStart - header.getOffset();
    }

}
