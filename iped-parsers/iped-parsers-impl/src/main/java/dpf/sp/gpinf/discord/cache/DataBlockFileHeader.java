package dpf.sp.gpinf.discord.cache;

import java.io.IOException;
import java.io.InputStream;

public class DataBlockFileHeader {

	private static final int BLOCK_HEADER_SIZE = 8192;
	private static final int MAX_BLOCKS = (BLOCK_HEADER_SIZE - 80) * 8;
	private final long signature;
	private final long version;
	private final int fileNumber;
	private final int nextFileNumber;
	private final int blockSize;
	private final int entriesNumber;
	private final int entriesMax;
	private final int updating;
	private final int[] user;
	private final int[] emptyEntries;
	private final int[] hints;
	private final long allocMap[];

	/**
	 * Creation of Data Block as defined in:
	 * https://forensicswiki.xyz/wiki/index.php?title=Chrome_Disk_Cache_Format
	 * 
	 * @param is
	 * @throws IOException
	 */
	public DataBlockFileHeader(InputStream is) throws IOException {

		signature = read4bytes(is);
		version = read4bytes(is);
		fileNumber = read2bytes(is);
		nextFileNumber = read2bytes(is);
		blockSize = read4bytes(is);
		entriesNumber = read4bytes(is);
		entriesMax = read4bytes(is);

		emptyEntries = new int[4];
		emptyEntries[0] = read4bytes(is);
		emptyEntries[1] = read4bytes(is);
		emptyEntries[2] = read4bytes(is);
		emptyEntries[3] = read4bytes(is);

		hints = new int[4];
		hints[0] = read4bytes(is);
		hints[1] = read4bytes(is);
		hints[2] = read4bytes(is);
		hints[3] = read4bytes(is);

		updating = read4bytes(is);

		user = new int[5];
		user[0] = read4bytes(is);
		user[1] = read4bytes(is);
		user[2] = read4bytes(is);
		user[3] = read4bytes(is);
		user[4] = read4bytes(is);

		allocMap = new long[MAX_BLOCKS / 32];
		for (int i = 0; i < allocMap.length; i++) {
			allocMap[i] = read4bytes(is);
		}
	}

	public static int getBlockHeaderSize() {
		return BLOCK_HEADER_SIZE;
	}

	public static int getMaxBlocks() {
		return MAX_BLOCKS;
	}

	public long getSignature() {
		return signature;
	}

	public long getVersion() {
		return version;
	}

	public int getFileNumber() {
		return fileNumber;
	}

	public int getNextFileNumber() {
		return nextFileNumber;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public int getEntriesNumber() {
		return entriesNumber;
	}

	public int getEntriesMax() {
		return entriesMax;
	}

	public int getUpdating() {
		return updating;
	}

	public int[] getUser() {
		return user;
	}

	public int[] getEmptyEntries() {
		return emptyEntries;
	}

	public int[] getHints() {
		return hints;
	}

	public long[] getAllocMap() {
		return allocMap;
	}
	
	private int read2bytes(InputStream is) throws IOException {
		return (is.read() + (is.read() << 8));
	}

	private int read4bytes(InputStream is) throws IOException {
		return (read2bytes(is) + (read2bytes(is) << 16)) & 0xffffffff;
	}
}
