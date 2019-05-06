package macee.core.extension;

import java.lang.annotation.*;

/**
 * Anotação que determina se um método é um provedor de extensão. O tipo
 * indica o ponto de extensão. O nome é para identificar a extensão.
 */
@Documented @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface ExtensionProvider {

    String type();

    String name();
}
