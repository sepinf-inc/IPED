package dpf.sp.gpinf.carving;

import dpf.sp.gpinf.carver.api.Hit;
import iped3.IItem;
import iped3.io.SeekableInputStream;

import java.io.IOException;

public class DefaultCarver extends AbstractCarver {

    public DefaultCarver() {
    }

    @Override
    public long getLengthFromHit(IItem parentEvidence, Hit header) throws IOException {
        return getDefaultLengthFromHeader(parentEvidence, header);
    }

    // mÃ©todo padrÃ£o caso nÃ£o haja um script definido
    public long getDefaultLengthFromHeader(IItem parentEvidence, Hit header) throws IOException {
        // se nao tiver a informacao de posicao da informacao de tamanho considera o
        // tamanho maximo como o tamanho do item
        if (header.getSignature().getCarverType().getSizePos() == -1) {
            return header.getSignature().getCarverType().getMaxLength();
        }

        try (SeekableInputStream is = parentEvidence.getStream()) {
            is.seek(header.getOffset() + header.getSignature().getCarverType().getSizePos());
            byte buf[] = new byte[header.getSignature().getCarverType().getSizeBytes()];
            int i = 0, off = 0;
            do {
                i = is.read(buf, off, buf.length - off);
            } while (i != -1 && (off += i) < buf.length);

            long length = 0;
            for (int j = 0; j < header.getSignature().getCarverType().getSizeBytes(); j++) {
                if (!header.getSignature().getCarverType().isBigendian()) {
                    length |= (long) (buf[j] & 0xff) << (8 * j);
                } else {
                    length |= (long) (buf[j] & 0xff) << (8
                            * (header.getSignature().getCarverType().getSizeBytes() - j - 1));
                }
            }

            if (header.getSignature().getCarverType().getName().startsWith("RIFF")) { //$NON-NLS-1$
                length += 8;
            }

            long evidenceLen = parentEvidence.getLength();
            // se a informaÃ§Ã£o de tamanho indicar que o arquivo termina em posiÃ§Ã£o maior
            // que
            // o tamanho da evidÃªncia
            if (header.getOffset() + length > evidenceLen) {
                // retorna como tamanho o tamanho entre o inicio do cabecalho e o tamanho fim da
                // evidencia.
                length = evidenceLen - header.getOffset();
            }

            return length;
        }
    }

}