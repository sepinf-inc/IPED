package macee.core.forensic;

import macee.core.util.ObjectRef;

/**
 * Defines a piece of evidence.
 *
 * @author werneck.bwph
 */
public interface EvidenceItem extends ObjectRef {

    String OBJECT_REF_TYPE = "EVIDENCE_ITEM";

    /**
     * Gets the case associated with the evidence.
     *
     * @return the case associated with the evidence.
     */
    String getCaseId();

    @Override
    default String getRefType() {
        return OBJECT_REF_TYPE;
    }

    /**
     * Sets the case associated with the evidence.
     *
     * @param cse
     *            the case to set.
     */
    // void setCaseGuid(ObjectRef caseRef);
    //
    // void setCaseGuid(UUID caseGuid);
}
