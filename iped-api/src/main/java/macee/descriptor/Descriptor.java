package macee.descriptor;

import java.io.Serializable;
import macee.core.util.ObjectRef;

public interface Descriptor extends ObjectRef, Serializable, Comparable<Descriptor> {

    boolean isShared();

    void setShared(boolean value);

    boolean isPublic();

    void setPublic(boolean value);

    boolean isReadOnly();

    void setReadOnly(boolean value);

    String getOwner();

    void setOwner(String owner);

    DescriptorType getDescriptorType();

    void setDescriptorType(CoreTypes type);

    void setDescriptorType(DescriptorType type);

    String toJson();

    @Override
    default String guid() {
        return getId().toString();
    }

    boolean isRemote();

    void setRemote(boolean value);

    String getLastUpdate();

    void setLastUpdate(String update);

    int getVersion();

    void setVersion(int version);

    void incrementVersion();

    void touch();
}
