package macee.components;

import java.util.Comparator;
import java.util.Objects;
import macee.CaseItem;

public class SimpleItemRef implements ItemRef {

    private final int id;
    private final String sourceId;
    private final String caseId;

    public SimpleItemRef(int id, String sourceId, String caseId) {
        this.id = id;
        this.sourceId = sourceId;
        this.caseId = caseId;
    }

    public static ItemRef create(CaseItem item) {
        if (item == null) {
            return null;
        }
        return new SimpleItemRef(item.getId(), item.getDataSourceId(), item.getCaseId());
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public String getCaseId() {
        return caseId;
    }

    @Override
    public String toString() {
        return caseId + '/' + sourceId + "/" + id;
    }

    @Override
    public int compareTo(ItemRef other) {
        return Comparator.comparing(ItemRef::getCaseId).thenComparing(ItemRef::getSourceId)
                .thenComparingInt(ItemRef::getId).compare(this, other);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + this.id;
        hash = 19 * hash + Objects.hashCode(this.sourceId);
        hash = 19 * hash + Objects.hashCode(this.caseId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SimpleItemRef other = (SimpleItemRef) obj;
        return this.toString().equals(other.toString());
    }
}
