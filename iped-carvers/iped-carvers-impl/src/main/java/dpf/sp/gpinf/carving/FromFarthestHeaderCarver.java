package dpf.sp.gpinf.carving;

import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Hit;
import iped3.Item;

import java.io.IOException;
import java.util.ArrayDeque;

public class FromFarthestHeaderCarver extends DefaultCarver {
    
    public FromFarthestHeaderCarver() {
        maxWaitingHeaders = 100000;
    }

    @Override
    public Item carveFromFooter(Item parentEvidence, Hit footer) throws IOException {
        Hit farthestHeaderOcurrence = null;

        ArrayDeque<Hit> headersWaitingFooters = super.headersWaitingFooters;

        while (headersWaitingFooters.size() > 0) {
            Hit lastHeaderOcurrence = headersWaitingFooters.peekLast();
            // if the last header ocurrence is too far, making the size greater than
            // maxlength
            if (footer.getOffset() - lastHeaderOcurrence.getOffset() > footer.getSignature().getCarverType()
                    .getMaxLength()) {
                // clears the stack from unused headers
                headersWaitingFooters.clear();
                // breaks to use the farthest header ocurrence
                break;
            }
            // makes the last header ocurrence the farthest and remove from stack.
            farthestHeaderOcurrence = headersWaitingFooters.pollLast();
        }

        Hit header = farthestHeaderOcurrence;
        if(header!=null) {
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
        }else {
        	return null;
        }
    }
}