package macee.annotations.params;

import java.lang.annotation.*;

/**
 * Um parâmetro anotado com ItemTextParam, passa o texto extraído para o método.
 * Ver AttributeParam para um exemplo.
 * 
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER)
public @interface ItemTextParam {

}
