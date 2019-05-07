package dpf.sp.gpinf.carver.api;

import java.io.IOException;

import iped3.Item;

public interface Carver {

    public void notifyHit(Item parentEvidence, Hit hit) throws IOException;

    public void notifyEnd(Item parentEvidence) throws IOException;

    // Métodos principais
    // faz um carve a partir do hit do cabecalho
    public Item carveFromHeader(Item parentEvidence, Hit header) throws IOException;

    // faz um carve a partir do hit do cabecalho e do footer
    public Item carveFromFooter(Item parentEvidence, Hit footer) throws IOException;

    // descobre o tamanho a partir do cabecalho
    public long getLengthFromHit(Item parentEvidence, Hit headerOffset) throws IOException;

    // retorna carver types típicos para a classe sendo implementada
    public CarverType[] getCarverTypes();

    public void registerCarvedItemListener(CarvedItemListener carvedItemListener);

    public void removeCarvedItemListener(CarvedItemListener carvedItemListener);

    public void setIgnoreCorrupted(boolean ignore);
}
