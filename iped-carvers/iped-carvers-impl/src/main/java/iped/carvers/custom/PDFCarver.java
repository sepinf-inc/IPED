package iped.carvers.custom;

import java.io.IOException;
import java.util.ArrayDeque;

import iped.carvers.api.Hit;
import iped.carvers.standard.DefaultCarver;
import iped.data.IItem;
import iped.io.SeekableInputStream;

public class PDFCarver extends DefaultCarver {
    private Hit lastFooter;
    private Hit lastXREF;
    private long lastXREFOffset = -1;
    private boolean lastHitWasStartXRef;

    @Override
    public void notifyHit(IItem parentEvidence, Hit hit) throws IOException {
        ArrayDeque<Hit> headersWaitingFooters = super.headersWaitingFooters;
        if (hit.getSignature().isHeader()) {
            // if previously occurred a footer hit and a new header hit is found, carve from
            // last footer
            carveFromLastFooter(parentEvidence);
            headersWaitingFooters.addLast(hit);
        }

        if (hit.getSignature().isFooter()) {
            if (lastXREF == null) {
                // footer without corresponding crossref => invalid footer
                // so try to carve from last valid footer
                carveFromLastFooter(parentEvidence);
            } else {
                Hit lastHead = headersWaitingFooters.peekLast();
                if (lastHead != null) {
                    // checks consistency of crossref offset information against header offset
                    if (lastXREF.getOffset() - lastHead.getOffset() == lastXREFOffset) {
                        lastFooter = hit;
                    } else {
                        // probably invalid footer as crossref offset info is inconsistent
                        // so try to carve from last valid footer
                        carveFromLastFooter(parentEvidence);
                    }
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

    //carves from each header hit in headersWaitingFooters
    private void carveRemainingPDFHeaders(IItem parentEvidence){
        Hit header = headersWaitingFooters.pollLast();
        while(header!=null){
            CarverType typeCarved = header.getSignature().getCarverType();
            carveFromHeader(parentEvidence, header, typeCarved.getMaxLength());
            header = headersWaitingFooters.pollLast();
        }
    }

    private void resetState() {
        headersWaitingFooters.clear();
        lastFooter = null;
        lastXREF = null;
        lastXREFOffset = -1;
        lastHitWasStartXRef = false;
    }

    private boolean isStartXrefHit(Hit hit) {
        return hit.getSignature().getSigString().equals("startxref");
    }

    private long readXREFOffset(IItem parentEvidence, Hit hit) {
        try (SeekableInputStream is = parentEvidence.getSeekableInputStream()) {
            long offset = 0;
            is.seek(hit.getOffset() + 10);
            int i = is.read();
            while (i != -1 && i >= '0' && i <= '9') {
                offset = offset * 10 + (i - '0');
                i = is.read();
            }
            return offset;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;
    }

    private boolean isXrefHit(Hit hit) {
        return hit.getSignature().getSigString().equals("xref");
    }

    private void carveFromLastFooter(IItem parentEvidence) throws IOException {
        if (lastFooter != null) {
            Hit head, firstHead = null;
            while ((head = headersWaitingFooters.peekLast()) != null && lastFooter.getOffset()
                    - head.getOffset() <= head.getSignature().getCarverType().getMaxLength()) {
                firstHead = headersWaitingFooters.pollLast();
            }
            if (firstHead != null) {
                headersWaitingFooters.addLast(firstHead);
                carveFromFooter(parentEvidence, lastFooter);
            }
            lastFooter = null;
        }

        //carves any from remaining PDF header hit without footer hit
        carveRemainingPDFHeaders(parentEvidence);
        
        resetState();
    }

    @Override
    public void notifyEnd(IItem parentEvidence) throws IOException {
        carveFromLastFooter(parentEvidence);
        super.notifyEnd(parentEvidence);
    }
}
