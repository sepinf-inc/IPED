package macee.descriptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) public @interface ModuleDescriptor {

    String guid();

    String name();

    String version() default "1.0";

    String namespace() default "";
}
