package macee.annotations.components;

import java.lang.annotation.*;

/**
 * A parser reads a analyzes an input to extract information. It differs from
 * the extractor, as it doesn't generate new itens in the output, only
 * transforms the inputs.
 *
 * @author werneck.bwph
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Parser {

    static final ComponentType TYPE = ComponentType.PARSER;

    String[] contentType();
}
