package macee.annotations.params;

import java.lang.annotation.*;

/**
 * COMENTÁRIO (Werneck): igual a AttributeParam. Foi colocada separadamente
 * inicialmente, mas não vejo necessidade de manter separado.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MetadataParam {

    /**
     * The name of the metadata field.
     *
     * @return the name of the metadata field.
     */
    String value();
}
