package dpf.mt.gpinf.mapas.impl;

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
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import dpf.mt.gpinf.mapas.util.Messages;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;

public class JMapOptionsPane extends JOptionPane {
	
	static final String BING_URL = "http://r{s}.ortho.tiles.virtualearth.net/tiles/r{quad}.png?g=1";
	static final String OSM_URL = "https://tile.openstreetmap.org/${z}/${x}/${y}.png";

	static File tileServerUrlFile=null;
	static File googleApiKeyFile=null;
	static String googleApiKey=null;

	JDialog dialog = null;
	static JMapOptionsPane singleton = null;
	String url = "";
	JTextField txTileLayerURl;
	JRadioButton btnGoogleMaps;
	String rbOutraCache="";
	boolean canceled=true;
	JTextField txGoogleApiKey=new JTextField();
	HashMap<String,String> defaultTilesSources;
	JRadioButton btnLeaflet;
	JComboBox cbTileSrcs;
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

        int style = JRootPane.PLAIN_DIALOG;

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
                if ("".equals(txGoogleApiKey.getText())) {
                    btOk.setEnabled(false);
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                if ((!"".equals(txGoogleApiKey.getText())) && (!btOk.isEnabled())) {
                    btOk.setEnabled(true);
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
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
    	JLabel lbTileLayerURL = new JLabel(Messages.getString("JMapOptionsPane.UrlPatternLabel"));
    	paneLeaflet.add(lbTileLayerURL, BorderLayout.LINE_START);
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

        cbTileSrcs = new JComboBox();

        MapaPanelConfig mpc = (MapaPanelConfig) ConfigurationManager.get().findObjects(MapaPanelConfig.class).toArray()[0];
        defaultTilesSources = mpc.getDefaultTilesSources();

        Set<String> ms=defaultTilesSources.keySet();
        for (Iterator iterator = ms.iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();

			cbTileSrcs.addItem(key);
		}
        
        JRadioButton rbList =  new JRadioButton("");
        rbOutra =  new JRadioButton(Messages.getString("JMapOptionsPane.AnotherURL"));
        
        ButtonGroup bgTileUrl = new ButtonGroup();
        
        bgTileUrl.add(rbList);
        bgTileUrl.add(rbOutra);
        paneTileUrlSelect.add(rbList);
        paneTileUrlSelect.add(cbTileSrcs,BorderLayout.BEFORE_FIRST_LINE);
        paneTileUrlSelect.add(rbOutra);
        paneLeaflet.add(paneTileUrlSelect, BorderLayout.LINE_START);
        
        txTileLayerURl.setText("");
        txTileLayerURl.setEnabled(false);
        txTileLayerURl.setMinimumSize(new Dimension(150,10));
    	paneLeaflet.add(txTileLayerURl, BorderLayout.AFTER_LAST_LINE);
        pane.add(paneLeaflet);

        btnGoogleMaps = new JRadioButton(Messages.getString("JMapOptionsPane.UseGoogleMaps"));
    	paneGoogle.add(btnGoogleMaps,BorderLayout.BEFORE_FIRST_LINE);
    	paneGoogleKey.setLayout(new BorderLayout());
    	paneGoogleKey.add(lbGoogleApiKey,BorderLayout.BEFORE_FIRST_LINE);
    	txGoogleApiKey.setMinimumSize(new Dimension(150,10));
    	paneGoogleKey.add(txGoogleApiKey,BorderLayout.AFTER_LAST_LINE);
    	txGoogleApiKey.setEnabled(false);
    	paneGoogle.add(paneGoogleKey,BorderLayout.CENTER);
    	pane.add(paneGoogle, BorderLayout.BEFORE_FIRST_LINE);

    	ButtonGroup bgMapImpl = new ButtonGroup();
    	bgMapImpl.add(btnLeaflet);
    	bgMapImpl.add(btnGoogleMaps);

        Container contentPane = dialog.getContentPane();
        contentPane.setLayout(new BorderLayout());
        ((BorderLayout)pane.getLayout()).setVgap(10);
        contentPane.add(pane, BorderLayout.CENTER);

        JPanel btPanels = new JPanel();
        btPanels.add(btOk);
        btPanels.add(btCancel);
        contentPane.add(btPanels, BorderLayout.AFTER_LAST_LINE);
        ((JPanel)contentPane).setBorder(new EmptyBorder(30, 30, 30, 20));
        btOk.setMinimumSize(new Dimension(40, 5));
        btOk.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				canceled=false;
				dialog.dispose();
			}
		});
        btCancel.setMinimumSize(new Dimension(40, 5));
        btCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				canceled=true;
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
				if(e.getSource()== rbList) {
					if(rbList.isSelected()) {
						bgMapImpl.setSelected(btnLeaflet.getModel(), true);
						txTileLayerURl.setEnabled(false);
						txTileLayerURl.setText(defaultTilesSources.get(cbTileSrcs.getSelectedItem()));
						btOk.setEnabled(true);
					}
				}
				if(e.getSource()== rbOutra) {
					if(rbOutra.isSelected()) {
						bgMapImpl.setSelected(btnLeaflet.getModel(), true);
						txTileLayerURl.setEnabled(true);
						txTileLayerURl.setText(rbOutraCache);
						if("".equals(rbOutraCache)){
							btOk.setEnabled(false);
						}
					}
				}
				txTileLayerURl.repaint();
			}
		};
		rbList.addItemListener(ilTileUrlSelect);
		//rbOpenStreetMaps.addItemListener(ilTileUrlSelect);
		rbOutra.addItemListener(ilTileUrlSelect);
		
		txTileLayerURl.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				rbOutraCache=txTileLayerURl.getText();
			}
		});
		
		ItemListener ilMapImpl = new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if(e.getSource()==btnGoogleMaps) {
					if(btnGoogleMaps.isSelected()) {
						bgTileUrl.clearSelection();
						txTileLayerURl.setEnabled(false);
						txTileLayerURl.setText("");
						txGoogleApiKey.setEnabled(true);
						if("".equals(txGoogleApiKey.getText().trim())) {
							btOk.setEnabled(false);
						}else {
							btOk.setEnabled(true);
						}
						txTileLayerURl.repaint();
					}
				}
				if(e.getSource()==btnLeaflet) {					
					if(btnLeaflet.isSelected()) {
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
		if(tilesSourceURL.contains("googlemaps")) {
			txTileLayerURl.setText("");
			btnGoogleMaps.setSelected(true);
		}else {
			btnGoogleMaps.setSelected(false);
			btnLeaflet.setSelected(true);

			boolean achou = false;
	        Set<String> ms=defaultTilesSources.keySet();
	        for (Iterator iterator = ms.iterator(); iterator.hasNext();) {
				String key = (String) iterator.next();
				if(defaultTilesSources.get(key).equals(tilesSourceURL)) {
					cbTileSrcs.setSelectedItem(key);
					achou = true;
					break;
				}
			}
	        if(!achou) {
	        	rbOutra.setSelected(true);
				txTileLayerURl.setText(tilesSourceURL);
	        }
		}
	}

	public void show() {
        dialog.show();
        if(btnGoogleMaps.isSelected()) {
        	url="http://www.googlemaps.com.br";
        }else {
            url=txTileLayerURl.getText();
        }
        dialog.dispose();
	}
	
	public static File getTempTileSourceURLFile() {
    	try {
    		if(tileServerUrlFile==null) {
    	    	String tempdir = System.getProperty("java.io.basetmpdir"); //$NON-NLS-1$
    	        if (tempdir == null)
    	            tempdir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
    	        tileServerUrlFile = new File(tempdir, "iped_tileserver" + ".tmp"); //$NON-NLS-1$
    		}
			tileServerUrlFile.createNewFile();
		} catch (Exception e) {
			tileServerUrlFile=null;
		}
		return tileServerUrlFile;
	}

	public static File getTempGoogleAPIKey() {
    	try {
    		if(googleApiKeyFile==null) {
    	    	String tempdir = System.getProperty("java.io.basetmpdir"); //$NON-NLS-1$
    	        if (tempdir == null)
    	            tempdir = System.getProperty("java.io.tmpdir"); //$NON-NLS-1$
    	        googleApiKeyFile = new File(tempdir, "iped_googleapikey" + ".tmp"); //$NON-NLS-1$
    		}
    		googleApiKeyFile.createNewFile();
		} catch (Exception e) {
			googleApiKeyFile=null;
		}
		return googleApiKeyFile;
	}

	public static String getGoogleAPIKey() {
		if(googleApiKey==null) {
			File f = getTempGoogleAPIKey();
			try {
				if(f!=null) {
					DataInputStream dis;
					dis = new DataInputStream(new FileInputStream(f));
					googleApiKey = dis.readLine();
					if(googleApiKey==null) return "";
					return googleApiKey;
				}
			} catch (FileNotFoundException e) {
			} catch (IOException e) {
			}
			return "";
		}else {
			return googleApiKey;
		}
	}
	
	public static String getSavedTilesSourceURL() {
		File f = getTempTileSourceURLFile();
		String tileSourceURL=null;
		try {
			if(f!=null) {
				DataInputStream dis;
				dis = new DataInputStream(new FileInputStream(f));
				tileSourceURL = dis.readLine();
				if(tileSourceURL==null) return null;
				if(tileSourceURL.length()<=2) {
					return null;
				}else {
					return tileSourceURL;
				}
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}
		return null;
	}
    
    public String getUrl() {
    	return url;
    }

	public static String showOptionsDialog(Component parentComponent, String tilesSourceURL) {
    	if(singleton==null) {
    		singleton = new JMapOptionsPane(parentComponent);
    	}

    	if(tilesSourceURL!=null) {
    		if(!"".equals(tilesSourceURL)) {
    			singleton.config(tilesSourceURL);
    		}
    	}
    	singleton.show();
    	
    	if(singleton.canceled) {
    		return null;
    	}
    	
    	if(tileServerUrlFile!=null) {
        	try {
	            FileWriter fw = new FileWriter(tileServerUrlFile);
	            fw.write(singleton.getUrl()+"\n");
	            fw.flush();
	            fw.close();
			} catch (IOException e) {
				//skip temp cache
			}
    	}

    	//salva temporariamente a chave API do google utilizada
    	if(singleton.btnGoogleMaps.isSelected()) {
        	try {
        		getTempGoogleAPIKey();
	            FileWriter fw = new FileWriter(googleApiKeyFile);
	            fw.write(singleton.txGoogleApiKey.getText()+"\n");
	            fw.flush();
	            fw.close();
			} catch (IOException e) {
				//skip temp cache
			}
    	}
    	
    	return singleton.getUrl();
    }

	public static String showOptionsDialog(Component parentComponent)
            throws HeadlessException {
		return showOptionsDialog(parentComponent, null);
	}
}
