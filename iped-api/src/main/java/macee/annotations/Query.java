package macee.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anota um método que é chamado para registrar uma query de um determinado tipo (Lucene, SQL, etc.)
 * Com essa anotação, um módulo pode acrescentar consultas próprias para campos do índice, metadados ou 
 * estruturas próprias. As anotação são identificadas via reflection/package scan e os métodos invocados 
 * no carregamento do módulo.
 */
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) public @interface Query {

    String name();

    String type();
}
