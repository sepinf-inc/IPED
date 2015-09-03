package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class FilterOutputStream extends OutputStream{
	
	private OutputStream redirectStream, defaultStream;
	private String redirectPrefix;

	public FilterOutputStream(OutputStream defaultStream, OutputStream redirectStream, String redirectPrefix) {
		this.redirectStream = redirectStream;
		this.defaultStream = defaultStream;
		this.redirectPrefix = redirectPrefix;
	}
	
	@Override
	public void write(byte[] buf, int off, int len) throws IOException {
		String msg = new String(buf, off, len);
		if(msg.startsWith(redirectPrefix))
			redirectStream.write(buf, off, len);
		else
			defaultStream.write(buf, off, len);
	}

	@Override
	public void write(int b) throws IOException {
		defaultStream.write(b);
	}
	
	@Override
	public void close() throws IOException {
		//redirectStream.close();
		defaultStream.close();
	}

}
