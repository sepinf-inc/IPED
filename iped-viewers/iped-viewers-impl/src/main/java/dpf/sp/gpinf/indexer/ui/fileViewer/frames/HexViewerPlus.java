package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileView;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.exbin.deltahex.CaretMovedListener;
import org.exbin.deltahex.CaretPosition;
import org.exbin.deltahex.CodeAreaLineNumberLength;
import org.exbin.deltahex.CodeAreaUtils;
import org.exbin.deltahex.CodeType;
import org.exbin.deltahex.EditationAllowed;
import org.exbin.deltahex.EditationMode;
import org.exbin.deltahex.PositionCodeType;
import org.exbin.deltahex.Section;
import org.exbin.deltahex.SelectionChangedListener;
import org.exbin.deltahex.SelectionRange;
import org.exbin.deltahex.ViewMode;
import org.exbin.deltahex.highlight.swing.HighlightCodeAreaPainter;
import org.exbin.deltahex.swing.CodeArea;
import org.exbin.deltahex.swing.CodeArea.BackgroundMode;
import org.exbin.deltahex.swing.CodeAreaCaret;
import org.exbin.deltahex.swing.CodeAreaCaret.CursorShape;
import org.exbin.deltahex.swing.ColorsGroup;
import org.exbin.utils.binary_data.BinaryData;
import org.exbin.utils.binary_data.ByteArrayEditableData;
import org.exbin.utils.binary_data.OutOfBoundsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.SeekableFileInputStream;
import iped3.io.IStreamSource;
import iped3.io.SeekableInputStream;

/**
 *
 * @author guilherme.dutra
 */

public class HexViewerPlus extends Viewer implements KeyListener, MouseListener {

    private CodeArea codeArea;
    private FilterComboBox charsetComboBox;

    JPanel bottomPanel1, bottomPanel2, bottomPanelLeft, bottomPanelRight;
    private JTextField posicao;
    private JTextField selecao_inicial;
    private JTextField selecao_final;
    private String formatoNumero = "%x";
    private int ultimaBase = 16;

    String appName = Messages.getString("HexViewerPlus.appName");
    String extensao = ".hvp";
    String[] extensoes = { extensao };
    String descricaoExtensoes = Messages.getString("HexViewerPlus.HvpFileSettings") + " (*" + extensao + ")";

    private ByteArraySeekData data;
    private static Hits hits = new Hits();
    private HighlightCodeAreaPainter painter;

    private HexSearcher hexSearcher;

    private JPopupMenu jPopupMenu;
    private JMenuItem menuItemPesquisar, menuItemGo, menuItemSelecionar, menuItemOpcoes, menuItemCopyCode,
            menuItemCopyText, menuItemGoResult, menuItemGoNextHit, menuItemGoPreviousHit, menuItemSelecionarTudo;
    private JDialog dialogPesquisar, dialogSelecionar, dialogOpcoes, dialogIrParaResultado, dialogIrParaEndereco;
    private JLabel lblTotal;

    JComboBox<String> modeComboBox;
    JComboBox<String> codeTypeComboBox;
    JComboBox<String> positionCodeTypeComboBox;
    JComboBox<String> backGroundComboBox;
    JComboBox<String> fontComboBox;
    JComboBox<String> formatoNumeroComboBox;
    JComboBox<String> cursorComboBox;

    JCheckBox hexCharactersCaseCheckBox;
    JCheckBox showHeaderCheckBox;
    JCheckBox showLineNumbersCheckBox;
    JCheckBox wrapLineModeCheckBox;
    JCheckBox showUnprintableCharactersCheckBox;
    JCheckBox showPositionBarCheckBox;
    JCheckBox showSelectionBarCheckBox;
    JCheckBox showLineNumberBackgroundCheckBox;

    RoundButton btnColorFontMain;
    RoundButton btnColorFontAlt;
    RoundButton btnColorBackgroundMain;
    RoundButton btnColorBackgroundAlt;
    RoundButton btnColorSelectionMainText;
    RoundButton btnColorSelectionMainBackground;
    RoundButton btnColorSelectionMirrorText;
    RoundButton btnColorSelectionMirrorBackground;
    RoundButton btnColorFoundMatchText;
    RoundButton btnColorFoundMatchBackground;
    RoundButton btnColorCurrentMatchText;
    RoundButton btnColorCurrentMatchBackground;
    RoundButton btnColorCursor;
    RoundButton btnColorHeaderText;
    RoundButton btnColorHeaderBackground;

    JSpinner fontSizeSpinner;
    JSpinner cursorBlinkSpinner;
    JSpinner lineSizeSpinner;

    HVPComboField jtfTextoHex;

    FilterComboBox fcbCharset;

    private HVPSettings defaultSettings = new HVPSettings();
    String defaultSettingsPath = "";
    boolean defaultSettingsFileExists = false;

    private ActionListener menuListener;

    private IStreamSource contentAux;

    private JLabel resultSearch = new JLabel("");

    private int fontSize = defaultSettings.fontSize;
    private String font = defaultSettings.font;

    private CursorComponent cursorExample;

    private int max_hits = 1;
    private int max_terms = 10000;

    Color labelColor = new Color(214, 217, 223);

    public interface HexSearcher {

        abstract void doSearch(CodeArea codeArea, HighlightCodeAreaPainter painter, Hits hits, SeekableInputStream data,
                Charset charset, Set<String> highlightTerms, long offset, boolean searchString,
                boolean ignoreCaseSearch, JLabel resultSearch, int max_hits) throws Exception;
    }

    public HexViewerPlus(HexSearcher hexSearcher, String appPath) {

        super(new GridBagLayout());

        this.hexSearcher = hexSearcher;

        posicao = new JTextField();
        posicao.setHorizontalAlignment(SwingConstants.RIGHT);
        posicao.setBackground(labelColor);
        posicao.setEditable(false);

        selecao_inicial = new JTextField();
        selecao_inicial.setEditable(false);
        selecao_inicial.setBackground(labelColor);
        selecao_inicial.setHorizontalAlignment(SwingConstants.RIGHT);

        selecao_final = new JTextField();
        selecao_final.setBackground(labelColor);
        selecao_final.setHorizontalAlignment(SwingConstants.RIGHT);
        selecao_final.setEditable(false);

        codeArea = new CodeArea();
        codeArea.setHandleClipboard(true);
        codeArea.setEditationAllowed(EditationAllowed.READ_ONLY);
        codeArea.setBorder(new LineBorder(Color.BLACK));
        painter = new HighlightCodeAreaPainter(this.codeArea);
        codeArea.setPainter(painter);

        charsetComboBox = new FilterComboBox(new ArrayList<>(Charset.availableCharsets().keySet()));
        charsetComboBox.setSelectedItem(codeArea.getCharset().toString());
        charsetComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                String charset = (String) charsetComboBox.getSelectedItem();
                if (charset != null && !charset.isEmpty() && Charset.isSupported(charset)
                        && Charset.forName(charset).canEncode()) {
                    codeArea.setCharset(Charset.forName(charset));
                    fcbCharset.setSelectedItem(charset);
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
                    if (codeArea.getActiveSection().ordinal() == 0) { // HexArea
                        codeArea.copyAsCode();
                    } else {
                        codeArea.copy();
                    }
                }
                if ((e.getKeyCode() == KeyEvent.VK_F) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
                    dialogPesquisar.setLocationRelativeTo(codeArea);
                    dialogPesquisar.setVisible(true);
                }
                if ((e.getKeyCode() == KeyEvent.VK_G) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
                    dialogIrParaEndereco.setLocationRelativeTo(codeArea);
                    dialogIrParaEndereco.setVisible(true);
                }
            }
        });

        try {
            data = new ByteArraySeekData() {
                @Override
                void fireReadError() {
                    try {
                        this.setData(
                                new SeekableFileInputStream(new SeekableInMemoryByteChannel("READ ERROR!".getBytes())));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    codeArea.setViewMode(ViewMode.TEXT_PREVIEW);
                    codeArea.resetPosition();
                    codeArea.notifyDataChanged();
                    codeArea.repaint();
                }
            };
            SeekableInputStream nothing = null;
            data.setData(nothing);
        } catch (Exception e) {
            e.printStackTrace();
        }

        codeArea.setData(data);

        bottomPanelLeft = new JPanel();
        bottomPanelLeft.setLayout(new BoxLayout(bottomPanelLeft, BoxLayout.LINE_AXIS));
        bottomPanelLeft.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomPanelLeft.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JLabel JLabelPosition = new JLabel(Messages.getString("HexViewerPlus.position"));
        JLabelPosition.setPreferredSize(new Dimension(60, JLabelPosition.getMinimumSize().height));
        JLabelPosition.setMaximumSize(new Dimension(60, JLabelPosition.getMinimumSize().height));
        bottomPanelLeft.add(JLabelPosition);
        bottomPanelLeft.add(Box.createHorizontalGlue());
        posicao.setMaximumSize(new Dimension(200, posicao.getMinimumSize().height));
        posicao.setMinimumSize(new Dimension(100, posicao.getMinimumSize().height));
        posicao.setPreferredSize(new Dimension(200, posicao.getMinimumSize().height));
        bottomPanelLeft.add(posicao);

        bottomPanelLeft
                .add(new JLabel(" " + Messages.getString("HexViewerPlus.to").toString().replaceAll(".", "  ") + "  "));
        bottomPanelLeft.add(new JLabel(Messages.getString("HexViewerPlus.charset") + " "));
        charsetComboBox.setMaximumSize(new Dimension(150, charsetComboBox.getMinimumSize().height));
        bottomPanelLeft.add(charsetComboBox);

        bottomPanelRight = new JPanel();
        bottomPanelRight.setLayout(new BoxLayout(bottomPanelRight, BoxLayout.LINE_AXIS));
        bottomPanelRight.add(resultSearch);
        bottomPanelRight.add(new JLabel("   "));
        JButton Options = new JButton(Messages.getString("HexViewerPlus.options"));
        Options.setMaximumSize(new Dimension(100, charsetComboBox.getMinimumSize().height));
        Options.setMinimumSize(new Dimension(100, posicao.getMinimumSize().height));
        bottomPanelRight.add(Options);

        bottomPanel1 = new JPanel();
        bottomPanel1.setLayout(new BorderLayout());
        bottomPanel1.add(bottomPanelLeft, BorderLayout.WEST);
        bottomPanel1.add(bottomPanelRight, BorderLayout.EAST);

        bottomPanel2 = new JPanel();
        bottomPanel2.setLayout(new BoxLayout(bottomPanel2, BoxLayout.LINE_AXIS));
        bottomPanel2.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomPanel2.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JLabel JLabelSelection = new JLabel(Messages.getString("HexViewerPlus.selection"));
        JLabelSelection.setPreferredSize(new Dimension(60, JLabelSelection.getMinimumSize().height));
        JLabelSelection.setMaximumSize(new Dimension(60, JLabelSelection.getMinimumSize().height));
        bottomPanel2.add(JLabelSelection);
        selecao_inicial.setPreferredSize(new Dimension(200, selecao_inicial.getMinimumSize().height));
        selecao_inicial.setMaximumSize(new Dimension(200, selecao_inicial.getMinimumSize().height));
        bottomPanel2.add(selecao_inicial);
        bottomPanel2.add(new JLabel(" " + Messages.getString("HexViewerPlus.to") + " "));
        selecao_final.setPreferredSize(new Dimension(200, selecao_final.getMinimumSize().height));
        selecao_final.setMaximumSize(new Dimension(200, selecao_final.getMinimumSize().height));
        bottomPanel2.add(selecao_final);
        bottomPanel2.add(new JLabel(" "));

        Options.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                verifyEnabledItensPopupMenu();
                jPopupMenu.show(Options, (int) Options.getX(), (int) Options.getY());
            }
        });

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;

        c.gridx = 0;
        c.gridy = 0;
        c.weighty = 0;
        c.weightx = 0;
        c.anchor = GridBagConstraints.NORTH;

        c.gridx = 0;
        c.gridy = 1;
        c.weighty = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTH;
        this.getPanel().add(codeArea, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weighty = 0;
        this.getPanel().add(bottomPanel1, c);

        c.gridx = 0;
        c.gridy = 3;
        c.weighty = 0;
        this.getPanel().add(bottomPanel2, c);

        createMenuAndDialogs();

        // Load Defaults
        defaultSettings = null;
        defaultSettingsPath = appPath + File.separator + "default.hvp";
        File defaultSettingsFile = new File(defaultSettingsPath);
        if (defaultSettingsFile != null && defaultSettingsFile.exists() && defaultSettingsFile.isFile()) {
            if (defaultSettingsFile.canRead()) {
                defaultSettings = HVPSettings.loadObject(defaultSettingsPath);
                if (defaultSettings != null) {
                    defaultSettingsFileExists = true;
                } else {// something is wrong, make default hard coded
                    defaultSettings = new HVPSettings();
                }
                loadSettings(defaultSettings);
            }
        } else { // there is no default.hvp, make new one
            defaultSettings = new HVPSettings();
            defaultSettingsFileExists = HVPSettings.saveObject(defaultSettingsPath, defaultSettings);
            loadSettings(defaultSettings);
        }

        codeArea.repaint();

    }

    @Override
    public String getName() {
        return Messages.getString("HexViewerPlus.TabName");
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
    public void loadFile(final IStreamSource content, Set<String> highlightTerms) {

        boolean high = false;

        if (highlightTerms == null)
            high = false;
        else {
            if (highlightTerms.size() == 0)
                high = false;
            else
                high = true;
        }

        hits.currentHit = 0;
        hits.totalHits = 0;
        resultSearch.setText("");

        contentAux = content;

        jtfTextoHex.removeAllItems();

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

                codeArea.setLineNumberType(CodeAreaLineNumberLength.LineNumberType.AUTO);

                painter.clearMatches();
                ViewMode selectedViewMode = ViewMode.values()[modeComboBox.getSelectedIndex()];
                if (codeArea.getViewMode() != selectedViewMode) {
                    codeArea.setViewMode(selectedViewMode);
                }
                codeArea.setData(data);
                codeArea.resetPosition();
                codeArea.notifyDataChanged();

                if (high) {
                    // Uncomment the line below if you want the automatic search when opening a
                    // file. If so, see how not to trigger Search text at the same time.
                    // hexSearcher.doSearch(codeArea, painter,
                    // hits,content.getStream(),codeArea.getCharset(),highlightTerms,0 ,true, true,
                    // resultSearch, max_terms);
                    ;
                }

            } catch (Exception ex) {
                ex.printStackTrace();
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

        if (hits.totalHits > 0) {
            if (forward) {
                if (hits.currentHit < (hits.totalHits - 1)) {
                    hits.currentHit++;
                    painter.setCurrentMatchIndex(hits.currentHit);
                    HighlightCodeAreaPainter.SearchMatch match = painter.getCurrentMatch();
                    codeArea.revealPosition(match.getPosition(), codeArea.getActiveSection());
                    codeArea.setCaretPosition(match.getPosition() + match.getLength());
                    codeArea.repaint();
                    codeArea.requestFocus();
                }
            } else {
                if (hits.currentHit > 0) {
                    hits.currentHit--;
                    painter.setCurrentMatchIndex(hits.currentHit);
                    HighlightCodeAreaPainter.SearchMatch match = painter.getCurrentMatch();
                    codeArea.revealPosition(match.getPosition(), codeArea.getActiveSection());
                    codeArea.setCaretPosition(match.getPosition() + match.getLength());
                    codeArea.repaint();
                    codeArea.requestFocus();
                }
            }
            resultSearch.setText(Messages.getString("HexViewerPlus.hit") + " " + (hits.currentHit + 1) + " "
                    + Messages.getString("HexViewerPlus.of") + " " + hits.totalHits);
        }

    }

    @Override
    public void mouseClicked(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseEntered(MouseEvent arg0) {

    }

    @Override
    public void mouseExited(MouseEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mousePressed(MouseEvent event) {
        // TODO Auto-generated method stub
    }

    @Override
    public void mouseReleased(MouseEvent arg0) {
        // TODO Auto-generated method stub
    }

    public byte[] hexStringToByteArray(String s) {
        s = s.replace(" ", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public void createMenuAndDialogs() {

        dialogPesquisar();
        dialogIrParaEndereco();
        dialogIrParaResultado();
        dialogOpcoes();
        dialogSelecionar();

        jPopupMenu = new JPopupMenu();

        menuListener = new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                if (event.getSource() == menuItemPesquisar) {
                    dialogPesquisar.setLocationRelativeTo(codeArea);
                    dialogPesquisar.setVisible(true);
                }
                if (event.getSource() == menuItemGo) {
                    dialogIrParaEndereco.setLocationRelativeTo(codeArea);
                    dialogIrParaEndereco.setVisible(true);
                }
                if (event.getSource() == menuItemGoResult) {
                    lblTotal.setText(Messages.getString("HexViewerPlus.of") + "  " + hits.totalHits);
                    dialogIrParaResultado.setLocationRelativeTo(codeArea);
                    dialogIrParaResultado.setVisible(true);
                }
                if (event.getSource() == menuItemOpcoes) {
                    // Comment this to show Options Dialog on center of the screen.
                    // dialogOpcoes.setLocationRelativeTo(codeArea);
                    dialogOpcoes.setVisible(true);
                }
                if (event.getSource() == menuItemSelecionar) {
                    dialogSelecionar.setLocationRelativeTo(codeArea);
                    dialogSelecionar.setVisible(true);
                }
                if (event.getSource() == menuItemSelecionarTudo) {
                    codeArea.selectAll();
                }
                if (event.getSource() == menuItemGoNextHit) {
                    scrollToNextHit(true);
                }
                if (event.getSource() == menuItemGoPreviousHit) {
                    scrollToNextHit(false);
                }
                if (event.getSource() == menuItemCopyCode) {
                    codeArea.copyAsCode();
                }
                if (event.getSource() == menuItemCopyText) {
                    codeArea.copyAsCode();
                    try {
                        String data = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                                .getData(DataFlavor.stringFlavor);
                        StringSelection selection = new StringSelection(
                                new String(hexStringToByteArray(data), codeArea.getCharset()));
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                    } catch (Exception ex) {
                        ;
                    }
                }

            }
        };

        menuItemPesquisar = new JMenuItem(Messages.getString("HexViewerPlus.search"), KeyEvent.VK_F);
        menuItemPesquisar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, ActionEvent.CTRL_MASK));
        menuItemPesquisar.setPreferredSize(new Dimension(150, menuItemPesquisar.getPreferredSize().height));
        menuItemPesquisar.addActionListener(menuListener);
        jPopupMenu.add(menuItemPesquisar);

        menuItemGoNextHit = new JMenuItem(Messages.getString("HexViewerPlus.nextHit"));
        menuItemGoNextHit.addActionListener(menuListener);
        jPopupMenu.add(menuItemGoNextHit);

        menuItemGoPreviousHit = new JMenuItem(Messages.getString("HexViewerPlus.preHit"));
        menuItemGoPreviousHit.addActionListener(menuListener);
        jPopupMenu.add(menuItemGoPreviousHit);

        menuItemGoResult = new JMenuItem(Messages.getString("HexViewerPlus.goToHit"));
        menuItemGoResult.addActionListener(menuListener);
        jPopupMenu.add(menuItemGoResult);

        jPopupMenu.add(new JSeparator());

        menuItemGo = new JMenuItem(Messages.getString("HexViewerPlus.goToPosition"), KeyEvent.VK_G);
        menuItemGo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.CTRL_MASK));
        menuItemGo.addActionListener(menuListener);
        jPopupMenu.add(menuItemGo);

        jPopupMenu.add(new JSeparator());

        menuItemSelecionar = new JMenuItem(Messages.getString("HexViewerPlus.selectBlock"));
        menuItemSelecionar.addActionListener(menuListener);
        jPopupMenu.add(menuItemSelecionar);

        menuItemSelecionarTudo = new JMenuItem(Messages.getString("HexViewerPlus.selectAll"));
        menuItemSelecionarTudo.addActionListener(menuListener);
        jPopupMenu.add(menuItemSelecionarTudo);

        jPopupMenu.add(new JSeparator());

        menuItemCopyCode = new JMenuItem(Messages.getString("HexViewerPlus.copyHex"));
        menuItemCopyCode.addActionListener(menuListener);
        jPopupMenu.add(menuItemCopyCode);

        menuItemCopyText = new JMenuItem(Messages.getString("HexViewerPlus.copyText"));
        menuItemCopyText.addActionListener(menuListener);
        jPopupMenu.add(menuItemCopyText);

        jPopupMenu.add(new JSeparator());

        menuItemOpcoes = new JMenuItem(Messages.getString("HexViewerPlus.settings"));
        menuItemOpcoes.addActionListener(menuListener);
        jPopupMenu.add(menuItemOpcoes);

        this.codeArea.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == event.BUTTON3) {
                    verifyEnabledItensPopupMenu();
                    jPopupMenu.show(event.getComponent(), event.getX(), event.getY());
                }
            }
        });

    }

    public void verifyEnabledItensPopupMenu() {
        if (hits.totalHits > 0) {
            menuItemGoResult.setEnabled(true);
            if (hits.currentHit < hits.totalHits - 1)
                menuItemGoNextHit.setEnabled(true);
            else
                menuItemGoNextHit.setEnabled(false);
            if (hits.currentHit > 0)
                menuItemGoPreviousHit.setEnabled(true);
            else
                menuItemGoPreviousHit.setEnabled(false);
        } else {
            menuItemGoResult.setEnabled(false);
            menuItemGoNextHit.setEnabled(false);
            menuItemGoPreviousHit.setEnabled(false);
        }
        if (codeArea.hasSelection()) {
            menuItemCopyCode.setEnabled(true);
            menuItemCopyText.setEnabled(true);
        } else {
            menuItemCopyCode.setEnabled(false);
            menuItemCopyText.setEnabled(false);
        }
    }

    public void dialogOpcoes() {

        dialogOpcoes = new JDialog();
        dialogOpcoes.setModal(true);
        dialogOpcoes.setTitle(Messages.getString("HexViewerPlus.settings") + " - " + appName);
        dialogOpcoes.setBounds(0, 0, 810, 600);

        String selectColorText = Messages.getString("HexViewerPlus.selectColor");

        cursorExample = new CursorComponent(codeArea);

        JLabel lblFormato = new JLabel(Messages.getString("HexViewerPlus.position"));
        JLabel lblConteudo = new JLabel(Messages.getString("HexViewerPlus.codeArea"));
        JLabel lblCabecalho = new JLabel(Messages.getString("HexViewerPlus.header"));
        JLabel lblBackground = new JLabel(Messages.getString("HexViewerPlus.layout"));
        JLabel lblCursor = new JLabel(Messages.getString("HexViewerPlus.cursor"));
        JLabel lblMode = new JLabel(Messages.getString("HexViewerPlus.mode"));
        JLabel lblFont = new JLabel(Messages.getString("HexViewerPlus.font"));
        JLabel lblTamFont = new JLabel(Messages.getString("HexViewerPlus.size"));
        JLabel lblTamCursor = new JLabel(Messages.getString("HexViewerPlus.blink"));
        JLabel lblTamLine = new JLabel(Messages.getString("HexViewerPlus.bytesPerLine"));
        JLabel lblColorCursor = new JLabel(Messages.getString("HexViewerPlus.cursorColor"));
        JLabel lblColorFont = new JLabel(Messages.getString("HexViewerPlus.fontColor"));
        JLabel lblColorBackground = new JLabel(Messages.getString("HexViewerPlus.backGroundColor"));
        JLabel lblSelectionMirror = new JLabel(Messages.getString("HexViewerPlus.selectionCharactersMirror"));
        JLabel lblColorSelectionMirrorText = new JLabel(Messages.getString("HexViewerPlus.fontColor"));
        JLabel lblColorSelectionMirrorBackground = new JLabel(Messages.getString("HexViewerPlus.backGroundColor"));
        JLabel lblSelectionMain = new JLabel(Messages.getString("HexViewerPlus.selectionCharactersMain"));
        JLabel lblHeaderColor = new JLabel(Messages.getString("HexViewerPlus.headerLineBackground"));
        JLabel lblColorHeaderText = new JLabel(Messages.getString("HexViewerPlus.fontColor"));
        JLabel lblColorHeaderBackground = new JLabel(Messages.getString("HexViewerPlus.backGroundColor"));
        JLabel lblColorSelectionMainText = new JLabel(Messages.getString("HexViewerPlus.fontColor"));
        JLabel lblColorSelectionMainBackground = new JLabel(Messages.getString("HexViewerPlus.backGroundColor"));
        JLabel lblColorFoundMatch = new JLabel(Messages.getString("HexViewerPlus.searchHitsFoundMatch"));
        JLabel lblColorCurrentMatch = new JLabel(Messages.getString("HexViewerPlus.searchHitsCurrentMatch"));
        JLabel lblColorFoundMatchText = new JLabel(Messages.getString("HexViewerPlus.fontColor"));
        JLabel lblColorCurrentMatchText = new JLabel(Messages.getString("HexViewerPlus.fontColor"));
        JLabel lblColorFoundMatchBackground = new JLabel(Messages.getString("HexViewerPlus.backGroundColor"));
        JLabel lblColorCurrentMatchBackground = new JLabel(Messages.getString("HexViewerPlus.backGroundColor"));

        JButton buttonLoadDefault = new JButton(Messages.getString("HexViewerPlus.loadDefault"));
        JButton buttonSaveDefault = new JButton(Messages.getString("HexViewerPlus.saveDefault"));
        JButton buttonLoad = new JButton(Messages.getString("HexViewerPlus.loadFile"));
        JButton buttonSave = new JButton(Messages.getString("HexViewerPlus.saveFile"));
        JButton buttonCancel = new JButton(Messages.getString("HexViewerPlus.close"));

        hexCharactersCaseCheckBox = new JCheckBox(Messages.getString("HexViewerPlus.hexLowerCase"), false);
        showHeaderCheckBox = new JCheckBox(Messages.getString("HexViewerPlus.showHeader"), true);
        showLineNumbersCheckBox = new JCheckBox(Messages.getString("HexViewerPlus.showLines"), true);
        wrapLineModeCheckBox = new JCheckBox(Messages.getString("HexViewerPlus.lineBreak"), true);
        showUnprintableCharactersCheckBox = new JCheckBox(Messages.getString("HexViewerPlus.showAllCharacters"), false);
        showLineNumberBackgroundCheckBox = new JCheckBox(Messages.getString("HexViewerPlus.showLineNumberBackground"),
                true);
        showPositionBarCheckBox = new JCheckBox(Messages.getString("HexViewerPlus.showPositionBar"), true);
        showSelectionBarCheckBox = new JCheckBox(Messages.getString("HexViewerPlus.showSelectionBar"), true);

        modeComboBox = new JComboBox<>();
        codeTypeComboBox = new JComboBox<>();
        positionCodeTypeComboBox = new JComboBox<>();
        backGroundComboBox = new JComboBox<>();
        fontComboBox = new JComboBox<>();
        formatoNumeroComboBox = new JComboBox<>();
        cursorComboBox = new JComboBox<>();

        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(fontSize, 1, 50, 1));
        cursorBlinkSpinner = new JSpinner(new SpinnerNumberModel(codeArea.getCaret().getBlinkRate(), 50, 950, 50));
        lineSizeSpinner = new JSpinner(new SpinnerNumberModel(codeArea.getLineLength(), 1, 999, 1));

        btnColorSelectionMainText = new RoundButton("", codeArea.getSelectionColors().getTextColor());
        btnColorSelectionMainBackground = new RoundButton("", codeArea.getSelectionColors().getBackgroundColor());
        btnColorSelectionMirrorText = new RoundButton("", codeArea.getMirrorSelectionColors().getTextColor());
        btnColorSelectionMirrorBackground = new RoundButton("",
                codeArea.getMirrorSelectionColors().getBackgroundColor());
        btnColorFoundMatchBackground = new RoundButton("", painter.getFoundMatchesBackgroundColor());
        btnColorCurrentMatchBackground = new RoundButton("", painter.getCurrentMatchBackgroundColor());
        btnColorFoundMatchText = new RoundButton("", painter.getFoundMatchesTextColor());
        btnColorCurrentMatchText = new RoundButton("", painter.getCurrentMatchTextColor());
        btnColorFontMain = new RoundButton("", defaultSettings.ColorFontMain);
        btnColorFontAlt = new RoundButton("", codeArea.getAlternateColors().getTextColor());
        btnColorCursor = new RoundButton("", codeArea.getCursorColor());
        btnColorBackgroundMain = new RoundButton("", codeArea.getMainColors().getBackgroundColor());
        btnColorBackgroundAlt = new RoundButton("", codeArea.getAlternateColors().getBackgroundColor());
        btnColorHeaderText = new RoundButton("", codeArea.getForeground());
        btnColorHeaderBackground = new RoundButton("", codeArea.getBackground());

        List<String> fonts = Arrays
                .asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        String[] cursors = new String[CursorShape.values().length];
        int i = 0;
        for (Enum e : CursorShape.values()) {
            cursors[i++] = e.toString();
        }

        // LISTENERS
        modeComboBox.setModel(
                new javax.swing.DefaultComboBoxModel<>(new String[] { "DUAL", "CODE_MATRIX", "TEXT_PREVIEW" }));
        modeComboBox.setSelectedIndex(0);
        modeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeArea.setViewMode(ViewMode.values()[modeComboBox.getSelectedIndex()]);
            }
        });

        codeTypeComboBox.setModel(
                new javax.swing.DefaultComboBoxModel<>(new String[] { "BINARY", "OCTAL", "DECIMAL", "HEXADECIMAL" }));
        codeTypeComboBox.setSelectedIndex(3);
        codeTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeArea.setCodeType(CodeType.values()[codeTypeComboBox.getSelectedIndex()]);
            }
        });

        positionCodeTypeComboBox
                .setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "OCTAL", "DECIMAL", "HEXADECIMAL" }));
        positionCodeTypeComboBox.setSelectedIndex(2);
        positionCodeTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeArea.setPositionCodeType(PositionCodeType.values()[positionCodeTypeComboBox.getSelectedIndex()]);
            }
        });

        backGroundComboBox.setModel(
                new javax.swing.DefaultComboBoxModel<>(new String[] { "NONE", "PLAIN", "STRIPPED", "GRIDDED" }));
        backGroundComboBox.setSelectedIndex(2);
        backGroundComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeArea.setBackgroundMode(BackgroundMode.values()[backGroundComboBox.getSelectedIndex()]);
            }
        });

        cursorComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(cursors));
        cursorComboBox.setSelectedIndex(2);
        cursorComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                codeArea.getCaret().setOverwriteCursorShape(CursorShape.values()[cursorComboBox.getSelectedIndex()]);
                codeArea.getCaret().setInsertCursorShape(CursorShape.values()[cursorComboBox.getSelectedIndex()]);
            }
        });

        fontComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>((String[]) fonts.toArray()));
        fontComboBox.setSelectedItem(getAndVerifyFontName(font, fonts));
        fontComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                font = getAndVerifyFontName((String) fontComboBox.getSelectedItem(), fonts);
                codeArea.setFont(new Font(font, Font.PLAIN, fontSize));
            }
        });

        fontSizeSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                fontSize = (Integer) ((JSpinner) e.getSource()).getValue();
                codeArea.setFont(new Font(font, Font.PLAIN, fontSize));
            }
        });

        cursorBlinkSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int blink = (Integer) ((JSpinner) e.getSource()).getValue();
                codeArea.getCaret().setBlinkRate(blink);
                cursorExample.setBlinkRate(blink);
            }
        });

        lineSizeSpinner.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int line = (Integer) ((JSpinner) e.getSource()).getValue();
                codeArea.setLineLength(line);
            }
        });

        formatoNumeroComboBox.setModel(
                new javax.swing.DefaultComboBoxModel<>(new String[] { "BINARY", "OCTAL", "DECIMAL", "HEXADECIMAL" }));
        formatoNumeroComboBox.setSelectedIndex(3);
        formatoNumeroComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                switch (formatoNumeroComboBox.getSelectedIndex()) {
                    case 0: {
                        formatoNumero = "%s";
                        if (!posicao.getText().isEmpty())
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
                        if (!posicao.getText().isEmpty())
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
                        if (!posicao.getText().isEmpty())
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
                        if (!posicao.getText().isEmpty())
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

        wrapLineModeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                codeArea.setWrapMode(!codeArea.isWrapMode());
            }
        });

        showUnprintableCharactersCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                codeArea.setShowUnprintableCharacters(!codeArea.isShowUnprintableCharacters());
            }
        });

        showLineNumberBackgroundCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                codeArea.setLineNumberBackground(!codeArea.isLineNumberBackground());
            }
        });

        hexCharactersCaseCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                if (codeArea.getHexCharactersCase() == org.exbin.deltahex.HexCharactersCase.LOWER)
                    codeArea.setHexCharactersCase(org.exbin.deltahex.HexCharactersCase.UPPER);
                else
                    codeArea.setHexCharactersCase(org.exbin.deltahex.HexCharactersCase.LOWER);
            }
        });

        showHeaderCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                codeArea.setShowHeader(!codeArea.isShowHeader());
            }
        });

        showLineNumbersCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                codeArea.setShowLineNumbers(!codeArea.isShowLineNumbers());
            }
        });

        showPositionBarCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                bottomPanel1.setVisible(!bottomPanel1.isVisible());
                codeArea.repaint();
            }
        });

        showSelectionBarCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                bottomPanel2.setVisible(!bottomPanel2.isVisible());
                codeArea.repaint();
            }
        });

        btnColorFontMain.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        codeArea.getMainColors().getTextColor());
                if (c != null) {
                    ColorsGroup cg = codeArea.getMainColors();
                    cg.setTextColor(c);
                    cg.setUnprintablesColor(c);
                    codeArea.setMainColors(cg);
                    btnColorFontMain.setBackground(c);
                }
            }
        });

        btnColorFontAlt.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        codeArea.getAlternateColors().getTextColor());
                if (c != null) {
                    ColorsGroup cg = codeArea.getAlternateColors();
                    cg.setTextColor(c);
                    cg.setUnprintablesColor(c);
                    codeArea.setAlternateColors(cg);
                    btnColorFontAlt.setBackground(c);
                }
            }
        });

        btnColorCursor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText, codeArea.getCursorColor());
                if (c != null) {
                    codeArea.setCursorColor(c);
                    btnColorCursor.setBackground(c);
                }
            }
        });

        btnColorBackgroundMain.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        codeArea.getMainColors().getBackgroundColor());
                if (c != null) {
                    ColorsGroup cg = codeArea.getMainColors();
                    cg.setBackgroundColor(c);
                    cg.setUnprintablesBackgroundColor(c);
                    codeArea.setMainColors(cg);
                    btnColorBackgroundMain.setBackground(c);
                }
            }
        });

        btnColorBackgroundAlt.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        codeArea.getAlternateColors().getBackgroundColor());
                if (c != null) {
                    ColorsGroup cg = codeArea.getAlternateColors();
                    cg.setBackgroundColor(c);
                    cg.setUnprintablesBackgroundColor(c);
                    codeArea.setAlternateColors(cg);
                    btnColorBackgroundAlt.setBackground(c);
                }
            }
        });

        btnColorHeaderBackground.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText, codeArea.getBackground());
                if (c != null) {
                    codeArea.setBackground(c);
                    btnColorHeaderBackground.setBackground(c);
                }
            }
        });

        btnColorHeaderText.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText, codeArea.getForeground());
                if (c != null) {
                    codeArea.setForeground(c);
                    btnColorHeaderText.setBackground(c);
                }
            }
        });

        btnColorSelectionMainBackground.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        codeArea.getSelectionColors().getBackgroundColor());
                if (c != null) {
                    ColorsGroup cg = codeArea.getSelectionColors();
                    cg.setBackgroundColor(c);
                    cg.setUnprintablesBackgroundColor(c);
                    codeArea.setSelectionColors(cg);
                    btnColorSelectionMainBackground.setBackground(c);
                }
            }
        });

        btnColorSelectionMainText.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        codeArea.getSelectionColors().getTextColor());
                if (c != null) {
                    ColorsGroup cg = codeArea.getSelectionColors();
                    cg.setTextColor(c);
                    cg.setUnprintablesColor(c);
                    codeArea.setSelectionColors(cg);
                    btnColorSelectionMainText.setBackground(c);
                }
            }
        });

        btnColorSelectionMirrorBackground.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        codeArea.getMirrorSelectionColors().getBackgroundColor());
                if (c != null) {
                    ColorsGroup cg = codeArea.getMirrorSelectionColors();
                    cg.setBackgroundColor(c);
                    cg.setUnprintablesBackgroundColor(c);
                    codeArea.setMirrorSelectionColors(cg);
                    btnColorSelectionMirrorBackground.setBackground(c);
                }
            }
        });

        btnColorSelectionMirrorText.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        codeArea.getMirrorSelectionColors().getTextColor());
                if (c != null) {
                    ColorsGroup cg = codeArea.getMirrorSelectionColors();
                    cg.setTextColor(c);
                    cg.setUnprintablesColor(c);
                    codeArea.setMirrorSelectionColors(cg);
                    btnColorSelectionMirrorText.setBackground(c);
                }
            }
        });

        btnColorFoundMatchText.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText, painter.getFoundMatchesTextColor());
                if (c != null) {
                    painter.setFoundMatchesTextColor(c);
                    painter.setFoundMatchesUnprintablesTextColor(c);
                    btnColorFoundMatchText.setBackground(c);
                    codeArea.repaint();
                }
            }
        });

        btnColorFoundMatchBackground.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        painter.getFoundMatchesBackgroundColor());
                if (c != null) {
                    painter.setFoundMatchesBackgroundColor(c);
                    painter.setFoundMatchesUnprintablesBackgroundColor(c);
                    btnColorFoundMatchBackground.setBackground(c);
                    codeArea.repaint();
                }
            }
        });

        btnColorCurrentMatchText.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText, painter.getCurrentMatchTextColor());
                if (c != null) {
                    painter.setCurrentMatchTextColor(c);
                    painter.setCurrentMatchUnprintablesTextColor(c);
                    btnColorCurrentMatchText.setBackground(c);
                    codeArea.repaint();
                }
            }
        });

        btnColorCurrentMatchBackground.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Color c = JColorChooser.showDialog(dialogOpcoes, selectColorText,
                        painter.getCurrentMatchBackgroundColor());
                if (c != null) {
                    painter.setCurrentMatchBackgroundColor(c);
                    painter.setCurrentMatchUnprintablesBackgroundColor(c);
                    btnColorCurrentMatchBackground.setBackground(c);
                    codeArea.repaint();
                }
            }
        });

        buttonLoadDefault.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                loadSettings(defaultSettings);
                codeArea.repaint();
            }
        });

        buttonSaveDefault.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                saveSettings(defaultSettings);

                // Try persist the defaults on disc
                if (defaultSettingsFileExists) {
                    HVPSettings.saveObject(defaultSettingsPath, defaultSettings);
                }

            }
        });

        buttonLoad.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                String caminho = "";
                int retDialog = 0;

                String openPath = ""; // Initialize openPath with "defaultSettingsPath' to open IPED root path
                JFileChooser jfc = new JFileChooser(openPath);
                jfc.setFileFilter(new customFileFilter(extensoes, descricaoExtensoes));
                jfc.setFileView(new ImageFileView());
                jfc.setDialogTitle(Messages.getString("HexViewerPlus.loadFile"));
                retDialog = jfc.showOpenDialog(dialogOpcoes);
                if (retDialog == JFileChooser.APPROVE_OPTION) {
                    caminho = jfc.getCurrentDirectory().toString() + File.separator + jfc.getSelectedFile().getName();

                    HVPSettings objHVP = HVPSettings.loadObject(caminho);
                    if (objHVP != null) {
                        loadSettings(objHVP);
                        codeArea.repaint();
                    } else {
                        JOptionPane.showMessageDialog(dialogOpcoes, Messages.getString("HexViewerPlus.failOpenFile"),
                                appName, JOptionPane.ERROR_MESSAGE);
                    }

                    objHVP = null;
                    jfc = null;
                }
                jfc = null;

            }
        });

        buttonSave.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                String caminho = "";
                int retDialog = 0;

                String openPath = ""; // Initialize openPath with "defaultSettingsPath' to open IPED root path
                JFileChooser jfc = new JFileChooser(openPath);
                jfc.setFileFilter(new customFileFilter(extensoes, descricaoExtensoes));
                jfc.setFileView(new ImageFileView());
                jfc.setDialogTitle(Messages.getString("HexViewerPlus.saveFile"));
                retDialog = jfc.showSaveDialog(dialogOpcoes);
                if (retDialog == JFileChooser.APPROVE_OPTION) {
                    caminho = jfc.getCurrentDirectory().toString() + File.separator + jfc.getSelectedFile().getName();
                    if (!caminho.endsWith(extensao)) {
                        caminho += extensao;
                    }
                    if (verificarSalvarArquivo(caminho))
                        return;
                    HVPSettings objHVP = new HVPSettings();
                    saveSettings(objHVP);
                    if (!HVPSettings.saveObject(caminho, objHVP)) {
                        JOptionPane.showMessageDialog(dialogOpcoes, Messages.getString("HexViewerPlus.failSaveFile"),
                                appName, JOptionPane.ERROR_MESSAGE);
                    }
                    objHVP = null;
                    jfc = null;
                }
                jfc = null;
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dialogOpcoes.setVisible(false);
            }
        });

        int roundButtonSize = 28;
        int space = 30;
        int coluna = 0;
        int linha = 20;
        int size1 = 0;
        int size2 = 0;
        int gap = 30;
        int sep = 10;

        int line_space = 40;

        //
        size1 = 220;
        coluna = 30;
        showPositionBarCheckBox.setBounds(coluna, linha, size1, space);

        coluna += size1 + gap;
        showSelectionBarCheckBox.setBounds(coluna, linha, size1, space);

        coluna += size1 + gap + sep;
        showLineNumberBackgroundCheckBox.setBounds(coluna, linha, size1, space);

        //
        size1 = 220;
        coluna = 30;
        linha += line_space;
        wrapLineModeCheckBox.setBounds(coluna, linha, size1, space);

        coluna += size1 + gap;
        showUnprintableCharactersCheckBox.setBounds(coluna, linha, size1, space);

        coluna += size1 + gap + sep;
        hexCharactersCaseCheckBox.setBounds(coluna, linha, size1, space);

        //

        size1 = 60;
        size2 = 150;
        linha += line_space;
        coluna = 30;
        lblMode.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        modeComboBox.setBounds(coluna, linha, size2, space);

        coluna += size2 + gap;
        size1 = 220;
        showHeaderCheckBox.setBounds(coluna, linha, size1, space);

        coluna += size1 + gap + sep;
        showLineNumbersCheckBox.setBounds(coluna, linha, size1, space);

        //
        size1 = 60;
        size2 = 150;
        linha += line_space;
        coluna = 30;
        lblFormato.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        formatoNumeroComboBox.setBounds(coluna, linha, size2, space);

        coluna += size2 + gap;
        size1 = 100;
        lblConteudo.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        size2 = 120;
        codeTypeComboBox.setBounds(coluna, linha, size2, space);

        coluna += size2 + gap;
        lblCabecalho.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        positionCodeTypeComboBox.setBounds(coluna, linha, size2, space);

        //
        size1 = 60;
        size2 = 150;
        linha += line_space;
        coluna = 30;
        lblFont.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        fontComboBox.setBounds(coluna, linha, size2, space);

        coluna += size2 + gap;
        size1 = 100;
        lblTamFont.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        size2 = 120;
        fontSizeSpinner.setBounds(coluna, linha, size2, space);

        size1 = 100;
        coluna += size2 + gap;
        lblColorFont.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + sep + sep;
        btnColorFontMain.setBounds(coluna, linha, roundButtonSize, roundButtonSize);
        coluna += size2 + gap;
        btnColorFontAlt.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        //
        size1 = 60;
        size2 = 150;
        linha += line_space;
        coluna = 30;
        lblBackground.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        backGroundComboBox.setBounds(coluna, linha, size2, space);

        coluna += size2 + gap;
        size1 = 100;
        lblTamLine.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        size2 = 120;
        lineSizeSpinner.setBounds(coluna, linha, size2, space);

        size1 = 100;
        coluna += size2 + gap;
        lblColorBackground.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + sep + sep;
        btnColorBackgroundMain.setBounds(coluna, linha, roundButtonSize, roundButtonSize);
        coluna += size2 + gap;
        btnColorBackgroundAlt.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        //
        size1 = 60;
        size2 = 150;
        linha += line_space;
        coluna = 30;
        lblCursor.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        cursorComboBox.setBounds(coluna, linha, size2, space);

        coluna += size2 + gap;
        size1 = 100;
        lblTamCursor.setBounds(coluna, linha, size1, space);
        coluna += size1 + sep;
        size2 = 120;
        cursorBlinkSpinner.setBounds(coluna, linha, size2, space);

        size1 = 120;
        coluna += size2 + gap;
        lblColorCursor.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorCursor.setBounds(coluna, linha, roundButtonSize, roundButtonSize);
        coluna += size2 + sep;
        cursorExample.setBounds(coluna + 25, linha + 8, roundButtonSize, roundButtonSize);

        //
        size1 = 220;
        size2 = 0;
        linha += line_space;
        coluna = 30;
        lblHeaderColor.setBounds(coluna, linha, size1, space);
        coluna += size1;

        size1 = 120;
        coluna += size2 + gap;
        lblColorHeaderText.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorHeaderText.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        size1 = 120;
        coluna += 110 + gap + -(3 * sep);
        lblColorHeaderBackground.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorHeaderBackground.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        //
        size1 = 220;
        size2 = 0;
        linha += line_space;
        coluna = 30;
        lblSelectionMain.setBounds(coluna, linha, size1, space);
        coluna += size1;

        size1 = 120;
        coluna += size2 + gap;
        lblColorSelectionMainText.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorSelectionMainText.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        size1 = 120;
        coluna += 110 + gap + -(3 * sep);
        lblColorSelectionMainBackground.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorSelectionMainBackground.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        //
        size1 = 220;
        size2 = 0;
        linha += line_space;
        coluna = 30;
        lblSelectionMirror.setBounds(coluna, linha, size1, space);
        coluna += size1;

        size1 = 120;
        coluna += size2 + gap;
        lblColorSelectionMirrorText.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorSelectionMirrorText.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        size1 = 120;
        coluna += 110 + gap + -(3 * sep);
        lblColorSelectionMirrorBackground.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorSelectionMirrorBackground.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        //
        size1 = 220;
        size2 = 0;
        linha += line_space;
        coluna = 30;
        lblColorCurrentMatch.setBounds(coluna, linha, size1, space);
        coluna += size1;

        size1 = 120;
        coluna += size2 + gap;
        lblColorCurrentMatchText.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorCurrentMatchText.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        size1 = 120;
        coluna += 110 + gap + -(3 * sep);
        lblColorCurrentMatchBackground.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorCurrentMatchBackground.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        //
        size1 = 220;
        size2 = 0;
        linha += line_space;
        coluna = 30;
        lblColorFoundMatch.setBounds(coluna, linha, size1, space);
        coluna += size1;

        size1 = 120;
        coluna += size2 + gap;
        lblColorFoundMatchText.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorFoundMatchText.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        size1 = 120;
        coluna += 110 + gap + -(3 * sep);
        lblColorFoundMatchBackground.setBounds(coluna, linha, size1, space);
        size2 = space;
        coluna += size1 + (3 * sep);
        btnColorFoundMatchBackground.setBounds(coluna, linha, roundButtonSize, roundButtonSize);

        //
        size1 = 120;
        linha += line_space + 10;
        coluna = 35;
        buttonLoadDefault.setBounds(coluna, linha, size1, space);
        coluna += size1 + gap;
        buttonSaveDefault.setBounds(coluna, linha, size1, space);
        coluna += size1 + gap;
        buttonLoad.setBounds(coluna, linha, size1, space);
        coluna += size1 + gap;
        buttonSave.setBounds(coluna, linha, size1, space);
        coluna += size1 + gap;
        buttonCancel.setBounds(coluna, linha, size1, space);

        dialogOpcoes.getContentPane().add(lblSelectionMain);
        dialogOpcoes.getContentPane().add(lblHeaderColor);
        dialogOpcoes.getContentPane().add(lblColorSelectionMainText);
        dialogOpcoes.getContentPane().add(lblColorHeaderBackground);
        dialogOpcoes.getContentPane().add(lblColorHeaderText);
        dialogOpcoes.getContentPane().add(btnColorSelectionMainText);
        dialogOpcoes.getContentPane().add(lblColorSelectionMainBackground);
        dialogOpcoes.getContentPane().add(btnColorSelectionMainBackground);
        dialogOpcoes.getContentPane().add(btnColorHeaderText);
        dialogOpcoes.getContentPane().add(btnColorHeaderBackground);
        dialogOpcoes.getContentPane().add(lblSelectionMirror);
        dialogOpcoes.getContentPane().add(lblColorSelectionMirrorText);
        dialogOpcoes.getContentPane().add(btnColorSelectionMirrorText);
        dialogOpcoes.getContentPane().add(lblColorSelectionMirrorBackground);
        dialogOpcoes.getContentPane().add(btnColorSelectionMirrorBackground);
        dialogOpcoes.getContentPane().add(lblColorFoundMatch);
        dialogOpcoes.getContentPane().add(lblColorCurrentMatch);
        dialogOpcoes.getContentPane().add(lblColorFoundMatchText);
        dialogOpcoes.getContentPane().add(lblColorFoundMatchBackground);
        dialogOpcoes.getContentPane().add(btnColorFoundMatchText);
        dialogOpcoes.getContentPane().add(btnColorFoundMatchBackground);
        dialogOpcoes.getContentPane().add(lblColorCurrentMatchText);
        dialogOpcoes.getContentPane().add(btnColorCurrentMatchText);
        dialogOpcoes.getContentPane().add(lblColorCurrentMatchBackground);
        dialogOpcoes.getContentPane().add(btnColorCurrentMatchBackground);
        dialogOpcoes.getContentPane().add(cursorExample);
        dialogOpcoes.getContentPane().add(fontComboBox);
        dialogOpcoes.getContentPane().add(lblTamFont);
        dialogOpcoes.getContentPane().add(lblColorFont);
        dialogOpcoes.getContentPane().add(btnColorFontMain);
        dialogOpcoes.getContentPane().add(btnColorFontAlt);
        dialogOpcoes.getContentPane().add(lblColorBackground);
        dialogOpcoes.getContentPane().add(lblTamLine);
        dialogOpcoes.getContentPane().add(btnColorBackgroundMain);
        dialogOpcoes.getContentPane().add(btnColorBackgroundAlt);
        dialogOpcoes.getContentPane().add(lineSizeSpinner);
        dialogOpcoes.getContentPane().add(lblTamCursor);
        dialogOpcoes.getContentPane().add(lblColorCursor);
        dialogOpcoes.getContentPane().add(cursorBlinkSpinner);
        dialogOpcoes.getContentPane().add(btnColorCursor);
        dialogOpcoes.getContentPane().add(lblMode);
        dialogOpcoes.getContentPane().add(modeComboBox);
        dialogOpcoes.getContentPane().add(lblFormato);
        dialogOpcoes.getContentPane().add(formatoNumeroComboBox);
        dialogOpcoes.getContentPane().add(lblConteudo);
        dialogOpcoes.getContentPane().add(codeTypeComboBox);
        dialogOpcoes.getContentPane().add(lblCabecalho);
        dialogOpcoes.getContentPane().add(lblBackground);
        dialogOpcoes.getContentPane().add(backGroundComboBox);
        dialogOpcoes.getContentPane().add(lblCursor);
        dialogOpcoes.getContentPane().add(cursorComboBox);
        dialogOpcoes.getContentPane().add(lblFont);
        dialogOpcoes.getContentPane().add(fontSizeSpinner);
        dialogOpcoes.getContentPane().add(positionCodeTypeComboBox);
        dialogOpcoes.getContentPane().add(wrapLineModeCheckBox);
        dialogOpcoes.getContentPane().add(showUnprintableCharactersCheckBox);
        dialogOpcoes.getContentPane().add(showLineNumberBackgroundCheckBox);
        dialogOpcoes.getContentPane().add(hexCharactersCaseCheckBox);
        dialogOpcoes.getContentPane().add(showHeaderCheckBox);
        dialogOpcoes.getContentPane().add(showLineNumbersCheckBox);
        dialogOpcoes.getContentPane().add(buttonLoadDefault);
        dialogOpcoes.getContentPane().add(buttonSaveDefault);
        dialogOpcoes.getContentPane().add(buttonLoad);
        dialogOpcoes.getContentPane().add(buttonSave);
        dialogOpcoes.getContentPane().add(buttonCancel);
        dialogOpcoes.getContentPane().add(showPositionBarCheckBox);
        dialogOpcoes.getContentPane().add(showSelectionBarCheckBox);
        dialogOpcoes.getContentPane().add(new JLabel(""));

        dialogOpcoes.setLocationRelativeTo(codeArea);

        // Close window with ESC key
        JRootPane rootPane = dialogOpcoes.getRootPane();
        InputMap iMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");

        ActionMap aMap = rootPane.getActionMap();
        aMap.put("escape", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dialogOpcoes.setVisible(false);
            };
        });

    }

    public static String getAndVerifyFontName(String name, List<String> fonts) {

        if (fonts != null) {
            int index = fonts.indexOf(name);
            if (index != -1)
                return fonts.get(index);
            else
                return Font.MONOSPACED;
        } else {
            return Font.MONOSPACED;
        }
    }

    public boolean verificarSalvarArquivo(String arquivo) {

        Object[] options = { Messages.getString("HexViewerPlus.yes"), Messages.getString("HexViewerPlus.no") };
        File fileName = new File(arquivo);

        if (fileName != null && fileName.exists()) {
            fileName = null;
            int n = JOptionPane.showOptionDialog(dialogOpcoes,
                    "'" + arquivo + "' " + Messages.getString("HexViewerPlus.overWriteFile"), appName,
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
            if (n == JOptionPane.NO_OPTION)
                return true;
        }

        fileName = null;
        return false;
    }

    public void loadSettings(HVPSettings values) {

        modeComboBox.setSelectedIndex(values.mode);
        codeTypeComboBox.setSelectedIndex(values.codeType);
        positionCodeTypeComboBox.setSelectedIndex(values.positionCodeType);
        backGroundComboBox.setSelectedIndex(values.backGround);
        fontComboBox.setSelectedItem(values.font);
        formatoNumeroComboBox.setSelectedIndex(values.formatoNumero);
        cursorComboBox.setSelectedIndex(values.cursor);

        showPositionBarCheckBox.setSelected(values.showPositionBar);
        showSelectionBarCheckBox.setSelected(values.showSelectionBar);
        hexCharactersCaseCheckBox.setSelected(values.hexCharactersCase);
        showHeaderCheckBox.setSelected(values.showHeader);
        showLineNumbersCheckBox.setSelected(values.showLineNumbers);
        wrapLineModeCheckBox.setSelected(values.wrapLineMode);
        showUnprintableCharactersCheckBox.setSelected(values.showUnprintableCharacters);
        showLineNumberBackgroundCheckBox.setSelected(values.showLineNumberBackground);

        fontSizeSpinner.setValue(values.fontSize);
        cursorBlinkSpinner.setValue(values.cursorBlink);
        lineSizeSpinner.setValue(values.lineSize);

        ColorsGroup cg;

        cg = codeArea.getMainColors();
        cg.setTextColor(values.ColorFontMain);
        cg.setBackgroundColor(values.ColorBackgroundMain);
        cg.setUnprintablesColor(values.ColorFontMain);
        cg.setUnprintablesBackgroundColor(values.ColorBackgroundMain);
        codeArea.setMainColors(cg);
        btnColorFontMain.setBackground(values.ColorFontMain);
        btnColorBackgroundMain.setBackground(values.ColorBackgroundMain);

        cg = codeArea.getAlternateColors();
        cg.setTextColor(values.ColorFontAlt);
        cg.setBackgroundColor(values.ColorBackgroundAlt);
        cg.setUnprintablesColor(values.ColorFontAlt);
        cg.setUnprintablesBackgroundColor(values.ColorBackgroundAlt);
        codeArea.setAlternateColors(cg);
        btnColorFontAlt.setBackground(values.ColorFontAlt);
        btnColorBackgroundAlt.setBackground(values.ColorBackgroundAlt);

        cg = codeArea.getSelectionColors();
        cg.setTextColor(values.ColorSelectionMainText);
        cg.setBackgroundColor(values.ColorSelectionMainBackground);
        cg.setUnprintablesColor(values.ColorSelectionMainText);
        cg.setUnprintablesBackgroundColor(values.ColorSelectionMainBackground);
        codeArea.setSelectionColors(cg);
        btnColorSelectionMainText.setBackground(values.ColorSelectionMainText);
        btnColorSelectionMainBackground.setBackground(values.ColorSelectionMainBackground);

        cg = codeArea.getMirrorSelectionColors();
        cg.setTextColor(values.ColorSelectionMirrorText);
        cg.setBackgroundColor(values.ColorSelectionMirrorBackground);
        cg.setUnprintablesColor(values.ColorSelectionMirrorText);
        cg.setUnprintablesBackgroundColor(values.ColorSelectionMirrorBackground);
        codeArea.setMirrorSelectionColors(cg);
        btnColorSelectionMirrorText.setBackground(values.ColorSelectionMirrorText);
        btnColorSelectionMirrorBackground.setBackground(values.ColorSelectionMirrorBackground);

        painter.setFoundMatchesTextColor(values.ColorFoundMatchText);
        painter.setFoundMatchesUnprintablesTextColor(values.ColorFoundMatchText);
        painter.setFoundMatchesBackgroundColor(values.ColorFoundMatchBackground);
        painter.setFoundMatchesUnprintablesBackgroundColor(values.ColorFoundMatchBackground);
        btnColorFoundMatchText.setBackground(values.ColorFoundMatchText);
        btnColorFoundMatchBackground.setBackground(values.ColorFoundMatchBackground);

        painter.setCurrentMatchTextColor(values.ColorCurrentMatchText);
        painter.setCurrentMatchUnprintablesBackgroundColor(values.ColorCurrentMatchText);
        painter.setCurrentMatchBackgroundColor(values.ColorCurrentMatchBackground);
        painter.setCurrentMatchUnprintablesBackgroundColor(values.ColorCurrentMatchBackground);
        btnColorCurrentMatchText.setBackground(values.ColorCurrentMatchText);
        btnColorCurrentMatchBackground.setBackground(values.ColorCurrentMatchBackground);

        codeArea.setForeground(values.ColorHeaderText);
        btnColorHeaderText.setBackground(values.ColorHeaderText);

        codeArea.setBackground(values.ColorHeaderBackground);
        btnColorHeaderBackground.setBackground(values.ColorHeaderBackground);

        codeArea.setCursorColor(values.ColorCursor);
        btnColorCursor.setBackground(values.ColorCursor);

    }

    public void saveSettings(HVPSettings values) {

        values.mode = modeComboBox.getSelectedIndex();
        values.codeType = codeTypeComboBox.getSelectedIndex();
        values.positionCodeType = positionCodeTypeComboBox.getSelectedIndex();
        values.backGround = backGroundComboBox.getSelectedIndex();
        values.font = (String) fontComboBox.getSelectedItem();
        values.formatoNumero = formatoNumeroComboBox.getSelectedIndex();
        values.cursor = cursorComboBox.getSelectedIndex();

        values.showSelectionBar = showSelectionBarCheckBox.isSelected();
        values.showPositionBar = showPositionBarCheckBox.isSelected();
        values.hexCharactersCase = hexCharactersCaseCheckBox.isSelected();
        values.showHeader = showHeaderCheckBox.isSelected();
        values.showLineNumbers = showLineNumbersCheckBox.isSelected();
        values.wrapLineMode = wrapLineModeCheckBox.isSelected();
        values.showUnprintableCharacters = showUnprintableCharactersCheckBox.isSelected();
        values.showLineNumberBackground = showLineNumberBackgroundCheckBox.isSelected();

        values.fontSize = (Integer) fontSizeSpinner.getValue();
        values.cursorBlink = (Integer) cursorBlinkSpinner.getValue();
        values.lineSize = (Integer) lineSizeSpinner.getValue();

        values.ColorFontMain = codeArea.getMainColors().getTextColor();
        values.ColorFontAlt = codeArea.getAlternateColors().getTextColor();
        values.ColorBackgroundMain = codeArea.getMainColors().getBackgroundColor();
        values.ColorBackgroundAlt = codeArea.getAlternateColors().getBackgroundColor();
        values.ColorSelectionMainText = codeArea.getSelectionColors().getTextColor();
        values.ColorSelectionMainBackground = codeArea.getSelectionColors().getBackgroundColor();
        values.ColorSelectionMirrorText = codeArea.getMirrorSelectionColors().getTextColor();
        values.ColorSelectionMirrorBackground = codeArea.getMirrorSelectionColors().getBackgroundColor();

        values.ColorFoundMatchText = painter.getFoundMatchesTextColor();
        values.ColorFoundMatchBackground = painter.getFoundMatchesBackgroundColor();
        values.ColorCurrentMatchText = painter.getCurrentMatchTextColor();
        values.ColorCurrentMatchBackground = painter.getCurrentMatchBackgroundColor();
        values.ColorCursor = codeArea.getCursorColor();

        values.ColorHeaderText = codeArea.getForeground();
        values.ColorHeaderBackground = codeArea.getBackground();

    }

    public void dialogSelecionar() {

        dialogSelecionar = new JDialog();
        dialogSelecionar.setModal(true);
        dialogSelecionar.setTitle(Messages.getString("HexViewerPlus.selectBlock") + " - " + appName);
        dialogSelecionar.setBounds(0, 0, 550, 240);

        JLabel lblPosicao = new JLabel(Messages.getString("HexViewerPlus.type"));
        JRadioButton jrbInt = new JRadioButton(Messages.getString("HexViewerPlus.interval"));
        JRadioButton jrbTam = new JRadioButton(Messages.getString("HexViewerPlus.size"));
        ButtonGroup bg2 = new ButtonGroup();
        bg2.add(jrbInt);
        bg2.add(jrbTam);
        jrbInt.setSelected(true);

        JLabel lblEnd1 = new JLabel(Messages.getString("HexViewerPlus.startPosition"));
        HVPTextField jtfEnd1 = new HVPTextField();

        JLabel lblEnd2 = new JLabel(Messages.getString("HexViewerPlus.endPosition"));
        HVPTextField jtfEnd2 = new HVPTextField();

        jtfEnd1.setHorizontalAlignment(SwingConstants.RIGHT);
        jtfEnd2.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel lblTipo = new JLabel(Messages.getString("HexViewerPlus.format"));
        JRadioButton jrbHex = new JRadioButton(Messages.getString("HexViewerPlus.hexadecimal"));
        JRadioButton jrbDec = new JRadioButton(Messages.getString("HexViewerPlus.decimal"));
        JRadioButton jrbOct = new JRadioButton(Messages.getString("HexViewerPlus.octal"));
        ButtonGroup bg1 = new ButtonGroup();
        bg1.add(jrbHex);
        bg1.add(jrbDec);
        bg1.add(jrbOct);
        jrbDec.setSelected(true);

        jrbHex.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jtfEnd1.isValidNumber()) {
                    long aux = Long.parseLong(jtfEnd1.getText(), jtfEnd1.getBase());
                    jtfEnd1.setHexdecimalBase();
                    jtfEnd1.setText(Long.toHexString(aux).toUpperCase());
                } else {
                    jtfEnd1.setText("");
                    jtfEnd1.setHexdecimalBase();
                }
                if (jtfEnd2.isValidNumber()) {
                    long aux = Long.parseLong(jtfEnd2.getText(), jtfEnd2.getBase());
                    jtfEnd2.setHexdecimalBase();
                    jtfEnd2.setText(Long.toHexString(aux).toUpperCase());
                } else {
                    jtfEnd2.setText("");
                    jtfEnd2.setHexdecimalBase();
                }
            }
        });
        jrbDec.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jtfEnd1.isValidNumber()) {
                    long aux = Long.parseLong(jtfEnd1.getText(), jtfEnd1.getBase());
                    jtfEnd1.setDecimalBase();
                    jtfEnd1.setText(Long.toString(aux));
                } else {
                    jtfEnd1.setText("");
                    jtfEnd1.setDecimalBase();
                }
                if (jtfEnd2.isValidNumber()) {
                    long aux = Long.parseLong(jtfEnd2.getText(), jtfEnd2.getBase());
                    jtfEnd2.setDecimalBase();
                    jtfEnd2.setText(Long.toString(aux));
                } else {
                    jtfEnd2.setText("");
                    jtfEnd2.setDecimalBase();
                }
            }
        });
        jrbOct.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jtfEnd1.isValidNumber()) {
                    long aux = Long.parseLong(jtfEnd1.getText(), jtfEnd1.getBase());
                    jtfEnd1.setOctalBase();
                    jtfEnd1.setText(Long.toOctalString(aux));
                } else {
                    jtfEnd1.setText("");
                    jtfEnd1.setOctalBase();
                }
                if (jtfEnd2.isValidNumber()) {
                    long aux = Long.parseLong(jtfEnd2.getText(), jtfEnd2.getBase());
                    jtfEnd2.setOctalBase();
                    jtfEnd2.setText(Long.toOctalString(aux));
                } else {
                    jtfEnd2.setText("");
                    jtfEnd2.setOctalBase();
                }
            }
        });
        JButton buttonOK = new JButton(Messages.getString("HexViewerPlus.ok"));
        JButton buttonCancel = new JButton(Messages.getString("HexViewerPlus.cancel"));

        jrbInt.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                lblEnd2.setText(Messages.getString("HexViewerPlus.endPosition"));
            }
        });

        jrbTam.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                lblEnd2.setText(Messages.getString("HexViewerPlus.size"));
            }
        });

        lblEnd1.setBounds(15, 30, 100, 30);
        jtfEnd1.setBounds(120, 30, 130, 30);

        lblEnd2.setBounds(270, 30, 100, 30);
        jtfEnd2.setBounds(370, 30, 130, 30);

        lblPosicao.setBounds(15, 60, 100, 30);
        jrbInt.setBounds(100, 60, 100, 30);
        jrbTam.setBounds(200, 60, 100, 30);

        lblTipo.setBounds(15, 90, 100, 30);
        jrbHex.setBounds(100, 90, 100, 30);
        jrbDec.setBounds(200, 90, 100, 30);
        jrbOct.setBounds(300, 90, 100, 30);

        buttonOK.setBounds(170, 140, 80, 30);
        buttonCancel.setBounds(270, 140, 80, 30);

        dialogSelecionar.getContentPane().add(lblPosicao);
        dialogSelecionar.getContentPane().add(jrbInt);
        dialogSelecionar.getContentPane().add(jrbTam);
        dialogSelecionar.getContentPane().add(lblEnd1);
        dialogSelecionar.getContentPane().add(jtfEnd1);
        dialogSelecionar.getContentPane().add(lblEnd2);
        dialogSelecionar.getContentPane().add(jtfEnd2);
        dialogSelecionar.getContentPane().add(lblTipo);
        dialogSelecionar.getContentPane().add(jrbHex);
        dialogSelecionar.getContentPane().add(jrbDec);
        dialogSelecionar.getContentPane().add(jrbOct);
        dialogSelecionar.getContentPane().add(buttonOK);
        dialogSelecionar.getContentPane().add(buttonCancel);
        dialogSelecionar.getContentPane().add(new JLabel());

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jtfEnd1.requestFocus();
                dialogSelecionar.setVisible(false);
            }
        });

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                if (!jtfEnd1.isValidNumber()) {
                    JOptionPane.showMessageDialog(dialogSelecionar,
                            Messages.getString("HexViewerPlus.invalidStartPosition"), appName,
                            JOptionPane.ERROR_MESSAGE);
                    jtfEnd1.requestFocus();
                    return;
                }
                if (!jtfEnd2.isValidNumber()) {
                    jtfEnd2.requestFocus();
                    if (jrbTam.isSelected())
                        JOptionPane.showMessageDialog(dialogSelecionar, Messages.getString("HexViewerPlus.invalidSize"),
                                appName, JOptionPane.ERROR_MESSAGE);
                    else
                        JOptionPane.showMessageDialog(dialogSelecionar,
                                Messages.getString("HexViewerPlus.invalidEndPosition"), appName,
                                JOptionPane.ERROR_MESSAGE);
                    return;
                }

                jtfEnd1.requestFocus();

                int base = jtfEnd1.getBase();

                long position1 = Long.parseLong(jtfEnd1.getText().toString().trim(), base);
                long position2 = Long.parseLong(jtfEnd2.getText().toString().trim(), base);

                if (jrbTam.isSelected())
                    position2 += position1;

                long size = codeArea.getDataSize();

                if (position1 > size)
                    position1 = size;

                if (position2 > size)
                    position2 = size;

                codeArea.revealPosition(position2, codeArea.getActiveSection());
                codeArea.setCaretPosition(position2);
                codeArea.setSelection(new SelectionRange(position1, position2));
                codeArea.repaint();

                dialogSelecionar.setVisible(false);

            }
        });

        // Close window with ESC key
        dialogSelecionar.setLocationRelativeTo(codeArea);

        JRootPane rootPane = dialogSelecionar.getRootPane();
        InputMap iMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");

        ActionMap aMap = rootPane.getActionMap();
        aMap.put("escape", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dialogSelecionar.setVisible(false);
            };
        });

    }

    public void dialogIrParaResultado() {

        dialogIrParaResultado = new JDialog();
        dialogIrParaResultado.setModal(true);
        dialogIrParaResultado.setTitle(Messages.getString("HexViewerPlus.goToHit") + " - " + appName);
        dialogIrParaResultado.setBounds(0, 0, 530, 170);

        JLabel lblTexto = new JLabel(Messages.getString("HexViewerPlus.hit"));
        HVPTextField jtfTexto = new HVPTextField();
        jtfTexto.setHorizontalAlignment(SwingConstants.RIGHT);

        lblTotal = new JLabel(Messages.getString("HexViewerPlus.of") + hits.totalHits);

        JButton buttonOK = new JButton(Messages.getString("HexViewerPlus.ok"));
        JButton buttonCancel = new JButton(Messages.getString("HexViewerPlus.cancel"));

        lblTexto.setBounds(15, 30, 100, 30);
        jtfTexto.setBounds(100, 30, 100, 30);

        lblTotal.setBounds(210, 30, 100, 30);

        buttonOK.setBounds(170, 80, 80, 30);
        buttonCancel.setBounds(270, 80, 80, 30);

        dialogIrParaResultado.getContentPane().add(lblTexto);
        dialogIrParaResultado.getContentPane().add(jtfTexto);
        dialogIrParaResultado.getContentPane().add(lblTotal);
        dialogIrParaResultado.getContentPane().add(buttonOK);
        dialogIrParaResultado.getContentPane().add(buttonCancel);
        dialogIrParaResultado.getContentPane().add(new JLabel());

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jtfTexto.requestFocus();
                dialogIrParaResultado.setVisible(false);
            }
        });

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                jtfTexto.requestFocus();

                int result = -1;
                int base = jtfTexto.getBase();

                if (jtfTexto.isValidNumber()) {
                    result = Integer.parseInt(jtfTexto.getText().toString().trim(), base);
                    if (result > hits.totalHits || result <= 0) {
                        JOptionPane.showMessageDialog(dialogIrParaResultado,
                                Messages.getString("HexViewerPlus.invalidHit"), appName, JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    JOptionPane.showMessageDialog(dialogIrParaResultado, Messages.getString("HexViewerPlus.invalidHit"),
                            appName, JOptionPane.ERROR_MESSAGE);
                    return;
                }

                result -= 1;

                hits.currentHit = result;
                painter.setCurrentMatchIndex(hits.currentHit);
                HighlightCodeAreaPainter.SearchMatch match = painter.getCurrentMatch();
                codeArea.revealPosition(match.getPosition(), codeArea.getActiveSection());
                codeArea.setCaretPosition(match.getPosition() + match.getLength());
                codeArea.repaint();

                resultSearch.setText(Messages.getString("HexViewerPlus.hit") + " " + (hits.currentHit + 1)
                        + Messages.getString("HexViewerPlus.of") + hits.totalHits);

                dialogIrParaResultado.setVisible(false);

            }
        });

        // Close window with ESC key
        dialogIrParaResultado.setLocationRelativeTo(codeArea);

        JRootPane rootPane = dialogIrParaResultado.getRootPane();
        InputMap iMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");

        ActionMap aMap = rootPane.getActionMap();
        aMap.put("escape", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dialogIrParaResultado.setVisible(false);
            };
        });

    }

    public void dialogIrParaEndereco() {

        dialogIrParaEndereco = new JDialog();
        dialogIrParaEndereco.setModal(true);
        dialogIrParaEndereco.setTitle(Messages.getString("HexViewerPlus.goToPosition") + " - " + appName);
        dialogIrParaEndereco.setBounds(0, 0, 530, 240);

        JLabel lblTexto = new JLabel(Messages.getString("HexViewerPlus.position"));
        HVPTextField jtfTexto = new HVPTextField();
        jtfTexto.setHorizontalAlignment(SwingConstants.RIGHT);

        JLabel lblTipo = new JLabel(Messages.getString("HexViewerPlus.type"));
        JRadioButton jrbHex = new JRadioButton(Messages.getString("HexViewerPlus.hexadecimal"));
        JRadioButton jrbDec = new JRadioButton(Messages.getString("HexViewerPlus.decimal"));
        JRadioButton jrbOct = new JRadioButton(Messages.getString("HexViewerPlus.octal"));
        ButtonGroup bg1 = new ButtonGroup();
        bg1.add(jrbHex);
        bg1.add(jrbDec);
        bg1.add(jrbOct);
        jrbDec.setSelected(true);

        jrbHex.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jtfTexto.isValidNumber()) {
                    long aux = Long.parseLong(jtfTexto.getText(), jtfTexto.getBase());
                    jtfTexto.setHexdecimalBase();
                    jtfTexto.setText(Long.toHexString(aux).toUpperCase());
                } else {
                    jtfTexto.setText("");
                    jtfTexto.setHexdecimalBase();
                }
            }
        });
        jrbDec.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jtfTexto.isValidNumber()) {
                    long aux = Long.parseLong(jtfTexto.getText(), jtfTexto.getBase());
                    jtfTexto.setDecimalBase();
                    jtfTexto.setText(Long.toString(aux));
                } else {
                    jtfTexto.setText("");
                    jtfTexto.setDecimalBase();
                }
            }
        });
        jrbOct.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jtfTexto.isValidNumber()) {
                    long aux = Long.parseLong(jtfTexto.getText(), jtfTexto.getBase());
                    jtfTexto.setOctalBase();
                    jtfTexto.setText(Long.toOctalString(aux));
                } else {
                    jtfTexto.setText("");
                    jtfTexto.setOctalBase();
                }
            }
        });

        JLabel lblPosicao = new JLabel(Messages.getString("HexViewerPlus.relative"));
        JRadioButton jrbInicio = new JRadioButton(Messages.getString("HexViewerPlus.start"));
        JRadioButton jrbCursor = new JRadioButton(Messages.getString("HexViewerPlus.cursor"));
        ButtonGroup bg2 = new ButtonGroup();
        bg2.add(jrbInicio);
        bg2.add(jrbCursor);
        jrbInicio.setSelected(true);

        JButton buttonOK = new JButton("OK");
        JButton buttonCancel = new JButton("Cancel");

        lblTexto.setBounds(15, 30, 100, 30);
        jtfTexto.setBounds(100, 30, 250, 30);

        lblTipo.setBounds(15, 60, 100, 30);
        jrbHex.setBounds(100, 60, 100, 30);
        jrbDec.setBounds(200, 60, 100, 30);
        jrbOct.setBounds(300, 60, 100, 30);

        lblPosicao.setBounds(15, 90, 100, 30);
        jrbInicio.setBounds(100, 90, 100, 30);
        jrbCursor.setBounds(200, 90, 100, 30);

        buttonOK.setBounds(170, 140, 80, 30);
        buttonCancel.setBounds(270, 140, 80, 30);

        dialogIrParaEndereco.getContentPane().add(lblTexto);
        dialogIrParaEndereco.getContentPane().add(jtfTexto);
        dialogIrParaEndereco.getContentPane().add(lblTipo);
        dialogIrParaEndereco.getContentPane().add(jrbHex);
        dialogIrParaEndereco.getContentPane().add(jrbHex);
        dialogIrParaEndereco.getContentPane().add(jrbDec);
        dialogIrParaEndereco.getContentPane().add(jrbOct);
        dialogIrParaEndereco.getContentPane().add(lblPosicao);
        dialogIrParaEndereco.getContentPane().add(jrbInicio);
        dialogIrParaEndereco.getContentPane().add(jrbCursor);
        dialogIrParaEndereco.getContentPane().add(buttonOK);
        dialogIrParaEndereco.getContentPane().add(buttonCancel);
        dialogIrParaEndereco.getContentPane().add(new JLabel());

        jtfTexto.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_ENTER)) {
                    buttonOK.doClick();
                }
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jtfTexto.requestFocus();
                dialogIrParaEndereco.setVisible(false);
            }
        });

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                jtfTexto.requestFocus();

                if (!jtfTexto.isValidNumber()) {
                    JOptionPane.showMessageDialog(dialogIrParaEndereco,
                            Messages.getString("HexViewerPlus.invalidPosition"), appName, JOptionPane.ERROR_MESSAGE);
                    return;
                }

                int base = jtfTexto.getBase();

                long position = Long.parseLong(jtfTexto.getText().toString().trim(), base);

                if (jrbCursor.isSelected())
                    position += codeArea.getCaretPosition().getDataPosition();

                long size = codeArea.getDataSize();

                if (position > size)
                    position = size;

                codeArea.revealPosition(position, codeArea.getActiveSection());
                codeArea.setCaretPosition(position);
                codeArea.repaint();

                dialogIrParaEndereco.setVisible(false);

            }
        });

        dialogIrParaEndereco.setLocationRelativeTo(codeArea);

        // Close window with ESC key
        JRootPane rootPane = dialogIrParaEndereco.getRootPane();
        InputMap iMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");

        ActionMap aMap = rootPane.getActionMap();
        aMap.put("escape", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dialogIrParaEndereco.setVisible(false);
            };
        });

    }

    public void dialogPesquisar() {

        dialogPesquisar = new JDialog();
        dialogPesquisar.setModal(true);
        dialogPesquisar.setTitle(Messages.getString("HexViewerPlus.search") + " - " + appName);
        dialogPesquisar.setBounds(0, 0, 570, 300);

        JLabel lblTexto = new JLabel(Messages.getString("HexViewerPlus.search"));

        jtfTextoHex = new HVPComboField();
        jtfTextoHex.setTextAllowed(true);
        jtfTextoHex.setHexdecimalBase();

        JLabel lblTipo = new JLabel(Messages.getString("HexViewerPlus.type"));
        JRadioButton jrbTexto = new JRadioButton(Messages.getString("HexViewerPlus.text"));
        JRadioButton jrbHex = new JRadioButton(Messages.getString("HexViewerPlus.hexadecimal"));
        ButtonGroup bg1 = new ButtonGroup();
        bg1.add(jrbTexto);
        bg1.add(jrbHex);

        jrbTexto.setSelected(true);

        JLabel lblCharset = new JLabel(Messages.getString("HexViewerPlus.charset"));

        List<String> charSets = new ArrayList<String>();
        for (int i = 0; i < charsetComboBox.getItemCount(); i++) {
            charSets.add(charsetComboBox.getItemAt(i).toString());
        }
        fcbCharset = new FilterComboBox(charSets);
        fcbCharset.setSelectedItem(codeArea.getCharset().toString());

        JLabel lblPosicao = new JLabel(Messages.getString("HexViewerPlus.relative"));
        JRadioButton jrbInicio = new JRadioButton(Messages.getString("HexViewerPlus.start"));
        JRadioButton jrbCursor = new JRadioButton(Messages.getString("HexViewerPlus.cursor"));
        ButtonGroup bg2 = new ButtonGroup();
        bg2.add(jrbInicio);
        bg2.add(jrbCursor);
        jrbCursor.setSelected(true);

        JLabel lblEscopo = new JLabel(Messages.getString("HexViewerPlus.hits"));
        JRadioButton jrbCompleto = new JRadioButton(Messages.getString("HexViewerPlus.all"));
        JRadioButton jrbProximo = new JRadioButton(Messages.getString("HexViewerPlus.max"));
        ButtonGroup bg3 = new ButtonGroup();
        bg3.add(jrbCompleto);
        bg3.add(jrbProximo);
        jrbProximo.setSelected(true);

        JCheckBox jcbCase = new JCheckBox(Messages.getString("HexViewerPlus.caseSensitive"));
        jcbCase.setSelected(false);

        JButton buttonOK = new JButton(Messages.getString("HexViewerPlus.ok"));
        JButton buttonCancel = new JButton(Messages.getString("HexViewerPlus.cancel"));

        jtfTextoHex.addTextKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getKeyCode() == KeyEvent.VK_ENTER)) {
                    buttonOK.doClick();
                }
            }
        });

        jrbHex.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jcbCase.setVisible(false);
                lblCharset.setVisible(false);
                fcbCharset.setVisible(false);
                jtfTextoHex.setTextAllowed(false);
            }
        });

        jrbTexto.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jcbCase.setVisible(true);
                lblCharset.setVisible(true);
                fcbCharset.setVisible(true);
                jtfTextoHex.setTextAllowed(true);
            }
        });

        HVPTextField jtfMaxHits = new HVPTextField();
        jtfMaxHits.setHorizontalAlignment(SwingConstants.RIGHT);
        jtfMaxHits.setText("1");

        lblTexto.setBounds(15, 30, 100, 30);
        jtfTextoHex.setBounds(100, 30, 400, 30);

        lblTipo.setBounds(15, 60, 100, 30);
        jrbTexto.setBounds(100, 60, 100, 30);
        jrbHex.setBounds(200, 60, 100, 30);

        lblPosicao.setBounds(15, 90, 100, 30);
        jrbInicio.setBounds(100, 90, 100, 30);
        jrbCursor.setBounds(200, 90, 100, 30);

        lblEscopo.setBounds(15, 120, 100, 30);
        jrbCompleto.setBounds(100, 120, 100, 30);
        jrbProximo.setBounds(200, 120, 100, 30);
        jtfMaxHits.setBounds(300, 120, 60, 30);

        lblCharset.setBounds(15, 160, 50, 30);
        fcbCharset.setBounds(100, 160, 150, 30);
        jcbCase.setBounds(300, 160, 235, 30);

        buttonOK.setBounds(190, 210, 80, 30);
        buttonCancel.setBounds(290, 210, 80, 30);

        dialogPesquisar.getContentPane().add(lblTexto);
        dialogPesquisar.getContentPane().add(jtfTextoHex);
        dialogPesquisar.getContentPane().add(lblTipo);
        dialogPesquisar.getContentPane().add(jrbTexto);
        dialogPesquisar.getContentPane().add(jrbHex);
        dialogPesquisar.getContentPane().add(jcbCase);
        dialogPesquisar.getContentPane().add(lblCharset);
        dialogPesquisar.getContentPane().add(fcbCharset);
        dialogPesquisar.getContentPane().add(lblPosicao);
        dialogPesquisar.getContentPane().add(jrbInicio);
        dialogPesquisar.getContentPane().add(jrbCursor);
        dialogPesquisar.getContentPane().add(lblEscopo);
        dialogPesquisar.getContentPane().add(jrbCompleto);
        dialogPesquisar.getContentPane().add(jrbProximo);
        dialogPesquisar.getContentPane().add(jtfMaxHits);
        dialogPesquisar.getContentPane().add(buttonOK);
        dialogPesquisar.getContentPane().add(buttonCancel);
        dialogPesquisar.getContentPane().add(new JLabel());

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                jtfTextoHex.requestFocus();
                dialogPesquisar.setVisible(false);
            }
        });

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                String texto = "";

                if (jrbTexto.isSelected()) {
                    if (jtfTextoHex.getText().toString().trim().isEmpty()) {
                        JOptionPane.showMessageDialog(dialogPesquisar,
                                Messages.getString("HexViewerPlus.invalidSearchText"), appName,
                                JOptionPane.ERROR_MESSAGE);
                        jtfTextoHex.requestFocus();
                        return;
                    }
                } else if (jrbHex.isSelected()) {
                    if (!jtfTextoHex.isValidNumber()) {
                        JOptionPane.showMessageDialog(dialogPesquisar,
                                Messages.getString("HexViewerPlus.invalidHexadecimalText"), appName,
                                JOptionPane.ERROR_MESSAGE);
                        jtfTextoHex.requestFocus();
                        return;
                    }
                }
                texto = jtfTextoHex.getText().toString();
                jtfTextoHex.requestFocus();

                if (((DefaultComboBoxModel) jtfTextoHex.getModel()).getIndexOf(texto) == -1) {
                    jtfTextoHex.addItem(texto);
                }
                jtfTextoHex.setSelectedItem(texto);

                if (jrbCompleto.isSelected()) {
                    max_hits = max_terms;
                } else {
                    if (!jtfMaxHits.isValidNumber()) {
                        JOptionPane.showMessageDialog(dialogPesquisar, Messages.getString("HexViewerPlus.invalidMax"),
                                appName, JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    try {
                        max_hits = Integer.parseInt(jtfMaxHits.getText().toString().trim());
                    } catch (NumberFormatException nfe) {
                        JOptionPane.showMessageDialog(dialogPesquisar,
                                Messages.getString("HexViewerPlus.invalidMaxLessThen") + max_terms, appName,
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (max_hits > max_terms) {
                        JOptionPane.showMessageDialog(dialogPesquisar,
                                Messages.getString("HexViewerPlus.invalidMaxLessThen") + max_terms, appName,
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                }

                Set<String> palavras = new HashSet<String>();
                palavras.add(texto);

                Charset charsetParam = null;
                String charSetString = fcbCharset.getSelectedItem().toString();
                if (jrbTexto.isSelected()) {
                    if (charSetString != null && !charSetString.isEmpty() && Charset.isSupported(charSetString)) {
                        charsetParam = Charset.forName(charSetString);
                    } else {
                        JOptionPane.showMessageDialog(dialogPesquisar,
                                Messages.getString("HexViewerPlus.charset") + " \"" + charSetString + "\" "
                                        + Messages.getString("HexViewerPlus.invalid") + "!",
                                appName, JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                long off = jrbInicio.isSelected() ? 0 : codeArea.getCaretPosition().getDataPosition();

                if (contentAux != null) {
                    try {

                        dialogPesquisar.setVisible(false);
                        hexSearcher.doSearch(codeArea, painter, hits, contentAux.getStream(), charsetParam, palavras,
                                off, jrbTexto.isSelected(), !jcbCase.isSelected(), resultSearch, max_hits);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }

            }
        });

        // Close window with ESC key
        dialogPesquisar.setLocationRelativeTo(codeArea);

        JRootPane rootPane = dialogPesquisar.getRootPane();
        InputMap iMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        iMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");

        ActionMap aMap = rootPane.getActionMap();
        aMap.put("escape", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dialogPesquisar.setVisible(false);
            };
        });

    }

}

class HVPComboField extends JComboBox {
    private int base = 10;
    private boolean textAllowed = false;
    JTextField textfield;

    public HVPComboField() {
        super();
        this.setEditable(true);
        textfield = (JTextField) this.getEditor().getEditorComponent();

        textfield.addKeyListener(new KeyAdapter() {

            @Override
            public void keyTyped(KeyEvent ev) {

                if (textAllowed) {
                    return;
                } else {
                    char c = ev.getKeyChar();
                    int k = ev.getKeyCode();
                    boolean copy = ((ev.getKeyCode() == KeyEvent.VK_C)
                            && ((ev.getModifiers() & KeyEvent.CTRL_MASK) != 0));
                    boolean paste = ((ev.getKeyCode() == KeyEvent.VK_V)
                            && ((ev.getModifiers() & KeyEvent.CTRL_MASK) != 0));

                    if (isDigitBase(c) || k == KeyEvent.VK_BACK_SPACE || k == KeyEvent.VK_DELETE
                            || k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_LEFT || k == KeyEvent.VK_ENTER
                            || k == KeyEvent.VK_KP_LEFT || k == KeyEvent.VK_KP_RIGHT || k == KeyEvent.VK_END
                            || k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_HOME || paste || copy) {
                        return;
                    } else {
                        ev.consume();
                    }
                }

                return;
            }
        });

    }

    public void addTextKeyListener(KeyAdapter ka) {
        textfield.addKeyListener(ka);
    }

    public void setTextAllowed(boolean textAllowed) {
        this.textAllowed = textAllowed;
    }

    public int getBase() {
        return this.base;
    }

    public void setDecimalBase() {
        this.base = 10;
    }

    public void setHexdecimalBase() {
        this.base = 16;
    }

    public void setOctalBase() {
        this.base = 8;
    }

    public void setBinaryBase() {
        this.base = 2;
    }

    public String getText() {
        return textfield.getText();
    }

    public void setText(String text) {
        textfield.setText(text);
    }

    public void setHorizontalAlignment(int h) {
        textfield.setHorizontalAlignment(h);
    }

    public boolean isValidNumber() {

        String value = getText();
        if (value == null)
            return false;
        if (value.isEmpty())
            return false;

        char[] charArray = value.toCharArray();
        for (int i = 0; i < charArray.length; i++) {
            if (!isDigitBase(charArray[i]))
                return false;
        }

        try {// Try transform value in long, bigger than Long.MAX_VALUE ?
            Long.parseLong(value, getBase());
        } catch (NumberFormatException nfe) {
            return false;
        }

        return true;
    }

    private boolean isDigitBase(char c) {

        int index = -1;

        if (Character.isLetterOrDigit(c)) {

            switch (c) {
                case '0':
                    index = 0;
                    break;
                case '1':
                    index = 1;
                    break;
                case '2':
                    index = 2;
                    break;
                case '3':
                    index = 3;
                    break;
                case '4':
                    index = 4;
                    break;
                case '5':
                    index = 5;
                    break;
                case '6':
                    index = 6;
                    break;
                case '7':
                    index = 7;
                    break;
                case '8':
                    index = 8;
                    break;
                case '9':
                    index = 9;
                    break;
                case 'a':
                case 'A':
                    index = 10;
                    break;
                case 'b':
                case 'B':
                    index = 11;
                    break;
                case 'c':
                case 'C':
                    index = 12;
                    break;
                case 'd':
                case 'D':
                    index = 13;
                    break;
                case 'e':
                case 'E':
                    index = 14;
                    break;
                case 'f':
                case 'F':
                    index = 15;
                    break;
                default:
                    index = -1;
                    break;
            }
            if (index >= 0 && index < this.base)
                return true;

        }
        return false;
    }

}

class HVPTextField extends JTextField {
    private int base = 10;
    private boolean textAllowed = false;

    @Override
    public void processKeyEvent(KeyEvent ev) {

        if (this.textAllowed) {
            super.processKeyEvent(ev);
            return;
        }

        char c = ev.getKeyChar();
        int k = ev.getKeyCode();
        boolean copy = ((ev.getKeyCode() == KeyEvent.VK_C) && ((ev.getModifiers() & KeyEvent.CTRL_MASK) != 0));
        boolean paste = ((ev.getKeyCode() == KeyEvent.VK_V) && ((ev.getModifiers() & KeyEvent.CTRL_MASK) != 0));

        if (isDigitBase(c) || k == KeyEvent.VK_BACK_SPACE || k == KeyEvent.VK_DELETE || k == KeyEvent.VK_RIGHT
                || k == KeyEvent.VK_LEFT || k == KeyEvent.VK_ENTER || k == KeyEvent.VK_KP_LEFT
                || k == KeyEvent.VK_KP_RIGHT || k == KeyEvent.VK_END || k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_HOME
                || paste || copy) {
            super.processKeyEvent(ev);
            return;
        }
        ev.consume();
        return;
    }

    public HVPTextField() {
        super();
    }

    public void setTextAllowed(boolean textAllowed) {
        this.textAllowed = textAllowed;
    }

    public int getBase() {
        return this.base;
    }

    public void setDecimalBase() {
        this.base = 10;
    }

    public void setHexdecimalBase() {
        this.base = 16;
    }

    public void setOctalBase() {
        this.base = 8;
    }

    public void setBinaryBase() {
        this.base = 2;
    }

    public boolean isValidNumber() {

        String value = getText();
        if (value == null)
            return false;
        if (value.isEmpty())
            return false;

        char[] charArray = value.toCharArray();
        for (int i = 0; i < charArray.length; i++) {
            if (!isDigitBase(charArray[i]))
                return false;
        }

        try {// Try transform value in long, bigger than Long.MAX_VALUE ?
            Long.parseLong(value, getBase());
        } catch (NumberFormatException nfe) {
            return false;
        }

        return true;
    }

    private boolean isDigitBase(char c) {

        int index = -1;

        if (Character.isLetterOrDigit(c)) {

            switch (c) {
                case '0':
                    index = 0;
                    break;
                case '1':
                    index = 1;
                    break;
                case '2':
                    index = 2;
                    break;
                case '3':
                    index = 3;
                    break;
                case '4':
                    index = 4;
                    break;
                case '5':
                    index = 5;
                    break;
                case '6':
                    index = 6;
                    break;
                case '7':
                    index = 7;
                    break;
                case '8':
                    index = 8;
                    break;
                case '9':
                    index = 9;
                    break;
                case 'a':
                case 'A':
                    index = 10;
                    break;
                case 'b':
                case 'B':
                    index = 11;
                    break;
                case 'c':
                case 'C':
                    index = 12;
                    break;
                case 'd':
                case 'D':
                    index = 13;
                    break;
                case 'e':
                case 'E':
                    index = 14;
                    break;
                case 'f':
                case 'F':
                    index = 15;
                    break;
                default:
                    index = -1;
                    break;
            }
            if (index >= 0 && index < this.base)
                return true;

        }
        return false;
    }

}

class ByteArraySeekData extends ByteArrayEditableData {

    private long len;
    private Map<Long, int[]> memoBytes = new HashMap<Long, int[]>();
    private byte[] readBuf = new byte[1 << 12];
    private LinkedList<Long> fifo = new LinkedList<Long>();
    private static final int MAX_MEMO = 2000;

    private SeekableInputStream file;

    public ByteArraySeekData() {
    }

    public void setData(SeekableInputStream file) throws IOException {

        if (file != null && file.equals(this.file)) {
            return;
        }
        
        clear();

        this.file = file;
        if (file != null) {
            len = file.size();
        }
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
            e.printStackTrace();
            this.clear();
            fireReadError();
            return -1;
        }

    }
    
    void fireReadError() {
        // do nothing by default
    }

    @Override
    public BinaryData copy() {
        return copy(0, getDataSize());
    }

    @Override
    public BinaryData copy(long startFrom, long length) {
        if (startFrom + length > getDataSize()) {
            throw new OutOfBoundsException("Attemt to copy outside of data");
        }

        // Max size of a String and max size of array in JAVA - 2GB
        int max = (length > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) length;
        byte[] copy = new byte[max];
        copyToArray(startFrom, copy, 0, max);

        return new ByteArrayEditableData(copy);
    }

    public void copyToArray(long startFrom, byte[] target, int offset, int length) {

        try {

            int k = 0;
            while (k < length) {
                target[offset] = getByte(startFrom);
                offset++;
                startFrom++;
                k++;
            }

        } catch (IndexOutOfBoundsException ex) {
            throw new OutOfBoundsException(ex);
        }
    }

    @Override
    public void clear() {
        len = 0;
        memoBytes.clear();
        IOUtil.closeQuietly(file);
        file = null;
    }

}

class Hits {

    public static int currentHit = 0;
    public static int totalHits = 0;

}

class FilterComboBox extends JComboBox {

    private List<String> entries;

    public List<String> getEntries() {
        return entries;
    }

    public FilterComboBox(List<String> entries) {
        super(entries.toArray());
        this.entries = entries;
        this.setEditable(true);

        final JTextField textfield = (JTextField) this.getEditor().getEditorComponent();

        textfield.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent ke) {

                if (ke.getKeyCode() != KeyEvent.VK_UP && ke.getKeyCode() != KeyEvent.VK_DOWN) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            comboFilter(textfield.getText());
                        }
                    });
                }
            }
        });

    }

    public void comboFilter(String enteredText) {
        List<String> entriesFiltered = new ArrayList<String>();

        for (String entry : getEntries()) {
            if (entry.toLowerCase().contains(enteredText.toLowerCase())) {
                entriesFiltered.add(entry);
            }
        }

        if (entriesFiltered.size() > 0) {
            this.setModel(new DefaultComboBoxModel(entriesFiltered.toArray()));
            this.setSelectedItem(enteredText);
            this.showPopup();
        } else {
            this.hidePopup();
        }
    }
}

class HVPSettings implements Serializable {

    private static Logger LOGGER = LoggerFactory.getLogger(HVPSettings.class);

    public int mode = 0;
    public int codeType = 3;
    public int positionCodeType = 2;
    public int backGround = 2;
    public String font = Font.MONOSPACED;
    public int formatoNumero = 3;
    public int cursor = 6;

    public boolean hexCharactersCase = false;
    public boolean showHeader = true;
    public boolean showLineNumbers = true;
    public boolean wrapLineMode = true;
    public boolean showUnprintableCharacters = false;
    public boolean showLineNumberBackground = true;
    public boolean showSelectionBar = true;
    public boolean showPositionBar = true;

    public int fontSize = 14;
    public int cursorBlink = 450;
    public int lineSize = 16;

    public Color ColorFontMain = Color.BLACK;
    public Color ColorFontAlt = Color.BLACK;
    public Color ColorBackgroundMain = new Color(214, 217, 223);
    public Color ColorBackgroundAlt = new Color(198, 201, 207);
    public Color ColorSelectionMainText = Color.WHITE;
    public Color ColorSelectionMainBackground = Color.BLUE;
    public Color ColorSelectionMirrorText = Color.WHITE;
    public Color ColorSelectionMirrorBackground = new Color(149, 149, 149);
    public Color ColorFoundMatchText = Color.BLACK;
    public Color ColorFoundMatchBackground = Color.YELLOW;
    public Color ColorCurrentMatchText = Color.BLACK;
    public Color ColorCurrentMatchBackground = Color.GREEN;
    public Color ColorCursor = Color.BLACK;

    public Color ColorHeaderText = Color.BLACK;
    public Color ColorHeaderBackground = new Color(198, 201, 207);

    public static boolean saveObject(String path, HVPSettings obj) {

        if (obj == null) {
            return false;
        }

        try {
            FileOutputStream fileOut = new FileOutputStream(path);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(obj);
            out.close();
            fileOut.close();
        } catch (Exception ex) {
            LOGGER.warn("Failed to save HexviewerPlus settings file. Error:{}", ex.toString());
            return false;
        }
        return true;

    }

    public static HVPSettings loadObject(String path) {

        HVPSettings obj = null;
        try {
            FileInputStream fileIn = new FileInputStream(path);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            obj = (HVPSettings) in.readObject();
            in.close();
            fileIn.close();
        } catch (Exception ex) {
            LOGGER.warn("Failed to load HexviewerPlus settings file. Corrupted?. Error:{}", ex.toString());
            return null;
        }
        return obj;

    }

}

class ImageFileView extends FileView {

    static byte[] imageArray = { (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A,
            (byte) 0x1A, (byte) 0x0A, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0D, (byte) 0x49, (byte) 0x48,
            (byte) 0x44, (byte) 0x52, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x10, (byte) 0x08, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x90,
            (byte) 0x91, (byte) 0x68, (byte) 0x36, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0x70,
            (byte) 0x48, (byte) 0x59, (byte) 0x73, (byte) 0x00, (byte) 0x00, (byte) 0x0E, (byte) 0xC4, (byte) 0x00,
            (byte) 0x00, (byte) 0x0E, (byte) 0xC4, (byte) 0x01, (byte) 0x95, (byte) 0x2B, (byte) 0x0E, (byte) 0x1B,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xE0, (byte) 0x49, (byte) 0x44, (byte) 0x41, (byte) 0x54,
            (byte) 0x78, (byte) 0xDA, (byte) 0x75, (byte) 0x92, (byte) 0xBD, (byte) 0x11, (byte) 0x83, (byte) 0x30,
            (byte) 0x0C, (byte) 0x85, (byte) 0xDF, (byte) 0x71, (byte) 0x19, (byte) 0x05, (byte) 0x28, (byte) 0x72,
            (byte) 0x4C, (byte) 0xA0, (byte) 0x0D, (byte) 0xB8, (byte) 0xF4, (byte) 0xB4, (byte) 0xE9, (byte) 0x9C,
            (byte) 0x96, (byte) 0x01, (byte) 0x52, (byte) 0x66, (byte) 0x00, (byte) 0x53, (byte) 0x86, (byte) 0x29,
            (byte) 0x52, (byte) 0x99, (byte) 0x0D, (byte) 0x3C, (byte) 0x41, (byte) 0x8E, (byte) 0x02, (byte) 0xB1,
            (byte) 0x8B, (byte) 0x52, (byte) 0x04, (byte) 0xFC, (byte) 0x87, (byte) 0xF9, (byte) 0x2A, (byte) 0x23,
            (byte) 0x78, (byte) 0xB2, (byte) 0x9E, (byte) 0x1E, (byte) 0x10, (byte) 0x11, (byte) 0x11, (byte) 0x31,
            (byte) 0x0A, (byte) 0xA4, (byte) 0x59, (byte) 0x3C, (byte) 0x87, (byte) 0xC2, (byte) 0x4E, (byte) 0x01,
            (byte) 0x00, (byte) 0xC0, (byte) 0xF2, (byte) 0x45, (byte) 0x53, (byte) 0x97, (byte) 0x70, (byte) 0x4C,
            (byte) 0x9F, (byte) 0x91, (byte) 0xBA, (byte) 0x5B, (byte) 0x50, (byte) 0x70, (byte) 0x6C, (byte) 0x82,
            (byte) 0xD9, (byte) 0xD2, (byte) 0xB5, (byte) 0xF2, (byte) 0xD5, (byte) 0x35, (byte) 0x6D, (byte) 0x90,
            (byte) 0x08, (byte) 0xA6, (byte) 0xC7, (byte) 0x18, (byte) 0xBF, (byte) 0xE7, (byte) 0xA4, (byte) 0x41,
            (byte) 0x88, (byte) 0x88, (byte) 0xB0, (byte) 0xA6, (byte) 0x63, (byte) 0x5D, (byte) 0x19, (byte) 0xC9,
            (byte) 0x82, (byte) 0x8C, (byte) 0x41, (byte) 0xD6, (byte) 0x04, (byte) 0x92, (byte) 0x13, (byte) 0x8A,
            (byte) 0x8C, (byte) 0x63, (byte) 0x9E, (byte) 0x2D, (byte) 0x75, (byte) 0x38, (byte) 0xC3, (byte) 0xF5,
            (byte) 0x73, (byte) 0xF7, (byte) 0xB0, (byte) 0xA6, (byte) 0x6D, (byte) 0x20, (byte) 0x7F, (byte) 0xF2,
            (byte) 0x5C, (byte) 0xC0, (byte) 0xB3, (byte) 0x45, (byte) 0x03, (byte) 0xA0, (byte) 0xBA, (byte) 0x92,
            (byte) 0x9D, (byte) 0x19, (byte) 0x28, (byte) 0x9D, (byte) 0xE3, (byte) 0xE1, (byte) 0xDE, (byte) 0x43,
            (byte) 0x73, (byte) 0x7B, (byte) 0x34, (byte) 0x9D, (byte) 0xCF, (byte) 0x2A, (byte) 0x7C, (byte) 0x0C,
            (byte) 0xCE, (byte) 0x17, (byte) 0x2F, (byte) 0x75, (byte) 0x57, (byte) 0x00, (byte) 0xC0, (byte) 0x3A,
            (byte) 0xBC, (byte) 0x46, (byte) 0xF5, (byte) 0x14, (byte) 0xEF, (byte) 0x6C, (byte) 0x77, (byte) 0x59,
            (byte) 0x78, (byte) 0x41, (byte) 0x59, (byte) 0x37, (byte) 0xF8, (byte) 0x2E, (byte) 0xEB, (byte) 0xFF,
            (byte) 0xF3, (byte) 0x7B, (byte) 0x0F, (byte) 0xFD, (byte) 0x6E, (byte) 0x83, (byte) 0x1C, (byte) 0x33,
            (byte) 0x23, (byte) 0x79, (byte) 0x8F, (byte) 0x46, (byte) 0x81, (byte) 0xF2, (byte) 0xF9, (byte) 0x90,
            (byte) 0x0E, (byte) 0x05, (byte) 0xDB, (byte) 0xA8, (byte) 0x46, (byte) 0x25, (byte) 0xB1, (byte) 0x45,
            (byte) 0xCB, (byte) 0x8A, (byte) 0x04, (byte) 0xAC, (byte) 0x09, (byte) 0x44, (byte) 0xE9, (byte) 0x2A,
            (byte) 0xE3, (byte) 0xE5, (byte) 0x46, (byte) 0x02, (byte) 0x31, (byte) 0x0A, (byte) 0x48, (byte) 0x7F,
            (byte) 0xEB, (byte) 0x24, (byte) 0x8C, (byte) 0x1F, (byte) 0x75, (byte) 0x59, (byte) 0x74, (byte) 0x31,
            (byte) 0xBC, (byte) 0xB2, (byte) 0x98, (byte) 0x40, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x49, (byte) 0x45, (byte) 0x4E, (byte) 0x44, (byte) 0xAE, (byte) 0x42, (byte) 0x60, (byte) 0x82 };

    public static ImageIcon hvpIcon = new ImageIcon(imageArray);
    public final static String hvp = "hvp";

    public Icon getIcon(File f) {
        String extension = getExtensao(f);
        Icon icon = null;

        if (extension != null) {
            if (extension.equals(hvp)) {
                icon = hvpIcon;
            }
        }
        return icon;
    }

    public String getExtensao(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if (i > 0 && i < s.length() - 1) {
            ext = s.substring(i + 1).toLowerCase();
        }
        return ext;
    }

}

class customFileFilter extends javax.swing.filechooser.FileFilter {

    String[] extensions;
    String description;

    public customFileFilter(String ext) {
        this(new String[] { ext }, null);
    }

    public customFileFilter(String[] exts, String descr) {

        extensions = new String[exts.length];
        for (int i = exts.length - 1; i >= 0; i--) {
            extensions[i] = exts[i].toLowerCase();
        }

        description = (descr == null ? exts[0] + " files" : descr);
    }

    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }

        String name = f.getName().toLowerCase();
        for (int i = extensions.length - 1; i >= 0; i--) {
            if (name.endsWith(extensions[i])) {
                return true;
            }
        }
        return false;
    }

    public String getDescription() {
        return description;
    }
}

class RoundButton extends JButton {

    Shape shape;
    int radius = 12;
    Color buttonFocusColor = UIManager.getColor("Button.focus");

    public RoundButton(String label, Color color) {
        super(label);
        setBackground(color);
        setContentAreaFilled(false);
        setOpaque(false);
    }

    public RoundButton(String label) {
        super(label);
        setContentAreaFilled(false);
        setOpaque(false);
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    protected void paintComponent(Graphics g) {

        if (this.hasFocus()) {
            if (buttonFocusColor == null)
                buttonFocusColor = new Color(115, 164, 209);

            g.setColor(buttonFocusColor);
            g.drawRoundRect(0, 0, getSize().width - 1, getSize().height - 1, radius, radius);
        }

        if (getModel().isArmed()) {
            g.setColor(Blend(getBackground(), Color.BLACK, 0.65f));
        } else if (getModel().isRollover()) {
            if (getBackground().equals(Color.WHITE))
                g.setColor(Blend(getBackground(), Color.LIGHT_GRAY, 0.65f));
            else
                g.setColor(Blend(getBackground(), Color.WHITE, 0.65f));
        } else {
            g.setColor(getBackground());
        }

        g.fillRoundRect(1, 1, getSize().width - 3, getSize().height - 3, radius / 2, radius / 2);
        super.paintComponent(g);
    }

    Color Blend(Color clOne, Color clTwo, float fAmount) {
        float fInverse = 1.0f - fAmount;

        float afOne[] = new float[3];
        clOne.getColorComponents(afOne);
        float afTwo[] = new float[3];
        clTwo.getColorComponents(afTwo);

        float afResult[] = new float[3];
        afResult[0] = afOne[0] * fAmount + afTwo[0] * fInverse;
        afResult[1] = afOne[1] * fAmount + afTwo[1] * fInverse;
        afResult[2] = afOne[2] * fAmount + afTwo[2] * fInverse;

        return new Color(afResult[0], afResult[1], afResult[2]);
    }

    protected void paintBorder(Graphics g) {

        if (this.hasFocus()) {
            if (buttonFocusColor != null) {
                g.setColor(Blend(buttonFocusColor, getBackground(), 0.65f));
            } else {
                g.setColor(getForeground());
            }
        } else {
            g.setColor(getForeground());
        }
        g.drawRoundRect(1, 1, getSize().width - 3, getSize().height - 3, radius / 2, radius / 2);
    }

    public boolean contains(int x, int y) {
        if (shape == null || !shape.getBounds().equals(getBounds())) {
            shape = new Rectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1);
        }

        return shape.contains(x, y);
    }
}

// Code for rendering Cursor - implemented on deltahex lib

class CursorComponent extends JComponent {

    CodeArea codeArea;
    private Charset charMappingCharset = null;
    protected final char[] charMapping = new char[256];
    protected Map<Character, Character> unprintableCharactersMapping = null;

    private int blinkRate = 0;
    private javax.swing.Timer blinkTimer = null;
    private boolean cursorVisible = true;

    public CursorComponent(CodeArea codeArea) {
        this.codeArea = codeArea;
        setBlinkRate(450);
    }

    public void resetBlink() {
        if (blinkTimer != null) {
            cursorVisible = true;
            blinkTimer.restart();
        }
    }

    private void cursorRepaint() {
        this.repaint();
    }

    private class Blink implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            cursorVisible = !cursorVisible;
            cursorRepaint();
        }
    }

    public void setBlinkRate(int blinkRate) {
        if (blinkRate < 0) {
            throw new IllegalArgumentException("Blink rate cannot be negative");
        }
        this.blinkRate = blinkRate;
        if (blinkTimer != null) {
            if (blinkRate == 0) {
                blinkTimer.stop();
                blinkTimer = null;
                cursorVisible = true;
                cursorRepaint();
            } else {
                blinkTimer.setDelay(blinkRate);
                blinkTimer.setInitialDelay(blinkRate);
            }
        } else if (blinkRate > 0) {
            blinkTimer = new javax.swing.Timer(blinkRate, new Blink());
            blinkTimer.setRepeats(true);
            blinkTimer.start();
        }
    }

    private void buildCharMapping(Charset charset) {
        for (int i = 0; i < 256; i++) {
            charMapping[i] = new String(new byte[] { (byte) i }, charset).charAt(0);
        }
        charMappingCharset = charset;
    }

    protected void drawCenteredChar(Graphics g, char[] drawnChars, int charOffset, int charWidthSpace, int startX,
            int positionY) {
        FontMetrics fontMetrics = codeArea.getFontMetrics();
        if (charOffset >= 0) {
            int charWidth = fontMetrics.charWidth(drawnChars[charOffset]);
            drawShiftedChar(g, drawnChars, charOffset, charWidthSpace, startX, positionY,
                    (charWidthSpace + 1 - charWidth) >> 1);
        }
    }

    protected void drawShiftedChar(Graphics g, char[] drawnChars, int charOffset, int charWidthSpace, int startX,
            int positionY, int shift) {
        g.drawChars(drawnChars, charOffset, 1, startX + shift, positionY);
    }

    private void buildUnprintableCharactersMapping() {
        unprintableCharactersMapping = new HashMap<>();
        // Unicode control characters, might not be supported by font
        for (int i = 0; i < 32; i++) {
            unprintableCharactersMapping.put((char) i, Character.toChars(9216 + i)[0]);
        }
        // Space -> Middle Dot
        unprintableCharactersMapping.put(' ', Character.toChars(183)[0]);
        // Tab -> Right-Pointing Double Angle Quotation Mark
        unprintableCharactersMapping.put('\t', Character.toChars(187)[0]);
        // Line Feed -> Currency Sign
        unprintableCharactersMapping.put('\r', Character.toChars(164)[0]);
        // Carriage Return -> Pilcrow Sign
        unprintableCharactersMapping.put('\n', Character.toChars(182)[0]);
        // Ideographic Space -> Degree Sign
        unprintableCharactersMapping.put(Character.toChars(127)[0], Character.toChars(176)[0]);
    }

    public void paint(Graphics g) {

        CodeAreaCaret caret = codeArea.getCaret();
        int bytesPerLine = codeArea.getBytesPerLine();
        int lineHeight = codeArea.getLineHeight();
        int charWidth = codeArea.getCharWidth();
        int linesPerRect = codeArea.getLinesPerRect();
        int codeDigits = codeArea.getCodeType().getMaxDigits();
        Point cursorPoint = caret.getCursorPoint(bytesPerLine, lineHeight, charWidth, linesPerRect);

        if (cursorPoint == null) {
            cursorPoint = new Point();
        }

        cursorPoint.x = 0;
        cursorPoint.y = 0;
        boolean cursorVisible = this.cursorVisible;
        CodeAreaCaret.CursorRenderingMode renderingMode = caret.getRenderingMode();

        if (cursorVisible && cursorPoint != null) {
            g.setColor(codeArea.getCursorColor());
            if (renderingMode == CodeAreaCaret.CursorRenderingMode.XOR) {
                g.setXORMode(Color.WHITE);
            }

            CodeAreaCaret.CursorShape cursorShape = codeArea.getEditationMode() == EditationMode.INSERT
                    ? caret.getInsertCursorShape()
                    : caret.getOverwriteCursorShape();
            int cursorThickness = 0;
            if (cursorShape.getWidth() != CodeAreaCaret.CursorShapeWidth.FULL) {
                cursorThickness = caret.getCursorThickness(cursorShape, charWidth, lineHeight);
            }
            switch (cursorShape) {
                case LINE_TOP:
                case DOUBLE_TOP:
                case QUARTER_TOP:
                case HALF_TOP: {
                    paintCursorRect(g, cursorPoint.x, cursorPoint.y, charWidth, cursorThickness, renderingMode);
                    break;
                }
                case LINE_BOTTOM:
                case DOUBLE_BOTTOM:
                case QUARTER_BOTTOM:
                case HALF_BOTTOM: {
                    paintCursorRect(g, cursorPoint.x, cursorPoint.y + lineHeight - cursorThickness, charWidth,
                            cursorThickness, renderingMode);
                    break;
                }
                case LINE_LEFT:
                case DOUBLE_LEFT:
                case QUARTER_LEFT:
                case HALF_LEFT: {
                    paintCursorRect(g, cursorPoint.x, cursorPoint.y, cursorThickness, lineHeight, renderingMode);
                    break;
                }
                case LINE_RIGHT:
                case DOUBLE_RIGHT:
                case QUARTER_RIGHT:
                case HALF_RIGHT: {
                    paintCursorRect(g, cursorPoint.x + charWidth - cursorThickness, cursorPoint.y, cursorThickness,
                            lineHeight, renderingMode);
                    break;
                }
                case BOX: {
                    paintCursorRect(g, cursorPoint.x, cursorPoint.y, charWidth, lineHeight, renderingMode);
                    break;
                }
                case FRAME: {
                    g.drawRect(cursorPoint.x, cursorPoint.y, charWidth, lineHeight - 1);
                    break;
                }
                case BOTTOM_CORNERS:
                case CORNERS: {
                    int quarterWidth = charWidth / 4;
                    int quarterLine = lineHeight / 4;
                    if (cursorShape == CodeAreaCaret.CursorShape.CORNERS) {
                        g.drawLine(cursorPoint.x, cursorPoint.y, cursorPoint.x + quarterWidth, cursorPoint.y);
                        g.drawLine(cursorPoint.x + charWidth - quarterWidth, cursorPoint.y, cursorPoint.x + charWidth,
                                cursorPoint.y);

                        g.drawLine(cursorPoint.x, cursorPoint.y + 1, cursorPoint.x, cursorPoint.y + quarterLine);
                        g.drawLine(cursorPoint.x + charWidth, cursorPoint.y + 1, cursorPoint.x + charWidth,
                                cursorPoint.y + quarterLine);
                    }

                    g.drawLine(cursorPoint.x, cursorPoint.y + lineHeight - quarterLine - 1, cursorPoint.x,
                            cursorPoint.y + lineHeight - 2);
                    g.drawLine(cursorPoint.x + charWidth, cursorPoint.y + lineHeight - quarterLine - 1,
                            cursorPoint.x + charWidth, cursorPoint.y + lineHeight - 2);

                    g.drawLine(cursorPoint.x, cursorPoint.y + lineHeight - 1, cursorPoint.x + quarterWidth,
                            cursorPoint.y + lineHeight - 1);
                    g.drawLine(cursorPoint.x + charWidth - quarterWidth, cursorPoint.y + lineHeight - 1,
                            cursorPoint.x + charWidth, cursorPoint.y + lineHeight - 1);
                    break;
                }
                default: {
                    throw new IllegalStateException("Unexpected cursor shape type " + cursorShape.name());
                }
            }

            if (renderingMode == CodeAreaCaret.CursorRenderingMode.XOR) {
                g.setPaintMode();
            }
        }
    }

    private void paintCursorRect(Graphics g, int x, int y, int width, int height,
            CodeAreaCaret.CursorRenderingMode renderingMode) {
        switch (renderingMode) {
            case PAINT: {
                g.fillRect(x, y, width, height);
                break;
            }
            case XOR: {
                Rectangle rect = new Rectangle(x, y, width, height);
                Rectangle intersection = rect.intersection(g.getClipBounds());
                if (!intersection.isEmpty()) {
                    g.fillRect(intersection.x, intersection.y, intersection.width, intersection.height);
                }
                break;
            }
            case NEGATIVE: {
                Rectangle rect = new Rectangle(x, y, width, height);
                Rectangle intersection = rect.intersection(g.getClipBounds());
                if (intersection.isEmpty()) {
                    break;
                }
                Shape clip = g.getClip();
                g.setClip(intersection.x, intersection.y, intersection.width, intersection.height);
                CodeArea.ScrollPosition scrollPosition = codeArea.getScrollPosition();
                g.fillRect(x, y, width, height);
                g.setColor(codeArea.getNegativeCursorColor());
                Rectangle codeRect = codeArea.getCodeSectionRectangle();
                int previewX = codeArea.getPreviewX();
                int charWidth = codeArea.getCharWidth();
                int lineHeight = codeArea.getLineHeight();
                int line = (y + scrollPosition.getScrollLineOffset() - codeRect.y) / lineHeight;
                int scrolledX = x + scrollPosition.getScrollCharPosition() * charWidth
                        + scrollPosition.getScrollCharOffset();
                int posY = codeRect.y + (line + 1) * lineHeight - codeArea.getSubFontSpace()
                        - scrollPosition.getScrollLineOffset();
                if (codeArea.getViewMode() != ViewMode.CODE_MATRIX && scrolledX >= previewX) {
                    int charPos = (scrolledX - previewX) / charWidth;
                    long dataSize = codeArea.getDataSize();
                    long dataPosition = (line + scrollPosition.getScrollLinePosition()) * codeArea.getBytesPerLine()
                            + charPos - scrollPosition.getLineByteShift();
                    if (dataPosition >= dataSize) {
                        g.setClip(clip);
                        break;
                    }

                    char[] previewChars = new char[1];
                    Charset charset = codeArea.getCharset();
                    CharsetEncoder encoder = charset.newEncoder();
                    int maxCharLength = (int) encoder.maxBytesPerChar();
                    byte[] data = new byte[maxCharLength];

                    if (maxCharLength > 1) {
                        int charDataLength = maxCharLength;
                        if (dataPosition + maxCharLength > dataSize) {
                            charDataLength = (int) (dataSize - dataPosition);
                        }

                        codeArea.getData().copyToArray(dataPosition, data, 0, charDataLength);
                        String displayString = new String(data, 0, charDataLength, charset);
                        if (!displayString.isEmpty()) {
                            previewChars[0] = displayString.charAt(0);
                        }
                    } else {
                        if (charMappingCharset == null || charMappingCharset != charset) {
                            buildCharMapping(charset);
                        }

                        previewChars[0] = charMapping[codeArea.getData().getByte(dataPosition) & 0xFF];
                    }

                    if (codeArea.isShowUnprintableCharacters()) {
                        if (unprintableCharactersMapping == null) {
                            buildUnprintableCharactersMapping();
                        }
                        Character replacement = unprintableCharactersMapping.get(previewChars[0]);
                        if (replacement != null) {
                            previewChars[0] = replacement;
                        }
                    }
                    int posX = previewX + charPos * charWidth - scrollPosition.getScrollCharPosition() * charWidth
                            - scrollPosition.getScrollCharOffset();
                    if (codeArea.getCharRenderingMode() == CodeArea.CharRenderingMode.LINE_AT_ONCE) {
                        g.drawChars(previewChars, 0, 1, posX, posY);
                    } else {
                        drawCenteredChar(g, previewChars, 0, charWidth, posX, posY);
                    }
                } else {
                    int charPos = (scrolledX - codeRect.x) / charWidth;
                    int byteOffset = codeArea.computeByteOffsetPerCodeCharOffset(charPos);
                    int codeCharPos = codeArea.computeByteCharPos(byteOffset);
                    char[] lineChars = new char[codeArea.getCodeType().getMaxDigits()];
                    long dataSize = codeArea.getDataSize();
                    long dataPosition = (line + scrollPosition.getScrollLinePosition()) * codeArea.getBytesPerLine()
                            + byteOffset - scrollPosition.getLineByteShift();
                    if (dataPosition >= dataSize) {
                        g.setClip(clip);
                        break;
                    }

                    byte dataByte = codeArea.getData().getByte(dataPosition);
                    CodeAreaUtils.byteToCharsCode(dataByte, codeArea.getCodeType(), lineChars, 0,
                            codeArea.getHexCharactersCase());
                    int posX = codeRect.x + codeCharPos * charWidth - scrollPosition.getScrollCharPosition() * charWidth
                            - scrollPosition.getScrollCharOffset();
                    int charsOffset = charPos - codeCharPos;
                    if (codeArea.getCharRenderingMode() == CodeArea.CharRenderingMode.LINE_AT_ONCE) {
                        g.drawChars(lineChars, charsOffset, 1, posX + (charsOffset * charWidth), posY);
                    } else {
                        drawCenteredChar(g, lineChars, charsOffset, charWidth, posX + (charsOffset * charWidth), posY);
                    }
                }
                g.setClip(clip);
                break;
            }

        }
    }

}

// Class that emulates java ResourceBundle - comment if needed
/*
 * class Messages {
 * 
 * static Map<String,String> map = new HashMap<String,String>();
 * 
 * static {
 * 
 * map.put( "HexViewerPlus.TabName", "Hex" ); map.put( "HexViewerPlus.appName",
 * "Hex Viewer Plus" ); map.put( "HexViewerPlus.HvpFileSettings",
 * "Arquivo de Configuração do Hex Viewer Plus" ); map.put(
 * "HexViewerPlus.position", "Posição" ); map.put( "HexViewerPlus.relative",
 * "Relativo" ); map.put( "HexViewerPlus.charset", "Charset" ); map.put(
 * "HexViewerPlus.selection", "Seleção" ); map.put( "HexViewerPlus.to", "a" );
 * map.put( "HexViewerPlus.settings", "Configurações" ); map.put(
 * "HexViewerPlus.hit", "Ocorrência" ); map.put( "HexViewerPlus.hits",
 * "Ocorrências" ); map.put( "HexViewerPlus.of", "de" ); map.put(
 * "HexViewerPlus.search", "Pesquisar" ); map.put( "HexViewerPlus.nextHit",
 * "Próxima ocorrência" ); map.put( "HexViewerPlus.preHit",
 * "Ocorrência anterior" ); map.put( "HexViewerPlus.goToHit",
 * "Ir para ocorrência" ); map.put( "HexViewerPlus.goToPosition",
 * "Ir para Posição" ); map.put( "HexViewerPlus.selectBlock", "Selecionar Bloco"
 * ); map.put( "HexViewerPlus.selectAll", "Selecionar Tudo" ); map.put(
 * "HexViewerPlus.copyHex", "Copiar Hexadecimal" ); map.put(
 * "HexViewerPlus.copyText", "Copiar Texto" ); map.put( "HexViewerPlus.options",
 * "Opções" ); map.put( "HexViewerPlus.selectColor", "Selecione uma cor" );
 * map.put( "HexViewerPlus.codeArea", "Conteúdo" ); map.put(
 * "HexViewerPlus.header", "Cabeçalho" ); map.put( "HexViewerPlus.layout",
 * "Layout" ); map.put( "HexViewerPlus.cursor", "Cursor" ); map.put(
 * "HexViewerPlus.mode", "Modo" ); map.put( "HexViewerPlus.font", "Fonte" );
 * map.put( "HexViewerPlus.size", "Tamanho" ); map.put( "HexViewerPlus.blink",
 * "Taxa" ); map.put( "HexViewerPlus.bytesPerLine", "Bytes por linha" );
 * map.put( "HexViewerPlus.cursorColor", "Cor do Cursor" ); map.put(
 * "HexViewerPlus.fontColor", "Cor da Fonte" ); map.put(
 * "HexViewerPlus.backGroundColor", "Cor do Fundo" ); map.put(
 * "HexViewerPlus.selectionCharactersMirror", "Seleção de Caracteres - Espelho"
 * ); map.put( "HexViewerPlus.selectionCharactersMain",
 * "Seleção de Caracteres - Principal" ); map.put(
 * "HexViewerPlus.searchHitsFoundMatch", "Ocorrência ao Pesquisar - Encontrado"
 * ); map.put( "HexViewerPlus.searchHitsCurrentMatch",
 * "Ocorrência ao Pesquisar - Selecionado" ); map.put(
 * "HexViewerPlus.loadDefault", "Carregar Padrão" ); map.put(
 * "HexViewerPlus.saveDefault", "Salvar Padrão" ); map.put(
 * "HexViewerPlus.loadFile", "Carregar Arquivo" ); map.put(
 * "HexViewerPlus.saveFile", "Salvar Arquivo" ); map.put( "HexViewerPlus.close",
 * "Fechar" ); map.put( "HexViewerPlus.ok", "OK" ); map.put(
 * "HexViewerPlus.cancel", "Cancel" ); map.put( "HexViewerPlus.hexLowerCase",
 * "Hexadecimal minúsculo" ); map.put( "HexViewerPlus.showHeader",
 * "Mostrar cabecalho" ); map.put( "HexViewerPlus.showLines", "Mostrar linhas"
 * ); map.put( "HexViewerPlus.lineBreak", "Quebra de linha" ); map.put(
 * "HexViewerPlus.showAllCharacters", "Exibir todos os caracteres" ); map.put(
 * "HexViewerPlus.failOpenFile", "Erro ao abrir arquivo!" ); map.put(
 * "HexViewerPlus.failSaveFile", "Erro ao salvar arquivo!" ); map.put(
 * "HexViewerPlus.yes", "Sim" ); map.put( "HexViewerPlus.no", "Não" ); map.put(
 * "HexViewerPlus.overWriteFile", "já existe.\nDeseja substituí-lo?" ); map.put(
 * "HexViewerPlus.select", "Seleção" ); map.put( "HexViewerPlus.type", "Tipo" );
 * map.put( "HexViewerPlus.interval", "Intervalo" ); map.put(
 * "HexViewerPlus.startPosition", "Posição Inicial" ); map.put(
 * "HexViewerPlus.endPosition", "Posição Final" ); map.put(
 * "HexViewerPlus.format", "Formato" ); map.put( "HexViewerPlus.hexadecimal",
 * "Hexadecimal" ); map.put( "HexViewerPlus.decimal", "Decimal" ); map.put(
 * "HexViewerPlus.octal", "Octal" ); map.put(
 * "HexViewerPlus.invalidStartPosition", "Posição Inicial inválida!" ); map.put(
 * "HexViewerPlus.invalidSize", "Tamanho inválido!" ); map.put(
 * "HexViewerPlus.invalidEndPosition", "Posição final inválida!" ); map.put(
 * "HexViewerPlus.start", "Início" ); map.put( "HexViewerPlus.cursor", "Cursor"
 * ); map.put( "HexViewerPlus.invalidHit", "Ocorrência inválida!" ); map.put(
 * "HexViewerPlus.text", "Texto" ); map.put( "HexViewerPlus.all", "Todas" );
 * map.put( "HexViewerPlus.max", "Máximo" ); map.put(
 * "HexViewerPlus.caseSensitive", "Diferenciar MAIÚSCULAS / minúsculas" );
 * map.put( "HexViewerPlus.invalidSearchText", "Texto para pesquisa inválido!"
 * ); map.put( "HexViewerPlus.invalidHexadecimalText",
 * "Hexadecimal para pesquisa inválido!" ); map.put( "HexViewerPlus.invalidMax",
 * "Máximo inválido!" ); map.put( "HexViewerPlus.invalidMaxLessThen",
 * "Máximo inválido!. Menor ou igual a " ); map.put( "HexViewerPlus.invalid",
 * "inválido" ); map.put( "HexViewerPlus.showPositionBar",
 * "Mostra barra de Posição" ); map.put(
 * "HexViewerPlus.showSelectionBar","Mostrar barra de Seleção" ); map.put(
 * "HexViewerPlus.showLineNumberBackground","Mesclar Cabeçalho com Layout" );
 * map.put( "HexViewerPlus.headerLineBackground","Cabeçalho e Linhas Numeradas:"
 * );
 * 
 * map.put( "HexSearcherImpl.hits", "ocorrências" ); map.put(
 * "HexSearcherImpl.hit", "Ocorrência" ); map.put( "HexSearcherImpl.of", "de" );
 * map.put( "HexSearcherImpl.timeLeft", "Tempo Restante" ); map.put(
 * "HexSearcherImpl.noHits", "Nenhum resultado encontrado" );
 * 
 * 
 * //English
 * 
 * map.put( "HexViewerPlus.TabName", "Hex" ); map.put( "HexViewerPlus.appName",
 * "Hex Viewer Plus" ); map.put( "HexViewerPlus.HvpFileSettings",
 * "Hex Viewer Plus File Settings" ); map.put( "HexViewerPlus.position",
 * "Position" ); map.put( "HexViewerPlus.relative", "Relative" ); map.put(
 * "HexViewerPlus.charset", "Charset" ); map.put( "HexViewerPlus.selection",
 * "Selection" ); map.put( "HexViewerPlus.to", "to" ); map.put(
 * "HexViewerPlus.settings", "Settings" ); map.put( "HexViewerPlus.hit", "Hit"
 * ); map.put( "HexViewerPlus.hits", "Hits" ); map.put( "HexViewerPlus.of", "of"
 * ); map.put( "HexViewerPlus.search", "Search" ); map.put(
 * "HexViewerPlus.nextHit", "Next Hit" ); map.put( "HexViewerPlus.preHit",
 * "Previous Hit" ); map.put( "HexViewerPlus.goToHit", "Go to Hit" ); map.put(
 * "HexViewerPlus.goToPosition", "Go to Position" ); map.put(
 * "HexViewerPlus.selectBlock", "Select Block" ); map.put(
 * "HexViewerPlus.selectAll", "Select All" ); map.put( "HexViewerPlus.copyHex",
 * "Copy Hexadecimal" ); map.put( "HexViewerPlus.copyText", "Copy Text" );
 * map.put( "HexViewerPlus.options", "Options" ); map.put(
 * "HexViewerPlus.selectColor", "Pick a Color" ); map.put(
 * "HexViewerPlus.codeArea", "Code Area" ); map.put( "HexViewerPlus.header",
 * "Header" ); map.put( "HexViewerPlus.layout", "Layout" ); map.put(
 * "HexViewerPlus.cursor", "Cursor" ); map.put( "HexViewerPlus.mode", "Mode" );
 * map.put( "HexViewerPlus.font", "Font" ); map.put( "HexViewerPlus.size",
 * "Size" ); map.put( "HexViewerPlus.blink", "Blink" ); map.put(
 * "HexViewerPlus.bytesPerLine", "Bytes per line" ); map.put(
 * "HexViewerPlus.cursorColor", "Cursor Color" ); map.put(
 * "HexViewerPlus.fontColor", "Font Color" ); map.put(
 * "HexViewerPlus.backGroundColor", "Background Color" ); map.put(
 * "HexViewerPlus.selectionCharactersMirror", "Select Characters - Mirror" );
 * map.put( "HexViewerPlus.selectionCharactersMain", "Select Characters - Main"
 * ); map.put( "HexViewerPlus.searchHitsFoundMatch", "Search Hits - Found Match"
 * ); map.put( "HexViewerPlus.searchHitsCurrentMatch",
 * "Search Hits - Current Match" ); map.put( "HexViewerPlus.loadDefault",
 * "Load Default" ); map.put( "HexViewerPlus.saveDefault", "Save Default" );
 * map.put( "HexViewerPlus.close", "Close" ); map.put( "HexViewerPlus.ok", "OK"
 * ); map.put( "HexViewerPlus.cancel", "Cancel" ); map.put(
 * "HexViewerPlus.hexLowerCase", "Hexadecimal lowercase" ); map.put(
 * "HexViewerPlus.showHeader", "Show Header" ); map.put(
 * "HexViewerPlus.showLines", "Show Lines" ); map.put(
 * "HexViewerPlus.lineBreak", "Line Break" ); map.put(
 * "HexViewerPlus.showAllCharacters", "Show All Characters" ); map.put(
 * "HexViewerPlus.loadFile", "Load File" ); map.put( "HexViewerPlus.saveFile",
 * "Save File" ); map.put( "HexViewerPlus.failOpenFile", "Failed to Open File!"
 * ); map.put( "HexViewerPlus.failSaveFile", "Failed to Save File!" ); map.put(
 * "HexViewerPlus.yes", "Yes" ); map.put( "HexViewerPlus.no", "No" ); map.put(
 * "HexViewerPlus.overWriteFile", "already exists. Overwrite?" ); map.put(
 * "HexViewerPlus.select", "Selection" ); map.put( "HexViewerPlus.type", "Type"
 * ); map.put( "HexViewerPlus.interval", "Interval" ); map.put(
 * "HexViewerPlus.startPosition", "Start Position" ); map.put(
 * "HexViewerPlus.endPosition", "End Position" ); map.put(
 * "HexViewerPlus.format", "Format" ); map.put( "HexViewerPlus.hexadecimal",
 * "Hexadecimal" ); map.put( "HexViewerPlus.decimal", "Decimal" ); map.put(
 * "HexViewerPlus.octal", "Octal" ); map.put(
 * "HexViewerPlus.invalidStartPosition", "Invalid Start Position!" ); map.put(
 * "HexViewerPlus.invalidSize", "Invalid Size!" ); map.put(
 * "HexViewerPlus.invalidEndPosition", "Invalid End Position!" ); map.put(
 * "HexViewerPlus.start", "Start" ); map.put( "HexViewerPlus.cursor", "Cursor"
 * ); map.put( "HexViewerPlus.invalidHit", "Invalid Hit!" ); map.put(
 * "HexViewerPlus.text", "Text" ); map.put( "HexViewerPlus.all", "All" );
 * map.put( "HexViewerPlus.max", "Max" ); map.put(
 * "HexViewerPlus.caseSensitive", "Case Sensitive" ); map.put(
 * "HexViewerPlus.invalidSearchText", "Invalid Serach Text!" ); map.put(
 * "HexViewerPlus.invalidHexadecimalText", "Invalid Hexadecimal Search!" );
 * map.put( "HexViewerPlus.invalidMax", "Invalid Max" ); map.put(
 * "HexViewerPlus.invalidMaxLessThen", "Invalid Max. Less than or equal to " );
 * map.put( "HexViewerPlus.invalid", "inválid" ); map.put(
 * "HexViewerPlus.showPositionBar", "Show Position Bar" ); map.put(
 * "HexViewerPlus.showSelectionBar","Show Selection Bar" ); map.put(
 * "HexViewerPlus.showLineNumberBackground","Merge Header and Line Column Background"
 * ); map.put(
 * "HexViewerPlus.headerLineBackground","Header and Line Number Column:" );
 * 
 * map.put( "HexSearcherImpl.hits", "Hits" ); map.put( "HexSearcherImpl.hit",
 * "Hit" ); map.put( "HexSearcherImpl.of", "of" ); map.put(
 * "HexSearcherImpl.timeLeft", "Time Left" ); map.put( "HexSearcherImpl.noHits",
 * "No results found" );
 * 
 * 
 * 
 * }
 * 
 * public static String getString(String key) { if ( map.containsKey( key ) ) {
 * return map.get(key); }else{ return "NOT FOUND MSG"; } } }
 */