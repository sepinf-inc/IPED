package iped.geo.impl;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import iped.engine.config.ConfigurationManager;
import iped.geo.localization.Messages;

public class JMapOptionsPane extends JOptionPane {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    static final String BING_URL = "http://r{s}.ortho.tiles.virtualearth.net/tiles/r{quad}.png?g=1";
    static final String OSM_URL = "https://tile.openstreetmap.org/${z}/${x}/${y}.png";

    static File userHome = new File(System.getProperty("user.home"), ".iped");

    static File tileServerUrlFile = null;
    static File googleApiKeyFile = null;
    static String googleApiKey = null;

    JDialog dialog = null;
    static JMapOptionsPane singleton = null;
    String url = "";
    JTextField txTileLayerURl;
    JRadioButton btnGoogleMaps;

    JComboBox<String> cbGoogleTileType;

    String rbOutraCache = "";
    boolean canceled = true;
    JTextField txGoogleApiKey = new JTextField();
    HashMap<String, String> defaultTilesSources;
    JRadioButton btnLeaflet;
    JComboBox<String> cbTileSrcs;
    JRadioButton rbOutra;

    private JMapOptionsPane(Component parentComponent) {
        Window window = SwingUtilities.windowForComponent(parentComponent);
        String title = Messages.getString("JMapOptionsPane.title");
        if (window instanceof Frame)
            dialog = new JDialog((Frame) window, title, true);
        else if (window instanceof Dialog)
            dialog = new JDialog((Dialog) window, title, true);
        else
            dialog = new JDialog((Frame) null, title, true);

        JButton btOk = new JButton("Ok");
        JButton btCancel = new JButton("Cancel");
        btOk.setEnabled(false);

        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout());

        JPanel paneGoogle = new JPanel();
        paneGoogle.setLayout(new BorderLayout());
        JPanel paneGoogleKey = new JPanel();
        JLabel lbGoogleApiKey = new JLabel(Messages.getString("JMapOptionsPane.GoogleApiKeyLabel"));

        txGoogleApiKey.setText(getGoogleAPIKey());
        txGoogleApiKey.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                googleApiKey = txGoogleApiKey.getText();
                if ("".equals(txGoogleApiKey.getText())) {
                    btOk.setEnabled(false);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                googleApiKey = txGoogleApiKey.getText();
                if ((!"".equals(txGoogleApiKey.getText())) && (!btOk.isEnabled())) {
                    btOk.setEnabled(true);
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                googleApiKey = txGoogleApiKey.getText();
                if ("".equals(txGoogleApiKey.getText())) {
                    btOk.setEnabled(false);
                } else {
                    btOk.setEnabled(true);
                }
            }
        });

        JPanel paneLeaflet = new JPanel();
        btnLeaflet = new JRadioButton(Messages.getString("JMapOptionsPane.UseLeaflet"));
        paneLeaflet.setLayout(new BorderLayout());
        paneLeaflet.add(btnLeaflet, BorderLayout.BEFORE_FIRST_LINE);

        txTileLayerURl = new JTextField();

        txTileLayerURl.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                if ("".equals(txTileLayerURl.getText())) {
                    btOk.setEnabled(false);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                if ((!"".equals(txTileLayerURl.getText())) && (!btOk.isEnabled())) {
                    btOk.setEnabled(true);
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                if ("".equals(txTileLayerURl.getText())) {
                    btOk.setEnabled(false);
                } else {
                    btOk.setEnabled(true);
                }
            }
        });

        JPanel paneTileUrlSelect = new JPanel();
        paneTileUrlSelect.setLayout(new BorderLayout());
        paneTileUrlSelect.setBorder(BorderFactory.createEmptyBorder(1, 10, 1, 1));

        cbTileSrcs = new JComboBox<>();

        MapPanelConfig mpc = (MapPanelConfig) ConfigurationManager.get().findObjects(MapPanelConfig.class).toArray()[0];
        defaultTilesSources = mpc.getDefaultTilesSources();

        Set<String> ms = defaultTilesSources.keySet();
        for (Iterator<String> iterator = ms.iterator(); iterator.hasNext();) {
            String key = iterator.next();

            cbTileSrcs.addItem(key);
        }

        JRadioButton rbList = new JRadioButton("");
        rbOutra = new JRadioButton(Messages.getString("JMapOptionsPane.AnotherURL"));

        btnLeaflet.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rbList.setSelected(true);
            }
        });

        ButtonGroup bgTileUrl = new ButtonGroup();

        bgTileUrl.add(rbList);
        bgTileUrl.add(rbOutra);
        paneTileUrlSelect.add(rbList, BorderLayout.WEST);
        paneTileUrlSelect.add(cbTileSrcs, BorderLayout.CENTER);
        paneTileUrlSelect.add(rbOutra, BorderLayout.SOUTH);
        paneLeaflet.add(paneTileUrlSelect, BorderLayout.CENTER);

        JLabel lbTileLayerURL = new JLabel(Messages.getString("JMapOptionsPane.UrlPatternLabel"));

        txTileLayerURl.setText("");
        txTileLayerURl.setEnabled(false);
        txTileLayerURl.setMinimumSize(new Dimension(150, 10));

        JPanel urlPanel = new JPanel(new BorderLayout());
        urlPanel.setBorder(BorderFactory.createEmptyBorder(1, 10, 1, 1));
        urlPanel.add(lbTileLayerURL, BorderLayout.NORTH);
        urlPanel.add(txTileLayerURl, BorderLayout.SOUTH);
        paneLeaflet.add(urlPanel, BorderLayout.SOUTH);
        pane.add(paneLeaflet);

        cbGoogleTileType = new JComboBox<String>();
        cbGoogleTileType.addItem("Hybrid");
        cbGoogleTileType.addItem("Satellite");
        cbGoogleTileType.addItem("Roadmap");
        cbGoogleTileType.addItem("Terrain");
        cbGoogleTileType.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                btnGoogleMaps.setEnabled(true);
                btnGoogleMaps.setSelected(true);
            }
        });
        JPanel cbGoogleTileTypePanel = new JPanel(new BorderLayout());
        cbGoogleTileTypePanel.setBorder(BorderFactory.createEmptyBorder(1, 10, 1, 1));
        cbGoogleTileTypePanel.add(cbGoogleTileType, BorderLayout.CENTER);

        btnGoogleMaps = new JRadioButton(Messages.getString("JMapOptionsPane.UseGoogleMaps"));
        JPanel paneGoogleOpt = new JPanel(new BorderLayout());
        paneGoogleOpt.add(btnGoogleMaps, BorderLayout.BEFORE_FIRST_LINE);
        paneGoogleOpt.add(cbGoogleTileTypePanel, BorderLayout.CENTER);
        paneGoogleKey.setBorder(BorderFactory.createEmptyBorder(1, 10, 1, 1));
        paneGoogleKey.setLayout(new BorderLayout());
        paneGoogleKey.add(lbGoogleApiKey, BorderLayout.BEFORE_FIRST_LINE);
        txGoogleApiKey.setMinimumSize(new Dimension(150, 10));
        paneGoogleKey.add(txGoogleApiKey, BorderLayout.AFTER_LAST_LINE);
        txGoogleApiKey.setEnabled(false);
        paneGoogle.add(paneGoogleOpt, BorderLayout.BEFORE_FIRST_LINE);
        paneGoogle.add(paneGoogleKey, BorderLayout.CENTER);
        pane.add(paneGoogle, BorderLayout.BEFORE_FIRST_LINE);

        ButtonGroup bgMapImpl = new ButtonGroup();
        bgMapImpl.add(btnLeaflet);
        bgMapImpl.add(btnGoogleMaps);

        Container contentPane = dialog.getContentPane();
        contentPane.setLayout(new BorderLayout());
        ((BorderLayout) pane.getLayout()).setVgap(10);
        contentPane.add(pane, BorderLayout.CENTER);

        JPanel btPanels = new JPanel();
        btPanels.add(btOk);
        btPanels.add(btCancel);
        contentPane.add(btPanels, BorderLayout.AFTER_LAST_LINE);
        ((JPanel) contentPane).setBorder(new EmptyBorder(30, 30, 30, 20));
        btOk.setMinimumSize(new Dimension(40, 5));
        btOk.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canceled = false;
                dialog.dispose();
            }
        });
        btCancel.setMinimumSize(new Dimension(40, 5));
        btCancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                canceled = true;
                dialog.dispose();
            }
        });

        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(parentComponent);

        cbTileSrcs.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                txTileLayerURl.setText(defaultTilesSources.get(cbTileSrcs.getSelectedItem()));
                bgTileUrl.setSelected(rbList.getModel(), true);
            }
        });

        ItemListener ilTileUrlSelect = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getSource() == rbList) {
                    if (rbList.isSelected()) {
                        bgMapImpl.setSelected(btnLeaflet.getModel(), true);
                        txTileLayerURl.setEnabled(false);
                        txTileLayerURl.setText(defaultTilesSources.get(cbTileSrcs.getSelectedItem()));
                        btOk.setEnabled(true);
                    }
                }
                if (e.getSource() == rbOutra) {
                    if (rbOutra.isSelected()) {
                        bgMapImpl.setSelected(btnLeaflet.getModel(), true);
                        txTileLayerURl.setEnabled(true);
                        txTileLayerURl.setText(rbOutraCache);
                        if ("".equals(rbOutraCache)) {
                            btOk.setEnabled(false);
                        }
                    }
                }
                txTileLayerURl.repaint();
            }
        };
        rbList.addItemListener(ilTileUrlSelect);
        // rbOpenStreetMaps.addItemListener(ilTileUrlSelect);
        rbOutra.addItemListener(ilTileUrlSelect);

        txTileLayerURl.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rbOutraCache = txTileLayerURl.getText();
            }
        });

        ItemListener ilMapImpl = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getSource() == btnGoogleMaps) {
                    if (btnGoogleMaps.isSelected()) {
                        bgTileUrl.clearSelection();
                        txTileLayerURl.setEnabled(false);
                        txTileLayerURl.setText("");
                        txGoogleApiKey.setEnabled(true);
                        if ("".equals(txGoogleApiKey.getText().trim())) {
                            btOk.setEnabled(false);
                        } else {
                            btOk.setEnabled(true);
                        }
                        txTileLayerURl.repaint();
                    }
                }
                if (e.getSource() == btnLeaflet) {
                    if (btnLeaflet.isSelected()) {
                        txGoogleApiKey.setEnabled(false);
                        txGoogleApiKey.repaint();
                    }
                }
            }
        };
        btnGoogleMaps.addItemListener(ilMapImpl);
        btnLeaflet.addItemListener(ilMapImpl);
    }

    public void config(String tilesSourceURL) {
        if (tilesSourceURL.contains("googleapis")) {
            txTileLayerURl.setText("");

            for (int i = 0; i < cbGoogleTileType.getItemCount(); i++) {
                String type = cbGoogleTileType.getItemAt(i);
                if (tilesSourceURL.contains(type.toLowerCase())) {
                    cbGoogleTileType.setSelectedIndex(i);
                }
            }

            btnGoogleMaps.setSelected(true);
        } else {
            btnGoogleMaps.setSelected(false);
            btnLeaflet.setSelected(true);

            boolean achou = false;
            Set<String> ms = defaultTilesSources.keySet();
            for (Iterator<String> iterator = ms.iterator(); iterator.hasNext();) {
                String key = iterator.next();
                if (defaultTilesSources.get(key).equals(tilesSourceURL)) {
                    cbTileSrcs.setSelectedItem(key);
                    achou = true;
                    break;
                }
            }
            if (!achou) {
                rbOutra.setSelected(true);
                txTileLayerURl.setText(tilesSourceURL);
            }
        }
    }

    public void show(Component parent) {
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        if (btnGoogleMaps.isSelected()) {
            url = "https://maps.googleapis.com/maps/api/js?mapType=" + ((String) cbGoogleTileType.getSelectedItem()).toLowerCase() + "&key=" + txGoogleApiKey.getText().trim();
        } else {
            url = txTileLayerURl.getText();
        }
        dialog.dispose();
    }

    public static File getLastTileSourceURLFile() {
        try {
            if (tileServerUrlFile == null) {
                tileServerUrlFile = new File(userHome, "last_tileserver" + ".txt"); //$NON-NLS-1$
            }
            tileServerUrlFile.createNewFile();
        } catch (Exception e) {
            tileServerUrlFile = null;
        }
        return tileServerUrlFile;
    }

    public static File getLastGoogleAPIKey() {
        try {
            if (googleApiKeyFile == null) {
                googleApiKeyFile = new File(userHome, "googleApi.key"); //$NON-NLS-1$
            }
            googleApiKeyFile.createNewFile();
        } catch (Exception e) {
            googleApiKeyFile = null;
        }
        return googleApiKeyFile;
    }

    public static String getGoogleAPIKey() {
        if (googleApiKey == null) {
            File f = getLastGoogleAPIKey();
            if (f != null) {
                try (BufferedReader reader = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                    googleApiKey = reader.readLine();
                    if (googleApiKey == null)
                        return "";
                    return googleApiKey;
                } catch (IOException e) {
                    // ignore
                }
            }
            return "";
        } else {
            return googleApiKey;
        }
    }

    public static String getSavedTilesSourceURL() {
        File f = getLastTileSourceURLFile();
        String tileSourceURL = null;
        if (f != null) {
            try (BufferedReader reader = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                tileSourceURL = reader.readLine();
                if (tileSourceURL == null)
                    return null;
                if (tileSourceURL.length() <= 2) {
                    return null;
                } else {
                    return tileSourceURL;
                }
            } catch (IOException e) {
            }
        }
        return null;
    }

    public String getUrl() {
        return url;
    }

    public static String showOptionsDialog(Component parentComponent, String tilesSourceURL) {
        if (singleton == null) {
            singleton = new JMapOptionsPane(parentComponent);
        }

        if (tilesSourceURL != null) {
            if (!"".equals(tilesSourceURL)) {
                singleton.config(tilesSourceURL);
            }
        }
        singleton.show(parentComponent);

        if (singleton.canceled) {
            return null;
        }

        if (tileServerUrlFile != null) {
            try (BufferedWriter fw = Files.newBufferedWriter(tileServerUrlFile.toPath(), StandardCharsets.UTF_8)) {
                fw.write(singleton.getUrl() + "\n");
            } catch (IOException e) {
                // skip temp cache
            }
        }

        // salva temporariamente a chave API do google utilizada
        if (singleton.btnGoogleMaps.isSelected()) {
            getLastGoogleAPIKey();
            try (BufferedWriter fw = Files.newBufferedWriter(googleApiKeyFile.toPath(), StandardCharsets.UTF_8)) {
                fw.write(singleton.txGoogleApiKey.getText() + "\n");
            } catch (IOException e) {
                // skip temp cache
            }
        }

        return singleton.getUrl();
    }

    public String getGoogleMapType() {
        return (String) cbGoogleTileType.getSelectedItem();
    }

    public static String showOptionsDialog(Component parentComponent) throws HeadlessException {
        return showOptionsDialog(parentComponent, null);
    }
}
