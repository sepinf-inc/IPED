package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;

import dpf.sp.gpinf.indexer.ITextParser;
import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.ui.hitsViewer.HitsTable;
import dpf.sp.gpinf.indexer.util.LuceneSimpleHTMLEncoder;
import iped3.io.IStreamSource;

public abstract class ATextViewer extends AbstractViewer implements KeyListener {

    public static Font font = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    
    /**
     * Maximum number of text break lines to track/keep on memory.
     */
    public static final int MAX_LINES = 100000;

    /**
     * Maximum size of text line
     */
    public static final int MAX_LINE_SIZE = 100;

    public static final String HIGHLIGHT_START_TAG = "<font color=\"black\" bgcolor=\"yellow\">";

    public static final String HIGHLIGHT_END_TAG = "</font>";

    public static final String TEXT_ENCODING = "UTF-32BE"; //$NON-NLS-1$

    public static final int CHAR_BYTE_COUNT = 4;

    public JTable textTable;
    public TextViewerModel textViewerModel;
    private JScrollPane viewerScroll;
    public ITextParser textParser;
    protected TemporaryResources tmp;
    protected HitsTable hitsTable;
    private AbstractTableModel hitsModel;

    public ATextViewer() {
        super(new GridLayout());
        textViewerModel = new TextViewerModel();
        textTable = new JTable(textViewerModel) {
            private static final long serialVersionUID = -5129153322350459095L;

            @Override
            public void updateUI() {
                Color background = UIManager.getColor("Viewer.background");
                if (background == null)
                    background = Color.WHITE;
                setBackground(background);

                Color foreground = UIManager.getColor("Viewer.foreground");
                setForeground(foreground);

                super.updateUI();
            }
        };
        textTable.setFont(font);
        // textTable.getColumnModel().getColumn(0).setCellRenderer(new
        // ViewerCellRenderer());
        viewerScroll = new JScrollPane(textTable);
        textTable.setFillsViewportHeight(true);
        textTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        textTable.getColumnModel().getColumn(0).setPreferredWidth(4096);
        textTable.setShowGrid(false);
        textTable.getTableHeader().setPreferredSize(new Dimension(0, 0));
        textTable.addKeyListener(this);
        this.getPanel().add(viewerScroll);
    }

    public void setHitsTable(HitsTable hitsTable) {
        this.hitsTable = hitsTable;
    }

    public void setHitsModel(AbstractTableModel hitsModel) {
        this.hitsModel = hitsModel;
    }

    public AbstractTableModel getHitsModel() {
        return hitsModel;
    }

    @Override
    public String getName() {
        return Messages.getString("ATextViewer.TabName"); //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return true;
    }

    @Override
    public void init() {
        tmp = new TemporaryResources();

    }

    @Override
    public void dispose() {
        try {
            tmp.dispose();
        } catch (TikaException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {
        if (content == null && textParser != null) {
            textParser.cancel(false);

            textParser.setSortedHits(new TreeMap<Long, int[]>());
            textParser.setHits(new ArrayList<Long>());
            textParser.setViewRows(new ArrayList<Long>());
            if (hitsModel != null) {
                hitsModel.fireTableDataChanged();
            }
            textViewerModel.fireTableDataChanged();

        }
    }

    @Override
    public abstract void loadFile(IStreamSource content, String contentType, Set<String> highlightTerms);

    public class TextViewerModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            if (textParser != null) {
                try {
                    int lines = textParser.getViewRows().size() - 1;
                    if (lines == MAX_LINES) {
                        lines = MAX_LINES + (int) ((textParser.getParsedFile().size()
                                - textParser.getViewRows().get(MAX_LINES))
                                / (CHAR_BYTE_COUNT * MAX_LINE_SIZE)) + 1;
                    }
                    return lines;

                } catch (Exception e) {
                }
            }
            return 0;
        }

        @Override
        public String getColumnName(int col) {
            return ""; //$NON-NLS-1$
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int col) {
            try {
                long off = 0, len;
                if (row < MAX_LINES) {
                    off = textParser.getViewRows().get(row);
                    len = textParser.getViewRows().get(row + 1) - off;
                } else {
                    len = MAX_LINE_SIZE * CHAR_BYTE_COUNT;
                    off = textParser.getViewRows().get(MAX_LINES)
                            + (long) (row - MAX_LINES) * len;

                    // Tratamento para não dividir hits destacados
                    // Desloca início da linha para final de fragmento com hit
                    Long hitOff = textParser.getSortedHits().floorKey(off);
                    if (hitOff != null) {
                        int[] hit = textParser.getSortedHits().get(hitOff);
                        if (hitOff < off && hitOff + hit[0] > off) {
                            len -= (hitOff + hit[0] - off);
                            if (len < 0) {
                                len = 0;
                            }
                            off = hitOff + hit[0];
                        }
                    }
                    // estende linha até final do fragmento com hit
                    hitOff = textParser.getSortedHits().floorKey(off + len);
                    if (hitOff != null) {
                        int[] hit = textParser.getSortedHits().get(hitOff);
                        if (hitOff < off + len && hitOff + hit[0] > off + len) {
                            len = hitOff + hit[0] - off;
                        }
                    }

                    if (off + len > textParser.getParsedFile().size()) {
                        len = textParser.getParsedFile().size() - off;
                    }
                }

                ByteBuffer data = ByteBuffer.allocate((int) len);
                int nread;
                do {
                    nread = textParser.getParsedFile().read(data, off);
                    off += nread;
                } while (nread != -1 && data.hasRemaining());

                data.flip();
                String line = (new String(data.array(), TEXT_ENCODING)).replaceAll("\n", " ").replaceAll("\r", " "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                // .replaceAll("\t","&#09;").replaceAll(" ","&nbsp;&nbsp; ")

                return "<html><pre>" + line + "</pre></html>"; //$NON-NLS-1$ //$NON-NLS-2$

            } catch (Exception e) {
                // e.printStackTrace();
                return ""; //$NON-NLS-1$
            }

        }

    }

    int keyBefore = -1;

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent evt) {
        if (textTable.getSelectedRow() == -1) {
            return;
        }

        if ((keyBefore == KeyEvent.VK_CONTROL && evt.getKeyCode() == KeyEvent.VK_C)
                || (keyBefore == KeyEvent.VK_C && evt.getKeyCode() == KeyEvent.VK_CONTROL)) {
            StringBuilder copy = new StringBuilder();
            for (Integer row : textTable.getSelectedRows()) {
                String value = textViewerModel.getValueAt(row, 0).toString();
                value = value.replaceAll("<html><pre>", "").replaceAll("</pre></html>", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                value = value.replaceAll(HIGHLIGHT_START_TAG, "") //$NON-NLS-1$
                        .replaceAll(HIGHLIGHT_END_TAG, ""); //$NON-NLS-1$
                value = LuceneSimpleHTMLEncoder.htmlDecode(value);
                copy.append(value + "\r\n"); //$NON-NLS-1$
            }
            StringSelection stringSelection = new StringSelection(copy.toString());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, stringSelection);
        }
        keyBefore = evt.getKeyCode();
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void scrollToNextHit(boolean forward) {
        if (hitsTable == null) {
            return;
        }
        currentHit = hitsTable.getSelectedRow();
        totalHits = textParser.getHits().size();
        if (forward) {
            if (currentHit < totalHits - 1) {
                hitsTable.setRowSelectionInterval(currentHit + 1, currentHit + 1);
            }

        } else {
            if (currentHit > 0) {
                hitsTable.setRowSelectionInterval(currentHit - 1, currentHit - 1);
            }

        }
        hitsTable.scrollRectToVisible(
                hitsTable.getCellRect(hitsTable.getSelectionModel().getLeadSelectionIndex(), 0, false));

    }
}
