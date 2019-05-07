package macee.core.forensic;

import java.util.Set;
import java.util.UUID;
import macee.core.util.ObjectRef;

/**
 * Defines a case containing evidence items.
 * 
 * COMENTÁRIO (Werneck): talvez fosse melhor chamar de CaseRef porque é só um
 * conjunto de referências do caso. Não tem informações do caso propriamente.
 *
 * @author werneck.bwph
 */
public interface Case extends ObjectRef {

    String OBJECT_REF_TYPE = "CASE";

    @Override
    default String getRefType() {
        return OBJECT_REF_TYPE;
    }

    void addDataSource(UUID source);

    void removeDataSource(UUID source);

    /**
     * Gets the evidence items associated with the case.
     *
     * @return the evidence associated with the case.
     */
    Set<UUID> getEvidenceItems();

    Set<UUID> getDataSources();

    /**
     * Adds an evidence item to the case.
     *
     * @param item
     *            the evidence item to add.
     */
    void addEvidenceItem(UUID item);

    /**
     * Removes an evidence item from the case.
     *
     * @param item
     *            the item to remove.
     */
    void removeEvidenceItem(UUID item);
}
