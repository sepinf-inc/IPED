package dpf.sp.gpinf.carving.carvers;

import java.io.IOException;
import java.util.ArrayDeque;

import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Hit;
import dpf.sp.gpinf.carving.FromFarthestHeaderCarver;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.IItem;
import iped3.io.SeekableInputStream;

/*
 * Author: Patrick Dalla Bernardina
 * Based on: https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
 */

public class ZIPCarver extends FromFarthestHeaderCarver {

    public ZIPCarver() {
        maxWaitingHeaders = 65535; // maximum number of files in a zip
    }

    @Override
    public IItem carveFromFooter(IItem parentEvidence, Hit footer) throws IOException {
        Hit firstHeaderOcurrence = null;

        ArrayDeque<Hit> headersWaitingFooters = super.headersWaitingFooters;

        if (headersWaitingFooters.size() > 0) {
            int numOfEntries = getNumberOfEntries(parentEvidence, footer);

            // if zip64
            if (numOfEntries == -1) {
                return super.carveFromFooter(parentEvidence, footer);
            }

            for (int i = 0; i < numOfEntries; i++) {
                Hit prior = headersWaitingFooters.pollLast();
                if (prior != null) {
                    firstHeaderOcurrence = prior;
                } else {
                    /*
                     * there is no more headers, the carve will proceed as it may be possible to
                     * parse the recovered headers
                     */
                    break;
                }
            }
            
            if(firstHeaderOcurrence == null)
                return null;

            Hit header = firstHeaderOcurrence;

            long len = footer.getOffset() + footer.getSignature().getLength() - header.getOffset();
            CarverType typeCarved = header.getSignature().getCarverType();

            // se menor que o tamanho mínimo
            if ((typeCarved.getMinLength() != null) && (len < typeCarved.getMinLength())) {
                return null;// não carveia
            }
            // se maior que o tamanho máximo
            if ((typeCarved.getMaxLength() != null) && (len > typeCarved.getMaxLength())) {
                return null;// não carveia
            }

            return carveFromHeader(parentEvidence, header, len);
        } else {
            return null;
        }
    }

    /* method to read size from data descriptor if it exists on the zip file */
    public int getNumberOfEntries(IItem parentEvidence, Hit hit) {
        int numberOfEntries = 0;

        SeekableInputStream is = null;
        try {
            is = parentEvidence.getStream();
            is.seek(hit.getOffset() + 4 + 2 + 2);
            byte[] data = new byte[2];
            /* total number of entries in the central directory on this disk */
            int i = 0, pos = 0;
            while (i != -1 && (pos += i) < data.length)
                i = is.read(data, pos, data.length - pos);
            numberOfEntries = Integer.valueOf(data[1] & 0xff) << 8 | (data[0] & 0xff);

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        } finally {
            IOUtil.closeQuietly(is);
        }
        return numberOfEntries;
    }

}
