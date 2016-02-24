package dpf.sp.gpinf.indexer.search;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
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
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.DIETask;
import dpf.sp.gpinf.indexer.process.task.HashTask;
import dpf.sp.gpinf.indexer.process.task.ImageThumbTask;
import dpf.sp.gpinf.indexer.process.task.KFFTask;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import dpf.sp.gpinf.indexer.util.Util;

public class ColumnsManager implements ActionListener, Serializable{
    
    private static final long serialVersionUID = 1057562688829969313L;

    private static File lastCols = new File(System.getProperty("user.home") + "/.indexador/visibleCols.dat");
    
    private static List<Integer> defaultWidths = Arrays.asList(50, 100, 200, 50, 100, 60, 150, 155, 155, 155, 250, 2000);
	
	private static String[] defaultFields =
		{ 
	        ResultTableModel.SCORE_COL,
            ResultTableModel.BOOKMARK_COL,
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
	
	private static String[] extraFields = 
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
	        IndexerDefaultParser.PARSER_EXCEPTION,
	        DIETask.DIE_SCORE,
	        DIETask.DIE_CLASS
	    };
	
	private static String[] email = {ExtraProperties.MESSAGE_SUBJECT, Message.MESSAGE_FROM, Message.MESSAGE_TO, Message.MESSAGE_CC, Message.MESSAGE_BCC};
	
	String[] indexFields = null;
	
	private Object[][] colGroups = {
			{"Básicas", "Avançadas", "Email", "Outras"},
			{defaultFields, extraFields, email, indexFields}
	};
	
	ColumnState colState = new ColumnState();
    	
	private static ColumnsManager instance = new ColumnsManager();
	
	private ArrayList<String> loadedFields = new ArrayList<String>();

		
	private JDialog dialog = new JDialog();
	private JPanel listPanel = new JPanel();
	private JComboBox<Object> combo;
	
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
	
	static class ColumnState implements Serializable{
	    
        private static final long serialVersionUID = 1L;
        List<Integer> initialWidths = new ArrayList<Integer>();
	    ArrayList<String> visibleFields = new ArrayList<String>();
	}
	
	public void saveColumnsState(){
	    ColumnState cs = new ColumnState();
	    for(int i = 0; i < App.get().resultsTable.getColumnModel().getColumnCount(); i++){
	        TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i);
	        if(tc.getModelIndex() >= ResultTableModel.fixedCols.length){    
                cs.visibleFields.add(loadedFields.get(tc.getModelIndex() - ResultTableModel.fixedCols.length));
                cs.initialWidths.add(tc.getWidth());
            }
	    }
	    try {
            Util.writeObject(cs, lastCols.getAbsolutePath());
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
	}
	
	private ColumnsManager(){
		
		dialog.setBounds(new Rectangle(400, 400));
		dialog.setTitle("Colunas visíveis");
		dialog.setAlwaysOnTop(true);
		
		JLabel label = new JLabel("Exibir colunas:");
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
		
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(Color.WHITE);
		
		combo = new JComboBox<Object>(colGroups[0]);
		
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
		
		boolean lastColsOk = false;
		if(lastCols.exists()){
		    try {
		        colState = (ColumnState)Util.readObject(lastCols.getAbsolutePath());
                loadedFields = (ArrayList<String>)colState.visibleFields.clone();
                lastColsOk = true;
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
            }
		}
		if(!lastColsOk){
		    for(String col : defaultFields)
		        loadedFields.add(col);
		    colState.visibleFields = (ArrayList<String>)loadedFields.clone();
		    colState.initialWidths = defaultWidths;
		}
		
		indexFields = LoadIndexFields.addExtraFields(App.get().reader, new String[0]);
		colGroups[1][colGroups[1].length - 1] = indexFields;
		
		for(Object fields : colGroups[1])
			Arrays.sort((String[])fields, Collator.getInstance());
		
		updateList();
		
		dialog.getContentPane().add(panel);
		dialog.setLocationRelativeTo(App.get());
		
	}
	
	private void updateList(){
	    listPanel.removeAll();
	    String[] fields = (String[])colGroups[1][combo.getSelectedIndex()];
	    
        for(String f : fields){
        	boolean insertField = true;
            if(combo.getSelectedIndex() == colGroups[0].length - 1){
            	for(int i = 0; i <= colGroups[1].length - 2; i++)
            		if(Arrays.asList((String[])colGroups[1][i]).contains(f)){
            			insertField = false;
            			break;
            		}
            }
            if(insertField){
            	JCheckBox check = new JCheckBox();
                check.setText(f);
                if(colState.visibleFields.contains(f))
                    check.setSelected(true);
                check.addActionListener(this);
                listPanel.add(check);
            }
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
	            colState.visibleFields.add(source.getText());
	            if(modelIdx == -1){
	                loadedFields.add(source.getText());
	                App.get().resultsModel.updateCols();
	                modelIdx = ResultTableModel.fixedCols.length + loadedFields.size() - 1;
	                ((ResultTableRowSorter)App.get().resultsTable.getRowSorter()).initComparator(modelIdx);
	            }else
	                modelIdx += ResultTableModel.fixedCols.length;
	            
	            TableColumn tc = new TableColumn(modelIdx);
	            App.get().resultsTable.addColumn(tc);
	            setColumnRenderer(tc);
	        }else{
	            colState.visibleFields.remove(source.getText());
	            modelIdx += ResultTableModel.fixedCols.length;
	            int viewIdx = App.get().resultsTable.convertColumnIndexToView(modelIdx);
	            App.get().resultsTable.removeColumn(App.get().resultsTable.getColumnModel().getColumn(viewIdx));
	        }
	        
	        saveColumnsState();
	    }
		
	}
	
	public void setColumnRenderer(TableColumn tc){
	    if(ResultTableModel.SCORE_COL.equals(loadedFields.get(tc.getModelIndex() - ResultTableModel.fixedCols.length)))
            tc.setCellRenderer(new ProgressCellRenderer());
	}

}
