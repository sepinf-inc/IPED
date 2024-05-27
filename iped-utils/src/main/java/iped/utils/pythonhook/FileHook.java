package iped.utils.pythonhook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

public class FileHook {
    FileInputStream fis;
    File f;
    String encoding;

    public FileHook(String path, String... args) {
        f = new File(path);
    }

    public FileHook(String path, String mode, String encoding) {
        f = new File(path);
        encoding = encoding;
    }

    public FileHook(String path, String mode) {
        f = new File(path);
    }

    public FileHook(String path) {
        this(path, "r");
    }

    public FileInputStream getFileInputStream() throws FileNotFoundException {
        if (fis == null) {
            fis = new FileInputStream(f);
        }
        return fis;
    }

    public byte[] read() throws IOException {
        return getFileInputStream().readAllBytes();
    }

    public byte[] read(int i) throws IOException {
        return getFileInputStream().readNBytes(i);
    }

    public void enter() {
        try {
            fis = null;
            System.out.println("Enter:" + f.getCanonicalPath());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void exit(Collection args) {
        try {
            if (fis != null) {
                fis.close();
                fis = null;
            }
            System.out.println("Exit:" + f.getCanonicalPath());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void write() {
        // do not write
    }

    public void close() throws IOException {
        if (fis != null) {
            fis.close();
        }

    }

}
