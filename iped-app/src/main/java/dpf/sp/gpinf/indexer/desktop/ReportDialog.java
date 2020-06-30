package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
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
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.CmdLineArgsImpl;
import dpf.sp.gpinf.indexer.IndexFiles;

public class ReportDialog implements ActionListener, TableModelListener {

    private static Logger logger = LoggerFactory.getLogger(ReportDialog.class);

    JDialog dialog = new JDialog(App.get());
    ReportInfoDialog caseInfo = new ReportInfoDialog(dialog);
    JLabel top = new JLabel(Messages.getString("ReportDialog.ChooseLabel")); //$NON-NLS-1$
    Object[] header = { Boolean.FALSE, Messages.getString("ReportDialog.TableHeader1"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ReportDialog.TableHeader2") }; //$NON-NLS-1$
    List<JCheckBox> checkboxes = new ArrayList<>();
    JPanel panel = new JPanel(new BorderLayout());
    JTable table;
    JScrollPane scrollPane;
    JTextField output = new JTextField();
    JTextField keywords = new JTextField();
    JButton outButton = new JButton("..."); //$NON-NLS-1$
    JButton infoButton = new JButton(Messages.getString("ReportDialog.FillInfo")); //$NON-NLS-1$
    JButton keywordsButton = new JButton("..."); //$NON-NLS-1$
    JButton generate = new JButton(Messages.getString("ReportDialog.Create")); //$NON-NLS-1$
    JCheckBox noAttachs = new JCheckBox(Messages.getString("ReportDialog.NoAttachments")); //$NON-NLS-1$
    JCheckBox noLinkedItems = new JCheckBox(Messages.getString("ReportDialog.noLinkedItems")); //$NON-NLS-1$
    JCheckBox append = new JCheckBox(Messages.getString("ReportDialog.AddToReport")); //$NON-NLS-1$
    JCheckBox selectAll = new JCheckBox();

    HashSet<String> noContent = new HashSet<>();

    public ReportDialog() {

        dialog.setTitle(Messages.getString("ReportDialog.Title")); //$NON-NLS-1$
        dialog.setBounds(0, 0, 500, 500);
        dialog.setLocationRelativeTo(null);

        JPanel footer1 = new JPanel(new BorderLayout());
        footer1.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer1.add(new JLabel(Messages.getString("ReportDialog.Output")), BorderLayout.NORTH); //$NON-NLS-1$
        footer1.add(output, BorderLayout.CENTER);
        footer1.add(outButton, BorderLayout.EAST);

        JPanel footer2 = new JPanel(new BorderLayout());
        footer2.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer2.add(new JLabel(Messages.getString("ReportDialog.CaseInfo")), BorderLayout.CENTER); //$NON-NLS-1$
        JPanel bpanel = new JPanel(new BorderLayout());
        bpanel.add(infoButton, BorderLayout.WEST);
        footer2.add(bpanel, BorderLayout.SOUTH);

        JPanel footer3 = new JPanel(new BorderLayout());
        footer3.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer3.add(new JLabel(Messages.getString("ReportDialog.KeywordsFile")), BorderLayout.NORTH); //$NON-NLS-1$
        footer3.add(keywords, BorderLayout.CENTER);
        footer3.add(keywordsButton, BorderLayout.EAST);

        JPanel okPanel = new JPanel(new BorderLayout());
        okPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        okPanel.add(generate, BorderLayout.EAST);
        
        append.setToolTipText(Messages.getString("ReportDialog.AppendWarning"));

        Box footer = Box.createVerticalBox();
        footer.add(noAttachs);
        footer.add(noLinkedItems);
        footer.add(footer1);
        footer.add(append);
        footer.add(footer3);
        footer.add(footer2);
        footer.add(okPanel);

        for (Component c : footer.getComponents())
            ((JComponent) c).setAlignmentX(0);

        updateList();

        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(top, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);

        outButton.addActionListener(this);
        infoButton.addActionListener(this);
        keywordsButton.addActionListener(this);
        generate.addActionListener(this);

        dialog.getContentPane().add(panel);
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
            Object[] row = { App.get().appCase.getMultiMarcadores().isInReport(label), label, false };
            data[i++] = row;
        }
        TableModel tableModel = new TableModel(data, header);
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setMaxWidth(20);
        table.getColumnModel().getColumn(2).setMaxWidth(150);
        tableModel.addTableModelListener(this);
        scrollPane = new JScrollPane(table);
        
        table.getColumnModel().getColumn(0).setHeaderRenderer(new DefaultTableCellRenderer() {
            
            private boolean listenerAdded = false;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                JTableHeader header = table.getTableHeader();
                if (!listenerAdded) {
                    header.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if(header.columnAtPoint(e.getPoint()) == 0){
                                selectAll.doClick();
                            }
                        }
                    });
                    listenerAdded = true;
                }
                return selectAll;
            }
        });
        
        selectAll.addActionListener(this);
        
    }

    private class TableModel extends DefaultTableModel {
        TableModel(Object[][] data, Object[] columnNames) {
            super(data, columnNames);
        }

        @Override
        public Class<?> getColumnClass(int col) {
            if (col != 1)
                return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int col) {
            if (col == 1)
                return false;
            return true;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == outButton) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION)
                output.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }

        if (e.getSource() == infoButton) {
            caseInfo.setVisible(true);
        }

        if (e.getSource() == keywordsButton) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(App.get().appCase.getCaseDir());
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (fileChooser.showOpenDialog(App.get()) == JFileChooser.APPROVE_OPTION)
                keywords.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }

        if (e.getSource() == generate) {
            if (isInputOK())
                generateReport();
        }
        
        if(e.getSource() == selectAll) {
            for(int i = 0; i < table.getRowCount(); i++) {
                table.setValueAt(selectAll.isSelected(), i, 0);
            }
        }

    }

    private boolean isInputOK() {
        if (this.output.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, Messages.getString("ReportDialog.OutputRequired")); //$NON-NLS-1$
            return false;
        }

        return true;
    }

    private void generateReport() {

        String caseInfo;
        try {
            caseInfo = this.caseInfo.getReportInfo().writeReportInfoFile().getAbsolutePath();
        } catch (IOException e1) {
            e1.printStackTrace();
            JOptionPane.showMessageDialog(null, Messages.getString("ReportDialog.ReportError"), //$NON-NLS-1$
                    Messages.getString("ReportDialog.ErrorTitle"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
            return;
        }
        String keywords = this.keywords.getText().trim();
        String output = this.output.getText().trim();
        logger.info("Generating report to " + output); //$NON-NLS-1$

        URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
        try {
            String classpath = new File(url.toURI()).getAbsolutePath();
            if (!classpath.endsWith(".jar")) //$NON-NLS-1$
                classpath = App.get().appCase.getAtomicSources().get(0).getModuleDir().getAbsolutePath()
                        + File.separator + "lib" + File.separator + "iped-search-app.jar"; //$NON-NLS-1$ //$NON-NLS-2$

            File input = File.createTempFile("report", ".iped"); //$NON-NLS-1$ //$NON-NLS-2$
            App.get().appCase.getMultiMarcadores().saveState(input);

            List<String> cmd = new ArrayList<>();
            cmd.addAll(Arrays.asList("java", "-cp", classpath, IndexFiles.class.getCanonicalName(), //$NON-NLS-1$ //$NON-NLS-2$
                    "-d", input.getAbsolutePath(), //$NON-NLS-1$
                    "-o", output)); //$NON-NLS-1$

            if (!caseInfo.isEmpty())
                cmd.addAll(Arrays.asList("-asap", caseInfo)); //$NON-NLS-1$

            if (!keywords.isEmpty())
                cmd.addAll(Arrays.asList("-l", keywords)); //$NON-NLS-1$

            if (noAttachs.isSelected())
                cmd.add("--nopstattachs"); //$NON-NLS-1$

            if (noLinkedItems.isSelected())
                cmd.add(CmdLineArgsImpl.noLinkedItemsOption); // $NON-NLS-1$

            if (append.isSelected())
                cmd.add("--append"); //$NON-NLS-1$

            for (String label : noContent) {
                cmd.add("-nocontent"); //$NON-NLS-1$
                cmd.add(label);
            }

            logger.info("Report command: " + cmd.toString()); //$NON-NLS-1$

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
                        if (!msg.isEmpty())
                            logger.info(msg);
                    }

                    int result = process.waitFor();
                    if (result == 0)
                        JOptionPane.showMessageDialog(null, Messages.getString("ReportDialog.ReportFinished")); //$NON-NLS-1$
                    else
                        JOptionPane.showMessageDialog(null, Messages.getString("ReportDialog.ReportError"), //$NON-NLS-1$
                                Messages.getString("ReportDialog.ErrorTitle"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private class AsapFileFilter extends FileFilter {
        @Override
        public boolean accept(File f) {
            return f.isDirectory() || f.getName().toLowerCase().endsWith(".asap"); //$NON-NLS-1$
        }

        @Override
        public String getDescription() {
            return "*.asap"; //$NON-NLS-1$
        }
    }

    @Override
    public void tableChanged(TableModelEvent e) {

        if (e.getColumn() == 0) {
            Boolean checked = (Boolean) table.getValueAt(e.getFirstRow(), 0);
            String label = (String) table.getValueAt(e.getFirstRow(), 1);
            App.get().appCase.getMultiMarcadores().setInReport(label, checked);
            App.get().appCase.getMultiMarcadores().saveState();
        }
        if (e.getColumn() == 2) {
            Boolean checked = (Boolean) table.getValueAt(e.getFirstRow(), 2);
            String label = (String) table.getValueAt(e.getFirstRow(), 1);
            if (checked)
                noContent.add(label);
            else
                noContent.remove(label);
        }

    }

}
