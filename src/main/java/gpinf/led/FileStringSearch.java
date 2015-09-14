package gpinf.led;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import dpf.sp.gpinf.indexer.util.SeekableInputStream;

public class FileStringSearch {
	private SeekableInputStream file;
	private boolean err;
	private SeekableInputStream raf;
	private long len, limit, lastKey = -1;
	private byte[] buf = new byte[1 << 16];
	private char[] charBuf = new char[1 << 16];
	private String[] wordList;
	private boolean[] hashes = new boolean[1 << 18];

	public FileStringSearch(Collection<String> words) {
		wordList = new String[words.size()];
		int wl = 0;
		for (String s : words) {
			wordList[wl] = s;
			if (s.length() < 3) continue;
			int p0 = s.charAt(0);
			int p1 = s.charAt(1);
			int p2 = s.charAt(2);
			int hash = m(m(p0) + p1) + p2;
			hashes[hash] = true;
			wl++;
		}
	}

	public void setLimit(long limit) {
		this.limit = limit;
	}

	public void setFile(SeekableInputStream file) throws IOException {
		this.file = file;
		len = 0;
		lastKey = -1;
		if (file != null) {
			len = file.size();
			if (limit > 0 && len > limit) len = limit;
		}
		if (raf != null) {
			try {
				raf.close();
			} catch (IOException e) {}
		}
		err = false;
		raf = null;
	}

	public Match search(long offset, boolean forward) {
		return search(offset, forward ? (len - 1) : 0, forward);
	}

	public Match search(long from, long to, boolean forward) {
		if (err) return null;
		if (forward) {
			int p1 = getChar(from);
			int p2 = getChar(from + 1);
			for (long i = from + 2; i <= to; i++) {
				int p3 = getChar(i);
				if (err||p3 == Character.MAX_VALUE) break;
				if (hashes[m(m(p1) + p2) + p3]) {
					NEXT: for (int j = 0; j < wordList.length; j++) {
						String s = wordList[j];
						for (int k = 0; k < s.length(); k++) {
							if (s.charAt(k) != getChar(i - 2 + k)) continue NEXT;
						}
						return new Match(i - 2, s.length(), s);
					}
				}
				p1 = p2;
				p2 = p3;
			}
		} else {
			int p2 = getChar(from + 1);
			int p3 = getChar(from + 2);
			for (long i = from; i >= to; i--) {
				int p1 = getChar(i);
				if (err||p1 == Character.MAX_VALUE) break;
				if (hashes[m(m(p1) + p2) + p3]) {
					NEXT: for (int j = 0; j < wordList.length; j++) {
						String s = wordList[j];
						for (int k = 0; k < s.length(); k++) {
							if (s.charAt(k) != getChar(i + k)) continue NEXT;
						}
						return new Match(i, s.length(), s);
					}
				}
				p3 = p2;
				p2 = p1;
			}
		}
		return null;
	}

	private static final int m(int v) {
		return (v << 5) - v;
	}

	private char getChar(long pos) {
		if (pos >= len || pos < 0) return Character.MAX_VALUE;
		try {
			long key = pos >>> 16;
			long off = key << 16;
			if (key != lastKey) {
				if (raf == null) raf = file;
				if (raf.position() != off) raf.seek(off);
				raf.read(buf);
				lastKey = key;
				for (int i = 0; i < buf.length; i++) {
					char c = (char) ((buf[i] + 256) & 255);
					if (c >= 'A' && c <= 'Z') c += 32;
					charBuf[i] = c;
				}
			}
			return charBuf[(int) (pos - off)];
		} catch (Exception e) {
			e.printStackTrace();
			err = true;
		}
		return 0;
	}

	public class Match {
		private final long offset;
		private final int length;
		private final String text;

		public Match(long offset, int length, String text) {
			super();
			this.offset = offset;
			this.length = length;
			this.text = text;
		}

		public long getOffset() {
			return offset;
		}

		public int getLength() {
			return length;
		}

		public String getText() {
			return text;
		}

		public String toString() {
			return "Match [offset=" + offset + ", length=" + length + ", text=" + text + "]";
		}
	}

	public static void main(String[] args) {
		Set<String> searches = new HashSet<String>();
		searches.add("teste");
		searches.add("supersecreto");
		searches.add("windows");
		FileStringSearch fileStringMatcher = new FileStringSearch(searches);
		//fileStringMatcher.setFile(new File("c:/temp/wlad/ipod.001"));
		long t = System.currentTimeMillis();
		System.out.println(fileStringMatcher.search(0, true));
		System.out.println("T=" + (System.currentTimeMillis() - t));
	}
}
