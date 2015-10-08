package dpf.sp.gpinf.indexer.search;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public class FilterManager implements ActionListener, ListSelectionListener {
	
	private static File userFilters = new File(System.getProperty("user.home") + "/.indexador/ipedFilters.txt");
	private File defaultFilter = new File(App.get().codePath + "/../conf/DefaultFilters.txt");
	
	private UTF8Properties filters = new UTF8Properties();
	private volatile boolean updatingCombo = false;
	private JComboBox<String> comboFilter;
	
	JDialog dialog;
	
	JLabel msg = new JLabel("Filtros:");
	JLabel texto = new JLabel("Expressão:");
	
	JButton save = new JButton("Salvar");
	JButton rename = new JButton("Renomear");
	JButton novo = new JButton("Criar");
	JButton delete = new JButton("Apagar");
	
	DefaultListModel<String> listModel = new DefaultListModel<String>();
	JList<String> list = new JList<String>(listModel);
	JScrollPane scrollList = new JScrollPane(list);
	
	JTextArea expression = new JTextArea();
	JScrollPane scrollExpression = new JScrollPane(expression);
	
	public void loadFilters(){
		try {
			if(userFilters.exists())
				filters.load(userFilters);
			else
				filters.load(defaultFilter);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		updateFilter();
	}
	
	private void updateFilter(){
		updatingCombo = true;
		Object prevSelected = comboFilter.getSelectedItem();
		
		comboFilter.removeAllItems();
		comboFilter.addItem(App.FILTRO_TODOS);
		comboFilter.addItem(App.FILTRO_SELECTED);
		
		Object[] filternames = filters.keySet().toArray();
		Arrays.sort(filternames, Collator.getInstance());
		for(Object filter : filternames)
			comboFilter.addItem((String)filter);
		
		if(prevSelected != null){
			if(prevSelected == App.FILTRO_TODOS || prevSelected == App.FILTRO_SELECTED
					|| filters.containsKey(prevSelected))
				comboFilter.setSelectedItem(prevSelected);
			else{
				comboFilter.setSelectedIndex(1);
				updatingCombo = false;
				comboFilter.setSelectedIndex(0);
			}
		}
			
		updatingCombo = false;
	}
	
	private void populateList(){
		
		String name = list.getSelectedValue();
		Object[] filternames = filters.keySet().toArray();
		Arrays.sort(filternames, Collator.getInstance());
		listModel.clear();
		for(Object filter : filternames)
			listModel.addElement((String)filter);
		list.setSelectedValue(name, true);
	}

	public FilterManager(JComboBox<String> comboFilter) {
		this.comboFilter = comboFilter;
	}
	
	private void createDialog(){
		dialog = new JDialog();
		dialog.setLayout(null);
		dialog.setTitle("Filtros");
		dialog.setBounds(0, 0, 680, 350);
		dialog.setAlwaysOnTop(true);
		
		expression.setLineWrap(true);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		expression.setToolTipText("Expressão do Filtro");
		novo.setToolTipText("Criar novo filtro");
		save.setToolTipText("Salvar alterações");
		delete.setToolTipText("Apagar filtro selecionado");
		
		msg.setBounds(20, 20, 200, 20);
		texto.setBounds(300, 20, 200, 20);
		scrollList.setBounds(20, 40, 260, 230);
		scrollExpression.setBounds(300, 40, 340, 230);
		novo.setBounds(550, 270, 90, 30);
		save.setBounds(450, 270, 90, 30);
		delete.setBounds(190, 270, 90, 30);
		
		populateList();
		
		dialog.getContentPane().add(msg);
		dialog.getContentPane().add(texto);
		dialog.getContentPane().add(scrollList);
		dialog.getContentPane().add(scrollExpression);
		dialog.getContentPane().add(novo);
		dialog.getContentPane().add(save);
		dialog.getContentPane().add(delete);
		
		list.addListSelectionListener(this);
		save.addActionListener(this);
		novo.addActionListener(this);
		delete.addActionListener(this);
		
	}
	
	public void setVisible(boolean visible){
		if(dialog == null)
			createDialog();
		dialog.setVisible(true);
		dialog.setLocationRelativeTo(null);
	}
	
	public String getFilterExpression(String filter){
		return filters.getProperty(filter);
	}
	
	public boolean isUpdatingFilter(){
		return updatingCombo;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == novo){
			String newLabel = JOptionPane.showInputDialog(dialog, "Nome do novo filtro", list.getSelectedValue());
			if (newLabel != null && !newLabel.trim().isEmpty() && !listModel.contains(newLabel.trim())){
				filters.put(newLabel.trim(), expression.getText());
			}
		}
		
		String filter = list.getSelectedValue();
		if(e.getSource() == save && filter != null){
			filters.put(filter, expression.getText());
		}
		if(e.getSource() == delete && filter != null){
			filters.remove(filter);
		}
		populateList();
		updateFilter();
		try {
			filters.store(userFilters);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		String filter = list.getSelectedValue();
		if(filter != null)
			expression.setText(getFilterExpression(filter));
		
	}

}
