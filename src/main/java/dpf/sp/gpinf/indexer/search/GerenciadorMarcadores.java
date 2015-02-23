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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Map.Entry;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

public class GerenciadorMarcadores implements ActionListener {

	JDialog dialog = new JDialog();
	JLabel msg = new JLabel("Conjunto de dados:");
	JRadioButton highlighted = new JRadioButton("Itens Destacados (" + App.get().resultsTable.getSelectedRowCount() + ")");
	JRadioButton checked = new JRadioButton("Itens Selecionados (" + App.get().marcadores.selectedItens + ")");
	ButtonGroup group = new ButtonGroup();
	JButton add = new JButton("Adicionar a");
	JButton remove = new JButton("Remover de");
	JButton rename = new JButton("Renomear");
	JTextField newLabel = new JTextField();
	JLabel texto = new JLabel("Marcadores existentes:");
	JButton novo = new JButton("Criar novo");
	JButton delete = new JButton("Apagar");
	DefaultListModel<String> listModel = new DefaultListModel<String>();
	JList<String> list = new JList<String>(listModel);
	JScrollPane scrollList = new JScrollPane(list);

	ArrayList<Integer> labelIds = new ArrayList<Integer>();

	public GerenciadorMarcadores() {

		dialog.setTitle("Marcadores");
		dialog.setBounds(0, 0, 440, 420);
		dialog.setAlwaysOnTop(true);

		group.add(highlighted);
		group.add(checked);
		highlighted.setSelected(true);

		for (int id : App.get().marcadores.labelNames.keySet()) {
			listModel.addElement(App.get().marcadores.labelNames.get(id));
			labelIds.add(id);
		}

		newLabel.setToolTipText("Novo marcador");
		novo.setToolTipText("Criar novo marcador");
		add.setToolTipText("Adicionar itens aos marcadores selecionados");
		remove.setToolTipText("Remover itens dos marcadores selecionados");
		delete.setToolTipText("Apagar marcadores selecionados");
		

		msg.setBounds(20, 15, 150, 20);
		highlighted.setBounds(20, 30, 180, 30);
		checked.setBounds(200, 30, 200, 30);
		novo.setBounds(20, 70, 120, 30);
		newLabel.setBounds(150, 70, 250, 30);
		delete.setBounds(20, 330, 120, 30);
		texto.setBounds(25, 80, 255, 20);
		scrollList.setBounds(150, 110, 250, 250);
		add.setBounds(20, 110, 120, 30);
		remove.setBounds(20, 140, 120, 30);
		rename.setBounds(20, 300, 120, 30);

		dialog.getContentPane().add(msg);
		dialog.getContentPane().add(highlighted);
		dialog.getContentPane().add(checked);
		//dialog.getContentPane().add(texto);
		dialog.getContentPane().add(newLabel);
		dialog.getContentPane().add(add);
		dialog.getContentPane().add(remove);
		dialog.getContentPane().add(rename);
		dialog.getContentPane().add(novo);
		dialog.getContentPane().add(delete);
		dialog.getContentPane().add(scrollList);
		dialog.getContentPane().add(new JLabel());

		add.addActionListener(this);
		remove.addActionListener(this);
		rename.addActionListener(this);
		novo.addActionListener(this);
		delete.addActionListener(this);

		dialog.setLocationRelativeTo(App.get());
		dialog.setVisible(true);

	}

	@Override
	public void actionPerformed(ActionEvent evt) {

		if (evt.getSource() == novo) {
			String texto = newLabel.getText().trim();
			if (!texto.isEmpty()) {
				int id = App.get().marcadores.newLabel(texto);
				listModel.addElement(texto);
				list.setSelectedIndex(listModel.getSize() - 1);
				labelIds.add(id);
			}

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
				if (evt.getSource() == add || evt.getSource() == novo)
					App.get().marcadores.addLabel(uniqueSelectedIds, labelIds.get(idx));
				else
					App.get().marcadores.removeLabel(uniqueSelectedIds, labelIds.get(idx));

				App.get().marcadores.saveState();
				App.get().marcadores.atualizarGUI();
			}

		} else if (evt.getSource() == delete) {
			int result = JOptionPane.showConfirmDialog(dialog, "Deseja realmente apagar os marcadores selecionados?", "Confirmar", JOptionPane.YES_NO_OPTION);
			if (result == JOptionPane.YES_OPTION){
				int del = 0;
				for (int idx : list.getSelectedIndices()){
					App.get().marcadores.delLabel(labelIds.get(idx - del));
					listModel.removeElementAt(idx - del);
					labelIds.remove(idx - del);
					del++;
				}
				App.get().marcadores.saveState();
				App.get().marcadores.atualizarGUI();
				
			}

		} else if (evt.getSource() == rename) {
			String newLabel = JOptionPane.showInputDialog(dialog, "Novo nome para os marcadores selecionados", list.getSelectedValue());
			if (newLabel != null && !newLabel.isEmpty()){
				for (int idx : list.getSelectedIndices()){
					App.get().marcadores.changeLabel(labelIds.get(idx), newLabel);;
					listModel.setElementAt(newLabel, idx);
				}
				App.get().marcadores.saveState();
				App.get().marcadores.atualizarGUI();
				
			}
		}

	}

}
