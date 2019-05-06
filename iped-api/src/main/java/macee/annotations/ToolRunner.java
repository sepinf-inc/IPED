package macee.annotations;

import java.lang.annotation.*;

/**
 * Defines the code responsible for running the specified tool.
 * Um método anotado com ToolRunner é chamado quando a execução de uma ferramenta
 * for invocada. Ele é registrado automaticamente via reflection/package scan.
 *
 * @author bruno
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface ToolRunner {

    /**
     * The name of the tool.
     *
     * @return name of the tool
     */
    String value();
}
