package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.table.TableColumn;

import org.apache.tika.metadata.Message;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.IndexTask;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.LoadIndexFields;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.EvidenceFile;

public class ColumnsManager implements ActionListener, Serializable{
    
    private static final long serialVersionUID = 1057562688829969313L;

    private static File globalCols = new File(System.getProperty("user.home") + "/.indexador/visibleCols.dat");
    
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
			IndexItem.RECORDDATE,
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
	        IndexItem.EVIDENCE_UUID,
	        IndexerDefaultParser.PARSER_EXCEPTION
	    };
	
	public static final String[] email = {ExtraProperties.MESSAGE_SUBJECT, ExtraProperties.MESSAGE_DATE.getName(), 
			Message.MESSAGE_FROM, Message.MESSAGE_TO, Message.MESSAGE_CC, Message.MESSAGE_BCC, ExtraProperties.MESSAGE_BODY};
	
	private static ColumnsManager instance;
	
	private File caseCols;
	
	String[] indexFields = null;
	
	private String[] groupNames = {"Básicas", "Avançadas", "Email", "Outras"};
	public String[][] fieldGroups;
	
	ColumnState colState = new ColumnState();
    
	private ArrayList<String> loadedFields = new ArrayList<String>();
		
	private JDialog dialog = new JDialog();
	private JPanel listPanel = new JPanel();
	private JComboBox<Object> combo;
	
	public static ColumnsManager getInstance(){
		if(instance == null)
			instance = new ColumnsManager();
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
	    if(cs.visibleFields.size() > 0)
		    try {
		    	globalCols.getParentFile().mkdirs();
	            Util.writeObject(cs, globalCols.getAbsolutePath());
	            Util.writeObject(cs, caseCols.getAbsolutePath());
	        } catch (IOException e1) {
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
		
		combo = new JComboBox<Object>(groupNames);
		
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.add(label);
		topPanel.add(combo);
		combo.addActionListener(this);
		
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		panel.add(topPanel, BorderLayout.NORTH);
		
		JScrollPane scrollList = new JScrollPane(listPanel);
		scrollList.getVerticalScrollBar().setUnitIncrement(10);
		panel.add(scrollList, BorderLayout.CENTER);
		
		indexFields = LoadIndexFields.addExtraFields(App.get().appCase.getReader(), new String[0]);
		
		File moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
		caseCols = new File(moduleDir, "visibleCols.dat");
		
		loadSavedCols();
		
		TreeSet<String> extraAttrs = new TreeSet<String>();
		extraAttrs.addAll(Arrays.asList(extraFields));
		for(IPEDSource source : App.get().appCase.getAtomicSources())
			extraAttrs.addAll(source.getExtraAttributes());
		extraFields = extraAttrs.toArray(new String[0]);
		
		fieldGroups = new String[][] {defaultFields, extraFields, email, indexFields};
		
		for(String[] fields : fieldGroups)
			Arrays.sort(fields, Collator.getInstance());
		
		updateList();
		
		dialog.getContentPane().add(panel);
		dialog.setLocationRelativeTo(App.get());
		
	}
	
	private void loadSavedCols(){
		boolean lastColsOk = false;
		File cols = caseCols;
		if(!cols.exists())
			cols = globalCols;
		if(cols.exists()){
		    try {
		        colState = (ColumnState)Util.readObject(cols.getAbsolutePath());
		        loadedFields = (ArrayList<String>)colState.visibleFields.clone();
		        if(loadedFields.size() > 0){
		        	lastColsOk = true;
		        	HashSet<String> indexedSet = new HashSet<String>();
		        	indexedSet.addAll(Arrays.asList(indexFields));
		        	//remove inexistent columns in current case
		        	int removed = 0;
		    		for(int i = 0; i < loadedFields.size(); i++){
		    			String field = loadedFields.get(i);
		    			if(!indexedSet.contains(field) && !field.equals(ResultTableModel.SCORE_COL)
		    					&& !field.equals(ResultTableModel.BOOKMARK_COL)){
		    				colState.visibleFields.remove(i - removed);
		    				colState.initialWidths.remove(i - removed);
		    				removed++;
		    			}
		    		}
		    		if(removed > 0)
		    			loadedFields = (ArrayList<String>)colState.visibleFields.clone();	
		        }
		        
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
	}
	
	private void updateList(){
	    listPanel.removeAll();
	    String[] fields = fieldGroups[combo.getSelectedIndex()];
	    
        for(String f : fields){
        	boolean insertField = true;
            if(combo.getSelectedIndex() == groupNames.length - 1){
            	for(int i = 0; i < fieldGroups.length - 1; i++)
            		if(Arrays.asList(fieldGroups[i]).contains(f)){
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
