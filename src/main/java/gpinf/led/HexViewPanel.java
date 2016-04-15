package gpinf.led;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JScrollBar;

import dpf.sp.gpinf.indexer.util.SeekableInputStream;

public class HexViewPanel extends JPanel {

  private static final long serialVersionUID = -1432882274158762790L;
  private static final int MAX_MEMO = 2000;
  private int height, width, offsetCols;
  private int cols;
  private static final Color darkBlue = new Color(0, 0, 192);
  private static final Color selColor1 = new Color(215, 225, 250);
  private static final Color selColor2 = new Color(240, 240, 245);
  private long len, rows;
  private SeekableInputStream file;
  private boolean err;
  private String offsetFormat;
  private Map<Long, int[]> memoBytes = new HashMap<Long, int[]>();
  private byte[] readBuf = new byte[1 << 12];
  private LinkedList<Long> fifo = new LinkedList<Long>();
  private LongScrollBar hsb, vsb;
  private JPanel corner, main;
  private int selMode;
  private long selStart = -1, selEnd = -1;
  private boolean isDragging;

  public HexViewPanel(int numCols) {
    this.cols = numCols;

    setLayout(new BorderLayout());
    hsb = new LongScrollBar(JScrollBar.HORIZONTAL);
    vsb = new LongScrollBar(JScrollBar.VERTICAL);

    add(vsb, BorderLayout.EAST);
    corner = new JPanel();
    JPanel aux = new JPanel(new BorderLayout());
    aux.add(corner, BorderLayout.EAST);
    aux.add(hsb, BorderLayout.CENTER);
    add(aux, BorderLayout.SOUTH);

    corner.setPreferredSize(new Dimension(15, 15));

    main = new JPanel() {
      private static final long serialVersionUID = -8682174150958743306L;

      public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        Rectangle rc = getBounds();

        if (file != null) {
          long dx = -hsb.getLongValue();
          if (!err) {
            long r1 = vsb.getLongValue();
            if (r1 < 0) {
              r1 = 0;
            }
            g2.setColor(darkBlue);
            for (long i = r1; i * cols < len; i++) {
              int y = (int) ((i - r1 + 1) * height - 4);
              g2.drawString(String.format(offsetFormat, i * cols), 2 + dx, y);
              if (y > rc.height) {
                break;
              }
            }
            g2.setColor(Color.LIGHT_GRAY);
            long x = width * offsetCols + 8 + dx;
            long x2 = x + width * 3 * cols - width + 16;
            g2.drawLine((int) x, 0, (int) x, rc.height);
            g2.drawLine((int) x2, 0, (int) x2, rc.height);

            x += 8;
            x2 += 8;
            g2.setColor(Color.BLACK);
            OUT:
            for (long i = r1; i * cols < len; i++) {
              int y = (int) (i - r1 + 1) * height - 4;
              for (int j = 0; j < cols; j++) {
                long idx = i * cols + j;
                int b = getByte(idx);
                if (err) {
                  break OUT;
                }
                if (b >= 0) {
                  if (idx >= Math.min(selStart, selEnd) && idx <= Math.max(selStart, selEnd)) {
                    g2.setColor(selMode == 1 ? selColor1 : selColor2);
                    g2.fillRect((int) (x + width * 3 * j - 4), y + 6 - height, 3 * width, height);
                    g2.setColor(selMode == 2 ? selColor1 : selColor2);
                    g2.fillRect((int) (x2 + width * j), y + 6 - height, width, height);
                    g2.setColor(Color.BLACK);
                  }
                  g2.drawString(String.format("%02x", b), x + width * 3 * j, y);
                  g2.drawString((b < 32 || b > 127) ? "." : String.valueOf((char) b), x2 + width * j, y);
                }
              }
              if (y > rc.height) {
                break;
              }
            }
          }
          if (err) {
            g2.setColor(Color.WHITE);
            g2.fill(rc);
            g2.setColor(Color.RED);
            g2.drawString("Erro acessando arquivo!!!", 0, rc.y + rc.height / 2 - height);
            g2.drawString(file.toString(), 0, rc.y + rc.height / 2 + height);
          }
        }
        g2.setColor(Color.GRAY);
        g2.draw(new Rectangle(0, 0, rc.width - 1, rc.height - 1));
      }
    };
    add(main, BorderLayout.CENTER);

    main.addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        updateScrollsBars();
      }
    });

    setFont(new Font("Monospaced", Font.PLAIN, 12));
    setBackground(Color.WHITE);
    main.setBackground(Color.WHITE);
    setOpaque(true);
    main.setOpaque(true);
    setFocusable(true);
    main.setFocusable(true);

    main.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseDragged(MouseEvent e) {
        if (!isDragging) {
          isDragging = true;
          long[] sel = translateToFileOffset(e.getX(), e.getY(), 0);
          if (sel != null) {
            selStart = selEnd = sel[0];
            selMode = (int) sel[1];
            main.repaint();
          } else {
            selStart = selEnd = -1;
            selMode = 0;
            main.repaint();
          }
        } else if (selStart >= 0) {
          long[] sel = translateToFileOffset(e.getX(), e.getY(), selMode);
          if (sel != null) {
            selEnd = sel[0];
            if (sel[2] > 0 && selEnd < selStart) {
              selEnd++;
            }
            if (sel[3] != 0) {
              vsb.setLongValue(vsb.getLongValue() + sel[3]);
            }
            main.repaint();
          }
        }
      }
    });

    main.addMouseListener(new MouseAdapter() {
      public void mouseReleased(MouseEvent e) {
        main.requestFocusInWindow();
        if (isDragging) {
          isDragging = false;
        }
      }
    });

    addMouseWheelListener(new MouseWheelListener() {
      public void mouseWheelMoved(MouseWheelEvent e) {
        vsb.setLongValue(vsb.getLongValue() + e.getWheelRotation());
      }
    });

    main.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if ((e.getKeyCode() == KeyEvent.VK_C) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
          if (selStart >= 0) {
            StringBuilder sb = new StringBuilder();
            long a = Math.min(selStart, selEnd);
            long b = Math.max(selStart, selEnd);
            if (selMode == 1) {
              for (long i = a; i <= b; i++) {
                sb.append(String.format("%02x", getByte(i)));
              }
            } else {
              for (long i = a; i <= b; i++) {
                int c = getByte(i);
                sb.append(c < 32 || c > 127 ? '.' : (char) c);
              }
            }
            StringSelection selection = new StringSelection(sb.toString());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
          }
        } else if (e.getKeyCode() == KeyEvent.VK_UP) {
          vsb.setLongValue(vsb.getLongValue() - 1);
        } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          vsb.setLongValue(vsb.getLongValue() + 1);
        } else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
          vsb.setLongValue(vsb.getLongValue() - main.getBounds().height / height);
        } else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
          vsb.setLongValue(vsb.getLongValue() + main.getBounds().height / height);
        } else if ((e.getKeyCode() == KeyEvent.VK_LEFT) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
          if (cols > 8) {
            cols -= 8;
            updateRows();
            adjustSize();
          }
        } else if ((e.getKeyCode() == KeyEvent.VK_RIGHT) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
          if (cols < 64) {
            cols += 8;
            updateRows();
            adjustSize();
          }
        }
      }
    });
  }

  private long[] translateToFileOffset(int x, int y, int mode) {
    long r1 = vsb.getLongValue();
    if (r1 < 0) {
      r1 = 0;
    }
    long off = 0;
    int yOver = 0;
    for (long i = r1 - 1;; i++) {
      int fy = (int) ((i - r1 + 1) * height - 4);
      if (fy >= y) {
        off = i * cols;
        if (i < r1) {
          yOver = -1;
        } else if (i - r1 > main.getBounds().height / height) {
          yOver = 1;
        }
        break;
      }
    }
    long dx = -hsb.getLongValue();
    long fx1 = width * offsetCols + 8 + dx;
    long fx2 = fx1 + width * 3 * cols - width + 16;

    if (mode == 0 && x <= fx1) {
      return null;
    }
    boolean xOver = false;
    if (mode == 1 || (mode == 0 && x < fx2)) {
      long c = (x - fx1 - 4) / (width * 3);
      if (c < 0) {
        xOver = true;
        c = -1;
      } else if (c > cols - 1) {
        xOver = true;
        c = cols - 1;
      }
      off += c;
      return new long[]{off, 1, xOver ? 1 : 0, yOver};
    }
    if (mode == 2 || (mode == 0 && x >= fx2)) {
      long c = (x - fx2 - 4) / width;
      if (c < 0) {
        xOver = true;
        c = -1;
      } else if (c > cols - 1) {
        xOver = true;
        c = cols - 1;
      }
      off += c;
      return new long[]{off, 2, xOver ? 1 : 0, yOver};
    }
    return null;
  }

  public SeekableInputStream getFile() {
    return file;
  }

  public void setFile(SeekableInputStream file) throws IOException {
    if (file != null && file.equals(this.file)) {
      return;
    }
    if (this.file != null) {
      try {
        this.file.close();
      } catch (IOException e) {
      }
      this.file = null;
    }

    this.file = file;
    selStart = selEnd = -1;
    selMode = 0;
    len = 0;
    rows = 0;
    if (file != null) {
      len = file.size();
      updateRows();
      offsetCols = Long.toHexString(len - 1).length();
      offsetFormat = "%0" + offsetCols + "x";
    }
    err = false;
    memoBytes.clear();
    adjustSize();
    hsb.setValue(0);
    vsb.setValue(0);

    hsb.addAdjustmentListener(new AdjustmentListener() {
      public void adjustmentValueChanged(AdjustmentEvent e) {
        main.repaint();
      }
    });
    vsb.addAdjustmentListener(new AdjustmentListener() {
      public void adjustmentValueChanged(AdjustmentEvent e) {
        main.repaint();
      }
    });
  }

  private void updateRows() {
    rows = (len + cols - 1) / cols;
  }

  private void adjustSize() {
    updateScrollsBars();
    revalidate();
    repaint();
  }

  private void updateScrollsBars() {
    if (main != null) {
      Rectangle rc = main.getBounds();

      long vc = (offsetCols + 4 + cols * 4) * width - rc.width;
      hsb.setVisible(vc > 0);
      hsb.setRange(vc);
      if (!hsb.isVisible()) {
        hsb.setValue(0);
      }
      corner.setVisible(hsb.isVisible());

      long vr = rows - rc.height / height;
      vsb.setVisible(vr > 0);
      vsb.setRange(vr);
      if (!vsb.isVisible()) {
        vsb.setValue(0);
      }
    }
  }

  public void setFont(Font font) {
    super.setFont(font);
    if (main != null) {
      main.setFont(font);
    }
    FontMetrics fontMetrics = getFontMetrics(getFont());
    width = fontMetrics.stringWidth("0");
    height = fontMetrics.getHeight() + 1;
    adjustSize();
  }

  private int getByte(long pos) {
    long vpos = pos;
    if (vpos >= len || vpos < 0) {
      return -1;
    }
    try {
      long key = vpos >>> 12;
      long off = key << 12;
      int[] buf = memoBytes.get(key);
      if (buf == null) {
        file.seek(off);
        buf = new int[readBuf.length];
        int n = file.read(readBuf);
        for (int i = 0; i < n; i++) {
          buf[i] = readBuf[i] & 255;
        }
        memoBytes.put(key, buf);
        fifo.addLast(key);
        if (fifo.size() > MAX_MEMO) {
          memoBytes.remove(fifo.removeFirst());
        }
      }
      int bpos = (int) (vpos - off);
      return buf[bpos];
    } catch (Exception e) {
      e.printStackTrace();
      err = true;
    }
    return 0;
  }
}

class LongScrollBar extends JScrollBar {

  private static final long serialVersionUID = -4465773187404736017L;
  private long longValue, longRange;
  private int mult;

  public LongScrollBar(int orientation) {
    super(orientation);
  }

  public void setValue(int value) {
    longValue = value * (long) mult;
    if (mult > 1 && longValue > longRange) {
      longValue = longRange;
    }
    super.setValue(value);
  }

  public void setLongValue(long value) {
    if (value < 0) {
      value = 0;
    }
    if (value > longRange) {
      value = longRange;
    }
    longValue = value;
    int v = (int) (value / mult);
    super.setValue(v);
    super.fireAdjustmentValueChanged(AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, AdjustmentEvent.TRACK, v);
  }

  public int getValue() {
    return getModel().getValue();
  }

  public void setRange(long range) {
    longRange = range;
    super.setMinimum(0);
    if (range < Integer.MAX_VALUE) {
      super.setMaximum((int) range);
      mult = 1;
    } else {
      mult = (int) ((range + Integer.MAX_VALUE - 1) / Integer.MAX_VALUE);
      super.setMaximum((int) ((range + mult - 1) / mult));
    }
    if (longValue > longRange) {
      setLongValue(longRange);
    }
  }

  long getLongValue() {
    return longValue;
  }
}
