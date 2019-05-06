package macee.core.forensic;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import macee.core.util.ObjectRef;

/**
 * Defines a data source of forensic evidence.
 *
 * @author werneck.bwph
 */
public interface DataSource extends ObjectRef {

    String OBJECT_REF_TYPE = "DATA_SOURCE";

    @Override default String getRefType() {
        return OBJECT_REF_TYPE;
    }

    String getCaseId();

    Collection<String> getEvidenceItems();

    File getEntryFile();

    default Path getEntryPath() {
        return getEntryFile().toPath();
    }

    default URI getEntryURI() {
        return getEntryFile().toURI();
    }

    default String getEntryPathAsString() {
        return getEntryPath().toString();
    }

    default boolean isRelative() {
        return !getEntryFile().isAbsolute();
    }

    /**
     * If the data source can be moved.
     *
     * @return
     */
    default boolean isVolatile() {
        return true;
    }

    /**
     * If the path is local.
     *
     * @return
     */
    default boolean isLocal() {
        return getEntryPath().isAbsolute();
    }

    /**
     * If the path is read-only.
     *
     * @return
     */
    default boolean isReadOnly() {
        return !getEntryFile().canWrite() && getEntryFile().canRead();
    }

    /**
     * If the path can be deleted and created again if necessary.
     *
     * @return
     */
    default boolean canDelete() {
        return false;
    }

    default boolean isTemporary() {
        return false;
    }

    /**
     * If the data source requires a key to be accessed.
     *
     * @return
     */
    default boolean isProtected() {
        return false;
    }
}
