package iped.carvers.custom;

import java.io.IOException;
import java.util.ArrayDeque;

import iped.carvers.api.Hit;
import iped.carvers.standard.DefaultCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;
import iped.utils.IOUtil;

public class PDFCarver extends DefaultCarver {
    Hit lastFooter = null;
    private Hit lastXREF;
    private long lastXREFOffset = -1;
    boolean lastHitWasStartXRef = false;

    @Override
    public void notifyHit(IItem parentEvidence, Hit hit) throws IOException {
        ArrayDeque<Hit> headersWaitingFooters = super.headersWaitingFooters;
        if (hit.getSignature().isHeader()) {
            // if previously occured a footer hit and a new header hit is found, carve from
            // last footer
            if (lastFooter != null) {
                carveFromLastFooter(parentEvidence);
            }
            resetState();
            headersWaitingFooters.addLast(hit);
        }

        if (hit.getSignature().isFooter()) {
            if (lastXREF == null) {
                // footer without corresponding crossref => invalid footer
                // so try to carve from last valid footer if not null
                if (lastFooter != null) {
                    carveFromLastFooter(parentEvidence);
                }
                resetState();
            } else {
                Hit lastHead = headersWaitingFooters.peekLast();
                if (lastHead != null) {
                    if (lastXREF.getOffset() - lastHead.getOffset() == lastXREFOffset) { // checks consistency of
                                                                                         // crossref offset information
                                                                                         // against header offset
                        lastFooter = hit;
                    } else {
                        // probably invalid footer as crossref offset info is inconsistent
                        // so try to carve from last valid footer if not null
                        if (lastFooter != null) {
                            carveFromLastFooter(parentEvidence);
                        }
                        resetState();
                    }
                } else {
                    // try to carve PDF without footer?
                }
            }
        }

        if (isXrefHit(hit) && !lastHitWasStartXRef) {
            lastXREF = hit;
        }

        if (isStartXrefHit(hit)) {
            lastXREFOffset = readXREFOffset(parentEvidence, hit);

            lastHitWasStartXRef = true;
        } else {
            lastHitWasStartXRef = false;
        }

        clearOldHeaders(parentEvidence);
    }

    private void resetState() {
        headersWaitingFooters.clear();
        lastFooter = null;
        lastXREF = null;
        lastXREFOffset = -1;
    }

    private boolean isStartXrefHit(Hit hit) {
        return hit.getSignature().getSigString().equals("startxref");
    }

    private long readXREFOffset(IItem parentEvidence, Hit hit) {
        SeekableInputStream is = null;
        try {
            is = parentEvidence.getSeekableInputStream();
            long offset = 0;
            is.seek(hit.getOffset() + 10);
            int i = is.read();
            while (i != -1 && i >= 48 && i <= 57) {
                offset = offset * 10 + (i - 48);
                i = is.read();
            }

            return offset;

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            IOUtil.closeQuietly(is);
        }

        return -1;
    }

    private boolean isXrefHit(Hit hit) {
        return hit.getSignature().getSigString().equals("xref");
    }

    private void carveFromLastFooter(IItem parentEvidence) throws IOException {
        Hit head, firstHead = null;
        while ((head = headersWaitingFooters.peekLast()) != null
                && lastFooter.getOffset() - head.getOffset() <= head.getSignature().getCarverType().getMaxLength()) {
            firstHead = headersWaitingFooters.pollLast();
        }
        if (firstHead != null) {
            headersWaitingFooters.addLast(firstHead);
            carveFromFooter(parentEvidence, lastFooter);
        }
        lastFooter = null;
    }

    public void notifyEnd(IItem parentEvidence) throws IOException {
        if (lastFooter != null) {
            carveFromLastFooter(parentEvidence);
        }
        super.notifyEnd(parentEvidence);
    }

}