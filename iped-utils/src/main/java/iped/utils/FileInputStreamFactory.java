package iped.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

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
                if (isDirectory(source, file)) {
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

    class FilePathVisitor implements FileVisitor<Path> {
        public File fileToFind;
        public Path foundPath;

        public FilePathVisitor(File fileToFind) {
            this.fileToFind = fileToFind;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            System.out.println("Entering:" + dir);
            if (!fileToFind.getCanonicalPath().startsWith(dir.toFile().getCanonicalPath())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            if (dir.toFile().getCanonicalPath().equals(fileToFind.getCanonicalPath())) {
                foundPath = dir;
                System.out.println("Found:" + dir);
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            System.out.println("Visiting:" + file);
            if (file.toFile().getCanonicalPath().equals(fileToFind.getCanonicalPath())) {
                foundPath = file;
                System.out.println("Found:" + file);
                return FileVisitResult.TERMINATE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }

    boolean isDirectory(Path source, File f) {
        String strpath = f.getAbsolutePath();
        if (strpath.endsWith(" ")) {

            Path startingDir = PathWrapper.create(source);
            Path path = null;
            try {
                FilePathVisitor filePathVisitor = new FilePathVisitor(f);
                System.out.println("Finding:" + f.getCanonicalPath());
                Files.walkFileTree(startingDir, filePathVisitor);
                path = filePathVisitor.foundPath;
            } catch (IOException e2) {
                e2.printStackTrace();
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
