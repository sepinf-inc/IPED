package macee.annotations.components;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Carver {

    static final ComponentType TYPE = ComponentType.CARVER;

    /**
     * @return the method used to perform data carving
     */
    CarvingMethod[] method() default { CarvingMethod.HEADER_FOOTER };

    /**
     * The minimum number of bytes required by the method. Default (-1) provides all
     * available bytes to the carver.
     *
     * @return the minimum number of bytes required by the carver
     */
    int requiredBytes() default -1;
}
