package gpinf.util;

import java.io.IOException;
import java.io.InputStream;

public class AlwaysEmptyInputStream extends InputStream {

	@Override
	public int read() throws IOException {
		return -1;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		return -1;
	}

	@Override
	public int available() throws IOException {
		return 0;
	}

}
