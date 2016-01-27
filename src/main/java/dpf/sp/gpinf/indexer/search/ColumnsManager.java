package dpf.sp.gpinf.indexer.search;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableColumn;

import dpf.sp.gpinf.indexer.process.IndexItem;

public class ColumnsManager implements ActionListener{
	
	public static String[] defaultFields =
		{
			IndexItem.NAME,
			IndexItem.TYPE, 
			IndexItem.LENGTH, 
			IndexItem.DELETED, 
			IndexItem.CATEGORY,
			IndexItem.CREATED,
			IndexItem.MODIFIED,
			IndexItem.ACCESSED,
			IndexItem.HASH,
			IndexItem.PATH
		};
	
	private static ColumnsManager instance = new ColumnsManager();
	
	private ArrayList<String> loadedFields = new ArrayList<String>();
	
	private JDialog dialog = new JDialog();
	
	public static ColumnsManager getInstance(){
		return instance;
	}
	
	public void setVisible(){
		dialog.setVisible(true);
	}
	
	public String[] getLoadedCols(){
		String[] cols = loadedFields.toArray(new String[0]);
		return cols;
	}
	
	private ColumnsManager(){
		
		dialog.setBounds(new Rectangle(400, 400));
		dialog.setTitle("Colunas visíveis");
		dialog.setAlwaysOnTop(true);
		
		JLabel label = new JLabel("Exibir colunas:");
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		
		JPanel listPanel = new JPanel();
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(Color.WHITE);
		
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		panel.add(label, BorderLayout.NORTH);
		
		JScrollPane scrollList = new JScrollPane(listPanel);
		panel.add(scrollList, BorderLayout.CENTER);
		
		for(String col : defaultFields)
			loadedFields.add(col);
		
		String[] indexFields = LoadIndexFields.addExtraFields(App.get().reader, new String[0]);
		Arrays.sort(indexFields, Collator.getInstance());
		
		int i = 0;
		for(String f : indexFields){
			if(f.startsWith("Unknown"))
				continue;
			JCheckBox check = new JCheckBox();
			check.setText(f);
			if(loadedFields.contains(f))
				check.setSelected(true);
			check.addActionListener(this);
			listPanel.add(check);
			i++;
		}
		System.out.println(i);
		
		dialog.getContentPane().add(panel);
		dialog.setLocationRelativeTo(App.get());
		
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		
		JCheckBox source = (JCheckBox)e.getSource();
		int modelIdx = loadedFields.indexOf(source.getText());
		if(source.isSelected()){
			if(modelIdx == -1){
				loadedFields.add(source.getText());
				App.get().resultsModel.updateCols();
				modelIdx = ResultTableModel.fixedCols.length + loadedFields.size() - 1;
				((ResultTableRowSorter)App.get().resultsTable.getRowSorter()).initComparator(modelIdx);
			}else
				modelIdx += ResultTableModel.fixedCols.length;
			
			App.get().resultsTable.addColumn(new TableColumn(modelIdx));
		}else{
			modelIdx += ResultTableModel.fixedCols.length;
			int viewIdx = App.get().resultsTable.convertColumnIndexToView(modelIdx);
			App.get().resultsTable.removeColumn(App.get().resultsTable.getColumnModel().getColumn(viewIdx));
		}
	}

}
