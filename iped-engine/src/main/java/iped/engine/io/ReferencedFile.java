package iped.engine.io;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class ReferencedFile implements Closeable {
    private volatile int numRef = 1;
    private final File file;

    public ReferencedFile(File file) {
        this.file = file;
    }

    public void increment() {
        synchronized (this) {
            numRef++;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (--numRef == 0) {
                file.delete();
            }
        }
    }
}
