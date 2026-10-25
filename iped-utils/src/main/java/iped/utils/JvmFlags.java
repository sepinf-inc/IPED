package iped.utils;

import java.util.List;

/**
 * JVM flags IPED and its libraries need on Java 25, shared by every JVM IPED
 * starts (processing/analysis child process, graph import...). Keep in sync
 * with iped.jvm.flags in the root pom.xml, used by the tests.
 */
public final class JvmFlags {

    /**
     * Native access (JEP 472) for JNI/JNA users: sleuthkit, sqlite, jep,
     * javacpp... and sun.misc.Unsafe memory access (JEP 498) for neo4j, lucene,
     * kryo...
     */
    public static final List<String> NATIVE_AND_UNSAFE = List.of( //
            "--enable-native-access=ALL-UNNAMED",
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

    private JvmFlags() {
    }

    /** All the flags above. */
    public static List<String> all() {
        List<String> flags = new java.util.ArrayList<>(NATIVE_AND_UNSAFE);
        flags.addAll(ADD_OPENS);
        return flags;
    }
}
