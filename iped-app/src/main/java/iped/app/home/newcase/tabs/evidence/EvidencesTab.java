package iped.app.home.newcase.tabs.evidence;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.FileUtils;

/*
 * @created 09/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

import iped.app.home.DefaultPanel;
import iped.app.home.MainFrame;
import iped.app.home.newcase.NewCaseContainerPanel;
import iped.app.home.newcase.model.Evidence;
import iped.app.home.newcase.tabs.evidence.table.EvidencesTableModel;
import iped.app.home.newcase.tabs.evidence.table.TableEvidenceOptionsCellEditor;
import iped.app.home.newcase.tabs.evidence.table.TableEvidenceOptionsCellRenderer;
import iped.app.home.style.StyleManager;
import iped.app.ui.Messages;

/**
 * A page to manage the evidences to be processed
 */
public class EvidencesTab extends DefaultPanel implements EvidenceListListener {

    private JButton buttonAddFolder;
    private JButton buttonAddFile;
    private JButton buttonAddImages;
    private JButton buttonAddPhysicalDrive;
    private JTable jtableEvidences;
    private EvidencesTableModel evidencesTableModel;
    private ArrayList<Evidence> evidencesList;
    private final String[] extensoesDeImagensSuportadas = { "raw", "RAW", "000", "001", "vhdx", "VHDX", "dd", "DD", "ex01", "EX01", "E01", "e01", "aff", "AFF", "iso", "ISO", "vhd", "VHD", "vmdk", "VMDK", "ad1", "AD1", "ufdr", "UFDR" };

    public EvidencesTab(MainFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Prepare everything to be displayed
     */
    protected void createAndShowGUI() {
        this.setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        createFormComponentInstances();
        this.add(createTitlePanel(), BorderLayout.NORTH);
        this.add(createFormPanel(), BorderLayout.CENTER);
        this.add(createNavigationButtonsPanel(), BorderLayout.SOUTH);
    }

    /**
     * Create a new JPanel instance containing the Page Title
     * 
     * @return - JPanel containing the Page Title
     */
    private JPanel createTitlePanel() {
        JPanel panelTitle = new JPanel();
        panelTitle.setBackground(Color.white);
        JLabel labelTitle = new JLabel(Messages.get("Home.Evidences.Title"));
        labelTitle.setFont(StyleManager.getPageTitleFont());
        panelTitle.add(labelTitle);
        return panelTitle;
    }

    /**
     * Create a new JPanel instance containing all inputs
     * 
     * @return JPanel - A JPanel containing all data input form itens
     */
    private JPanel createFormPanel() {
        JPanel panelForm = new JPanel();
        panelForm.setLayout(new BoxLayout(panelForm, BoxLayout.PAGE_AXIS));
        panelForm.setBackground(Color.white);
        setupTableButtonsPanel(panelForm);
        panelForm.add(Box.createRigidArea(new Dimension(10, 10)));
        setupEvidenceTables(panelForm);
        return panelForm;
    }

    private void createFormComponentInstances() {
        buttonAddFolder = new JButton(Messages.get("Home.Evidences.AddFolder"));
        buttonAddFile = new JButton(Messages.get("Home.Evidences.AddFile"));
        buttonAddImages = new JButton(Messages.get("Home.Evidences.AddImagesRecursively"));
        buttonAddPhysicalDrive = new JButton(Messages.get("Home.Evidences.AddDisk"));
    }

    /**
     * Create and setup a JPanel containing all buttons to add Evidences to Table
     * 
     * @param panel
     *            - JPanel containing JButtons
     */
    private void setupTableButtonsPanel(JPanel panel) {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.setBackground(Color.white);
        buttonPanel.add(buttonAddFolder);
        buttonAddFolder.addActionListener(e -> {
            JFileChooser fileChooserDestino = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            fileChooserDestino.setDialogTitle(Messages.get("Home.Evidences.SelectFolder"));
            fileChooserDestino.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooserDestino.setAcceptAllFileFilterUsed(false);
            if (fileChooserDestino.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                Evidence evidence = new Evidence();
                evidence.setFileName(fileChooserDestino.getSelectedFile().getName());
                evidence.setPath(fileChooserDestino.getSelectedFile().getPath());
                evidencesList.add(evidence);
                evidencesTableModel.fireTableDataChanged();
            }
        });
        buttonPanel.add(buttonAddFile);
        buttonAddFile.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            fileChooser.setDialogTitle(Messages.get("Home.Evidences.SelectFile"));
            fileChooser.setAcceptAllFileFilterUsed(false);
            if (fileChooser.showDialog(this, Messages.get("Home.Evidences.AddFileTitle")) == JFileChooser.APPROVE_OPTION) {
                Evidence evidence = new Evidence();
                evidence.setFileName(fileChooser.getSelectedFile().getName());
                evidence.setPath(fileChooser.getSelectedFile().getPath());
                evidencesList.add(evidence);
                evidencesTableModel.fireTableDataChanged();
            }
        });
        buttonPanel.add(buttonAddImages);
        buttonAddImages.addActionListener(e -> {
            JFileChooser fileChooserProcurarImagens = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            fileChooserProcurarImagens.setDialogTitle(Messages.get("Home.Evidences.ChooseSourceFolder"));
            fileChooserProcurarImagens.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooserProcurarImagens.setAcceptAllFileFilterUsed(false);
            String pastaDeOrigem;
            if (fileChooserProcurarImagens.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                pastaDeOrigem = fileChooserProcurarImagens.getSelectedFile().toString();
            } else {
                return;
            }
            Path path = Paths.get(pastaDeOrigem);
            try {
                List<String> files = findEvidences(path, extensoesDeImagensSuportadas);
                Collections.sort(files);
                String prev = null;
                for (String current : files) {
                    if (current.endsWith(".001")) {
                        String zeroSegment = current.substring(0, current.length() - 3) + "000";
                        if (prev != null && prev.equals(zeroSegment)) {
                            continue;
                        }
                    }
                    File file = new File(current);
                    Evidence evidence = new Evidence();
                    evidence.setFileName(file.getName());
                    evidence.setPath(file.getPath());
                    evidencesList.add(evidence);
                    prev = current;
                }
                evidencesTableModel.fireTableDataChanged();
            } catch (Exception ex) {
                System.out.println(Messages.get("Home.Evidences.ImageSearchError"));
            }
        });
        buttonPanel.add(buttonAddPhysicalDrive);
        buttonAddPhysicalDrive.addActionListener(e -> {
            DiskSelecionDialog diskSelecionDialog = new DiskSelecionDialog(this.mainFrame, evidencesList);
            diskSelecionDialog.addListener(this);
            diskSelecionDialog.showDialog();

        });
        panel.add(buttonPanel);
    }

    /**
     * Create and setup a JTable do manage all Evidences to be processed
     * 
     * @param panel
     *            - A JPanel to add JTable
     */
    private void setupEvidenceTables(JPanel panel) {
        evidencesList = NewCaseContainerPanel.getInstance().getIpedProcess().getEvidenceList();
        EvidenceInfoDialog infoDialog = new EvidenceInfoDialog(mainFrame);
        infoDialog.addListener(this);
        evidencesTableModel = new EvidencesTableModel(evidencesList, infoDialog);
        jtableEvidences = new JTable();
        jtableEvidences.setModel(evidencesTableModel);
        setupTableLayout(infoDialog);
        panel.add(new JScrollPane(jtableEvidences));
    }

    /**
     * Adjusts the JTable layout
     */
    private void setupTableLayout(EvidenceInfoDialog infoDialog) {
        jtableEvidences.setFillsViewportHeight(true);
        jtableEvidences.setRowHeight(30);
        StyleManager.setTableHeaderStyle(jtableEvidences);
        jtableEvidences.getColumn(jtableEvidences.getColumnName(3)).setCellRenderer(new TableEvidenceOptionsCellRenderer());
        jtableEvidences.getColumn(jtableEvidences.getColumnName(3)).setCellEditor(new TableEvidenceOptionsCellEditor(evidencesList, infoDialog));
        jtableEvidences.getColumn(jtableEvidences.getColumnName(3)).setMaxWidth(70);
    }

    /**
     * A JPanel containing "Back" and "Next" buttons
     * 
     * @return JPanel - a new JPanel instance containing the bottom page Button
     */
    private JPanel createNavigationButtonsPanel() {
        JPanel panelButtons = new JPanel();
        panelButtons.setBackground(Color.white);
        JButton buttoCancel = new JButton(Messages.get("Home.Back"));
        buttoCancel.addActionListener(e -> NewCaseContainerPanel.getInstance().goToPreviousTab());
        JButton buttonNext = new JButton(Messages.get("Home.Next"));
        buttonNext.addActionListener(e -> {
            if (evidencesList == null || evidencesList.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.get("Home.Evidences.NoEvidencesAlert"), Messages.get("Home.Evidences.NoEvidencesAlertTitle"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            NewCaseContainerPanel.getInstance().goToNextTab();
        });
        panelButtons.add(buttoCancel);
        panelButtons.add(buttonNext);
        return panelButtons;
    }

    public List<String> findEvidences(Path path, String[] fileExtensions) {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(Messages.get("Home.Evidences.PathMustBeDirectory"));
        }
        File root = path.toFile();
        List<String> result = new ArrayList<>();
        try {
            List<Collection<File>> resultList = listFiles(root, fileExtensions);
            Collection<File> files = resultList.get(0);
            for (File file : files) {
                result.add(file.getAbsolutePath());
            }
            Collection<File> skippedFolders = resultList.get(1);
            if (skippedFolders.size() > 0) {
                JOptionPane.showMessageDialog(this, Messages.get("Home.Evidences.SkippedFoldersOnRecursiveEvidenceFind"), Messages.get("Home.Evidences.SkippedFoldersOnRecursiveEvidenceFindAlertTitle"), JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static List<Collection<File>> listFiles(File root, String[] fileExtensions) {
        List<Collection<File>> result = new ArrayList<Collection<File>>();
        Collection<File> filesFound = new ArrayList<File>();
        result.add(filesFound);
        Collection<File> skippedFolders = new ArrayList<File>();
        result.add(skippedFolders);
        listFiles(root, fileExtensions, result);
        return result;
    }

    private static void listFiles(File root, String[] fileExtensions, List<Collection<File>> result) {
        Collection<File> filesFound = result.get(0);
        Collection<File> skippedFolders = result.get(1);
        try {
            Collection<File> files = FileUtils.listFiles(root, fileExtensions, false);
            for (File file : files) {
                filesFound.add(file);
            }
            for (File file : root.listFiles()) {
                if (file.isDirectory()) {
                    listFiles(file, fileExtensions, result);
                }
            }
        } catch (Exception e) {
            skippedFolders.add(root);
        }
    }

    @Override
    public void evidenceDataChange() {
        evidencesTableModel.fireTableDataChanged();
    }
}
