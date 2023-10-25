package iped.utils.fsw;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Set;

public class FileSystemWrapper extends FileSystem {
    FileSystem wrapped;
    FileSystemProviderWrapper fspw;

    public FileSystemWrapper(FileSystemProviderWrapper fileSystemProviderWrapper, FileSystem newFileSystem) {
        this.fspw = fileSystemProviderWrapper;
        this.wrapped = newFileSystem;
    }

    public int hashCode() {
        return wrapped.hashCode();
    }

    public boolean equals(Object obj) {
        return wrapped.equals(obj);
    }

    public FileSystemProvider provider() {
        return fspw;
    }

    public void close() throws IOException {
        wrapped.close();
    }

    public boolean isOpen() {
        return wrapped.isOpen();
    }

    public boolean isReadOnly() {
        return wrapped.isReadOnly();
    }

    public String getSeparator() {
        return wrapped.getSeparator();
    }

    public Iterable<Path> getRootDirectories() {
        Iterable<Path> i = wrapped.getRootDirectories();
        FileSystemWrapper self = this;
        return new Iterable<Path>() {
            @Override
            public Iterator<Path> iterator() {
                Iterator<Path> it = wrapped.getRootDirectories().iterator();
                return new Iterator<Path>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Path next() {
                        return new PathWrapper(self, it.next());
                    }
                };
            }
        };
    }

    public Iterable<FileStore> getFileStores() {
        return wrapped.getFileStores();
    }

    public String toString() {
        return wrapped.toString();
    }

    public Set<String> supportedFileAttributeViews() {
        return wrapped.supportedFileAttributeViews();
    }

    public Path getPath(String first, String... more) {
        return wrapped.getPath(first, more);
    }

    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return wrapped.getPathMatcher(syntaxAndPattern);
    }

    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return wrapped.getUserPrincipalLookupService();
    }

    public WatchService newWatchService() throws IOException {
        return wrapped.newWatchService();
    }

}
