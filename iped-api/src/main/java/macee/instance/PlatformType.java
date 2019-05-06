package macee.instance;

import java.util.UUID;

/**
 * Enumerates the types of platforms in the MACEE environment.
 *
 * @author Bruno W. P. Hoelz
 */
public enum PlatformType {

    CLIENT("8536db7b-027a-4e01-9075-af876bb2642c"), WORKER(
        "5f0b9ed7-5be2-4b82-9e17-64644babbc80"), SERVER("cbc80aff-20d6-4918-9570-7fa3bafd13ea");

    private final UUID guid;

    PlatformType(final String guid) {
        this.guid = UUID.fromString(guid);
    }

    public UUID guid() {
        return this.guid;
    }
}
