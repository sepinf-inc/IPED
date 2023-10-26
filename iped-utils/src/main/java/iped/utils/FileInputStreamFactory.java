package iped.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import iped.io.SeekableInputStream;
import iped.utils.fsw.PathWrapper;

public class FileInputStreamFactory extends SeekableInputStreamFactory {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

    public FileInputStreamFactory(Path dataSource) {
        super(dataSource.toUri());
    }

    public Path getPath(String subPath) {
        Path source = Paths.get(this.dataSource);
        try {
            return source.resolve(subPath);

        } catch (InvalidPathException e) {
            File file = new File(subPath);
            if (!file.isAbsolute()) {
                file = new File(source.toFile(), subPath);
            }
            if (IS_WINDOWS) {
                // workaround for https://github.com/sepinf-inc/IPED/issues/1861
                if (isDirectory(file)) {
                    try {
                        file = Files.createTempDirectory("iped").toFile();
                        file.deleteOnExit();
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                } else {
                    File f = new File("\\\\?\\" + file.getAbsolutePath());
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
                        file = File.createTempFile("iped", ".tmp");
                        file.deleteOnExit();
                        Files.copy(bis, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                }
            }
            return file.toPath();
        }
    }

    boolean isDirectory(File f) {
        String strpath = f.getAbsolutePath();
        if (strpath.endsWith(" ")) {
            Path parent = new PathWrapper(f.getParentFile().toPath());
            Path path = null;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(parent)) {
                for (Path child : ds) {
                    if (child.getFileName().toString().equals(f.getName())) {
                        path = child;
                        break;
                    }
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }

            if (path == null) {
                return false;
            }

            try (DirectoryStream ds = Files.newDirectoryStream(path)) {
            } catch (NotDirectoryException ioe) {
                return false;
            } catch (IOException e) {
                return new File(strpath).isDirectory();
            }
            return true;
        } else {
            return new File(strpath).isDirectory();
        }
    }

    @Override
    public SeekableInputStream getSeekableInputStream(String subPath) throws IOException {
        File file = getPath(subPath).toFile();
        if (file.isFile())
            return new SeekableFileInputStream(file);
        else
            return new EmptyInputStream();
    }

}
