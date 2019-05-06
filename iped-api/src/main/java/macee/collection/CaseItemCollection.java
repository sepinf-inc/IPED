package macee.collection;

import macee.CaseItem;
import macee.core.util.ObjectRef;
import macee.descriptor.Descriptor;

/**
 * Representa um coleção de itens do caso. Seria o equivalente aos Bookmarks,
 * mas é usado para outros propósitos (arquivos ignorados, resultado de busca,
 * linhas temporais, etc.)
 * 
 * InReport: se será incluído no relatório
 * Ignored: se foi ignorado
 * 
 * Recommendation é uma indicação da relevância da coleção.
 * 
 * Veja Descriptor para outro campos.
 * 
 */
public interface CaseItemCollection extends ObjectRef, BitSetCollection, Descriptor {

    String getComment();

    void setComment(String aComment);

    Recommendation getRecommendation();

    void setRecommendation(final Recommendation recommendation);

    boolean isIgnored();

    void setIgnored(final boolean ignored);

    boolean isInReport();

    void setInReport(final boolean inReport);

    default void remove(CaseItem ci) {
        remove(ci.getDataSourceId(), ci.getId());
    }

    default boolean add(CaseItem ci) {
        return add(ci.getDataSourceId(), ci.getId());
    }
}
