package net.sf.oereader;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

public class OEData {

	public long length;

	private FileChannel fc;

	private RandomAccessFile raf;
	private MappedByteBuffer buf;
	private boolean mapped;

	public OEData(File file) throws IOException {
		length = file.length();
		raf = new RandomAccessFile(file, "r");
		fc = raf.getChannel();
		try {
			buf = fc.map(MapMode.READ_ONLY, 0, length);
			buf.load();
			mapped = true;

		} catch (Throwable t) {
			mapped = false;
		}

	}

	public byte get(int i) {

		if (i < 0)
			i = 0;
		else if (i >= length)
			i = (int) length - 1;

		if (mapped)
			return buf.get(i);

		else
			try {
				raf.seek(i);
				return raf.readByte();

			} catch (IOException e) {
				return 0;
			}

	}

	public byte[] get(int off, int len) {

		if (off < 0)
			off = 0;
		else if (off >= length)
			off = (int) length - 1;
		if (off + len > length)
			len = (int) length - off;

		byte[] result = new byte[len];

		if (mapped) {
			buf.position(off);
			buf.get(result);
			return result;
		} else
			try {
				raf.seek(off);
				int i, start = 0;
				do {
					i = raf.read(result, start, len - start);
					start += i;
				} while (i != -1 && start < len);

				return result;

			} catch (IOException e) {
				return new byte[0];
			}

	}

	public void close() throws IOException {
		raf.close();
		fc.close();
	}

}
