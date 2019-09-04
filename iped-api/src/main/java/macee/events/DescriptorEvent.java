package macee.events;

import macee.descriptor.Descriptor;

public class DescriptorEvent<T extends Descriptor> {

    private final EventType type;
    private final String name;
    private final String source;
    private final T descriptor;

    public DescriptorEvent(String collectionName, EventType type, String source, T descriptor) {
        this.name = collectionName;
        this.type = type;
        this.source = source;
        this.descriptor = descriptor;
    }

    public EventType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getSource() {
        return source;
    }

    public T getDescriptor() {
        return descriptor;
    }

    public static enum EventType {

        MODIFIED, REMOVED, CREATED
    }

}
