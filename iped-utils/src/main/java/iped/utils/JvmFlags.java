package iped.utils;

import java.lang.module.ModuleFinder;
import java.util.List;

/**
 * JVM flags IPED and its libraries need on Java 25, shared by every JVM IPED
 * starts (processing/analysis child process, graph import...). Keep in sync
 * with iped.jvm.flags in the root pom.xml, used by the tests.
 */
public final class JvmFlags {

    /**
     * JavaFX modules that load native libraries. They come from the Java runtime
     * (JDK/JRE with JavaFX bundled), so they are named modules, not covered by
     * ALL-UNNAMED.
     */
    private static final List<String> JAVAFX_NATIVE_MODULES = List.of( //
            "javafx.graphics", "javafx.web", "javafx.media");

    /**
     * Native access (JEP 472) for JNI/JNA users: sleuthkit, sqlite, jep,
     * javacpp... and for JavaFX, and sun.misc.Unsafe memory access (JEP 498) for
     * neo4j, lucene, kryo...
     */
    public static final List<String> NATIVE_AND_UNSAFE = List.of( //
            nativeAccess(),
            "--sun-misc-unsafe-memory-access=allow");

    /**
     * Deep reflection into JDK internals by serialization libraries, neo4j
     * (java.nio.DirectByteBuffer), etc.
     */
    public static final List<String> ADD_OPENS = List.of( //
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.math=ALL-UNNAMED",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");

    /**
     * Incubator module with the Vector API, used by Lucene for its vectorized
     * (SIMD) code. Without it Lucene falls back to slower scalar code.
     */
    public static final String VECTOR_MODULE = "jdk.incubator.vector";

    private JvmFlags() {
    }

    /**
     * Native access for the code in the classpath and for the JavaFX modules this
     * Java runtime has: the JVM warns about modules it does not know.
     */
    private static String nativeAccess() {
        StringBuilder flag = new StringBuilder("--enable-native-access=ALL-UNNAMED");
        for (String module : JAVAFX_NATIVE_MODULES) {
            if (ModuleFinder.ofSystem().find(module).isPresent()) {
                flag.append(',').append(module);
            }
        }
        return flag.toString();
    }

    /**
     * Flag that enables the Vector API, or no flag if this Java runtime does not
     * have the module (e.g. a reduced JRE): the JVM refuses to start when asked
     * to add a module it does not have.
     */
    public static List<String> vectorApi() {
        if (ModuleFinder.ofSystem().find(VECTOR_MODULE).isPresent()) {
            return List.of("--add-modules=" + VECTOR_MODULE);
        }
        return List.of();
    }

    /** All the flags above. */
    public static List<String> all() {
        List<String> flags = new java.util.ArrayList<>(NATIVE_AND_UNSAFE);
        flags.addAll(ADD_OPENS);
        flags.addAll(vectorApi());
        return flags;
    }
}
