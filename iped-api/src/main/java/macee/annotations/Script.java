package macee.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registra um método que insere scripts em uma linguagem que será executada
 * DENTRO da JVM. O alias é usado para registrar o objeto com outro nome. Ex.:
 * um método pode inserir um objeto Searcher dentro de um ambiente Javascript
 * para realizar buscas. Não inclui a execução dinâmica de scripts (ex.: arquivo
 * externo via eval).
 * 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Script {

    String name();

    String alias() default "";

    String language() default "javascript";

    // boolean hasParams() default false;
}
