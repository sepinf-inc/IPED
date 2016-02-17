package dpf.sp.gpinf.indexer.search;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableColumn;

import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.process.task.ImageThumbTask;
import dpf.sp.gpinf.indexer.process.task.KFFTask;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;

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
	
	private String[] extraFields = 
	    {
	        IndexItem.CARVED,
	        IndexItem.CONTENTTYPE,
	        IndexItem.DUPLICATE,
	        IndexItem.EXPORT,
	        IndexItem.HASCHILD,
	        IndexItem.ID,
	        IndexItem.ISDIR,
	        IndexItem.ISROOT,
	        IndexItem.PARENTID,
	        IndexItem.PARENTIDs,
	        IndexItem.SLEUTHID,
	        IndexItem.SUBITEM,
	        IndexItem.TIMEOUT,
	        IndexItem.TREENODE,
	        KFFTask.KFF_STATUS,
	        KFFTask.KFF_GROUP,
	        HashTask.MD5,
	        HashTask.SHA1,
	        HashTask.SHA256,
	        HashTask.SHA512,
	        HashTask.EDONKEY,
	        ParsingTask.ENCRYPTED,
	        ImageThumbTask.HAS_THUMB,
	        OCRParser.OCR_CHAR_COUNT,
	        IndexerDefaultParser.PARSER_EXCEPTION
	    };
	
	String[] indexFields;
	
	private String[] colGroups = {"Básicas", "Estendidas", "Metadados"};
	
	private static ColumnsManager instance = new ColumnsManager();
	
	private ArrayList<String> loadedFields = new ArrayList<String>();
	
	private JDialog dialog = new JDialog();
	private JPanel listPanel = new JPanel();
	private JComboBox<String> combo;
	
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
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(Color.WHITE);
		
		combo = new JComboBox<String>(colGroups);
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.add(label);
		topPanel.add(combo);
		combo.addActionListener(this);
		
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		panel.add(topPanel, BorderLayout.NORTH);
		
		JScrollPane scrollList = new JScrollPane(listPanel);
		panel.add(scrollList, BorderLayout.CENTER);
		
		for(String col : defaultFields)
			loadedFields.add(col);
		
		indexFields = LoadIndexFields.addExtraFields(App.get().reader, new String[0]);
		
		Arrays.sort(defaultFields, Collator.getInstance());
		Arrays.sort(extraFields, Collator.getInstance());
		Arrays.sort(indexFields, Collator.getInstance());
		
		updateList();
		
		dialog.getContentPane().add(panel);
		dialog.setLocationRelativeTo(App.get());
		
	}
	
	private void updateList(){
	    listPanel.removeAll();
	    String[] fields = indexFields;
	    if(combo.getSelectedIndex() == 0)
	    	fields = defaultFields;
	    if(combo.getSelectedIndex() == 1)
	    	fields = extraFields;
	    
        for(String f : fields){
            if(combo.getSelectedIndex() == 2 && (Arrays.asList(defaultFields).contains(f) || Arrays.asList(extraFields).contains(f)))
                continue;
            JCheckBox check = new JCheckBox();
            check.setText(f);
            if(loadedFields.contains(f))
                check.setSelected(true);
            check.addActionListener(this);
            listPanel.add(check);
        }
        dialog.revalidate();
        dialog.repaint();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
	    
	    if(e.getSource().equals(combo)){
	        updateList();
	    }else{
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

}
