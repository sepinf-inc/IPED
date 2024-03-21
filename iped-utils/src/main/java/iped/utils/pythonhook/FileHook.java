package iped.utils.pythonhook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

public class FileHook {
    FileInputStream fis;
    File f;

    public FileHook(String path, String... args) {
        f = new File(path);
    }

    public FileHook(String path, String mode) {
        f = new File(path);
    }

    public FileHook(String path) {
        this(path, "r");
    }

    public String read() throws FileNotFoundException {
        if (fis == null) {
            fis = new FileInputStream(f);
        }
        return "old";
    }

    public void enter() {
        try {
            System.out.println("Enter:" + f.getCanonicalPath());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void exit(Collection args) {
        try {
            System.out.println("Exit:" + f.getCanonicalPath());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void close() throws IOException {
        if (fis != null) {
            fis.close();
        }

    }

}
