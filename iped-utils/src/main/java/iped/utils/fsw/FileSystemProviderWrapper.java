package iped.utils.fsw;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class FileSystemProviderWrapper extends FileSystemProvider{
	
	FileSystemProvider wrapped;
	
	public FileSystemProviderWrapper(FileSystemProvider fsp) {
		this.wrapped = fsp;
	}

	public int hashCode() {
		return wrapped.hashCode();
	}

	public boolean equals(Object obj) {
		return wrapped.equals(obj);
	}

	public String getScheme() {
		return wrapped.getScheme();
	}

	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		return new FileSystemWrapper(this,wrapped.newFileSystem(uri, env));
	}

	public String toString() {
		return wrapped.toString();
	}

	public FileSystem getFileSystem(URI uri) {
		return wrapped.getFileSystem(uri);
	}

	public Path getPath(URI uri) {
		return wrapped.getPath(uri);
	}

	public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
		return wrapped.newFileSystem(path, env);
	}

	public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return wrapped.newInputStream(((PathWrapper) path).getWrappedPath(), options);
	}

	public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        return wrapped.newOutputStream(((PathWrapper) path).getWrappedPath(), options);
	}

	public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
        return wrapped.newFileChannel(((PathWrapper) path).getWrappedPath(), options, attrs);
	}

	public AsynchronousFileChannel newAsynchronousFileChannel(Path path, Set<? extends OpenOption> options,
			ExecutorService executor, FileAttribute<?>... attrs) throws IOException {
        return wrapped.newAsynchronousFileChannel(((PathWrapper) path).getWrappedPath(), options, executor, attrs);
	}

	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
        return wrapped.newByteChannel(((PathWrapper) path).getWrappedPath(), options, attrs);
	}

	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		return new DirectoryStreamWrapper(wrapped.newDirectoryStream(((PathWrapper)dir).getWrappedPath(), filter));
	}

	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		wrapped.createDirectory(((PathWrapper)dir).getWrappedPath(), attrs);
	}

	public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
		wrapped.createSymbolicLink(link, target, attrs);
	}

	public void createLink(Path link, Path existing) throws IOException {
		wrapped.createLink(link, existing);
	}

	public void delete(Path path) throws IOException {
		wrapped.delete(path);
	}

	public boolean deleteIfExists(Path path) throws IOException {
		return wrapped.deleteIfExists(path);
	}

	public Path readSymbolicLink(Path link) throws IOException {
		return wrapped.readSymbolicLink(link);
	}

	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		wrapped.copy(source, target, options);
	}

	public void move(Path source, Path target, CopyOption... options) throws IOException {
		wrapped.move(source, target, options);
	}

	public boolean isSameFile(Path path, Path path2) throws IOException {
		return wrapped.isSameFile(path, path2);
	}

	public boolean isHidden(Path path) throws IOException {
		return wrapped.isHidden(path);
	}

	public FileStore getFileStore(Path path) throws IOException {
		return wrapped.getFileStore(path);
	}

	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		wrapped.checkAccess(path, modes);
	}

	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return wrapped.getFileAttributeView(path, type, options);
	}

	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		A result;
		try {
            if (path.toString().endsWith(" ")) {
                return (A) new FileAttributes((PathWrapper) path);
            }
            result = wrapped.readAttributes(((PathWrapper) path).getWrappedPath(), type, options);
		}catch (Exception e) {
			return (A) new FileAttributes((PathWrapper)path);
		}
		return  result;		
	}

	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return wrapped.readAttributes(path, attributes, options);
	}

	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		if(path.toFile().getAbsolutePath().endsWith(" ")) {
			System.out.println("");
		}
		wrapped.setAttribute(path, attribute, value, options);
	}

}
