package iped.utils.fsw;

import java.io.File;
import java.nio.file.Path;

/*
 * Extension to make the file object return a pathwrapper when toPath is called and
 * keep consistency with fsw package whenever toPath and path toFile are called.
 */

public class FileWrapper extends File {

    public FileWrapper(Path path) {
        super(path.toString());
    }

    @Override
    public Path toPath() {
        return new PathWrapper(super.toPath());
    }

}
