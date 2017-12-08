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
import org.apache.tika.parser.ner.NamedEntityParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.OutlookPSTParser;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.task.LanguageDetectTask;
import dpf.sp.gpinf.indexer.process.task.NamedEntityTask;
import dpf.sp.gpinf.indexer.process.task.regex.RegexTask;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.LoadIndexFields;
import dpf.sp.gpinf.indexer.util.Util;
import gpinf.dev.data.EvidenceFile;

public class ColumnsManager implements ActionListener, Serializable{
    
    private static final long serialVersionUID = 1057562688829969313L;
    
    private static Logger LOGGER = LoggerFactory.getLogger(ColumnsManager.class);

    private static final File globalCols = new File(System.getProperty("user.home") + "/.indexador/visibleCols.dat"); //$NON-NLS-1$ //$NON-NLS-2$
    
    private static final List<Integer> defaultWidths = Arrays.asList(50, 100, 200, 50, 100, 60, 150, 155, 155, 155, 155, 250, 2000);
    
    public static final String[] groupNames = {Messages.getString("ColumnsManager.Basic"), Messages.getString("ColumnsManager.Advanced"), Messages.getString("ColumnsManager.Email"), Messages.getString("ColumnsManager.Audio"), Messages.getString("ColumnsManager.Image"), Messages.getString("ColumnsManager.Video"),  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                                                Messages.getString("ColumnsManager.PDF"), Messages.getString("ColumnsManager.Office"), Messages.getString("ColumnsManager.HTML"), Messages.getString("ColumnsManager.Regex"), Messages.getString("ColumnsManager.Language"), Messages.getString("ColumnsManager.NamedEntity"), Messages.getString("ColumnsManager.Other")}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
	
	private static final String[] defaultFields =
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
	
	private static final String[] extraFields = 
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
	        IndexerDefaultParser.PARSER_EXCEPTION,
	        OCRParser.OCR_CHAR_COUNT,
	        ExtraProperties.WKFF_HITS,
	        ExtraProperties.P2P_REGISTRY_COUNT,
	        ExtraProperties.SHARED_HASHES
	    };
	
	public static final String[] email = {
			ExtraProperties.MESSAGE_SUBJECT,
			ExtraProperties.MESSAGE_DATE.getName(),
			ExtraProperties.MESSAGE_BODY,
			Message.MESSAGE_FROM, 
			Message.MESSAGE_TO, 
			Message.MESSAGE_CC, 
			Message.MESSAGE_BCC, 
			OutlookPSTParser.PST_ATTACH,
			OutlookPSTParser.HAS_ATTACHS
		};
	
	private static ColumnsManager instance;
	
	private IPEDSource lastCase;
	
	private File caseCols;
	
	String[] indexFields = null;
	
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
	
	public void dispose(){
	    ColumnsManager.getInstance().saveColumnsState();
		dialog.setVisible(false);
		instance = null;
	}
	
	public void setVisible(){
		updateDinamicFields();
		updateList();
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
		try {
			ColumnState cs = new ColumnState();
		    for(int i = 0; i < App.get().resultsTable.getColumnModel().getColumnCount(); i++){
		        TableColumn tc = App.get().resultsTable.getColumnModel().getColumn(i);
		        if(tc.getModelIndex() >= ResultTableModel.fixedCols.length){
		        	int idx = tc.getModelIndex() - ResultTableModel.fixedCols.length;
		        	cs.visibleFields.add(loadedFields.get(idx));
	                cs.initialWidths.add(tc.getWidth());
	            }
		    }
		    if(cs.visibleFields.size() > 0){
		    	globalCols.getParentFile().mkdirs();
	            Util.writeObject(cs, globalCols.getAbsolutePath());
	            Util.writeObject(cs, caseCols.getAbsolutePath());
		    }
	    } catch (Exception e1) {
	       e1.printStackTrace();
	    }
	}
	
	private ColumnsManager(){
		
		dialog.setBounds(new Rectangle(400, 400));
		dialog.setTitle(Messages.getString("ColumnsManager.Title")); //$NON-NLS-1$
		dialog.setAlwaysOnTop(true);
		
		JLabel label = new JLabel(Messages.getString("ColumnsManager.ShowCols")); //$NON-NLS-1$
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
		
		dialog.getContentPane().add(panel);
		dialog.setLocationRelativeTo(App.get());
		
		updateDinamicFields();
		
		loadSavedCols();
		
		updateList();
	}
	
	private void loadSavedCols(){
		boolean lastColsOk = false;
		File moduleDir = App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir();
		caseCols = new File(moduleDir, "visibleCols.dat"); //$NON-NLS-1$
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
			LOGGER.info("Loading default columns"); //$NON-NLS-1$
		    for(String col : defaultFields)
		        loadedFields.add(col);
		    colState.visibleFields = (ArrayList<String>)loadedFields.clone();
		    colState.initialWidths = defaultWidths;
		}
	}
	
	private void updateDinamicFields(){
		
		if(lastCase != App.get().appCase){
			lastCase = App.get().appCase;
			indexFields = LoadIndexFields.addExtraFields(App.get().appCase.getAtomicReader(), new String[0]);
		}
		
		TreeSet<String> extraAttrs = new TreeSet<String>();
		extraAttrs.addAll(Arrays.asList(extraFields));
		extraAttrs.addAll(EvidenceFile.getAllExtraAttributes());
		String[] allExtraAttrs = extraAttrs.toArray(new String[0]); 
        extraAttrs.clear();
		
		ArrayList<String> regexFields = new ArrayList<String>();
		ArrayList<String> languageFields = new ArrayList<String>();
		ArrayList<String> audioFields = new ArrayList<String>();
		ArrayList<String> imageFields = new ArrayList<String>();
		ArrayList<String> videoFields = new ArrayList<String>();
		ArrayList<String> pdfFields = new ArrayList<String>();
		ArrayList<String> officeFields = new ArrayList<String>();
		ArrayList<String> htmlFields = new ArrayList<String>();
		ArrayList<String> nerFields = new ArrayList<String>();
		
		for(String f : allExtraAttrs){
		    if(f.startsWith(RegexTask.REGEX_PREFIX))
                regexFields.add(f);
		    else if(f.startsWith(LanguageDetectTask.LANGUAGE_PREFIX))
		        languageFields.add(f);
		    else
		        extraAttrs.add(f);
		}
		
		for(String f : indexFields){
		    if(f.startsWith(ExtraProperties.AUDIO_META_PREFIX))
		        audioFields.add(f);
		    else if(f.startsWith(ExtraProperties.IMAGE_META_PREFIX))
		        imageFields.add(f);
		    else if(f.startsWith(ExtraProperties.VIDEO_META_PREFIX))
		        videoFields.add(f);
		    else if(f.startsWith(ExtraProperties.PDF_META_PREFIX))
		        pdfFields.add(f);
		    else if(f.startsWith(ExtraProperties.OFFICE_META_PREFIX))
		        officeFields.add(f);
		    else if(f.startsWith(ExtraProperties.HTML_META_PREFIX))
		        htmlFields.add(f);
		    else if(f.startsWith(NamedEntityTask.NER_PREFIX))
                nerFields.add(f); 
		}
		
		String[][] customGroups = new String[][] {
		    defaultFields.clone(), 
		    extraAttrs.toArray(new String[0]), 
		    email, 
		    audioFields.toArray(new String[0]),
		    imageFields.toArray(new String[0]),
		    videoFields.toArray(new String[0]),
		    pdfFields.toArray(new String[0]),
		    officeFields.toArray(new String[0]),
		    htmlFields.toArray(new String[0]),
		    regexFields.toArray(new String[0]),
		    languageFields.toArray(new String[0]),
		    nerFields.toArray(new String[0])
		    };
		
		ArrayList<String> otherFields = new ArrayList<String>();
		for(String f : indexFields){
		    boolean insertField = true;
		    for(int i = 0; i < customGroups.length; i++)
	            if(Arrays.asList(customGroups[i]).contains(f)){
	                insertField = false;
	                break;
	            }
		    if(insertField)
		        otherFields.add(f);
		}
		
		fieldGroups = new String[customGroups.length + 1][];
		for(int i = 0; i < customGroups.length; i++)
		    fieldGroups[i] = customGroups[i];
		fieldGroups[fieldGroups.length - 1] = otherFields.toArray(new String[0]);
		
		for(String[] fields : fieldGroups)
			Arrays.sort(fields, Collator.getInstance());
		
	}
	
	private void updateList(){
	    listPanel.removeAll();
	    String[] fields = fieldGroups[combo.getSelectedIndex()];
	    
        for(String f : fields){
            JCheckBox check = new JCheckBox();
            check.setText(f);
            if(colState.visibleFields.contains(f))
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
	            colState.visibleFields.add(source.getText());
	            if(modelIdx == -1){
	                loadedFields.add(source.getText());
	                App.get().resultsModel.updateCols();
	                modelIdx = ResultTableModel.fixedCols.length + loadedFields.size() - 1;
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
