/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

public class GerenciadorMarcadores implements ActionListener {

	JDialog dialog = new JDialog();
	JLabel msg = new JLabel("Conjunto de dados:");
	JRadioButton highlighted = new JRadioButton("Itens Destacados (" + App.get().resultsTable.getSelectedRowCount() + ")");
	JRadioButton checked = new JRadioButton("Itens Selecionados (" + App.get().marcadores.selectedItens + ")");
	ButtonGroup group = new ButtonGroup();
	JButton add = new JButton("Adicionar");
	JButton remove = new JButton("Remover");
	JButton rename = new JButton("Renomear");
	JTextField newLabel = new JTextField();
	JLabel texto = new JLabel("Marcadores existentes:");
	JButton novo = new JButton("Criar novo");
	JButton delete = new JButton("Apagar");
	DefaultListModel<String> listModel = new DefaultListModel<String>();
	JList<String> list = new JList<String>(listModel);
	JScrollPane scrollList = new JScrollPane(list);
	
	class Label{
		int id;
		public Label(int id){
			this.id = id;
		}
		public String ToString(){
			return App.get().marcadores.getLabelMap().get(id);
		}
	}

	public GerenciadorMarcadores() {

		dialog.setTitle("Marcadores");
		dialog.setBounds(0, 0, 440, 440);
		dialog.setAlwaysOnTop(true);

		group.add(highlighted);
		group.add(checked);
		highlighted.setSelected(true);

		populateList();

		newLabel.setToolTipText("Novo marcador");
		novo.setToolTipText("Criar novo marcador");
		add.setToolTipText("Adicionar itens aos marcadores selecionados");
		remove.setToolTipText("Remover itens dos marcadores selecionados");
		delete.setToolTipText("Apagar marcadores selecionados");
	
		JPanel top = new JPanel(new GridLayout(2, 2, 0, 0));
		top.add(msg);
		top.add(new JLabel());
		top.add(highlighted);
		top.add(checked);
		
		add.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		remove.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		
		JPanel left1 = new JPanel();
		left1.setLayout(new BoxLayout(left1, BoxLayout.PAGE_AXIS));
		left1.add(novo);
		left1.add(Box.createRigidArea(new Dimension(0,10)));
		left1.add(add);
		left1.add(remove);
		
		JPanel left2 = new JPanel(new GridLayout(0, 1, 0, 0));
		left2.add(rename);
		left2.add(delete);
		
		JPanel left = new JPanel(new BorderLayout());
		left.add(left1, BorderLayout.PAGE_START);
		left.add(left2, BorderLayout.PAGE_END);
		
		JPanel center = new JPanel(new BorderLayout(0, 10));
		center.add(newLabel, BorderLayout.PAGE_START);
		center.add(scrollList, BorderLayout.CENTER);
		
		JPanel pane = new JPanel(new BorderLayout(10, 10));
		pane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		pane.add(top, BorderLayout.PAGE_START);
		pane.add(left, BorderLayout.LINE_START);
		pane.add(center, BorderLayout.CENTER);
		dialog.getContentPane().add(pane);
		
		add.addActionListener(this);
		remove.addActionListener(this);
		rename.addActionListener(this);
		novo.addActionListener(this);
		delete.addActionListener(this);

		dialog.setLocationRelativeTo(App.get());
		dialog.setVisible(true);

	}
	
	private void populateList(){
		String[] labels = App.get().marcadores.getLabelMap().values().toArray(new String[0]);
		Arrays.sort(labels);
		listModel.clear();
		for (String label : labels)
			listModel.addElement(label);
	}

	@Override
	public void actionPerformed(ActionEvent evt) {

		if (evt.getSource() == novo) {
			String texto = newLabel.getText().trim();
			if (!texto.isEmpty() && !listModel.contains(texto)) {
				App.get().marcadores.newLabel(texto);
				populateList();	
			}
			for(int i = 0; i < listModel.size(); i ++)
				if(listModel.get(i).equals(texto))
					list.setSelectedIndex(i);

		}
		if (evt.getSource() == add || evt.getSource() == remove || evt.getSource() == novo) {

			App app = App.get();
			ArrayList<Integer> uniqueSelectedIds = new ArrayList<Integer>();

			if (checked.isSelected()) {
				for (int id = 0; id < App.get().marcadores.selected.length; id++)
					if (App.get().marcadores.selected[id])
						uniqueSelectedIds.add(id);
				
			} else if (highlighted.isSelected()){
				for (Integer row : App.get().resultsTable.getSelectedRows()) {
					int rowModel = App.get().resultsTable.convertRowIndexToModel(row);
					int id = app.ids[app.results.docs[rowModel]];
					uniqueSelectedIds.add(id);

					Integer id2 = app.viewToRawMap.getRaw(id);
					if (id2 == null)
						id2 = app.viewToRawMap.getView(id);

					if (id2 != null)
						uniqueSelectedIds.add(id2);

				}

			}

			for (int idx : list.getSelectedIndices()) {
				int labelId = App.get().marcadores.getLabelId(listModel.getElementAt(idx));
				if (evt.getSource() == add || evt.getSource() == novo)
					App.get().marcadores.addLabel(uniqueSelectedIds, labelId);
				else
					App.get().marcadores.removeLabel(uniqueSelectedIds, labelId);

				App.get().marcadores.saveState();
				App.get().marcadores.atualizarGUI();
			}

		} else if (evt.getSource() == delete) {
			int result = JOptionPane.showConfirmDialog(dialog, "Deseja realmente apagar os marcadores selecionados?", "Confirmar", JOptionPane.YES_NO_OPTION);
			if (result == JOptionPane.YES_OPTION){
				for (int idx : list.getSelectedIndices()){
					int labelId = App.get().marcadores.getLabelId(listModel.getElementAt(idx));
					App.get().marcadores.delLabel(labelId);
				}
				populateList();
				App.get().marcadores.saveState();
				App.get().marcadores.atualizarGUI();
				
			}

		} else if (evt.getSource() == rename) {
			String newLabel = JOptionPane.showInputDialog(dialog, "Novo nome para o primeiro marcador selecionado", list.getSelectedValue());
			if (newLabel != null && !newLabel.trim().isEmpty() && !listModel.contains(newLabel.trim())){
				for (int idx : list.getSelectedIndices()){
					int labelId = App.get().marcadores.getLabelId(listModel.getElementAt(idx));
					App.get().marcadores.changeLabel(labelId, newLabel.trim());;
					break;
				}
				populateList();
				App.get().marcadores.saveState();
				App.get().marcadores.atualizarGUI();
				
			}
		}

	}

}
