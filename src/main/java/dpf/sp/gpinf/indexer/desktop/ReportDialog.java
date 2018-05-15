package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.IndexFiles;
import dpf.sp.gpinf.indexer.parsers.util.Util;

public class ReportDialog implements ActionListener, TableModelListener{
    
    private static Logger logger = LoggerFactory.getLogger(ReportDialog.class);
    
    JDialog dialog = new JDialog(App.get());
    JLabel top = new JLabel("Escolha os marcadores para gerar o relatório:");
    Object[] header = {"", "Marcador", "JustThumb"};
    List<JCheckBox> checkboxes = new ArrayList<>();
    JPanel panel = new JPanel(new BorderLayout());
    JTable table;
    JScrollPane scrollPane;
    JTextField output = new JTextField();
    JTextField caseinfo = new JTextField();
    JTextField keywords = new JTextField();
    JButton outButton = new JButton("...");
    JButton infoButton = new JButton("...");
    JButton keywordsButton = new JButton("...");
    JButton fillInfo = new JButton("Preencher informações");
    JButton generate = new JButton("Gerar");
    JCheckBox noAttachs = new JCheckBox("Não exportar anexos de emails automaticamente");
    JCheckBox append = new JCheckBox("Adicionar ao relatório já existente");
    
    HashSet<String> noContent = new HashSet<>();
    
    JDialog infoDialog = new JDialog(dialog);
    JTextField rNumber = new JTextField();
    JTextField rDate = new JTextField();
    JTextField rTitle = new JTextField();
    JTextField rExaminers = new JTextField();
    JTextField rInvestigation = new JTextField();
    JTextField rRequestDoc = new JTextField();
    JTextField rRequester = new JTextField();
    JTextField rRecord = new JTextField();
    JTextField rRecordDate = new JTextField();
    JTextArea rEvidences = new JTextArea();
    
    public ReportDialog() {
        
        dialog.setTitle("Gerar Relatório");
        dialog.setBounds(0, 0, 500, 500);
        dialog.setLocationRelativeTo(null);
        
        JPanel footer1 = new JPanel(new BorderLayout());
        footer1.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer1.add(new JLabel("Pasta do relatório:"), BorderLayout.NORTH);
        footer1.add(output, BorderLayout.CENTER);
        footer1.add(outButton, BorderLayout.EAST);
        
        JPanel footer2 = new JPanel(new BorderLayout());
        footer2.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer2.add(new JLabel("Arquivo de informações do caso (opcional):"), BorderLayout.NORTH);
        footer2.add(caseinfo, BorderLayout.CENTER);
        footer2.add(infoButton, BorderLayout.EAST);
        
        JPanel footer3 = new JPanel(new BorderLayout());
        footer3.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer3.add(new JLabel("Arquivo de palavras-chave (opcional):"), BorderLayout.NORTH);
        footer3.add(keywords, BorderLayout.CENTER);
        footer3.add(keywordsButton, BorderLayout.EAST);
        
        JPanel okPanel = new JPanel(new BorderLayout());
        okPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        okPanel.add(generate, BorderLayout.EAST);
        
        Box footer = Box.createVerticalBox();
        footer.add(noAttachs);
        footer.add(footer1);
        footer.add(append);
        footer.add(footer3);
        footer.add(footer2);
        footer.add(okPanel);
        
        for(Component c : footer.getComponents())
            ((JComponent)c).setAlignmentX(0);
        
        updateList();
        
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(top, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        
        outButton.addActionListener(this);
        infoButton.addActionListener(this);
        keywordsButton.addActionListener(this);
        generate.addActionListener(this);
        fillInfo.addActionListener(this);
        
        dialog.getContentPane().add(panel);
        
        //createCaseInfoDialog();
    }
    
    private void createCaseInfoDialog() {
        
        infoDialog.setTitle("Informações do Caso");
        infoDialog.setBounds(0, 0, 500, 500);
        infoDialog.setLocationRelativeTo(null);
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        panel.add(infoButton, getGridBagConstraints(0,0,1,1));

        JLabel num = new JLabel("Número do Laudo");
        panel.add(num, getGridBagConstraints(0,1,1,1));
        panel.add(rNumber, getGridBagConstraints(1,1,2,1));
        
        JLabel date = new JLabel("Data");
        panel.add(date, getGridBagConstraints(0,2,1,1));
        panel.add(rDate, getGridBagConstraints(1,2,2,1));
        
        JLabel title = new JLabel("Título");
        panel.add(title, getGridBagConstraints(0,3,1,1));
        panel.add(rTitle, getGridBagConstraints(1,3,2,1));
        
        JLabel examiner = new JLabel("Examinador");
        panel.add(examiner, getGridBagConstraints(0,4,1,1));
        panel.add(rExaminers, getGridBagConstraints(1,4,2,1));

        JLabel ipl = new JLabel("Investigação");
        panel.add(ipl, getGridBagConstraints(0,5,1,1));
        panel.add(rInvestigation, getGridBagConstraints(1,5,2,1));
        
        JLabel request = new JLabel("Solicitação");
        panel.add(request, getGridBagConstraints(0,6,1,1));
        panel.add(rRequestDoc, getGridBagConstraints(1,6,2,1));
        
        JLabel requester = new JLabel("Solicitante");
        panel.add(requester, getGridBagConstraints(0,7,1,1));
        panel.add(rRequester, getGridBagConstraints(1,7,2,1));
        
        JLabel record = new JLabel("Registro/Protocolo");
        panel.add(record, getGridBagConstraints(0,8,1,1));
        panel.add(rRecord, getGridBagConstraints(1,8,2,1));
        
        JLabel recordDate = new JLabel("Data do Registro");
        panel.add(recordDate, getGridBagConstraints(0,9,1,1));
        panel.add(rRecordDate, getGridBagConstraints(1,9,2,1));
        
        JLabel evidences = new JLabel("Materiais");
        panel.add(evidences, getGridBagConstraints(0,10,1,1));
        panel.add(rEvidences, getGridBagConstraints(1,10,2,2));
        rEvidences.setLineWrap(true);
        rEvidences.setWrapStyleWord(true);
        
        infoDialog.getContentPane().add(panel);
    }
    
    private GridBagConstraints getGridBagConstraints(int x, int y, int width, int height) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        if(width > 1)
            c.weightx = 1.0;
        if(height > 1) {
            c.weighty = 1.0;
            c.fill = GridBagConstraints.BOTH;
        }
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.gridheight = height;
        return c;
    }
    
    public void setVisible() {
        dialog.setVisible(true);
    }
    
    public void updateList() {

        String[] labels = App.get().appCase.getMultiMarcadores().getLabelMap().toArray(new String[0]);
        Arrays.sort(labels, Collator.getInstance());
        
        Object[][] data = new Object[labels.length][];
        int i = 0;
        for (String label : labels) {
            Object[] row = {App.get().appCase.getMultiMarcadores().isInReport(label), label, false};
            data[i++] = row;
        }
        TableModel tableModel = new TableModel(data, header);
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setMaxWidth(20);
        table.getColumnModel().getColumn(2).setMaxWidth(150);
        tableModel.addTableModelListener(this);
        scrollPane = new JScrollPane(table);
        
      }
    
    private class TableModel extends DefaultTableModel{
        TableModel(Object[][] data, Object[] columnNames){
            super(data, columnNames);
        }
        @Override
        public Class<?> getColumnClass(int col) {
            if(col != 1)
                return Boolean.class;
            return String.class;
        }
        @Override
        public boolean isCellEditable(int rowIndex, int col){
            if(col == 1)
                return false;
            return true;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        
        if(e.getSource() == outButton) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION)
                output.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
        
        if(e.getSource() == infoButton) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setFileFilter(new AsapFileFilter());
            if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION)
                caseinfo.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
        
        if(e.getSource() == keywordsButton) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION)
                keywords.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
        
        if(e.getSource() == generate) {
            if(isInputOK())
                generateReport();
        }
        
        //if(e.getSource() == fillInfo)
        //    infoDialog.setVisible(true);
        
    }
    
    private boolean isInputOK() {
        if(this.output.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Necessário especificar pasta de saída!");
            return false;
        }
        
        return true;
    }
    
    private void generateReport() {
        
        String caseInfo = this.caseinfo.getText().trim();        
        String keywords = this.keywords.getText().trim();   
        String output = this.output.getText().trim();
        logger.info("Generating report to " + output);
        
        URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
        try {
            String classpath = new File(url.toURI()).getAbsolutePath();
            if(!classpath.endsWith(".jar"))
                classpath = App.get().appCase.getAtomicSources().get(0).getModuleDir().getAbsolutePath() +
                    File.separator + "lib" + File.separator + "iped-search-app.jar";
            
            File input = File.createTempFile("report", ".iped");
            App.get().appCase.getMultiMarcadores().saveState(input);
                    
            List<String> cmd = new ArrayList<>();
            cmd.addAll(Arrays.asList("java", "-cp", classpath, IndexFiles.class.getCanonicalName(),  //$NON-NLS-1$ //$NON-NLS-2$
                    "-d", input.getAbsolutePath(), //$NON-NLS-1$
                    "-o", output)); //$NON-NLS-1$
            
            if(!caseInfo.isEmpty())
                cmd.addAll(Arrays.asList("-asap", caseInfo));

            if(!keywords.isEmpty())
                cmd.addAll(Arrays.asList("-l", keywords));
            
            if(noAttachs.isSelected())
                cmd.add("--nopstattachs");
            
            if(append.isSelected())
                cmd.add("--append");
            
            for(String label : noContent) {
                cmd.add("-nocontent");
                cmd.add(label);
            }
            
            logger.info("Report command: " + cmd.toString());
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            monitorReport(process);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void monitorReport(final Process process) {
        new Thread() {
          public void run() {
            byte[] b = new byte[1024 * 1024];
            try {
              int r = 0;
              while ((r = process.getInputStream().read(b)) != -1) {
                String msg = new String(b, 0, r).trim();
                if(!msg.isEmpty())
                    logger.info(msg);
              }
              
              int result = process.waitFor();
              if(result == 0)
                  JOptionPane.showMessageDialog(null, "Geração de Relatório finalizada!");
              else
                  JOptionPane.showMessageDialog(null, "Erro ao gerar Relatório, verifique o log!", "Erro", JOptionPane.ERROR_MESSAGE);

            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }.start();
      }
    
    private class AsapFileFilter extends FileFilter {
        @Override
        public boolean accept(File f) {
            return f.isDirectory() || f.getName().toLowerCase().endsWith(".asap");
        }
        @Override
        public String getDescription() {
            return "*.asap";
        }
    }

    @Override
    public void tableChanged(TableModelEvent e) {

        if(e.getColumn() == 0) {
            Boolean checked = (Boolean)table.getValueAt(e.getFirstRow(), 0);
            String label = (String)table.getValueAt(e.getFirstRow(), 1);
            App.get().appCase.getMultiMarcadores().setInReport(label, checked);
            App.get().appCase.getMultiMarcadores().saveState();
        }
        if(e.getColumn() == 2) {
            Boolean checked = (Boolean)table.getValueAt(e.getFirstRow(), 2);
            String label = (String)table.getValueAt(e.getFirstRow(), 1);
            if(checked)
                noContent.add(label);
            else
                noContent.remove(label);
        }
        
    }

}
