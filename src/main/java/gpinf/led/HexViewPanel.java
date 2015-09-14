package gpinf.led;

import gpinf.led.FileStringSearch.Match;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.swing.JPanel;

import dpf.sp.gpinf.indexer.util.SeekableInputStream;
import sun.swing.SwingUtilities2;

public class HexViewPanel extends JPanel implements MouseListener {
	private static final long serialVersionUID = 7851192102711498776L;
	private static final Font font = new Font("Courier New", Font.PLAIN, 12);
	private int lines, height, width;
	private final int cols;
	private long len;
	private SeekableInputStream file;
	private boolean err;
	private SeekableInputStream raf;
	private Map<Integer, int[]> memoBytes = new HashMap<Integer, int[]>();
	private byte[] readBuf = new byte[1 << 12];
	private LinkedList<Integer> fifo = new LinkedList<Integer>();
	private FileStringSearch fileStringSearch;

	public HexViewPanel(int cols) {
		this.cols = cols;
		setBackground(Color.WHITE);
		setOpaque(true);
		setFocusable(true);
		addMouseListener(this);
		SwingUtilities2.getFontMetrics(null, font); //Adicionado para evitar o LAG na primeira execu��o desta chamada
	}

	public void setHighlightWords(Collection<String> words) throws IOException {
		if (fileStringSearch != null) fileStringSearch.setFile(null);
		fileStringSearch = new FileStringSearch(words);
		fileStringSearch.setLimit(Integer.MAX_VALUE - readBuf.length);
	}

	public void setFile(SeekableInputStream file) throws IOException {
		FontMetrics fontMetrics = getFontMetrics(font);
		width = fontMetrics.stringWidth("0");
		height = fontMetrics.getHeight() + 2;

		this.file = file;
		len = 0;
		lines = 0;
		if (file != null) {
			len = file.size();
			//if (len > Integer.MAX_VALUE - readBuf.length) len = Integer.MAX_VALUE - readBuf.length; //Limite de 2GB
			lines = (int) ((len + cols - 1) / cols);
		}
		if (fileStringSearch != null) {
			fileStringSearch.setFile(file);
		}
		err = false;
		memoBytes.clear();
		if (raf != null) {
			try {
				raf.close();
			} catch (IOException e) {}
		}
		raf = null;
		setPreferredSize(new Dimension(width * (cols * 4 + 8) + 32, lines * height + 4));
		revalidate();
	}

	public void scrollToPosition(long from, long to) {
		for (int i = 0; i < 100 && !isValid(); i++) {
			try {
				Thread.sleep(5);
			} catch (Exception e) {}
		}
		int x1 = (int) (from % cols * 3 * width);
		int y1 = (int) (from / cols * height);
		int x2 = (int) (to % cols * 3 * width);
		int y2 = (int) (to / cols * height);
		scrollRectToVisible(new Rectangle(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x1 - x2) + 3 * width, Math.abs(y1 - y2) + height));
		repaint();
	}

	public void paint(Graphics g) {
		super.paint(g);
		if (file != null) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setFont(font);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			Rectangle rc = getVisibleRect();
			if (!err) {
				int r1 = rc.y / height - 1;
				if (r1 < 0) r1 = 0;
				int r2 = (rc.y + rc.height) / height + 1;
				if (r2 >= lines) r2 = lines - 1;
				g2.setColor(Color.GRAY);
				for (int i = r1; i <= r2; i++) {
					g2.drawString(String.format("%08x", i * cols), 2, (i + 1) * height - 4);
				}
				int x = width * 8 + 8;
				int x2 = x + width * 3 * cols - width + 16;
				g2.drawLine(x, rc.y, x, rc.y + rc.height);
				g2.drawLine(x2, rc.y, x2, rc.y + rc.height);

				x += 8;
				x2 += 8;
				g2.setColor(Color.BLACK);
				OUT: for (int i = r1; i <= r2; i++) {
					int y = (i + 1) * height - 4;
					for (int j = 0; j < cols; j++) {
						int b = getByte(i * cols + j);
						if (err) break OUT;
						if (b >= 0) {
							if ((b & 1024) != 0) {
								b &= 255;
								g2.setColor(Color.YELLOW);
								g2.fillRect(x + width * 3 * j - 4, y + 6 - height, 2 * width + 8, height - 1);
								g2.fillRect(x2 + width * j, y + 6 - height, width, height - 1);
								g2.setColor(Color.BLACK);
							}
							g2.drawString(String.format("%02x", b), x + width * 3 * j, y);
							g2.drawString((b < 32 || b > 127) ? "." : String.valueOf((char) b), x2 + width * j, y);
						}
					}
				}
			}
			if (err) {
				g2.setColor(Color.WHITE);
				g2.fill(rc);
				g2.setColor(Color.RED);
				g2.drawString("Erro acessando arquivo!!!", 0, rc.y + rc.height / 2 - height);
				//g2.drawString(file.getAbsolutePath(), 0, rc.y + rc.height / 2 + height);
			}
		}
	}

	private int getByte(long pos) {
		if (pos >= len) return -1;
		try {
			int key = (int) pos >>> 12;
			long off = ((long)key) << 12;
			int[] buf = memoBytes.get(key);
			if (buf == null) {
				if (raf == null) {
					raf = file;
				}
				raf.seek(off);
				buf = new int[readBuf.length];
				int n = raf.read(readBuf);
				for (int i = 0; i < n; i++) {
					buf[i] = (readBuf[i] + 256) & 255;
				}
				memoBytes.put(key, buf);
				fifo.addLast(key);

				if (fileStringSearch != null) {
					long from = off;
					long to = from + buf.length;
					while (true) {
						Match match = fileStringSearch.search(from, to, true);
						if (match == null) break;
						from = match.getOffset() + 1;
						int a = (int) (match.getOffset() - off);
						int b = (int) (match.getOffset() + match.getLength() - off);
						for (int i = a; i < b; i++) {
							buf[i] |= 1024;
						}
					}
				}

				if (fifo.size() > 1000) {
					int oldest = fifo.removeFirst();
					memoBytes.remove(oldest);
				}
			}
			return buf[(int)(pos - off)];
		} catch (Exception e) {
			e.printStackTrace();
			err = true;
		}
		return 0;
	}

	public void mouseClicked(MouseEvent e) {
		requestFocusInWindow();
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}
}
