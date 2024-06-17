package iped.carvers.custom;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import iped.carvers.api.Hit;
import iped.carvers.standard.DefaultCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;

public class PDFCarver extends DefaultCarver {
    private Hit lastFooter;

    private static final String startxref = "startxref";
    private static final String xref = "xref";

    @Override
    public void notifyHit(IItem parentEvidence, Hit hit) throws IOException {
        if (hit.getSignature().isHeader()) {
            // Carve any pending header-footer pair
            carveFromLastHits(parentEvidence);
            headersWaitingFooters.add(hit);

        } else if (hit.getSignature().isFooter() && !headersWaitingFooters.isEmpty()) {
            if (lastFooter == null || hit.getOffset() == lastFooter.getOffset()
                    || matchFooterHeader(parentEvidence, headersWaitingFooters.peekLast(), hit)) {
                // If this is the first footer found after the last header, or it is a longer
                // one in the same offset, or it matches last header, then do not carve now.
                // Wait to see if there are more footers for the same header.
                lastFooter = hit;
            } else {
                // Otherwise carve using the pending the last header-footer pair
                carveFromLastHits(parentEvidence);
            }
        }
    }

    private void carveFromLastHits(IItem parentEvidence) throws IOException {
        if (!headersWaitingFooters.isEmpty() && lastFooter != null) {
            carveFromFooter(parentEvidence, lastFooter);
        }
        lastFooter = null;
    }

    private boolean matchFooterHeader(IItem parentEvidence, Hit header, Hit footer) {
        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            // Get "startxref" from the bytes right before the footer.
            long xrefOffset = -1;
            long pos = Math.max(0, footer.getOffset() - 24);
            is.seek(pos);
            byte[] lastBytes = is.readNBytes((int) (footer.getOffset() - pos));
            for (int i = 0; i <= lastBytes.length - startxref.length(); i++) {
                boolean found = true;
                for (int j = 0; j < startxref.length(); j++) {
                    if ((lastBytes[i + j] & 0xFF) != startxref.charAt(j)) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    int off = i + startxref.length();
                    String s = new String(lastBytes, off, lastBytes.length - off, StandardCharsets.ISO_8859_1);
                    xrefOffset = Long.parseLong(s.trim());
                    break;
                }
            }

            // If "xrefOffset" was found and it is between the header and the footer...
            if (xrefOffset > 0 && xrefOffset + header.getOffset() + xref.length() < footer.getOffset()) {
                // ...check if it really points to "xref"
                is.seek(header.getOffset() + xrefOffset);
                byte[] bytes = is.readNBytes(xref.length());
                for (int i = 0; i < xref.length(); i++) {
                    if ((bytes[i] & 0xFF) != xref.charAt(i)) {
                        return false;
                    }
                }
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    @Override
    public void notifyEnd(IItem parentEvidence) throws IOException {
        carveFromLastHits(parentEvidence);
        super.notifyEnd(parentEvidence);
    }
}
