package macee.annotations.filter;

import java.lang.annotation.*;

@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface StatusFilter {

    FilterOperation filter() default FilterOperation.INCLUDE_ONLY;

    ItemStatus[] status() default {ItemStatus.ACTIVE};
}
