package iped.utils.fsw;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

/*
 * This PathWrapper was implemented to overcome problem reported in issues https://github.com/sepinf-inc/IPED/issues/1861.Internal Path windows 
 * implementation (and windows SO itself) restricts folder and file names with trailing spaces.
 * 
 *  This wrapper, with all other installed internal NIO wrappers, overrides important methods what makes Files.walkFileTree work accordingly against 
 *  folder and file names with trailing spaces, with the exception that it cannot access the Attributes of them, returning an Empty BasicAttributes instance. 
 */

public class PathWrapper implements Path {
    Path wrapped;
    FileSystemWrapper fsw;

    public PathWrapper(FileSystemWrapper fs, Path path) {
        this.wrapped = path;
        this.fsw = fs;
    }

    public PathWrapper(Path path) {
        this.wrapped = path;
        this.fsw = new FileSystemWrapper(new FileSystemProviderWrapper(wrapped.getFileSystem().provider()), wrapped.getFileSystem());
    }

    public void forEach(Consumer<? super Path> action) {
        wrapped.forEach(action);
    }

    public Spliterator<Path> spliterator() {
        return wrapped.spliterator();
    }

    public FileSystem getFileSystem() {
        return fsw;
    }

    public boolean isAbsolute() {
        return wrapped.isAbsolute();
    }

    public Path getRoot() {
        return new PathWrapper(fsw, wrapped.getRoot());
    }

    public Path getFileName() {
        return new PathWrapper(fsw, wrapped.getFileName());
    }

    public Path getParent() {
        return new PathWrapper(fsw, wrapped.getParent());
    }

    public int getNameCount() {
        return wrapped.getNameCount();
    }

    public Path getName(int index) {
        return new PathWrapper(fsw, wrapped.getName(index));
    }

    public Path subpath(int beginIndex, int endIndex) {
        return new PathWrapper(fsw, wrapped.subpath(beginIndex, endIndex));
    }

    public boolean startsWith(Path other) {
        return wrapped.startsWith(other);
    }

    public boolean startsWith(String other) {
        return wrapped.startsWith(other);
    }

    public boolean endsWith(Path other) {
        return wrapped.endsWith(other);
    }

    public boolean endsWith(String other) {
        return wrapped.endsWith(other);
    }

    public Path normalize() {
        return new PathWrapper(fsw, wrapped.normalize());
    }

    public Path resolve(Path other) {
        try {
            return new PathWrapper(fsw, wrapped.resolve(other));
        } catch (Exception e) {
            return new PathWrapper(new File(this.toString(), other.toString()).toPath());
        }
    }

    public Path resolve(String other) {
        return new PathWrapper(fsw, wrapped.resolve(other));
    }

    public Path resolveSibling(Path other) {
        return new PathWrapper(fsw, wrapped.resolveSibling(other));
    }

    public Path resolveSibling(String other) {
        return new PathWrapper(fsw, wrapped.resolveSibling(other));
    }

    public Path relativize(Path other) {
        return new PathWrapper(fsw, wrapped.relativize(other));
    }

    public URI toUri() {
        return wrapped.toUri();
    }

    public Path toAbsolutePath() {
        return new PathWrapper(fsw, wrapped.toAbsolutePath());
    }

    public Path toRealPath(LinkOption... options) throws IOException {
        return new PathWrapper(fsw, wrapped.toRealPath(options));
    }

    public File toFile() {
        return new FileWrapper(wrapped);
    }

    public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
        return wrapped.register(watcher, events, modifiers);
    }

    public WatchKey register(WatchService watcher, Kind<?>... events) throws IOException {
        return wrapped.register(watcher, events);
    }

    public Iterator<Path> iterator() {
        Iterator<Path> wi = wrapped.iterator();
        return new Iterator<Path>() {

            @Override
            public boolean hasNext() {
                return wi.hasNext();
            }

            @Override
            public Path next() {
                return new PathWrapper(fsw, wi.next());
            }
        };
    }

    public int compareTo(Path other) {
        return wrapped.compareTo(other);
    }

    public boolean equals(Object other) {
        return wrapped.equals(other);
    }

    public int hashCode() {
        return wrapped.hashCode();
    }

    public String toString() {
        return wrapped.toString();
    }

    public Path getWrappedPath() {
        return wrapped;
    }

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

    public static Path create(Path path) {
        if (IS_WINDOWS) {
            return new PathWrapper(path);
        } else {
            return path;
        }
    }
}
