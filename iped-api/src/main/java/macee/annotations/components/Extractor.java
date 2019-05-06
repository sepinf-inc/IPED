package macee.annotations.components;

import java.lang.annotation.*;

/**
 * A component for extracting information from a file and inserting it into other data source.
 *
 * @author werneck.bwph
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Extractor {

    static final ComponentType TYPE = ComponentType.EXTRACTOR;
}
