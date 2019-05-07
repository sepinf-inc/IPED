package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.LineBorder;

import org.apache.commons.lang.StringUtils;
import org.exbin.deltahex.CaretMovedListener;
import org.exbin.deltahex.CaretPosition;
import org.exbin.deltahex.CodeAreaLineNumberLength;
import org.exbin.deltahex.CodeType;
import org.exbin.deltahex.EditationAllowed;
import org.exbin.deltahex.PositionCodeType;
import org.exbin.deltahex.Section;
import org.exbin.deltahex.SelectionChangedListener;
import org.exbin.deltahex.SelectionRange;
import org.exbin.deltahex.highlight.swing.HighlightCodeAreaPainter;
import org.exbin.deltahex.highlight.swing.HighlightCodeAreaPainter.SearchMatch;
import org.exbin.deltahex.swing.CodeArea;
import org.exbin.utils.binary_data.BinaryData;
import org.exbin.utils.binary_data.ByteArrayEditableData;
import org.exbin.utils.binary_data.OutOfBoundsException;

import iped3.io.SeekableInputStream;
import iped3.io.StreamSource;

public class HexViewerPlus extends Viewer implements KeyListener, MouseListener {

    private CodeArea codeArea;
    private javax.swing.JComboBox<String> charsetComboBox;
    private javax.swing.JComboBox<String> codeTypeComboBox;
    private javax.swing.JComboBox<String> formatoNumeroComboBox;
    private javax.swing.JComboBox<String> positionCodeTypeComboBox;
    private javax.swing.JCheckBox wrapLineModeCheckBox;
    private static javax.swing.JButton cancelButton;
    JPanel bottomPanel1, bottomPanel2;
    private static JLabel labelPesquisa;
    private javax.swing.JTextField posicao;
    private javax.swing.JTextField selecao_inicial;
    private javax.swing.JTextField selecao_final;
    private String formatoNumero;
    private int ultimaBase;
    private static JProgressBar progressBarSearch;
    private static searchKPMWorker objSearchKPMWorker;

    private ByteArraySeekData data;
    private static int currentHit = 0;
    private static int totalHits = 0;
    private HighlightCodeAreaPainter painter;

    public HexViewerPlus() {

        super(new GridBagLayout());

        posicao = new javax.swing.JTextField();
        posicao.setEditable(false);

        selecao_inicial = new javax.swing.JTextField();
        selecao_inicial.setEditable(false);
        selecao_final = new javax.swing.JTextField();
        selecao_final.setEditable(false);

        codeArea = new CodeArea();

        codeArea.setHandleClipboard(true);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        codeArea.setEditationAllowed(EditationAllowed.READ_ONLY);
        codeArea.setBorder(new LineBorder(Color.BLACK));
        codeArea.setPainter(new HighlightCodeAreaPainter(this.codeArea));

        charsetComboBox = new javax.swing.JComboBox<>();
        charsetComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(
                new String[] { "ISO-8859-1", "UTF-8", "UTF-16", "UTF-16BE", "US-ASCII", "IBM852" }));
        charsetComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeArea.setCharset(Charset.forName((String) charsetComboBox.getSelectedItem()));
            }
        });

        codeTypeComboBox = new javax.swing.JComboBox<>();
        codeTypeComboBox.setModel(
                new javax.swing.DefaultComboBoxModel<>(new String[] { "BINARY", "OCTAL", "DECIMAL", "HEXADECIMAL" }));
        codeTypeComboBox.setSelectedIndex(3);
        codeTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeArea.setCodeType(CodeType.values()[codeTypeComboBox.getSelectedIndex()]);
            }
        });

        positionCodeTypeComboBox = new javax.swing.JComboBox<>();
        positionCodeTypeComboBox
                .setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "OCTAL", "DECIMAL", "HEXADECIMAL" }));
        positionCodeTypeComboBox.setSelectedIndex(2);
        positionCodeTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeArea.setPositionCodeType(PositionCodeType.values()[positionCodeTypeComboBox.getSelectedIndex()]);
            }
        });

        formatoNumero = "%x";
        ultimaBase = 16;
        formatoNumeroComboBox = new javax.swing.JComboBox<>();
        formatoNumeroComboBox.setModel(
                new javax.swing.DefaultComboBoxModel<>(new String[] { "BINARY", "OCTAL", "DECIMAL", "HEXADECIMAL" }));
        formatoNumeroComboBox.setSelectedIndex(3);
        formatoNumeroComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                switch (formatoNumeroComboBox.getSelectedIndex()) {
                    case 0: {
                        formatoNumero = "%s";
                        posicao.setText(String
                                .format(formatoNumero,
                                        Long.toBinaryString(Long.parseLong(posicao.getText(), ultimaBase)))
                                .toUpperCase());
                        if (!selecao_inicial.getText().isEmpty()) {
                            selecao_inicial
                                    .setText(String
                                            .format(formatoNumero,
                                                    Long.toBinaryString(
                                                            Long.parseLong(selecao_inicial.getText(), ultimaBase)))
                                            .toUpperCase());
                            selecao_final
                                    .setText(String
                                            .format(formatoNumero,
                                                    Long.toBinaryString(
                                                            Long.parseLong(selecao_final.getText(), ultimaBase)))
                                            .toUpperCase());
                        }
                        ultimaBase = 2;
                        break;
                    }
                    case 1: {
                        formatoNumero = "%o";
                        posicao.setText(String.format(formatoNumero, Long.parseLong(posicao.getText(), ultimaBase))
                                .toUpperCase());
                        if (!selecao_inicial.getText().isEmpty()) {
                            selecao_inicial.setText(
                                    String.format(formatoNumero, Long.parseLong(selecao_inicial.getText(), ultimaBase))
                                            .toUpperCase());
                            selecao_final.setText(
                                    String.format(formatoNumero, Long.parseLong(selecao_final.getText(), ultimaBase))
                                            .toUpperCase());
                        }
                        ultimaBase = 8;
                        break;
                    }
                    case 2: {
                        formatoNumero = "%d";
                        posicao.setText(String.format(formatoNumero, Long.parseLong(posicao.getText(), ultimaBase))
                                .toUpperCase());
                        if (!selecao_inicial.getText().isEmpty()) {
                            selecao_inicial.setText(
                                    String.format(formatoNumero, Long.parseLong(selecao_inicial.getText(), ultimaBase))
                                            .toUpperCase());
                            selecao_final.setText(
                                    String.format(formatoNumero, Long.parseLong(selecao_final.getText(), ultimaBase))
                                            .toUpperCase());
                        }
                        ultimaBase = 10;
                        break;
                    }
                    case 3: {
                        formatoNumero = "%x";
                        posicao.setText(String.format(formatoNumero, Long.parseLong(posicao.getText(), ultimaBase))
                                .toUpperCase());
                        if (!selecao_inicial.getText().isEmpty()) {
                            selecao_inicial.setText(
                                    String.format(formatoNumero, Long.parseLong(selecao_inicial.getText(), ultimaBase))
                                            .toUpperCase());
                            selecao_final.setText(
                                    String.format(formatoNumero, Long.parseLong(selecao_final.getText(), ultimaBase))
                                            .toUpperCase());
                        }
                        ultimaBase = 16;
                        break;
                    }
                }

            }
        });

        codeArea.addCaretMovedListener(new CaretMovedListener() {
            @Override
            public void caretMoved(CaretPosition caretPosition, Section section) {
                if (formatoNumero.equals("%s"))
                    posicao.setText(String.format(formatoNumero, Long.toBinaryString(caretPosition.getDataPosition()))
                            .toUpperCase());
                else
                    posicao.setText(String.format(formatoNumero, caretPosition.getDataPosition()).toUpperCase());
            }
        });

        codeArea.addSelectionChangedListener(new SelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionRange selection) {
                if (selection != null) {
                    if (formatoNumero.equals("%s")) {
                        selecao_inicial.setText(
                                String.format(formatoNumero, Long.toBinaryString(codeArea.getSelection().getFirst()))
                                        .toUpperCase());
                        selecao_final.setText(
                                String.format(formatoNumero, Long.toBinaryString(codeArea.getSelection().getLast()))
                                        .toUpperCase());
                    } else {
                        selecao_inicial.setText(
                                String.format(formatoNumero, codeArea.getSelection().getFirst()).toUpperCase());
                        selecao_final
                                .setText(String.format(formatoNumero, codeArea.getSelection().getLast()).toUpperCase());
                    }
                } else {
                    selecao_inicial.setText("");
                    selecao_final.setText("");
                }
            }
        });

        codeArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_C) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
                    if (codeArea.getActiveSection().ordinal() == 0) {
                        codeArea.copyAsCode();
                    } else {
                        codeArea.copy();
                    }
                }
            }
        });

        wrapLineModeCheckBox = new javax.swing.JCheckBox();
        wrapLineModeCheckBox.setSelected(true);
        wrapLineModeCheckBox.setText("Quebra de linha");
        wrapLineModeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                codeArea.setWrapMode(!wrapLineModeCheckBox.isSelected());
            }
        });

        try {
            data = new ByteArraySeekData();
            SeekableInputStream nothing = null;
            data.setData(nothing);
        } catch (Exception e) {
            e.printStackTrace();
        }

        codeArea.setData(data);

        bottomPanel1 = new JPanel();
        bottomPanel1.setLayout(new BoxLayout(bottomPanel1, BoxLayout.LINE_AXIS));
        bottomPanel1.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomPanel1.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        bottomPanel1.add(new JLabel("Posição"));
        posicao.setMaximumSize(new Dimension(200, posicao.getMinimumSize().height));
        bottomPanel1.add(posicao);
        bottomPanel1.add(new JLabel("Seleção"));
        selecao_inicial.setMaximumSize(new Dimension(200, selecao_inicial.getMinimumSize().height));
        bottomPanel1.add(selecao_inicial);
        bottomPanel1.add(new JLabel("a"));
        selecao_final.setMaximumSize(new Dimension(200, selecao_final.getMinimumSize().height));
        bottomPanel1.add(selecao_final);
        bottomPanel1.add(new JLabel("Formato"));
        formatoNumeroComboBox.setMaximumSize(new Dimension(130, formatoNumeroComboBox.getMinimumSize().height));
        bottomPanel1.add(formatoNumeroComboBox);

        bottomPanel2 = new JPanel();
        bottomPanel2.setLayout(new BoxLayout(bottomPanel2, BoxLayout.LINE_AXIS));
        bottomPanel2.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomPanel2.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        bottomPanel2.add(new JLabel("Charset"));
        charsetComboBox.setMaximumSize(new Dimension(130, charsetComboBox.getMinimumSize().height));
        bottomPanel2.add(charsetComboBox);

        bottomPanel2.add(new JLabel("Conteúdo"));
        codeTypeComboBox.setMaximumSize(new Dimension(130, codeTypeComboBox.getMinimumSize().height));
        bottomPanel2.add(codeTypeComboBox);

        bottomPanel2.add(new JLabel("Cabeçalho"));
        positionCodeTypeComboBox.setMaximumSize(new Dimension(130, positionCodeTypeComboBox.getMinimumSize().height));
        bottomPanel2.add(positionCodeTypeComboBox);

        bottomPanel2.add(wrapLineModeCheckBox);

        bottomPanel2.add(new JLabel("   "));

        progressBarSearch = new JProgressBar();
        progressBarSearch.setStringPainted(true);
        progressBarSearch.setValue(0);
        progressBarSearch.setSize(new Dimension(10, 23));

        labelPesquisa = new JLabel("Pesquisa");
        bottomPanel2.add(labelPesquisa);
        bottomPanel2.add(progressBarSearch);

        cancelButton = new JButton("Parar");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (objSearchKPMWorker != null) {
                    objSearchKPMWorker.Parar();
                    cancelButton.setEnabled(false);
                }
            }
        });
        bottomPanel2.add(cancelButton);

        cancelButton.setVisible(false);
        progressBarSearch.setVisible(false);
        labelPesquisa.setVisible(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;

        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTH;
        this.getPanel().add(codeArea, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weighty = 0;
        this.getPanel().add(bottomPanel1, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weighty = 0;
        this.getPanel().add(bottomPanel2, c);

    }

    @Override
    public String getName() {
        return "Hex";
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return true;
    }

    @Override
    public void init() {

    }

    @Override
    public void dispose() {

    }

    @Override
    public void loadFile(final StreamSource content, Set<String> highlightTerms) {

        boolean high = false;

        if (highlightTerms == null)
            high = false;
        else {
            if (highlightTerms.size() == 0)
                high = false;
            else
                high = true;
        }

        painter = (HighlightCodeAreaPainter) this.codeArea.getPainter();

        painter.setCurrentMatchBackgroundColor(Color.GREEN);
        painter.setFoundMatchesBackgroundColor(Color.YELLOW);

        currentHit = 0;
        totalHits = 0;

        cancelButton.setEnabled(true);

        if (objSearchKPMWorker != null) {
            objSearchKPMWorker.Parar();
            objSearchKPMWorker.cancel(true);
        }

        if (content == null) {
            this.data.clear();
            codeArea.resetPosition();
            codeArea.notifyDataChanged();
            painter.clearMatches();
            codeArea.repaint();
        } else {

            try {

                data.clear();
                data.setData(content.getStream());

                if (data.getDataSize() > 4294967295L) {
                    codeArea.setLineNumberType(CodeAreaLineNumberLength.LineNumberType.AUTO);
                } else {
                    codeArea.setLineNumberType(CodeAreaLineNumberLength.LineNumberType.SPECIFIED);
                }

                painter.clearMatches();
                codeArea.setData(data);
                codeArea.resetPosition();
                codeArea.notifyDataChanged();

                if (high) {

                    objSearchKPMWorker = new searchKPMWorker(codeArea, highlightTerms, painter);
                    objSearchKPMWorker.execute();

                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }

    }

    static class searchKPMWorker extends iped3.desktop.CancelableWorker<String, Integer> {

        CodeArea codeArea;
        Set<String> highlightTerms;
        HighlightCodeAreaPainter painter;
        boolean interromper = false;

        searchKPMWorker(CodeArea codeArea, Set<String> highlightTerms, HighlightCodeAreaPainter painter) {
            this.codeArea = codeArea;
            this.highlightTerms = highlightTerms;
            this.painter = painter;
        }

        public void Parar() {
            this.interromper = true;
        }

        @Override
        protected String doInBackground() throws Exception {

            // Realiza busca simples com algoritmo KMP

            interromper = false;
            List<SearchMatch> hitsEncontrados = new ArrayList();
            long posicao = 0;
            Charset charset = this.codeArea.getCharset();

            BinaryData data = codeArea.getData();
            long dataSize = data.getDataSize();

            int maxTermLength = -1;
            int bufferLength = 4 * 1024;

            for (String texto : highlightTerms) {
                if (maxTermLength < texto.length())
                    maxTermLength = texto.length();
            }

            if (maxTermLength > bufferLength) {
                bufferLength = maxTermLength;
            }

            byte[] buffer = new byte[bufferLength + maxTermLength];
            int tempLength = 0;

            List<Integer> hitsKMP = null;
            String frase = "";

            cancelButton.setVisible(true);
            progressBarSearch.setVisible(true);
            labelPesquisa.setVisible(true);

            progressBarSearch.setValue(0);
            progressBarSearch.setString(0 + "%");

            while (posicao < dataSize - bufferLength) {

                for (String texto : highlightTerms) {

                    tempLength = bufferLength + texto.length() - 1;
                    data.copyToArray(posicao, buffer, 0, tempLength);

                    frase = new String(buffer, 0, tempLength, charset);

                    hitsKMP = KMP.searchString(texto.toLowerCase(), frase.toLowerCase());

                    for (int off : hitsKMP) {
                        HighlightCodeAreaPainter.SearchMatch match = new HighlightCodeAreaPainter.SearchMatch();
                        match.setPosition(posicao + off);
                        match.setLength(texto.length());
                        if ((hitsEncontrados.size() != 10000)) {
                            hitsEncontrados.add(match);
                        }
                    }

                    frase = null;
                    hitsKMP = null;
                }

                if (this.interromper) {
                    progressBarSearch
                            .setString((int) (((double) posicao / (double) dataSize) * 100) + "% - Interrompido");
                    break;
                }

                posicao += bufferLength;
                progressBarSearch.setValue((int) (((double) posicao / (double) dataSize) * 100));
                progressBarSearch.setString((int) (((double) posicao / (double) dataSize) * 100) + "%");

            }
            // Busca no que sobrou do dos dados e passaria do buffer
            tempLength = (int) (dataSize - posicao);
            if (!interromper && tempLength > 0) {

                data.copyToArray(posicao, buffer, 0, tempLength);
                frase = new String(buffer, 0, tempLength, charset);

                for (String texto : highlightTerms) {

                    hitsKMP = KMP.searchString(texto.toLowerCase(), frase.toLowerCase());

                    for (int off : hitsKMP) {
                        HighlightCodeAreaPainter.SearchMatch match = new HighlightCodeAreaPainter.SearchMatch();
                        match.setPosition(posicao + off);
                        match.setLength(texto.length());
                        if ((hitsEncontrados.size() != 10000)) {
                            hitsEncontrados.add(match);
                        }
                    }
                    hitsKMP = null;
                }

                progressBarSearch.setValue(100);
                progressBarSearch.setString(100 + "%");
            }

            Collections.sort(hitsEncontrados, new Comparator<HighlightCodeAreaPainter.SearchMatch>() {
                @Override
                public int compare(HighlightCodeAreaPainter.SearchMatch o1, HighlightCodeAreaPainter.SearchMatch o2) {
                    return Long.valueOf(o1.getPosition()).compareTo(Long.valueOf(o2.getPosition()));
                }
            });

            painter.setMatches(hitsEncontrados);
            totalHits = hitsEncontrados.size();

            if (totalHits > 0) {
                painter.setCurrentMatchIndex(currentHit);
                HighlightCodeAreaPainter.SearchMatch firstMatch = painter.getCurrentMatch();
                codeArea.revealPosition(firstMatch.getPosition(), codeArea.getActiveSection());
            }

            codeArea.repaint();

            return "";
        }

        @Override
        public void done() {
            if (!interromper) {
                cancelButton.setVisible(false);
                progressBarSearch.setVisible(false);
                labelPesquisa.setVisible(false);
            }
        }
    }

    int keyBefore = -1;

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent evt) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void scrollToNextHit(boolean forward) {

        if (forward) {
            if (currentHit < (totalHits - 1)) {
                currentHit++;
                painter.setCurrentMatchIndex(currentHit);
                HighlightCodeAreaPainter.SearchMatch match = painter.getCurrentMatch();
                codeArea.revealPosition(match.getPosition(), codeArea.getActiveSection());
                codeArea.repaint();
            }
        } else {
            if (currentHit > 0) {
                currentHit--;
                painter.setCurrentMatchIndex(currentHit);
                HighlightCodeAreaPainter.SearchMatch match = painter.getCurrentMatch();
                codeArea.revealPosition(match.getPosition(), codeArea.getActiveSection());
                codeArea.repaint();
            }
        }

    }

    @Override
    public void mouseClicked(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseEntered(MouseEvent arg0) {
        // CompositeViewerHelper.releaseLOFocus();
        // ViewerControl.getInstance().releaseLibreOfficeFocus();

    }

    @Override
    public void mouseExited(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mousePressed(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseReleased(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

}

class ByteArraySeekData extends ByteArrayEditableData {

    protected byte[] data = new byte[0];

    private long len, rows;
    private Map<Long, int[]> memoBytes = new HashMap<Long, int[]>();
    private byte[] readBuf = new byte[1 << 12];
    private LinkedList<Long> fifo = new LinkedList<Long>();
    private boolean err;
    private static final int MAX_MEMO = 2000;

    private SeekableInputStream file;

    public ByteArraySeekData() {
    }

    public void setData(SeekableInputStream file) throws IOException {

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
        len = 0;
        rows = 0;
        if (file != null) {
            len = file.size();
        }
        err = false;
        memoBytes.clear();

    }

    @Override
    public boolean isEmpty() {
        return len == 0;
    }

    @Override
    public long getDataSize() {
        return len;
    }

    @Override
    public byte getByte(long pos) {

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
                for (int i = 0; i < buf.length; i++)
                    buf[i] = -1;
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
            return (byte) buf[bpos];
        } catch (Exception e) {
            // e.printStackTrace();
            err = true;
            memoBytes.clear();
            throw new OutOfBoundsException(e);
        }

    }

    @Override
    public BinaryData copy() {
        // byte[] copy = Arrays.copyOf(data, (int)getDataSize());

        int max = (this.getDataSize() > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) this.getDataSize();

        byte[] copy = new byte[max];
        copyToArray(0, copy, 0, max);

        return new ByteArrayEditableData(copy);
    }

    @Override
    public BinaryData copy(long startFrom, long length) {
        if (startFrom + length > getDataSize()) {
            throw new OutOfBoundsException("Attemt to copy outside of data");
        }

        // byte[] copy = Arrays.copyOfRange(data, (int) startFrom, (int) (startFrom +
        // length));

        int max = (length > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) length;
        byte[] copy = new byte[max];
        copyToArray(startFrom, copy, 0, max);

        return new ByteArrayEditableData(copy);
    }

    @Override
    public void copyToArray(long startFrom, byte[] target, int offset, int length) {

        try {

            int i = offset;
            long j = startFrom;
            int k = 0;
            while (k < length) {
                target[i] = getByte(j);
                i++;
                j++;
                k++;
            }

        } catch (IndexOutOfBoundsException ex) {
            throw new OutOfBoundsException(ex);
        }
    }

    @Override
    public void clear() {
        data = new byte[0];
        memoBytes.clear();
    }

}

/**
 * 
 * Source https://gist.github.com/anuvrat/2382245
 * 
 * @author: anuvrat
 * 
 */

class KMP {

    /**
     * Searches for all occurances of the word in the sentence. Runs in O(n+k) where
     * n is the word length and k is the sentence length.
     * 
     * @param word
     *            The word that is being searched
     * @param sentence
     *            The collections of word over which the search happens.
     * @return The list of starting indices of the matched word in the sentence.
     *         Empty list in case of no match.
     */
    public static List<Integer> searchString(final String word, final String sentence) {
        final List<Integer> matchedIndices = new ArrayList<>();

        final int sentenceLength = sentence.length();
        final int wordLength = word.length();
        int beginMatch = 0; // the starting position in sentence from which the match started
        int idxWord = 0; // the index of the character of the word that is being compared to a character
                         // in string
        final List<Integer> partialTable = createPartialMatchTable(word);
        while (beginMatch + idxWord < sentenceLength)
            if (word.charAt(idxWord) == sentence.charAt(beginMatch + idxWord)) {
                // the characters have matched
                if (idxWord == wordLength - 1) {
                    // the word is complete. we have a match.
                    matchedIndices.add(beginMatch);
                    // restart the search
                    beginMatch = beginMatch + idxWord - partialTable.get(idxWord);
                    if (partialTable.get(idxWord) > -1)
                        idxWord = partialTable.get(idxWord);
                    else
                        idxWord = 0;
                } else
                    idxWord++;
            } else {
                // mismatch. restart the search.
                beginMatch = beginMatch + idxWord - partialTable.get(idxWord);
                if (partialTable.get(idxWord) > -1)
                    idxWord = partialTable.get(idxWord);
                else
                    idxWord = 0;
            }

        return Collections.unmodifiableList(matchedIndices);
    }

    /**
     * Creates the Partial Match Table for the word. Runs in O(n) where n is the
     * length of the word.
     * 
     * @param word
     *            The word whose Partial Match Table is required.
     * @return The table as a list of integers.
     */
    public static List<Integer> createPartialMatchTable(final String word) {
        if (StringUtils.isBlank(word))
            return Collections.EMPTY_LIST;

        final int length = word.length();
        final List<Integer> partialTable = new ArrayList<>(length + 1);
        partialTable.add(-1);
        partialTable.add(0);

        final char firstChar = word.charAt(0);
        for (int idx = 1; idx < word.length(); idx++) {
            final int prevVal = partialTable.get(idx);
            if (prevVal == 0) {
                if (word.charAt(idx) == firstChar)
                    partialTable.add(1);
                else
                    partialTable.add(0);
            } else if (word.charAt(idx) == word.charAt(prevVal))
                partialTable.add(prevVal + 1);
            else
                partialTable.add(0);
        }

        return Collections.unmodifiableList(partialTable);
    }
}