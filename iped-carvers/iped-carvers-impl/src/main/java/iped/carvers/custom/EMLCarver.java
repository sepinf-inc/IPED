package iped.carvers.custom;

import java.io.IOException;
import java.util.ArrayDeque;

import iped.carvers.api.Hit;
import iped.carvers.standard.DefaultCarver;
import iped.data.IItem;

public class EMLCarver extends DefaultCarver {
    Hit lastFooter = null;

    @Override
    public void notifyHit(IItem parentEvidence, Hit hit) throws IOException {
        ArrayDeque<Hit> headersWaitingFooters = super.headersWaitingFooters;
        if (hit.getSignature().isHeader()) {
            // se tem um footer encontrado e encontrou um header novo --> carveia
            if (lastFooter != null) {
                carveFromLastFooter(parentEvidence);
            }
            headersWaitingFooters.addLast(hit);
        }

        if (hit.getSignature().isFooter()) {
            if (lastFooter == null
                    || hit.getOffset() - lastFooter.getOffset() < hit.getSignature().getCarverType().getMaxLength())
                lastFooter = hit;
        }

        clearOldHeaders(parentEvidence);
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

    /*
     * @Override public long getLengthFromHit(Item parentEvidence, Hit header)
     * throws IOException { System.out.println("EMLCarver getLengthFromHit"); long
     * distanceFromEnd = parentEvidence.getLength() - firstHeader.getOffset(); long
     * len = 0; if(distanceFromEnd >
     * header.getSignature().getCarverType().getMaxLength()){ len =
     * header.getSignature().getCarverType().getMaxLength(); }else{ len =
     * distanceFromEnd; } return len; }
     */

}
