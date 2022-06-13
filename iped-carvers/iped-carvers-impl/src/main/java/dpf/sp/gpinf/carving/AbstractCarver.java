package dpf.sp.gpinf.carving;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import dpf.sp.gpinf.carver.api.CarvedItemListener;
import dpf.sp.gpinf.carver.api.Carver;
import dpf.sp.gpinf.carver.api.CarverType;
import dpf.sp.gpinf.carver.api.Hit;
import dpf.sp.gpinf.carver.api.InvalidCarvedObjectException;
import dpf.sp.gpinf.carver.api.Signature.SignatureType;
import iped3.IItem;
import iped3.util.ExtraProperties;
import iped3.util.MediaTypes;

public abstract class AbstractCarver implements Carver {
    protected static String carvedNamePrefix = "Carved-";// esta propriedade não foi declarada estatica para permitir
    protected CarverType[] carverTypes = null;

    protected ArrayDeque<Hit> headersWaitingFooters = new ArrayDeque<>();

    protected int maxWaitingHeaders = 1000;

    private ArrayList<CarvedItemListener> carvedItemListeners = new ArrayList<CarvedItemListener>();

    Hit lastEscapeFooter;
    boolean ignoreCorrupted;

    // carveia do cabeçalho a partir da informação de tamanho retornada pelo método
    // getLengthFromHit
    public IItem carveFromLengthRef(IItem parentEvidence, Hit header, Hit lengthRef) throws IOException {

        headersWaitingFooters.pollLast();

        long len = getLengthFromHit(parentEvidence, lengthRef);
        if (len <= 0) {
            return null;
        }

        CarverType typeCarved = header.getSignature().getCarverType();
        if ((typeCarved.getMinLength() != null) && (len < typeCarved.getMinLength())) {
            return null;// não carveia
        }
        if ((typeCarved.getMaxLength() != null) && (len > typeCarved.getMaxLength())) {
            return null;// não carveia
        }
        return carveFromHeader(parentEvidence, header, len);
    }

    // carveia do cabeçalho a partir da informação de tamanho do cabeçalho retornada
    // pelo método
    // getLengthFromHit
    @Override
    public IItem carveFromHeader(IItem parentEvidence, Hit header) throws IOException {

        long len = getLengthFromHit(parentEvidence, header);
        if (len <= 0) {
            return null;
        }

        CarverType typeCarved = header.getSignature().getCarverType();
        if ((typeCarved.getMinLength() != null) && (len < typeCarved.getMinLength())) {
            return null;// não carveia
        }
        if ((typeCarved.getMaxLength() != null) && (len > typeCarved.getMaxLength())) {
            return null;// não carveia
        }
        return carveFromHeader(parentEvidence, header, len);
    }

    public IItem carveFromHeader(IItem parentEvidence, Hit header, long len) throws IOException {
        // end of item can't be > end of parent, except for unalloc, possibly fragmented
        if (header.getOffset() + len > parentEvidence.getLength()
                && !MediaTypes.UNALLOCATED.equals(parentEvidence.getMediaType())) {
            len = parentEvidence.getLength() - header.getOffset();
        }
        // verifica a validade dos bytes carveados
        if ((!ignoreCorrupted && !isSpecificIgnoreCorrupted()) || isValid(parentEvidence, header, len)) {
            IItem offsetFile = parentEvidence.createChildItem();

            String name = carvedNamePrefix + header.getOffset();
            offsetFile.setName(name);
            offsetFile.setPath(parentEvidence.getPath() + ">>" + name);

            offsetFile.setLength(len);
            offsetFile.setSumVolume(false);

            offsetFile.setMediaType(header.getSignature().getCarverType().getMimeType());

            long prevOff = parentEvidence.getFileOffset();
            offsetFile.setFileOffset(prevOff == -1 ? header.getOffset() : prevOff + header.getOffset());

            offsetFile.getMetadata().add(ExtraProperties.CARVEDBY_METADATA_NAME, this.getClass().getName());
            offsetFile.getMetadata().add(ExtraProperties.CARVEDOFFSET_METADATA_NAME,
                    Long.toString(offsetFile.getFileOffset()));

            for (Iterator<CarvedItemListener> iterator = carvedItemListeners.iterator(); iterator.hasNext();) {
                CarvedItemListener carvedItemListener = iterator.next();
                carvedItemListener.processCarvedItem(parentEvidence, offsetFile, header.getOffset());
            }

            return offsetFile;
        } else {
            return null;
        }
    }

    // carveia do rodapé iniciando pelo último cabeçalho encontrado de tamanho
    // retornada pelo método getLengthFromHeader
    @Override
    public IItem carveFromFooter(IItem parentEvidence, Hit footer) throws IOException {
        Hit header = headersWaitingFooters.pollLast();

        if (header != null) {
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
        }

        return null;
    }

    public boolean isValid(IItem parentEvidence, Hit headerOffset, long length) {
        try {
            // tenta parsear o conteudo
            validateCarvedObject(parentEvidence, headerOffset, length);

        } catch (InvalidCarvedObjectException e) {
            // se der erro de parse retorna false
            return false;
        }

        // se não der erro de parse retora true, inclusive se não for feito parse algum
        return true;
    }

    public void validateCarvedObject(IItem parentEvidence, Hit header, long length)
            throws InvalidCarvedObjectException {
        // não faz qualquer parse, retornando nenhum objeto parseado.
    }

    @Override
    public CarverType[] getCarverTypes() {
        return carverTypes;
    }

    protected void clearOldHeaders(IItem parentEvidence) {
        while (headersWaitingFooters.size() > maxWaitingHeaders) {
            headersWaitingFooters.pollFirst();
        }
    }

    @Override
    public void notifyHit(IItem parentEvidence, Hit hit) throws IOException {

        // se é um cabeçalho de um carvertype sem footer
        if (hit.getSignature().isHeader() && !hit.getSignature().getCarverType().hasFooter()) {
            if (!hit.getSignature().getCarverType().hasLengthRef()) {
                // carveia a partir da informação de tamanho
                carveFromHeader(parentEvidence, hit);
            } else {
                // adiciona header para ser processado depois
                headersWaitingFooters.addLast(hit);
            }
        }

        if (hit.getSignature().getSignatureType() == SignatureType.LENGTHREF) {
            Hit header = headersWaitingFooters.peekLast();
            // se já foi encontrado um header anterior
            if (header != null) {
                // carveia a partir da informação de tamanho
                carveFromLengthRef(parentEvidence, header, hit);
            }
        }

        // se é um cabeçalho de um carvertype que tem footer
        if (hit.getSignature().isHeader() && hit.getSignature().getCarverType().hasFooter()) {
            // empilha
            headersWaitingFooters.addLast(hit);
            // ignorar header sem footer?
            // ou carvear até tamanho maximo ou até o inicio do header seguinte (o que for
            // menor)?
        }

        if (hit.getSignature().isFooter()) {
            // test if current footer matches a previous found escapeFooter
            if (lastEscapeFooter == null || lastEscapeFooter.getOffset()
                    + lastEscapeFooter.getSignature().getLength() != hit.getOffset() + hit.getSignature().getLength()) {
                carveFromFooter(parentEvidence, hit);
            }
        }
        if (hit.getSignature().getSignatureType().equals(SignatureType.ESCAPEFOOTER)) {
            lastEscapeFooter = hit;
        } else {
            lastEscapeFooter = null;
        }

        clearOldHeaders(parentEvidence);
    }

    @Override
    public void notifyEnd(IItem parentEvidence) throws IOException {
        headersWaitingFooters.clear();
    }

    @Override
    public void registerCarvedItemListener(CarvedItemListener carvedItemListener) {
        carvedItemListeners.add(carvedItemListener);
    }

    @Override
    public void removeCarvedItemListener(CarvedItemListener carvedItemListener) {
        carvedItemListeners.remove(carvedItemListener);
    }

    @Override
    public void setIgnoreCorrupted(boolean ignore) {
        ignoreCorrupted = ignore;
    }

    public boolean isSpecificIgnoreCorrupted() {
        return false;
    }
}
