package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.properties.BasicProps;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.MultiSearchResult;
import iped.engine.sleuthkit.SleuthkitInputStreamFactory;
import iped.engine.task.index.IndexItem;

import org.apache.lucene.document.Document;

import org.sleuthkit.datamodel.SleuthkitCase;

import iped.data.IItem;
import iped.data.IItemId;

import java.util.*;
import iped.engine.task.ExportFileTask.SQLiteInputStreamFactory;

import java.nio.file.DirectoryStream;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;

public class ExportCaseDialog implements ActionListener {

    private static Logger logger = LoggerFactory.getLogger(ReportDialog.class);

    JDialog dialog = new JDialog(App.get());

    JTextField newPath = null;
    JCheckBox chkCopyImages = new JCheckBox("Export Evidences",true);
    JButton btnExport = new JButton("Export");
    JButton btnClose = new JButton("Close");
    JButton btnOpen = new JButton("Open");
    JButton btnSize = new JButton("Estimate Size");



    File casePath = App.get().casesPathFile;
    String srcPath = casePath.getAbsolutePath();            
    String caseName = casePath.getName();		
    ArrayList<String> srcPaths = new ArrayList<String>();
    Map<Long, List<String>> imgPaths = new HashMap<Long, List<String>>();    
    ArrayList<String> otherPaths = new ArrayList<String>();
 

    HashSet<String> noContent = new HashSet<>();

    public ExportCaseDialog() {



        dialog.setTitle("Export Case");
        dialog.setBounds(0, 0, 800, 250);
        dialog.setModal(true);
        dialog.setLocationRelativeTo(null);


        JLabel msg = new JLabel("Export Folder:");
        newPath = new JTextField();
        newPath.setText("");

  
        msg.setBounds(15, 20, 600, 30);
        newPath.setBounds(15, 60, 600, 30);
        chkCopyImages.setBounds(15, 110, 150, 30);
        btnOpen.setBounds(650, 60, 80, 30);
        btnExport.setBounds(150, 150, 80, 30);
        btnSize.setBounds(270, 150, 130, 30);
        btnClose.setBounds(440, 150, 80, 30);
  
        dialog.getContentPane().add(msg);
        dialog.getContentPane().add(newPath);
        dialog.getContentPane().add(chkCopyImages);
        dialog.getContentPane().add(btnOpen);
        dialog.getContentPane().add(btnExport);
        dialog.getContentPane().add(btnSize);
        dialog.getContentPane().add(btnClose);
        dialog.getContentPane().add(new JLabel());

        btnOpen.addActionListener(this);
        btnExport.addActionListener(this);
        btnClose.addActionListener(this);
        btnSize.addActionListener(this);

        initPaths();

    }

    public void setVisible() {
        dialog.setVisible(true);
    }

    public void initPaths(){

      try{

        ArrayList<String> doNotIncludeInOthers = new ArrayList<String>();
        doNotIncludeInOthers.add(SleuthkitInputStreamFactory.class.getName());
        doNotIncludeInOthers.add(SQLiteInputStreamFactory.class.getName());

        srcPaths.add(srcPath);

        for(IPEDSource iCase: App.get().appCase.getAtomicSources()){
            SleuthkitCase sleuthCase = iCase.getSleuthCase();
            if (sleuthCase != null){
                Map<Long, List<String>> tmp = sleuthCase.getImagePaths();
                tmp.forEach(imgPaths::putIfAbsent);
                for (List<String> listPaths : tmp.values()){
                    for(String paths: listPaths){
                        if (!otherPaths.contains(paths)){
                            otherPaths.add(paths);
                        }
                    }                    
                }
            }
        }

        IPEDSearcher task = new IPEDSearcher(App.get().appCase, IndexItem.ISROOT + ":true");
        task.setTreeQuery(true);
        LuceneSearchResult rs = MultiSearchResult.get(task.multiSearch(), App.get().appCase);

        boolean shouldAdd = true;

        for (int docID : rs.getLuceneIds()) {
            

            Document doc = App.get().appCase.getReader().document(docID);
            String dtPath = doc.get(IndexItem.SOURCE_PATH);
            String dtDecoder = doc.get(IndexItem.SOURCE_DECODER);

            //workaround for empty datasource path
            if (dtPath == null || dtPath.isEmpty()){

                IItemId item = App.get().appCase.getItemId(docID);
                IItem e = App.get().appCase.getItemByItemId(item);

                String query = BasicProps.EVIDENCE_UUID + ":" + e.getDataSource().getUUID();
                IPEDSearcher task2 = new IPEDSearcher(App.get().appCase, query);
                LuceneSearchResult rs2 = MultiSearchResult.get(task2.multiSearch(), App.get().appCase);

                for (int docID2 : rs2.getLuceneIds()) {

                    Document doc2 = App.get().appCase.getReader().document(docID2);
                    String dtPath2 = doc2.get(IndexItem.SOURCE_PATH);
                    String dtDecorder2 = doc2.get(IndexItem.SOURCE_DECODER);
                    
                    if (dtPath2 != null && !dtPath2.isEmpty() && dtDecorder2 != null && !dtDecorder2.isEmpty()){


                        shouldAdd = true;
                        for (String decoder: doNotIncludeInOthers){
                            if(decoder.contains(dtDecorder2)){
                                shouldAdd = false;
                                break;
                            }
                        }

                        if (shouldAdd){
                            dtPath = dtPath2;
                            dtDecoder = dtDecorder2;
                            break;
                        }
                    }

                }

            }

            shouldAdd = true;
            for (String decoder: doNotIncludeInOthers){
                if(decoder.contains(dtDecoder)){
                    shouldAdd = false;
                    break;
                }
            }
            if (dtPath != null && !dtPath.isEmpty()){
                if (shouldAdd){
                    otherPaths.add(dtPath);
                }
            }

        }


        }catch (Exception ex){
            ex.printStackTrace();
        }
        

    }

    @Override
    public void actionPerformed(ActionEvent e) {


        if (e.getSource() == btnClose) {
            dialog.setVisible(false);
        }
        if (e.getSource() == btnOpen) {
            JFileChooser c = new JFileChooser();
            c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            c.setMultiSelectionEnabled(false);
            c.setDialogTitle("Open Export Folder");
            int rVal = c.showOpenDialog(dialog);
            if (rVal == JFileChooser.APPROVE_OPTION) {
                String arquivoAberto = c.getSelectedFile().getAbsolutePath();
                newPath.setText(arquivoAberto);
            }
            if (rVal == JFileChooser.CANCEL_OPTION) {
                ;
            }
            c = null;
        }
        if (e.getSource() == btnSize) {
            dialog.setVisible(false);
            (new ExportCase(this.dialog, srcPaths, "", imgPaths, otherPaths,chkCopyImages.isSelected(), true)).execute();
        }
        if (e.getSource() == btnExport) {

            try{

                String dstPath = newPath.getText();                                
            
                File file = new File(dstPath);
                if(!file.exists()){
                    JOptionPane.showMessageDialog(dialog, "Invalid Export Folder!","Export Case",JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                //dstPath cannot be in same folder as srcPath, consequence -> infinite loop
                if(isSubDirectory(casePath,file)){
                    JOptionPane.showMessageDialog(dialog, "Export directory cannot be inside case folder!","Export Case",JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                
                if (dstPath.substring(dstPath.length() - 1).compareTo(File.separator)==0 )
                    dstPath += caseName;
                else
                    dstPath += File.separator+caseName;
                
                File tmpDst = new File (dstPath);
                if (tmpDst.exists()){
                    if (!isDirEmpty(Paths.get(dstPath))){
                        JOptionPane.showMessageDialog(dialog, "Export Folder is not empty!\n"+dstPath,"Export Case",JOptionPane.ERROR_MESSAGE);
                        return;												
                    }	
                }		


                dialog.setVisible(false);
                (new ExportCase(this.dialog, srcPaths, dstPath, imgPaths, otherPaths,chkCopyImages.isSelected(), false)).execute();

            }catch (Exception ex){
                ex.printStackTrace();
            }        

        }


    }

	private boolean isDirEmpty(final Path directory){
		try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
			return !dirStream.iterator().hasNext();
		}
		catch (Exception ex){
			return false;
		}
	}


	private boolean isSubDirectory(File base, File child) throws IOException {
		base = base.getCanonicalFile();
		child = child.getCanonicalFile();

		File parentFile = child;
		while (parentFile != null) {
			if (base.equals(parentFile)) {
				return true;
			}
			parentFile = parentFile.getParentFile();
		}
		return false;
	}




}
