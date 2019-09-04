package macee.annotations.filter;

import java.lang.annotation.*;
import macee.ItemType;

/**
 * Specifies the item types that are accepted by the method, after both filter
 * (category and status) are applied.
 * 
 * COMENTÁRIO (Werneck): anotação que reune as demais. Talvez seja mais elegante
 * fazer as anotações separadas.
 *
 * @author WERNECK
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Requires {

    /**
     * Empty means all item types are accepted.
     *
     * @return
     */
    ItemType[] type() default {};

    CategoryFilter[] category() default {};

    StatusFilter[] status() default {};
}
