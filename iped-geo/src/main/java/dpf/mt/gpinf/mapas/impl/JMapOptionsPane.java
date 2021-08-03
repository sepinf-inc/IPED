package dpf.mt.gpinf.mapas.impl;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
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

import dpf.sp.gpinf.indexer.config.ConfigurationManager;

public class JMapOptionsPane extends JOptionPane {
	
	static final String BING_URL = "http://r{s}.ortho.tiles.virtualearth.net/tiles/r{quad}.png?g=1";
	static final String OSM_URL = "https://tile.openstreetmap.org/${z}/${x}/${y}.png";
	
	JDialog dialog = null;
	static JMapOptionsPane singleton = null;
	String url = "";
	JTextField txTileLayerURl;
	JRadioButton btnGoogleMaps;
	String rbOutraCache="";
	boolean canceled=true;
	HashMap<String,String> defaultTilesSources;

	
	private JMapOptionsPane(Component parentComponent){
    	Window window = SwingUtilities.windowForComponent(parentComponent);

    	dialog = new JDialog((Frame)window, "Selecione a fonte de mapas a ser usada" , true);

        int style = JRootPane.PLAIN_DIALOG;

        JButton btOk = new JButton("Ok");
        JButton btCancel = new JButton("Cancel");
        btOk.setEnabled(false);

        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout());

        JPanel paneGoogle = new JPanel();
        paneGoogle.setLayout(new BorderLayout());
        JPanel paneGoogleKey = new JPanel();
        JTextField txGoogleApiKey = new JTextField();
        JLabel lbGoogleApiKey = new JLabel("Entre uma chave licenciada para utilizar a API do google Maps.");
		
        txGoogleApiKey.getDocument().addDocumentListener(new DocumentListener() {		 
			 @Override 
			 public void removeUpdate(DocumentEvent e) {
				 if("".equals(txGoogleApiKey.getText())){ 
					 btOk.setEnabled(false); 
				 } 
			 }
				 
			@Override 
			public void insertUpdate(DocumentEvent e) {
				if((!"".equals(txGoogleApiKey.getText()))&& (!btOk.isEnabled())){
					btOk.setEnabled(true); 
				} 
			}
				 
			@Override 
			public void changedUpdate(DocumentEvent e) {
				if("".equals(txGoogleApiKey.getText())){ 
					btOk.setEnabled(false); 
				} else {
					btOk.setEnabled(true); 
				} 
			} 
		});
        
        JPanel paneLeaflet = new JPanel();
    	JRadioButton btnLeaflet = new JRadioButton("Usar o leaflet");
    	paneLeaflet.setLayout(new BorderLayout());
    	paneLeaflet.add(btnLeaflet, BorderLayout.BEFORE_FIRST_LINE);        
    	JLabel lbTileLayerURL = new JLabel("Entre com o padrão de URLs para a origem dos mapas:");
    	paneLeaflet.add(lbTileLayerURL, BorderLayout.LINE_START);
        txTileLayerURl = new JTextField();
        
        txTileLayerURl.getDocument().addDocumentListener(new DocumentListener() {
			 @Override 
			 public void removeUpdate(DocumentEvent e) {
				 if("".equals(txTileLayerURl.getText())){ 
					 btOk.setEnabled(false); 
				 } 
			 }
				 
			@Override 
			public void insertUpdate(DocumentEvent e) {
				if((!"".equals(txTileLayerURl.getText()))&& (!btOk.isEnabled())){
					btOk.setEnabled(true); 
				} 
			}
				 
			@Override 
			public void changedUpdate(DocumentEvent e) {
				if("".equals(txTileLayerURl.getText())){ 
					btOk.setEnabled(false); 
				} else {
					btOk.setEnabled(true); 
				} 
			} 
		});

        JPanel paneTileUrlSelect = new JPanel();

        JComboBox cbTileSrcs = new JComboBox();
        MapaPanelConfig mpc = (MapaPanelConfig) ConfigurationManager.getInstance().findObjects(MapaPanelConfig.class).toArray()[0];
        defaultTilesSources = mpc.getDefaultTilesSources();
        Set<String> ms=defaultTilesSources.keySet();
        for (Iterator iterator = ms.iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();

			cbTileSrcs.addItem(key);			
		}
        
        JRadioButton rbList =  new JRadioButton("");
        JRadioButton rbOutra =  new JRadioButton("Outra URL");
        
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

        btnGoogleMaps = new JRadioButton("Usar o GoogleMaps");
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
						btOk.setEnabled(false);
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

	public void show() {
        dialog.show();
        if(btnGoogleMaps.isSelected()) {
        	url="http://www.googlemaps.com.br";
        }else {
            url=txTileLayerURl.getText();
        }
        dialog.dispose();
	}

    public static String showOptionsDialog(Component parentComponent)
        throws HeadlessException {

    	if(singleton==null) {
    		singleton = new JMapOptionsPane(parentComponent);
    	}

    	singleton.show();
    	
    	if(singleton.canceled) {
    		return null;
    	}

    	return singleton.getUrl();
    }
    
    public String getUrl() {
    	return url;
    }
}
