package macee.annotations.components;

import java.lang.annotation.*;

/**
 * An annotator is a component that adds predicates or properties to an item.
 *
 * @author WERNECK
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Annotator {

    static final ComponentType TYPE = ComponentType.ANNOTATOR;

    /**
     * @return the names of the predicates associated with this annotator.
     */
    String[] predicates() default {};

    /**
     * @return the names of the properties associated with this annotator.
     */
    String[] properties() default {};

}
