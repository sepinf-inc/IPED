package iped.utils.fsw;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;

public class DirectoryStreamWrapper implements DirectoryStream<Path>{
	DirectoryStream<Path> wrapped;
	
	public DirectoryStreamWrapper(DirectoryStream<Path> wrapped) {
		this.wrapped = wrapped;
	}
	

	@Override
	public void close() throws IOException {
		wrapped.close();
		
	}

	@Override
	public Iterator<Path> iterator() {
		Iterator<Path> it = wrapped.iterator();
		return new Iterator<Path>() {

			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public Path next() {
				return new PathWrapper(it.next());
			}
		};
	}

}
