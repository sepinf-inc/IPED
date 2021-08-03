/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dpf.sp.gpinf.indexer.ui;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
//import java.lang.management.ManagementFactory;

/**
 *
 * @author blinddog
 */
public class FrontIPED extends javax.swing.JFrame {

    private DefaultListModel listmodel = new DefaultListModel();
    private String base_path = System.getProperty("user.dir") + File.separator;
    private String default_path = System.getProperty("user.dir") + File.separator + "profiles" + File.separator
            + "pt-BR" + File.separator + "default" + File.separator;
    private File ipedJAR = new File(this.base_path + "iped.jar");
    private String[] Command = null;
    private String ProjectName = null;
    private File chooser = new File(base_path);
    private File iped_dir = new File(base_path);

    /**
     * Creates new form Painel
     */
    public FrontIPED() {

        if (ipedJAR.exists()) {
            if (this.checkFilesConfig()) {
                if (!this.checkBackupFiles()) {
                    JOptionPane.showMessageDialog(null,
                            "Essa é primeira vez que você configura o FrontIped? Cópias dos arquivos de configurações: 'IPEDConfig.txt' e 'LocalConfig.txt' serão criados na pasta 'BKP-CONFIG'!");
                    // cria diretorio BKP-CONFIG
                    try {
                        File bkp = new File(this.base_path + "BKP-CONFIG");
                        // File scripts = new File(this.base_path+"SCRIPTS");
                        bkp.mkdir();
                        // scripts.mkdir();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null, "Erro: " + ex.getMessage());
                    }
                    File bkpLocalConfig = new File(this.base_path + "LocalConfig.txt");
                    File bkpIpedConfig = new File(this.default_path + "IPEDConfig.txt");
                    try {
                        this.backup(bkpLocalConfig);
                    } catch (IOException ex) {
                        Logger.getLogger(FrontIPED.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    try {
                        this.backup(bkpIpedConfig);
                    } catch (IOException ex) {
                        Logger.getLogger(FrontIPED.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    JOptionPane.showMessageDialog(null, "Os arquivos de configuração foram salvos com sucesso!");
                }
                initComponents();
                // this.carregaConfiguracoes();
                projectNameText.requestFocus();
            }
        } else {
            JOptionPane.showMessageDialog(null,
                    "O programa FrontIPED não pode funcionar corretamente até que o arquivo FrontIped.jar seja colocado dentro da pasta de instalação do Iped!");
            System.exit(0);
        }
    }

    private boolean checkFilesConfig() {
        File LocalConfig = new File(this.base_path + "LocalConfig.txt");
        File IpedConfig = new File(this.default_path + "IPEDConfig.txt");
        return LocalConfig.exists() && IpedConfig.exists();
    }

    private boolean checkBackupFiles() {
        File bkpLocalConfig = new File(this.base_path + "BKP-CONFIG" + File.separator + "LocalConfig.txt");
        File bkpIpedConfig = new File(this.base_path + "BKP-CONFIG" + File.separator + "IPEDConfig.txt");
        return bkpLocalConfig.exists() && bkpIpedConfig.exists();
        // if (bkpLocalConfig.exists() && bkpIpedConfig.exists()){
        // return true;
        // }else{
        // return false;
        // }
    }

    private void doBackupFiles(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[1024];
        int len;

        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    private void backup(File file) throws IOException {
        FileReader fis = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fis);
        StringBuilder buffer = new StringBuilder();
        String line = "";
        while ((line = bufferedReader.readLine()) != null) {
            buffer.append(line).append("\r\n");
        }
        fis.close();
        bufferedReader.close();
        File arquivoDestino = new File(this.base_path + "BKP-CONFIG" + File.separator + file.getName());
        FileWriter writer = new FileWriter(arquivoDestino);
        writer.write(buffer.toString());
        writer.flush();
        writer.close();
    }

    private void createScript(String fileName, String outputFolder, String[] content) throws IOException {
        // String abspath = this.base_path+"SCRIPTS"+File.separator+fileName+".cmd";

        String abspath = outputFolder + File.separator + fileName + ".cmd";

        File file = new File(abspath);
        Object[] options = { "SIM", "NÃO" };

        if (file.exists()) {

            int choice = JOptionPane.showOptionDialog(null,
                    "Já existe um projeto com este nome: '" + fileName + "'! Deseja salvar com outro nome?", "Atenção!",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

            if (choice == JOptionPane.YES_OPTION) {
                fileName = JOptionPane.showInputDialog(null, "Forneça um novo nome para o projeto!");
                // abspath = this.base_path+"SCRIPTS"+File.separator+fileName+".cmd";
                abspath = outputFolder + File.separator + fileName + ".cmd";
                projectNameText.setText(fileName);
            }
        }
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < content.length; i++) {
            buffer.append(content[i]).append("\r\n");
        }
        FileWriter writer = new FileWriter(abspath);
        writer.write(buffer.toString());
        writer.flush();
        writer.close();
        this.ProjectName = abspath;
        JOptionPane.showMessageDialog(null, "O arquivo: '" + abspath + "' foi salvo com sucesso!", "Aviso!",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private Properties getPropIpedConfig() throws IOException {
        Properties props = new Properties();
        File file = new File(this.default_path + "IPEDConfig.txt");
        // referência ao arquivo de propriedades
        FileInputStream fis = new FileInputStream(file);
        // carrega valores para o objeto Properties
        props.load(fis);
        // fecha arquivo
        fis.close();
        /*
         * Config configPadrao = new Config(); for (String item :
         * configPadrao.getHashes()){ System.out.printf(" %s", item.toLowerCase()); }
         */
        // JOptionPane.showMessageDialog(null, configPadrao.getHash());
        return props;
    }

    private Properties getPropLocalConfig() throws IOException {
        Properties props = new Properties();
        File file = new File(this.base_path + "LocalConfig.txt");
        // referência ao arquivo de propriedades
        FileInputStream fis = new FileInputStream(file);
        // carrega valores para o objeto Properties
        props.load(fis);
        // fecha arquivo
        fis.close();
        /*
         * Config configPadrao = new Config(); for (String item :
         * configPadrao.getHashes()){ System.out.printf(" %s", item.toLowerCase()); }
         */
        // JOptionPane.showMessageDialog(null, configPadrao.getHash());
        return props;
    }

    private void carregaConfiguracoes() {
        this.carregaLocalConfig();
        this.carregaIPEDConfig();
    }

    private void carregaLocalConfig() {

        try {
            Properties p = getPropLocalConfig();

            if (p.getProperty("locale") == null) {
                this.jcLocale.setSelectedItem("pt-BR");
                JOptionPane.showMessageDialog(null,
                        "Não foi encontrado o parâmetro locale! Antes de proseguir salve as configurações para corrigir a falta deste parâmetro.");
            } else {
                this.jcLocale.setSelectedItem(p.getProperty("locale"));
            }

            if (p.getProperty("indexTemp").isEmpty()) {
                this.indexTempText.setText("default");
            } else {
                this.indexTempText.setText(p.getProperty("indexTemp"));
            }
            if (p.getProperty("numThreads").isEmpty()) {
                this.numThreadsText.setText("default");
            } else {
                this.numThreadsText.setText(p.getProperty("numThreads"));
            }

            if ((p.getProperty("kffDb") != null)) {
                this.kffDbText.setText(p.getProperty("kffDb"));
                this.btnKffPath.setEnabled(true);
                this.enableKffCheckBox.setSelected(true);
                this.kffDbText.setEnabled(this.enableKffCheckBox.isSelected());
            }
            if (p.getProperty("ledWkffPath") != null) {
                this.ledWkffPathText.setText(p.getProperty("ledWkffPath"));
                this.btnHashLEDPath.setEnabled(true);
                this.enableLedWkffCheckBox.setSelected(true);
                this.ledWkffPathText.setEnabled(this.enableLedWkffCheckBox.isSelected());
            }
            if (p.getProperty("ledDie") != null) {
                this.ledDieText.setText(p.getProperty("ledDie"));
                this.btnDiePath.setEnabled(true);
                this.enableLedDieCheckBox.setSelected(true);
                this.ledDieText.setEnabled(this.enableLedDieCheckBox.isSelected());
            }
            if (p.getProperty("tskJarPath") != null) {
                this.tskJarPathText.setText(p.getProperty("tskJarPath"));
                this.btnTskJarPath.setEnabled(true);
                this.enableTskJarPathCheckBox.setSelected(true);
                this.tskJarPathText.setEnabled(this.enableTskJarPathCheckBox.isSelected());
            }
            if (p.getProperty("mplayerPath") != null) {
                this.mplayerPathText.setText(p.getProperty("mplayerPath"));
                this.btnMplayerPath.setEnabled(true);
                this.enableMplayerPathCheckBox.setSelected(true);
                this.mplayerPathText.setEnabled(this.enableMplayerPathCheckBox.isSelected());
            }
            if (p.getProperty("optional_jars") != null) {
                this.optionalJarsPathText.setText(p.getProperty("optional_jars"));
                this.btnOptionalJarsPath.setEnabled(true);
                this.enableOptionalJarsPathCheckBox.setSelected(true);
                this.optionalJarsPathText.setEnabled(this.enableOptionalJarsPathCheckBox.isSelected());
            }
            if (p.getProperty("regripperFolder") != null) {
                this.regripperFolderPathText.setText(p.getProperty("regripperFolder"));
                this.btnRegripperFolderPath.setEnabled(true);
                this.enableRegripperFolderPathCheckBox.setSelected(true);
                this.regripperFolderPathText.setEnabled(this.enableRegripperFolderPathCheckBox.isSelected());
            }
            // seta os checkbox
            this.indexTempOnSSDCheckBox.setSelected(Boolean.valueOf(p.getProperty("indexTempOnSSD")));
            this.outputOnSSDCheckBox.setSelected(Boolean.valueOf(p.getProperty("outputOnSSD")));

        } catch (IOException ex) {
            Logger.getLogger(FrontIPED.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void carregaIPEDConfig() {
        // System.out.print(jList1.getSelectedValue());
        try {
            Properties p = getPropIpedConfig();

            // String hash = p.getProperty("hash").isEmpty();
            if (p.getProperty("hash").isEmpty()) {
                this.hashText.setText("md5");
            } else {
                this.hashText.setText(p.getProperty("hash"));
            }
            //
            // String[] listData = p.getProperty("hash").trim().split(",");
            String[] hashes = { "md5", "sha-1", "sha-256", "sha-512", "edonkey" };
            // DefaultListModel h = new DefaultListModel();

            jlHash.setListData(hashes);
            jlHash.setSelectedIndex(0);

            // set os outos checkboxes
            this.excludeKffIgnorableCheckBox.setSelected(Boolean.valueOf(p.getProperty("excludeKffIgnorable")));
            this.ignoreDuplicatesCheckBox.setSelected(Boolean.valueOf(p.getProperty("ignoreDuplicates")));
            this.exportFilePropsCheckBox.setSelected(Boolean.valueOf(p.getProperty("exportFileProps")));
            this.processFileSignaturesCheckBox.setSelected(Boolean.valueOf(p.getProperty("processFileSignatures")));
            this.enableFileParsingCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableFileParsing")));
            this.expandContainersCheckBox.setSelected(Boolean.valueOf(p.getProperty("expandContainers")));
            this.indexFileContentsCheckBox.setSelected(Boolean.valueOf(p.getProperty("indexFileContents")));
            this.indexUnknownFilesCheckBox.setSelected(Boolean.valueOf(p.getProperty("indexUnknownFiles")));
            this.enableOCRCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableOCR")));
            this.addFileSlacksCheckBox.setSelected(Boolean.valueOf(p.getProperty("addFileSlacks")));
            this.addUnallocatedCheckBox.setSelected(Boolean.valueOf(p.getProperty("addUnallocated")));
            this.indexUnallocatedCheckBox.setSelected(Boolean.valueOf(p.getProperty("indexUnallocated")));
            this.enableCarvingCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableCarving")));
            this.enableKFFCarvingCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableKFFCarving")));
            this.enableKnownMetCarvingCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableKnownMetCarving")));
            this.enableImageThumbsCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableImageThumbs")));
            this.enableVideoThumbsCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableVideoThumbs")));
            this.enableHTMLReportCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableHTMLReport")));
            this.enableRegexSearchCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableRegexSearch")));
            this.enableLanguageDetectCheckBox.setSelected(Boolean.valueOf(p.getProperty("enableLanguageDetect")));
            this.enableNamedEntityRecognitonCheckBox
                    .setSelected(Boolean.valueOf(p.getProperty("enableNamedEntityRecogniton")));
            this.indexCorruptedFilesCheckBox.setSelected(Boolean.valueOf(p.getProperty("indexCorruptedFiles")));
        } catch (IOException ex) {
            Logger.getLogger(FrontIPED.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void savePropertiesLocalConfig(Properties props) {
        /*
         * for(String key : props.stringPropertyNames()) { if ("indexTemp".equals(key))
         * props.setProperty("indexTemp",
         * props.getProperty(key));//System.out.printf("%s = \"%s\"%n", key,
         * props.getProperty(key)); }
         */
        // save contents of table
        try {
            FileOutputStream output = new FileOutputStream(
                    System.getProperty("user.dir") + File.separator + "LocalConfig.txt");
            props.store(output, "Configurações Locais do Processamento"); // save properties
            output.close();
        } // end try
        catch (IOException ioException) {
            ioException.printStackTrace();
        } // end catch

    } // end method saveProperties

    private static void savePropertiesIPEDConfig(Properties props) {
        /*
         * for(String key : props.stringPropertyNames()) { if ("indexTemp".equals(key))
         * props.setProperty("indexTemp",
         * props.getProperty(key));//System.out.printf("%s = \"%s\"%n", key,
         * props.getProperty(key)); }
         */
        // save contents of table
        try {
            FileOutputStream output = new FileOutputStream(System.getProperty("user.dir") + File.separator + "profiles"
                    + File.separator + "pt-BR" + File.separator + "default" + File.separator + "IPEDConfig.txt");
            props.store(output, "Configurações IPEDConfig do Processamento"); // save properties
            output.close();
        } // end try
        catch (IOException ioException) {
            ioException.printStackTrace();
        } // end catch

    } // end method saveProperties

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")

    // <editor-fold defaultstate="collapsed" desc="Generated
    // Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jtpPrincipal = new javax.swing.JTabbedPane();
        jpProjeto = new javax.swing.JPanel();
        jPanel11 = new javax.swing.JPanel();
        PainelProjetoConfig = new javax.swing.JPanel();
        jLabel15 = new javax.swing.JLabel();
        projectNameText = new javax.swing.JTextField();
        pathListaExpressText = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        btnListExpress = new javax.swing.JButton();
        jLabel5 = new javax.swing.JLabel();
        pathAsapFileText = new javax.swing.JTextField();
        btnASAP = new javax.swing.JButton();
        PainelDestino = new javax.swing.JPanel();
        jLabel7 = new javax.swing.JLabel();
        outputText = new javax.swing.JTextField();
        btnDestino = new javax.swing.JButton();
        PainelMemoria = new javax.swing.JPanel();
        xmxParameterTxt = new javax.swing.JTextField();
        PainelPerfil = new javax.swing.JPanel();
        jcProfiles = new javax.swing.JComboBox<>();
        PainelOrigem = new javax.swing.JPanel();
        inputText = new javax.swing.JTextField();
        btnAddInput = new javax.swing.JButton();
        btnRemInput = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        inputsJList = new javax.swing.JList<>();
        jLabel6 = new javax.swing.JLabel();
        btnOrigem = new javax.swing.JButton();
        jLabel14 = new javax.swing.JLabel();
        DNameLabel = new javax.swing.JLabel();
        imageNameText = new javax.swing.JTextField();
        nameLogFileText = new javax.swing.JTextField();
        LogFileLabel = new javax.swing.JLabel();
        LogAlternativoCheckBox = new javax.swing.JCheckBox();
        DNameCheckBox = new javax.swing.JCheckBox();
        FAppendCheckBox = new javax.swing.JCheckBox();
        PortableCheckBox = new javax.swing.JCheckBox();
        btnSaveProject = new javax.swing.JButton();
        jpIPEDConfig = new javax.swing.JPanel();
        jPanel5 = new javax.swing.JPanel();
        jpOutrasConfiguracoes = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jlHash = new javax.swing.JList<>();
        jLabel12 = new javax.swing.JLabel();
        hashText = new javax.swing.JTextField();
        jpParametros = new javax.swing.JPanel();
        excludeKffIgnorableCheckBox = new javax.swing.JCheckBox();
        enableKFFCarvingCheckBox = new javax.swing.JCheckBox();
        exportFilePropsCheckBox = new javax.swing.JCheckBox();
        enableOCRCheckBox = new javax.swing.JCheckBox();
        ignoreDuplicatesCheckBox = new javax.swing.JCheckBox();
        enableCarvingCheckBox = new javax.swing.JCheckBox();
        enableFileParsingCheckBox = new javax.swing.JCheckBox();
        expandContainersCheckBox = new javax.swing.JCheckBox();
        enableImageThumbsCheckBox = new javax.swing.JCheckBox();
        enableVideoThumbsCheckBox = new javax.swing.JCheckBox();
        enableHTMLReportCheckBox = new javax.swing.JCheckBox();
        addUnallocatedCheckBox = new javax.swing.JCheckBox();
        indexUnknownFilesCheckBox = new javax.swing.JCheckBox();
        indexFileContentsCheckBox = new javax.swing.JCheckBox();
        addFileSlacksCheckBox = new javax.swing.JCheckBox();
        enableKnownMetCarvingCheckBox = new javax.swing.JCheckBox();
        indexUnallocatedCheckBox = new javax.swing.JCheckBox();
        processFileSignaturesCheckBox = new javax.swing.JCheckBox();
        enableRegexSearchCheckBox = new javax.swing.JCheckBox();
        enableLanguageDetectCheckBox = new javax.swing.JCheckBox();
        enableNamedEntityRecognitonCheckBox = new javax.swing.JCheckBox();
        indexCorruptedFilesCheckBox = new javax.swing.JCheckBox();
        btnSaveProfile = new javax.swing.JButton();
        jpFerramentasExternas = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        enableKffCheckBox = new javax.swing.JCheckBox();
        kffDbText = new javax.swing.JTextField();
        btnKffPath = new javax.swing.JButton();
        enableLedWkffCheckBox = new javax.swing.JCheckBox();
        ledWkffPathText = new javax.swing.JTextField();
        btnHashLEDPath = new javax.swing.JButton();
        enableLedDieCheckBox = new javax.swing.JCheckBox();
        ledDieText = new javax.swing.JTextField();
        btnDiePath = new javax.swing.JButton();
        tskJarPathText = new javax.swing.JTextField();
        btnTskJarPath = new javax.swing.JButton();
        enableTskJarPathCheckBox = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        indexTempText = new javax.swing.JTextField();
        btnDirTemp = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        numThreadsText = new javax.swing.JTextField();
        jPanel2 = new javax.swing.JPanel();
        indexTempOnSSDCheckBox = new javax.swing.JCheckBox();
        outputOnSSDCheckBox = new javax.swing.JCheckBox();
        jPanel8 = new javax.swing.JPanel();
        jcLocale = new javax.swing.JComboBox<>();
        jPanel9 = new javax.swing.JPanel();
        enableMplayerPathCheckBox = new javax.swing.JCheckBox();
        mplayerPathText = new javax.swing.JTextField();
        btnMplayerPath = new javax.swing.JButton();
        enableOptionalJarsPathCheckBox = new javax.swing.JCheckBox();
        optionalJarsPathText = new javax.swing.JTextField();
        btnOptionalJarsPath = new javax.swing.JButton();
        enableRegripperFolderPathCheckBox = new javax.swing.JCheckBox();
        regripperFolderPathText = new javax.swing.JTextField();
        btnRegripperFolderPath = new javax.swing.JButton();
        btnSalvar = new javax.swing.JButton();
        jpSobre = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jLabel17 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        btnIniciarProcesso = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Configurações Básicas do IPED");
        setResizable(false);

        PainelProjetoConfig.setBorder(javax.swing.BorderFactory.createTitledBorder("Configurações"));

        jLabel15.setText("Nome do Projeto");

        projectNameText.setToolTipText("Nome para o projeto");
        projectNameText.setMinimumSize(new java.awt.Dimension(6, 23));
        projectNameText.setNextFocusableComponent(pathListaExpressText);

        pathListaExpressText.setToolTipText(
                "Arquivo com lista de expressões a serem exibidas na busca. Expressões sem ocorrências são filtradas. Parâmetro -l");
        pathListaExpressText.setNextFocusableComponent(btnListExpress);

        jLabel8.setText("Caminho para o arquivo com lista de expressões (opcional)");

        btnListExpress.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnListExpress.setToolTipText("Clique para localizar");
        btnListExpress.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnListExpress.setNextFocusableComponent(pathAsapFileText);
        btnListExpress.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnListExpressActionPerformed(evt);
            }
        });

        jLabel5.setText("Caminho para o arquivo ASAP (opcional)");

        pathAsapFileText.setNextFocusableComponent(btnASAP);

        btnASAP.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnASAP.setToolTipText("Clique para localizar");
        btnASAP.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnASAP.setNextFocusableComponent(jcProfiles);
        btnASAP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnASAPActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout PainelProjetoConfigLayout = new javax.swing.GroupLayout(PainelProjetoConfig);
        PainelProjetoConfig.setLayout(PainelProjetoConfigLayout);
        PainelProjetoConfigLayout.setHorizontalGroup(PainelProjetoConfigLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(PainelProjetoConfigLayout.createSequentialGroup().addContainerGap()
                        .addGroup(PainelProjetoConfigLayout
                                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(PainelProjetoConfigLayout.createSequentialGroup()
                                        .addComponent(pathListaExpressText, javax.swing.GroupLayout.PREFERRED_SIZE, 387,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnListExpress, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(projectNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 184,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGroup(
                                        PainelProjetoConfigLayout.createSequentialGroup().addComponent(pathAsapFileText)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(btnASAP, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 94,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(jLabel8, javax.swing.GroupLayout.PREFERRED_SIZE, 371,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 318,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        PainelProjetoConfigLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL,
                new java.awt.Component[] { pathAsapFileText, pathListaExpressText });

        PainelProjetoConfigLayout.setVerticalGroup(
                PainelProjetoConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(PainelProjetoConfigLayout.createSequentialGroup().addContainerGap()
                                .addComponent(jLabel15)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(projectNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 29,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel8)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(PainelProjetoConfigLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(pathListaExpressText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(btnListExpress, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(PainelProjetoConfigLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(pathAsapFileText, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(btnASAP, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap(88, Short.MAX_VALUE)));

        PainelProjetoConfigLayout.linkSize(javax.swing.SwingConstants.VERTICAL,
                new java.awt.Component[] { pathAsapFileText, pathListaExpressText, projectNameText });

        PainelDestino.setBorder(javax.swing.BorderFactory.createTitledBorder("Destino:"));

        jLabel7.setText("Caminho: (Pasta)");

        outputText.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        outputText.setToolTipText("Local de saída do processamento");
        outputText.setNextFocusableComponent(btnDestino);
        outputText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                outputTextActionPerformed(evt);
            }
        });

        btnDestino.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnDestino.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnDestino.setNextFocusableComponent(btnSaveProject);
        btnDestino.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDestinoActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout PainelDestinoLayout = new javax.swing.GroupLayout(PainelDestino);
        PainelDestino.setLayout(PainelDestinoLayout);
        PainelDestinoLayout.setHorizontalGroup(PainelDestinoLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(PainelDestinoLayout.createSequentialGroup().addContainerGap().addGroup(PainelDestinoLayout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(jLabel7)
                        .addGroup(PainelDestinoLayout.createSequentialGroup()
                                .addComponent(outputText, javax.swing.GroupLayout.PREFERRED_SIZE, 517,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18).addComponent(btnDestino, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        PainelDestinoLayout.setVerticalGroup(PainelDestinoLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(PainelDestinoLayout.createSequentialGroup().addContainerGap().addComponent(jLabel7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PainelDestinoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(outputText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(btnDestino, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        PainelMemoria.setBorder(javax.swing.BorderFactory.createTitledBorder("Parâmetro xmx: (Megabytes)"));

        xmxParameterTxt.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        xmxParameterTxt.setText("32768");
        xmxParameterTxt.setNextFocusableComponent(inputText);

        javax.swing.GroupLayout PainelMemoriaLayout = new javax.swing.GroupLayout(PainelMemoria);
        PainelMemoria.setLayout(PainelMemoriaLayout);
        PainelMemoriaLayout
                .setHorizontalGroup(PainelMemoriaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(PainelMemoriaLayout.createSequentialGroup().addContainerGap()
                                .addComponent(xmxParameterTxt, javax.swing.GroupLayout.PREFERRED_SIZE, 118,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        PainelMemoriaLayout
                .setVerticalGroup(PainelMemoriaLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(PainelMemoriaLayout.createSequentialGroup().addContainerGap()
                                .addComponent(xmxParameterTxt, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        PainelPerfil.setBorder(javax.swing.BorderFactory.createTitledBorder("Perfis:"));

        jcProfiles.setModel(new javax.swing.DefaultComboBoxModel<>(
                new String[] { "Default", "Fastmode", "Forensic", "Pedo", "Blind", "Triage" }));
        jcProfiles.setToolTipText("");
        jcProfiles.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jcProfiles.setNextFocusableComponent(xmxParameterTxt);
        jcProfiles.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jcProfilesItemStateChanged(evt);
            }
        });
        jcProfiles.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jcProfilesFocusLost(evt);
            }
        });
        jcProfiles.addInputMethodListener(new java.awt.event.InputMethodListener() {
            public void caretPositionChanged(java.awt.event.InputMethodEvent evt) {
            }

            public void inputMethodTextChanged(java.awt.event.InputMethodEvent evt) {
                jcProfilesInputMethodTextChanged(evt);
            }
        });
        jcProfiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jcProfilesActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout PainelPerfilLayout = new javax.swing.GroupLayout(PainelPerfil);
        PainelPerfil.setLayout(PainelPerfilLayout);
        PainelPerfilLayout
                .setHorizontalGroup(PainelPerfilLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(PainelPerfilLayout.createSequentialGroup().addContainerGap()
                                .addComponent(jcProfiles, javax.swing.GroupLayout.PREFERRED_SIZE, 195,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        PainelPerfilLayout
                .setVerticalGroup(PainelPerfilLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(PainelPerfilLayout.createSequentialGroup().addGap(10, 10, 10)
                                .addComponent(jcProfiles, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(25, Short.MAX_VALUE)));

        PainelOrigem.setBorder(javax.swing.BorderFactory.createTitledBorder("Origem:"));
        PainelOrigem.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        inputText.setFont(new java.awt.Font("Tahoma", 0, 14)); // NOI18N
        inputText.setToolTipText("Caminho da imagem ou pasta a processar.");
        inputText.setNextFocusableComponent(btnOrigem);
        inputText.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                inputTextPropertyChange(evt);
            }
        });
        inputText.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                inputTextKeyPressed(evt);
            }
        });

        btnAddInput.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/install-icon.png"))); // NOI18N
        btnAddInput.setToolTipText(
                "Preencha o campo \"Caminho\" (imagem/pasta) ou clique no botão ao lado para localizá-lo e depois clique aqui adicioná-lo como uma fonte de dados.");
        btnAddInput.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnAddInput.setNextFocusableComponent(outputText);
        btnAddInput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddInputActionPerformed(evt);
            }
        });

        btnRemInput.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/remove-icon.png"))); // NOI18N
        btnRemInput.setToolTipText("Escolha um ou mais itens na lista abaixo e clique aqui para removê-lo.");
        btnRemInput.setEnabled(false);
        btnRemInput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemInputActionPerformed(evt);
            }
        });

        inputsJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                inputsJListValueChanged(evt);
            }
        });
        jScrollPane2.setViewportView(inputsJList);

        jLabel6.setText("Caminho: (Imagem / Pasta)");

        btnOrigem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnOrigem.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnOrigem.setNextFocusableComponent(DNameCheckBox);
        btnOrigem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOrigemActionPerformed(evt);
            }
        });

        jLabel14.setText("Lista de Diretórios ou Imagens");

        DNameLabel.setText("Nome para a imagem: (opcional)");
        DNameLabel.setEnabled(false);

        imageNameText.setToolTipText("Parâmetro: -dname");
        imageNameText.setEnabled(false);
        imageNameText.setNextFocusableComponent(LogAlternativoCheckBox);

        nameLogFileText.setEnabled(false);
        nameLogFileText.setNextFocusableComponent(btnAddInput);

        LogFileLabel.setText("Nome do Arquivo Log (sem extensão)");
        LogFileLabel.setToolTipText("");
        LogFileLabel.setEnabled(false);

        LogAlternativoCheckBox.setToolTipText("Ativar/Desativar");
        LogAlternativoCheckBox.setNextFocusableComponent(nameLogFileText);
        LogAlternativoCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                LogAlternativoCheckBoxStateChanged(evt);
            }
        });
        LogAlternativoCheckBox.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                LogAlternativoCheckBoxMouseClicked(evt);
            }
        });
        LogAlternativoCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LogAlternativoCheckBoxActionPerformed(evt);
            }
        });

        DNameCheckBox.setToolTipText("Ativar/Desativar");
        DNameCheckBox.setNextFocusableComponent(imageNameText);
        DNameCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                DNameCheckBoxStateChanged(evt);
            }
        });
        DNameCheckBox.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                DNameCheckBoxMouseClicked(evt);
            }
        });
        DNameCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DNameCheckBoxActionPerformed(evt);
            }
        });

        FAppendCheckBox.setText("Append");

        PortableCheckBox.setLabel("Portable");

        javax.swing.GroupLayout PainelOrigemLayout = new javax.swing.GroupLayout(PainelOrigem);
        PainelOrigem.setLayout(PainelOrigemLayout);
        PainelOrigemLayout.setHorizontalGroup(PainelOrigemLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(PainelOrigemLayout.createSequentialGroup().addContainerGap().addGroup(PainelOrigemLayout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 602, Short.MAX_VALUE)
                        .addComponent(jLabel14)
                        .addGroup(PainelOrigemLayout.createSequentialGroup()
                                .addComponent(btnAddInput, javax.swing.GroupLayout.PREFERRED_SIZE, 50,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(btnRemInput, javax.swing.GroupLayout.PREFERRED_SIZE, 50,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(jLabel6)
                        .addGroup(PainelOrigemLayout.createSequentialGroup().addGroup(PainelOrigemLayout
                                .createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addGroup(PainelOrigemLayout.createSequentialGroup().addComponent(DNameCheckBox)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(DNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addComponent(imageNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 223,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(PainelOrigemLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
                                                PainelOrigemLayout.createSequentialGroup()
                                                        .addGap(18, 18, Short.MAX_VALUE).addComponent(nameLogFileText,
                                                                javax.swing.GroupLayout.PREFERRED_SIZE, 236,
                                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                        .addGroup(PainelOrigemLayout.createSequentialGroup().addGap(12, 12, 12)
                                                .addComponent(LogAlternativoCheckBox)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(LogFileLabel, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                        .addGroup(PainelOrigemLayout.createSequentialGroup().addComponent(FAppendCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(PortableCheckBox))
                        .addGroup(PainelOrigemLayout.createSequentialGroup()
                                .addComponent(inputText, javax.swing.GroupLayout.PREFERRED_SIZE, 517,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18).addComponent(btnOrigem, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap()));
        PainelOrigemLayout.setVerticalGroup(PainelOrigemLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(PainelOrigemLayout.createSequentialGroup().addGap(6, 6, 6).addComponent(jLabel6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PainelOrigemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(PainelOrigemLayout.createSequentialGroup()
                                        .addComponent(inputText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGroup(PainelOrigemLayout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, PainelOrigemLayout
                                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(DNameLabel,
                                                                javax.swing.GroupLayout.Alignment.TRAILING)
                                                        .addComponent(DNameCheckBox))
                                                .addComponent(LogAlternativoCheckBox,
                                                        javax.swing.GroupLayout.Alignment.TRAILING)))
                                .addGroup(PainelOrigemLayout.createSequentialGroup()
                                        .addComponent(btnOrigem, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(LogFileLabel)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PainelOrigemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(imageNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(nameLogFileText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(PainelOrigemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(btnAddInput).addComponent(btnRemInput))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(jLabel14)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 123,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PainelOrigemLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(FAppendCheckBox).addComponent(PortableCheckBox))));

        btnSaveProject.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/disk.png"))); // NOI18N
        btnSaveProject.setText("  Salvar Projeto");
        btnSaveProject.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnSaveProject.setNextFocusableComponent(btnIniciarProcesso);
        btnSaveProject.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveProjectActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel11Layout.createSequentialGroup().addContainerGap()
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(PainelProjetoConfig, javax.swing.GroupLayout.PREFERRED_SIZE, 449,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(PainelPerfil, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(PainelMemoria, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(PainelOrigem, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(PainelDestino, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap())
                .addGroup(jPanel11Layout.createSequentialGroup().addGap(418, 418, 418)
                        .addComponent(btnSaveProject, javax.swing.GroupLayout.PREFERRED_SIZE, 225,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        jPanel11Layout.setVerticalGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel11Layout.createSequentialGroup().addContainerGap().addGroup(jPanel11Layout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addGroup(jPanel11Layout.createSequentialGroup()
                                .addComponent(PainelProjetoConfig, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(PainelPerfil, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(PainelOrigem, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(PainelMemoria, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(PainelDestino, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(38, 38, 38).addComponent(btnSaveProject, javax.swing.GroupLayout.PREFERRED_SIZE, 50,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(102, 102, 102)));

        PainelDestino.getAccessibleContext().setAccessibleName("Destino:");
        PainelOrigem.getAccessibleContext().setAccessibleDescription("");

        javax.swing.GroupLayout jpProjetoLayout = new javax.swing.GroupLayout(jpProjeto);
        jpProjeto.setLayout(jpProjetoLayout);
        jpProjetoLayout.setHorizontalGroup(
                jpProjetoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(jPanel11,
                        javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE,
                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE));
        jpProjetoLayout.setVerticalGroup(jpProjetoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jpProjetoLayout.createSequentialGroup().addComponent(jPanel11,
                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap()));

        jtpPrincipal.addTab("Projeto", jpProjeto);

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder("Perfil Personalizado:"));

        jpOutrasConfiguracoes.setBorder(javax.swing.BorderFactory.createTitledBorder("Algorítmos Disponíveis"));

        jlHash.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        jScrollPane1.setViewportView(jlHash);

        jLabel12.setText("Hash(s) Configurado(s)");

        hashText.setEditable(false);
        hashText.setToolTipText(
                "Habilita cálculo de hash dos arquivos.  Para calcular vários hashes utilize ; como separador Valores possíveis: md5, sha-1, sha-256, sha-512 e edonkey");
        hashText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hashTextActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jpOutrasConfiguracoesLayout = new javax.swing.GroupLayout(jpOutrasConfiguracoes);
        jpOutrasConfiguracoes.setLayout(jpOutrasConfiguracoesLayout);
        jpOutrasConfiguracoesLayout.setHorizontalGroup(
                jpOutrasConfiguracoesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jpOutrasConfiguracoesLayout.createSequentialGroup().addContainerGap()
                                .addGroup(jpOutrasConfiguracoesLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(hashText, javax.swing.GroupLayout.PREFERRED_SIZE, 300,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel12).addComponent(jScrollPane1,
                                                javax.swing.GroupLayout.PREFERRED_SIZE, 99,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        jpOutrasConfiguracoesLayout.setVerticalGroup(jpOutrasConfiguracoesLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jpOutrasConfiguracoesLayout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 115,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(jLabel12)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hashText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        jpParametros.setBorder(javax.swing.BorderFactory.createTitledBorder("Ativar / Desativar"));

        excludeKffIgnorableCheckBox.setText("excludeKffIgnorable");
        excludeKffIgnorableCheckBox.setToolTipText(
                "Exclui do restante do processamento e do caso arquivos ignoráveis conforme base de hashes.");

        enableKFFCarvingCheckBox.setText("KFFCarving");
        enableKFFCarvingCheckBox.setToolTipText(
                "Habilita carving que recupera arquivos conhecidos da base do LED baseado no início (64K) do arquivo. Necessário habilitar \"addUnallocated\" e configurar \"ledWkffPath\". ");

        exportFilePropsCheckBox.setText("exportFileProps");
        exportFilePropsCheckBox.setToolTipText(
                "Exporta as propriedades de todos os arquivos para o arquivo \"Lista de Arquivos.csv\"");

        enableOCRCheckBox.setText("OCR");
        enableOCRCheckBox.setToolTipText(
                "Habilita o OCR em imagens e PDFs digitalizados. Pode aumentar consideravelmente o tempo de processamento. Os resultados dependem da qualidade e resolução das imagens e do tamanho e tipo das fontes utilizadas. Opções avançadas do OCR, como o caminho do Tesseract no Linux, podem ser alteradas em conf/AdvancedConfig.txt");
        enableOCRCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableOCRCheckBoxActionPerformed(evt);
            }
        });

        ignoreDuplicatesCheckBox.setText("ignoreDuplicates");
        ignoreDuplicatesCheckBox.setToolTipText(
                "Ignora/descarta arquivos duplicados do restante do processamento e do caso.\nPode agilizar bastante o processamento de casos com backups. Necessita habilitar o cálculo de hash.");

        enableCarvingCheckBox.setText("Carving");
        enableCarvingCheckBox.setToolTipText(
                "Habilita o carving. Necessário habilitar \"addUnallocated\" para vasculhar áreas não alocadas.\nPor padrão o carving é executado sobre quase todos os itens do caso. Os tipos de arquivo a vasculhar e a recuperar, podem ser configurados em conf/CarvingConfig.txt");

        enableFileParsingCheckBox.setText("FileParsing");
        enableFileParsingCheckBox.setToolTipText(
                "Habilita a tarefa de parsing do conteúdo dos arquivos. Necessário para diversas outras funções como expansão de conteineres, detecção de cifrados, indexação de metadados, geração de preview, etc.");
        enableFileParsingCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableFileParsingCheckBoxActionPerformed(evt);
            }
        });

        expandContainersCheckBox.setText("expandContainers");
        expandContainersCheckBox.setToolTipText(
                "Expande containers, como arquivos compactados, caixas de e-mails, emails e documentos office.\nOs tipos de arquivo a expandir podem ser alterados no arquivo conf/CategoriesToExpand.txt");

        enableImageThumbsCheckBox.setText("ImageThumbs");
        enableImageThumbsCheckBox.setToolTipText(
                "Habilita a geração de miniaturas das imagens durante o processamento.\nPode deixar o processamento bem mais lento, porém torna a visualização na galeria muito rápida.");

        enableVideoThumbsCheckBox.setText("VideoThumbs");
        enableVideoThumbsCheckBox.setToolTipText(
                "Habilita extração de imagens de cenas de vídeos.\nOs parâmetros de extração podem ser alterados no arquivo conf/VideoThumbsConfig.txt");

        enableHTMLReportCheckBox.setText("HTMLReport");
        enableHTMLReportCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableHTMLReportCheckBoxActionPerformed(evt);
            }
        });

        addUnallocatedCheckBox.setText("addUnallocated");
        addUnallocatedCheckBox.setToolTipText("Adiciona e processa áreas não alocadas de imagens via sleuthkit");

        indexUnknownFilesCheckBox.setText("indexUnknownFiles");
        indexUnknownFilesCheckBox.setToolTipText(
                "Indexa arquivos sem decodificador específico, como binários, desconhecidos, pagefile, não alocado, etc. Nesse caso são indexadas strings brutas em claro extraídas dos arquivos.");

        indexFileContentsCheckBox.setText("indexFileContents");
        indexFileContentsCheckBox
                .setToolTipText("Indexa o conteúdo dos arquivos. Caso desabilitado, indexa apenas as propriedades.");

        addFileSlacksCheckBox.setText("addFileSlacks");

        enableKnownMetCarvingCheckBox.setText("KnownMetCarving");
        enableKnownMetCarvingCheckBox.setToolTipText(
                "Habilita carving específico de arquivos known.met do e-Mule. Necessário habilitar \"addUnallocated\". ");

        indexUnallocatedCheckBox.setText("indexUnallocated");
        indexUnallocatedCheckBox.setToolTipText(
                "Indexa o espaço não alocado adicionado. Necessário habilitar \"addUnallocated\" e \"indexUnknownFiles\".");

        processFileSignaturesCheckBox.setText("processFileSignatures");
        processFileSignaturesCheckBox.setToolTipText("Processa a assinatura dos arquivos");

        enableRegexSearchCheckBox.setText("RegexSearch");

        enableLanguageDetectCheckBox.setText("LanguageDetect");

        enableNamedEntityRecognitonCheckBox.setText("NamedEntityRecogniton");

        indexCorruptedFilesCheckBox.setLabel("indexCorruptedFiles");

        javax.swing.GroupLayout jpParametrosLayout = new javax.swing.GroupLayout(jpParametros);
        jpParametros.setLayout(jpParametrosLayout);
        jpParametrosLayout.setHorizontalGroup(jpParametrosLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jpParametrosLayout.createSequentialGroup().addContainerGap()
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(enableRegexSearchCheckBox).addComponent(excludeKffIgnorableCheckBox)
                                .addComponent(ignoreDuplicatesCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(exportFilePropsCheckBox).addComponent(processFileSignaturesCheckBox)
                                .addComponent(enableFileParsingCheckBox).addComponent(expandContainersCheckBox,
                                        javax.swing.GroupLayout.PREFERRED_SIZE, 131,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(enableLanguageDetectCheckBox)
                                .addComponent(enableNamedEntityRecognitonCheckBox)
                                .addComponent(indexFileContentsCheckBox).addComponent(indexUnknownFilesCheckBox)
                                .addComponent(indexCorruptedFilesCheckBox).addComponent(enableOCRCheckBox)
                                .addComponent(addFileSlacksCheckBox))
                        .addGap(16, 16, 16)
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(
                                        jpParametrosLayout.createSequentialGroup().addComponent(addUnallocatedCheckBox)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 17,
                                                        Short.MAX_VALUE)
                                                .addComponent(enableHTMLReportCheckBox).addGap(20, 20, 20))
                                .addGroup(jpParametrosLayout.createSequentialGroup().addGroup(jpParametrosLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(enableCarvingCheckBox).addComponent(indexUnallocatedCheckBox)
                                        .addComponent(enableKFFCarvingCheckBox)
                                        .addComponent(enableKnownMetCarvingCheckBox)
                                        .addComponent(enableImageThumbsCheckBox)
                                        .addComponent(enableVideoThumbsCheckBox))
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))));

        jpParametrosLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL,
                new java.awt.Component[] { enableCarvingCheckBox, enableHTMLReportCheckBox, enableImageThumbsCheckBox,
                        enableKFFCarvingCheckBox, enableOCRCheckBox, enableVideoThumbsCheckBox });

        jpParametrosLayout.setVerticalGroup(jpParametrosLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jpParametrosLayout.createSequentialGroup()
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(excludeKffIgnorableCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(enableLanguageDetectCheckBox)
                                .addComponent(addUnallocatedCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(enableHTMLReportCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(ignoreDuplicatesCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(enableNamedEntityRecognitonCheckBox)
                                .addComponent(indexUnallocatedCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(exportFilePropsCheckBox).addComponent(indexFileContentsCheckBox)
                                .addComponent(enableCarvingCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(enableKFFCarvingCheckBox)
                                .addGroup(jpParametrosLayout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(processFileSignaturesCheckBox,
                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(indexUnknownFilesCheckBox)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(enableFileParsingCheckBox).addComponent(indexCorruptedFilesCheckBox)
                                .addComponent(enableKnownMetCarvingCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(expandContainersCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(enableOCRCheckBox).addComponent(enableImageThumbsCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jpParametrosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(enableRegexSearchCheckBox)
                                .addComponent(addFileSlacksCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(enableVideoThumbsCheckBox))
                        .addGap(21, 21, 21)));

        jpParametrosLayout.linkSize(javax.swing.SwingConstants.VERTICAL,
                new java.awt.Component[] { enableCarvingCheckBox, enableHTMLReportCheckBox, enableImageThumbsCheckBox,
                        enableKFFCarvingCheckBox, enableOCRCheckBox, enableVideoThumbsCheckBox });

        btnSaveProfile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/disk.png"))); // NOI18N
        btnSaveProfile.setText("  Salvar Perfil Personalizado");
        btnSaveProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveProfileActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel5Layout.createSequentialGroup().addGroup(jPanel5Layout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel5Layout.createSequentialGroup().addContainerGap()
                                .addComponent(jpParametros, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jpOutrasConfiguracoes, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(jPanel5Layout.createSequentialGroup().addGap(406, 406, 406).addComponent(
                                btnSaveProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 225,
                                javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addContainerGap(158, Short.MAX_VALUE)));
        jPanel5Layout.setVerticalGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel5Layout.createSequentialGroup().addContainerGap()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(jpOutrasConfiguracoes, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jpParametros, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 330, Short.MAX_VALUE)
                        .addComponent(btnSaveProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 50,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(22, 22, 22)));

        javax.swing.GroupLayout jpIPEDConfigLayout = new javax.swing.GroupLayout(jpIPEDConfig);
        jpIPEDConfig.setLayout(jpIPEDConfigLayout);
        jpIPEDConfigLayout
                .setHorizontalGroup(jpIPEDConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jpIPEDConfigLayout.createSequentialGroup().addContainerGap()
                                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(16, Short.MAX_VALUE)));
        jpIPEDConfigLayout
                .setVerticalGroup(jpIPEDConfigLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
                                jpIPEDConfigLayout.createSequentialGroup().addContainerGap()
                                        .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addContainerGap()));

        jtpPrincipal.addTab("Perfil Personalizado", jpIPEDConfig);

        jpFerramentasExternas.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        jPanel7.setBorder(javax.swing.BorderFactory.createTitledBorder("Ferramentas Externas"));

        enableKffCheckBox.setText("Caminho para base de hashes (KFF) do IPED ( E:/kff/kff.db )");
        enableKffCheckBox.setToolTipText("Ativar/Desativar");
        enableKffCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableKffCheckBoxActionPerformed(evt);
            }
        });

        kffDbText.setBackground(new java.awt.Color(240, 240, 240));
        kffDbText.setToolTipText(
                "Desative caso não queira confrontar hashes na base.\nÉ altamente recomendado armazenar a base num disco SSD sob pena de impactar o tempo de processamento. Pode-se importar base NSRL via opção -importkff.");
        kffDbText.setEnabled(false);
        kffDbText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                kffDbTextActionPerformed(evt);
            }
        });

        btnKffPath.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnKffPath.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnKffPath.setEnabled(false);
        btnKffPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnKffPathActionPerformed(evt);
            }
        });

        enableLedWkffCheckBox.setText("Diretório contendo a base de hashes de alerta de pornografia infantil do LED.");
        enableLedWkffCheckBox.setToolTipText("Ativar/Desativar");
        enableLedWkffCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableLedWkffCheckBoxActionPerformed(evt);
            }
        });

        ledWkffPathText.setBackground(new java.awt.Color(240, 240, 240));
        ledWkffPathText.setToolTipText(
                "Caso ativado, os arquivos com hashes existentes na base são adicionados a uma categoria \"Hash com Alerta (PI)\". Necessário utilizar hash md5 ou sha-1.");
        ledWkffPathText.setEnabled(false);
        ledWkffPathText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ledWkffPathTextActionPerformed(evt);
            }
        });

        btnHashLEDPath.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnHashLEDPath.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnHashLEDPath.setEnabled(false);
        btnHashLEDPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnHashLEDPathActionPerformed(evt);
            }
        });

        enableLedDieCheckBox.setText(
                "Arquivo de dados utilizado pelo DIE (Detecção de Imagens Explícitas). ( E:/LED/V1.21.00/pedo/die/rfdie.dat )");
        enableLedDieCheckBox.setToolTipText("Ativar/Desativar");
        enableLedDieCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableLedDieCheckBoxActionPerformed(evt);
            }
        });

        ledDieText.setBackground(new java.awt.Color(240, 240, 240));
        ledDieText.setToolTipText(
                "Caso ativado, para cada imagem cria os atributos \"scoreNudez\" (1 a 1000) e classeNudez (1 a 5) para ordenação e/ou filtro. Necessário apontar para arquivo de dados presente no LED versão 1.21 ou superior.");
        ledDieText.setEnabled(false);
        ledDieText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ledDieTextActionPerformed(evt);
            }
        });

        btnDiePath.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnDiePath.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnDiePath.setEnabled(false);
        btnDiePath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDiePathActionPerformed(evt);
            }
        });

        tskJarPathText.setBackground(new java.awt.Color(240, 240, 240));
        tskJarPathText.setToolTipText(
                "É distribuída versão com patch para Windows com diversas correções de bugs e otimizações. No caso de Linux, recomenda-se compilar o código-fonte com patch (pasta sources)");
        tskJarPathText.setEnabled(false);
        tskJarPathText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tskJarPathTextActionPerformed(evt);
            }
        });

        btnTskJarPath.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnTskJarPath.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnTskJarPath.setEnabled(false);
        btnTskJarPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTskJarPathActionPerformed(evt);
            }
        });

        enableTskJarPathCheckBox.setText("Caminho absoluto obrigatório para o Tsk_DataModel.jar");
        enableTskJarPathCheckBox.setToolTipText("Ativar/Desativar");
        enableTskJarPathCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableTskJarPathCheckBoxActionPerformed(evt);
            }
        });

        jLabel4.setText("( /home/user/tsk-4.3-src-folder/bindings/java/dist/Tsk_DataModel.jar )");

        jLabel13.setText("( E:/LED/V1.21.00/pedo/wkff )");

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel7Layout.createSequentialGroup().addContainerGap()
                        .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(jPanel7Layout.createSequentialGroup()
                                        .addComponent(kffDbText, javax.swing.GroupLayout.PREFERRED_SIZE, 395,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnKffPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(enableLedWkffCheckBox).addComponent(enableKffCheckBox)
                                .addGroup(jPanel7Layout.createSequentialGroup().addGroup(jPanel7Layout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(ledWkffPathText, javax.swing.GroupLayout.PREFERRED_SIZE, 395,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel13))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnHashLEDPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 75, Short.MAX_VALUE)
                        .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(jPanel7Layout.createSequentialGroup()
                                        .addComponent(ledDieText, javax.swing.GroupLayout.PREFERRED_SIZE, 395,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnDiePath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(jPanel7Layout.createSequentialGroup()
                                        .addComponent(tskJarPathText, javax.swing.GroupLayout.PREFERRED_SIZE, 395,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(btnTskJarPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(enableTskJarPathCheckBox)
                                .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 348,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(enableLedDieCheckBox))
                        .addGap(28, 28, 28)));
        jPanel7Layout.setVerticalGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel7Layout.createSequentialGroup().addContainerGap().addGroup(jPanel7Layout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel7Layout.createSequentialGroup().addGap(5, 5, 5)
                                .addComponent(enableLedDieCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(ledDieText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(btnDiePath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGroup(jPanel7Layout.createSequentialGroup().addComponent(enableKffCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(kffDbText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(btnKffPath, javax.swing.GroupLayout.Alignment.TRAILING,
                                javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addGroup(jPanel7Layout.createSequentialGroup().addGroup(jPanel7Layout
                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(enableLedWkffCheckBox, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(enableTskJarPathCheckBox))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addGroup(jPanel7Layout
                                                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                .addComponent(ledWkffPathText, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        30, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(tskJarPathText, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                        30, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addComponent(btnTskJarPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                        javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addComponent(btnHashLEDPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(jLabel4).addComponent(jLabel13))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        jPanel7Layout.linkSize(javax.swing.SwingConstants.VERTICAL,
                new java.awt.Component[] { enableLedDieCheckBox, enableLedWkffCheckBox });

        jPanel7Layout.linkSize(javax.swing.SwingConstants.VERTICAL,
                new java.awt.Component[] { btnHashLEDPath, btnKffPath });

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Ambiente"));

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Diretório temporário de indexação:"));

        indexTempText.setToolTipText(
                "Diretório temporário de indexação: \"default\" utiliza o diretório temporário padrão do sistema.Configurar indexTemp num disco livre de antivirus, principalmente num SSD, pode aumentar consideravelmente o desempenho.");
        indexTempText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                indexTempTextActionPerformed(evt);
            }
        });

        btnDirTemp.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnDirTemp.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnDirTemp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDirTempActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel3Layout.createSequentialGroup().addContainerGap()
                        .addComponent(indexTempText, javax.swing.GroupLayout.PREFERRED_SIZE, 282,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDirTemp, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        jPanel3Layout.setVerticalGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel3Layout.createSequentialGroup().addContainerGap()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(btnDirTemp, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(indexTempText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Número de threads de processamento:"));
        jPanel4.setPreferredSize(new java.awt.Dimension(260, 75));

        numThreadsText.setText("default");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel4Layout
                        .createSequentialGroup().addContainerGap().addComponent(numThreadsText,
                                javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(61, Short.MAX_VALUE)));
        jPanel4Layout.setVerticalGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel4Layout.createSequentialGroup().addContainerGap()
                        .addComponent(numThreadsText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Parâmetros"));

        indexTempOnSSDCheckBox.setText("indexTempOnSSD");
        indexTempOnSSDCheckBox.setToolTipText(
                "<html>Habilite caso indexTemp esteja em disco SSD. </br>Nesse caso, são feitas otimizações que podem reduzir\no tempo de processamento em até 60%: o número de merge threads do índice é aumentado e \nsão utilizados arquivos temporários para evitar múltiplas leituras/descompactações dos itens.</html>");

        outputOnSSDCheckBox.setText("outputOnSSD");
        outputOnSSDCheckBox.setToolTipText(
                "Habilite caso a pasta de saída -o esteja em disco SSD. Só tem efeito em processamentos com a opção --append.");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel2Layout.createSequentialGroup().addContainerGap().addComponent(indexTempOnSSDCheckBox)
                        .addGap(18, 18, 18).addComponent(outputOnSSDCheckBox).addContainerGap(22, Short.MAX_VALUE)));
        jPanel2Layout.setVerticalGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel2Layout.createSequentialGroup().addContainerGap()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(indexTempOnSSDCheckBox).addComponent(outputOnSSDCheckBox))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        jPanel8.setBorder(javax.swing.BorderFactory.createTitledBorder("Localização: (Idioma)"));

        jcLocale.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "pt-BR", "en" }));

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel8Layout
                        .createSequentialGroup().addContainerGap().addComponent(jcLocale,
                                javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(68, Short.MAX_VALUE)));
        jPanel8Layout.setVerticalGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel8Layout.createSequentialGroup().addContainerGap()
                        .addComponent(jcLocale, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel6Layout.createSequentialGroup().addContainerGap()
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, 219,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap()));
        jPanel6Layout.setVerticalGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel6Layout.createSequentialGroup().addContainerGap()
                        .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));

        jPanel8.getAccessibleContext().setAccessibleName("");

        jPanel9.setBorder(javax.swing.BorderFactory.createTitledBorder("Ferramentas Opcionais"));

        enableMplayerPathCheckBox.setText("Caminho para mplayer em sistemas Windows.");
        enableMplayerPathCheckBox.setToolTipText("Ativar/Desativar");
        enableMplayerPathCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableMplayerPathCheckBoxActionPerformed(evt);
            }
        });

        mplayerPathText.setBackground(new java.awt.Color(240, 240, 240));
        mplayerPathText.setToolTipText(
                "É distribuída versão com patch para Windows com diversas correções de bugs e otimizações. No caso de Linux, recomenda-se compilar o código-fonte com patch (pasta sources)");
        mplayerPathText.setEnabled(false);
        mplayerPathText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mplayerPathTextActionPerformed(evt);
            }
        });

        btnMplayerPath.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnMplayerPath.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnMplayerPath.setEnabled(false);
        btnMplayerPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMplayerPathActionPerformed(evt);
            }
        });

        enableOptionalJarsPathCheckBox.setText("Pasta contendo bibliotecas java opcionais");
        enableOptionalJarsPathCheckBox.setToolTipText("Ativar/Desativar");
        enableOptionalJarsPathCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableOptionalJarsPathCheckBoxActionPerformed(evt);
            }
        });

        optionalJarsPathText.setBackground(new java.awt.Color(240, 240, 240));
        optionalJarsPathText.setToolTipText(
                "É distribuída versão com patch para Windows com diversas correções de bugs e otimizações. No caso de Linux, recomenda-se compilar o código-fonte com patch (pasta sources)");
        optionalJarsPathText.setEnabled(false);
        optionalJarsPathText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                optionalJarsPathTextActionPerformed(evt);
            }
        });

        btnOptionalJarsPath.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnOptionalJarsPath.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnOptionalJarsPath.setEnabled(false);
        btnOptionalJarsPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnOptionalJarsPathActionPerformed(evt);
            }
        });

        enableRegripperFolderPathCheckBox.setText("Caminho para pasta do RegRipper");
        enableRegripperFolderPathCheckBox.setToolTipText("Ativar/Desativar");
        enableRegripperFolderPathCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableRegripperFolderPathCheckBoxActionPerformed(evt);
            }
        });

        regripperFolderPathText.setBackground(new java.awt.Color(240, 240, 240));
        regripperFolderPathText.setToolTipText(
                "É distribuída versão com patch para Windows com diversas correções de bugs e otimizações. No caso de Linux, recomenda-se compilar o código-fonte com patch (pasta sources)");
        regripperFolderPathText.setEnabled(false);
        regripperFolderPathText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                regripperFolderPathTextActionPerformed(evt);
            }
        });

        btnRegripperFolderPath.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/folder_explore.png"))); // NOI18N
        btnRegripperFolderPath.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnRegripperFolderPath.setEnabled(false);
        btnRegripperFolderPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRegripperFolderPathActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel9Layout.createSequentialGroup().addContainerGap().addGroup(jPanel9Layout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(enableRegripperFolderPathCheckBox)
                        .addGroup(jPanel9Layout.createSequentialGroup().addGroup(jPanel9Layout
                                .createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(regripperFolderPathText, javax.swing.GroupLayout.DEFAULT_SIZE, 395,
                                        Short.MAX_VALUE)
                                .addComponent(enableOptionalJarsPathCheckBox, javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(enableMplayerPathCheckBox, javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(mplayerPathText, javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(optionalJarsPathText, javax.swing.GroupLayout.Alignment.LEADING))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(btnMplayerPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(btnOptionalJarsPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
                                                jPanel9Layout.createSequentialGroup()
                                                        .addComponent(btnRegripperFolderPath,
                                                                javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addGap(24, 24, 24)))))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        jPanel9Layout.setVerticalGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel9Layout.createSequentialGroup()
                        .addContainerGap(15, Short.MAX_VALUE).addComponent(enableMplayerPathCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addGroup(jPanel9Layout.createSequentialGroup()
                                        .addComponent(mplayerPathText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(enableOptionalJarsPathCheckBox)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(optionalJarsPathText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(jPanel9Layout.createSequentialGroup()
                                        .addComponent(btnMplayerPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(32, 32, 32).addComponent(btnOptionalJarsPath,
                                                javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(enableRegripperFolderPathCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(regripperFolderPathText, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(btnRegripperFolderPath, javax.swing.GroupLayout.PREFERRED_SIZE, 30,
                                        javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(22, 22, 22)));

        btnSalvar.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/disk.png"))); // NOI18N
        btnSalvar.setText("  Salvar Configurações Locais");
        btnSalvar.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnSalvar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSalvarActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jpFerramentasExternasLayout = new javax.swing.GroupLayout(jpFerramentasExternas);
        jpFerramentasExternas.setLayout(jpFerramentasExternasLayout);
        jpFerramentasExternasLayout.setHorizontalGroup(jpFerramentasExternasLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jpFerramentasExternasLayout.createSequentialGroup().addGap(416, 416, 416)
                        .addComponent(btnSalvar, javax.swing.GroupLayout.PREFERRED_SIZE, 225,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE)
                .addComponent(jPanel9, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE,
                        Short.MAX_VALUE));
        jpFerramentasExternasLayout.setVerticalGroup(jpFerramentasExternasLayout
                .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jpFerramentasExternasLayout.createSequentialGroup().addContainerGap()
                        .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 27, Short.MAX_VALUE)
                        .addComponent(btnSalvar, javax.swing.GroupLayout.PREFERRED_SIZE, 50,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(21, 21, 21)));

        jtpPrincipal.addTab("Configurações Locais", jpFerramentasExternas);

        jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jLabel9.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/LOGO_PF_Mono.png"))); // NOI18N

        jLabel10.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel10.setText("SETEC/GO");
        jLabel10.setToolTipText("");

        jLabel11.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        jLabel11.setText("GPINF");

        jLabel17.setForeground(new java.awt.Color(255, 0, 0));
        jLabel17.setText("FrontIPED: Versão: 1.8");

        jLabel1.setText("Desenvolvido para a versão:");

        jLabel2.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        jLabel2.setText("3.18.2");

        jLabel3.setText("do IPED");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup().addGap(0, 242, Short.MAX_VALUE).addGroup(jPanel1Layout
                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
                                jPanel1Layout.createSequentialGroup().addComponent(jLabel9).addGap(236, 236, 236))
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(jPanel1Layout.createSequentialGroup().addGap(53, 53, 53)
                                                .addGroup(jPanel1Layout
                                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
                                                                jPanel1Layout.createSequentialGroup()
                                                                        .addComponent(jLabel10).addGap(19, 19, 19))
                                                        .addComponent(jLabel17,
                                                                javax.swing.GroupLayout.Alignment.TRAILING)
                                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING,
                                                                jPanel1Layout.createSequentialGroup()
                                                                        .addComponent(jLabel11).addGap(33, 33, 33))))
                                        .addGroup(jPanel1Layout.createSequentialGroup().addComponent(jLabel1)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jLabel2)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jLabel3)))
                                .addGap(224, 224, 224)))));
        jPanel1Layout.setVerticalGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanel1Layout.createSequentialGroup().addGap(72, 72, 72).addComponent(jLabel9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED).addComponent(jLabel11)
                        .addGap(1, 1, 1)
                        .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 14,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED).addComponent(jLabel17)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(jLabel1).addComponent(jLabel2).addComponent(jLabel3))
                        .addContainerGap(26, Short.MAX_VALUE)));

        javax.swing.GroupLayout jpSobreLayout = new javax.swing.GroupLayout(jpSobre);
        jpSobre.setLayout(jpSobreLayout);
        jpSobreLayout.setHorizontalGroup(jpSobreLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jpSobreLayout.createSequentialGroup().addGap(189, 189, 189)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(228, Short.MAX_VALUE)));
        jpSobreLayout.setVerticalGroup(jpSobreLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jpSobreLayout.createSequentialGroup().addGap(84, 84, 84)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(137, Short.MAX_VALUE)));

        jtpPrincipal.addTab("Sobre", jpSobre);

        btnIniciarProcesso.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/lightning.png"))); // NOI18N
        btnIniciarProcesso.setText("  Iniciar Processamento");
        btnIniciarProcesso.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        btnIniciarProcesso.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnIniciarProcessoActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addComponent(jtpPrincipal)
                        .addGroup(layout.createSequentialGroup().addGap(425, 425, 425)
                                .addComponent(btnIniciarProcesso, javax.swing.GroupLayout.PREFERRED_SIZE, 225,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)));
        layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addComponent(jtpPrincipal, javax.swing.GroupLayout.PREFERRED_SIZE, 691,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(36, 36, 36).addComponent(btnIniciarProcesso, javax.swing.GroupLayout.PREFERRED_SIZE, 50,
                                javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 16, Short.MAX_VALUE)));

        jtpPrincipal.getAccessibleContext().setAccessibleName("Projeto");

        pack();
        // setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnSalvarActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnSalvarActionPerformed

        if ((enableKffCheckBox.isSelected() && kffDbText.getText().isEmpty())
                || (enableLedWkffCheckBox.isSelected() && ledWkffPathText.getText().isEmpty())
                || (enableLedDieCheckBox.isSelected() && ledDieText.getText().isEmpty())
                || (enableTskJarPathCheckBox.isSelected() && tskJarPathText.getText().isEmpty())) {
            JOptionPane.showMessageDialog(null,
                    "Se algum CheckBox do Grupo \"Ferramentas Externas\" estiver marcardo será necessário informar o seu caminho!");
        }
        /*
         * else if (jlHash.getSelectedValuesList().isEmpty()) {
         * JOptionPane.showMessageDialog(null,
         * "Pelo menos uma opção de Hash deve ser selecionada!"); }
         */
        else {

            Properties propsLocalConfig = new Properties();

            propsLocalConfig.setProperty("locale", this.jcLocale.getSelectedItem().toString());

            // salva valores dos checkboxes das ferramentas externas no arquivo
            // LocalConfig.txt
            propsLocalConfig.setProperty("enableKff", Boolean.toString(this.enableKffCheckBox.isSelected()));
            propsLocalConfig.setProperty("enableLedWkff", Boolean.toString(this.enableLedWkffCheckBox.isSelected()));
            propsLocalConfig.setProperty("enableLedDie", Boolean.toString(this.enableLedDieCheckBox.isSelected()));

            if (indexTempText.getText().isEmpty()) {
                indexTempText.setText("default");
            }
            // pega valores dos TextFields
            propsLocalConfig.setProperty("indexTemp", this.indexTempText.getText());
            propsLocalConfig.setProperty("numThreads", this.numThreadsText.getText());

            if (enableKffCheckBox.isSelected())
                propsLocalConfig.setProperty("kffDb", this.kffDbText.getText());

            if (enableLedWkffCheckBox.isSelected())
                propsLocalConfig.setProperty("ledWkffPath", this.ledWkffPathText.getText());

            if (enableLedDieCheckBox.isSelected())
                propsLocalConfig.setProperty("ledDie", this.ledDieText.getText());

            if (enableTskJarPathCheckBox.isSelected())
                propsLocalConfig.setProperty("tskJarPath", this.tskJarPathText.getText());

            if (enableMplayerPathCheckBox.isSelected())
                propsLocalConfig.setProperty("mplayerPath", this.mplayerPathText.getText());

            if (enableOptionalJarsPathCheckBox.isSelected())
                propsLocalConfig.setProperty("optional_jars", this.optionalJarsPathText.getText());

            if (enableRegripperFolderPathCheckBox.isSelected())
                propsLocalConfig.setProperty("regripperFolder", this.regripperFolderPathText.getText());

            // pega valores dos checkboxes do arquivo LocalConfig.txt
            propsLocalConfig.setProperty("indexTempOnSSD", Boolean.toString(this.indexTempOnSSDCheckBox.isSelected()));
            propsLocalConfig.setProperty("outputOnSSD", Boolean.toString(this.outputOnSSDCheckBox.isSelected()));

            // JOptionPane.showMessageDialog(null, props.getProperty("indexTemp"));
            savePropertiesLocalConfig(propsLocalConfig);
            JOptionPane.showMessageDialog(null, "Configurações Locais Salvas!");
            this.carregaLocalConfig();
            // carregaConfiguracoes();
        }
    }// GEN-LAST:event_btnSalvarActionPerformed

    private void enableOCRCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableOCRCheckBoxActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_enableOCRCheckBoxActionPerformed

    private void btnIniciarProcessoActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnIniciarProcessoActionPerformed

        Runtime run = Runtime.getRuntime();
        Process pro = null;
        // String append = "";
        // String parametro;
        // String command = null;
        // Integer tam = inputsJList.getModel().getSize();
        File project = new File(this.ProjectName);
        if (project.exists()) {
            try {
                pro = run.exec("cmd.exe /c start /wait " + project.getAbsolutePath());

                try {
                    int waitFor = pro.waitFor();
                    btnIniciarProcesso.setText("Processando... Aguarde!");
                    JOptionPane.showMessageDialog(null,
                            "O IPED Finalizou Seu Trabalho! Sempre Verifique os Arquivos de Log de Cada Processamento!");
                } catch (InterruptedException ex) {
                    Logger.getLogger(FrontIPED.class.getName()).log(Level.SEVERE, null, ex);
                }
            } catch (IOException iOException) {
                iOException.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(null, "Salve seu projeto primeiro para poder executá-lo!");
        }
        btnIniciarProcesso.setText("Iniciar Processamento");
    }// GEN-LAST:event_btnIniciarProcessoActionPerformed

    private void outputTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_outputTextActionPerformed

    }// GEN-LAST:event_outputTextActionPerformed

    private void btnDestinoActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnDestinoActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setCurrentDirectory(this.chooser);
        abreJanela(outputText, fc);
    }// GEN-LAST:event_btnDestinoActionPerformed

    private void btnOrigemActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnOrigemActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setCurrentDirectory(this.chooser);
        abreJanela(inputText, fc);
        if (!this.chooser.isDirectory()) {
            this.chooser = fc.getSelectedFile().getParentFile();
        } else {
            this.chooser = fc.getSelectedFile();
        }
    }// GEN-LAST:event_btnOrigemActionPerformed

    private void btnDirTempActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnDirTempActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(indexTempText, fc);
    }// GEN-LAST:event_btnDirTempActionPerformed

    private void indexTempTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_indexTempTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_indexTempTextActionPerformed

    private void hashTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_hashTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_hashTextActionPerformed

    private void enableFileParsingCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableFileParsingCheckBoxActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_enableFileParsingCheckBoxActionPerformed

    private void btnAddInputActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnAddInputActionPerformed
        if (inputText.getText().isEmpty()) {
            inputText.requestFocus();
            JOptionPane.showMessageDialog(null,
                    "Informe o caminho para um arquivo de imagem ou uma pasta na seção Origem:");
        } else {
            if (!imageNameText.getText().isEmpty() && !nameLogFileText.getText().isEmpty()) {
                listmodel.addElement(
                        inputText.getText() + "@" + imageNameText.getText() + "@" + nameLogFileText.getText() + ".log");
            } else if (!imageNameText.getText().isEmpty()) {
                listmodel.addElement(inputText.getText() + "@" + imageNameText.getText());
            } else if (!nameLogFileText.getText().isEmpty()) {
                listmodel.addElement(inputText.getText() + "@" + nameLogFileText.getText() + ".log");
            } else {
                listmodel.addElement(inputText.getText());
            }
            inputText.setText("");
            imageNameText.setText("");
            nameLogFileText.setText("");
            inputText.requestFocus();
            btnRemInput.setEnabled(true);
            DNameCheckBox.setSelected(false);
            LogAlternativoCheckBox.setSelected(false);
            inputsJList.setModel(listmodel);
        }
    }// GEN-LAST:event_btnAddInputActionPerformed

    private void btnRemInputActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnRemInputActionPerformed
        int[] selecionados = inputsJList.getSelectedIndices();
        int size = selecionados.length - 1;
        if (size >= 0) {
            for (int i = size; i >= 0; i--) {
                listmodel.remove(selecionados[i]);
            }
        } else {
            JOptionPane.showMessageDialog(null,
                    "Selecione um ou mais itens na lista abaixo e clique no botão para removê-lo(s)!");
        }
        if (listmodel.isEmpty()) {
            btnRemInput.setEnabled(false);
        }
    }// GEN-LAST:event_btnRemInputActionPerformed

    private void inputTextKeyPressed(java.awt.event.KeyEvent evt) {// GEN-FIRST:event_inputTextKeyPressed

    }// GEN-LAST:event_inputTextKeyPressed

    private void inputTextPropertyChange(java.beans.PropertyChangeEvent evt) {// GEN-FIRST:event_inputTextPropertyChange

    }// GEN-LAST:event_inputTextPropertyChange

    private void inputsJListValueChanged(javax.swing.event.ListSelectionEvent evt) {// GEN-FIRST:event_inputsJListValueChanged

    }// GEN-LAST:event_inputsJListValueChanged

    private void btnSaveProjectActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnSaveProjectActionPerformed
        // java.lang.management.OperatingSystemMXBean mxbean =
        // (com.sun.management.OperatingSystemMXBean)
        // java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        String antes = "";
        String depois = "";
        String nome_item = "";
        String append = "";
        String log = "";
        String xmx = "";
        String expressList = "";
        String asapFile = "";
        String profile = "";
        String portable = "";
        String dname = "";
        Integer mem = 2048;
        String origem = null, destino;
        Integer tam = inputsJList.getModel().getSize();
        String[] command = new String[tam];
        String iped = "\"" + this.ipedJAR + "\"";
        String java = "java -jar";

        /*
         * Runtime rt = Runtime.getRuntime(); Long TM = rt.totalMemory(); Long MM =
         * rt.maxMemory(); int NP = rt.availableProcessors(); Long mem = (TM /
         * rt.freeMemory()); /*JOptionPane.showMessageDialog(null, NP);
         * JOptionPane.showMessageDialog(null, TM); JOptionPane.showMessageDialog(null,
         * mem); JOptionPane.showMessageDialog(null, MM);
         * 
         * JOptionPane.showMessageDialog(null, "Nome: "+mxbean.getName());
         * JOptionPane.showMessageDialog(null, "Versão: "+mxbean.getVersion());
         * JOptionPane.showMessageDialog(null, "Arquitetura: "+mxbean.getArch());
         * JOptionPane.showMessageDialog(null,
         * "Processadores: "+mxbean.getAvailableProcessors());
         * JOptionPane.showMessageDialog(null,
         * "LoadAverage: "+mxbean.getSystemLoadAverage());
         */

        // tam = inputsJList.getModel().getSize();
        // JOptionPane.showMessageDialog(null, "tamanho do inputJList "+tam);
        if (!nameLogFileText.getText().isEmpty())
            log = " -log \"" + nameLogFileText.getText() + "\"";

        if (!pathListaExpressText.getText().isEmpty())
            expressList = " -l \"" + pathListaExpressText.getText() + "\"";

        if (!pathAsapFileText.getText().isEmpty())
            asapFile = " -asap \"" + pathAsapFileText.getText() + "\"";

        if (jcProfiles.getSelectedItem() != "Default")
            profile = " -profile " + jcProfiles.getSelectedItem().toString().toLowerCase();

        /*
         * if (FAppendCheckBox.isSelected()) append = " --append";
         */

        if (xmxParameterTxt.getText().isEmpty()) {
            xmx = " -Xmx" + mem.toString() + "m ";
        } else {
            xmx = " -Xmx" + xmxParameterTxt.getText() + "m ";
        }
        // || xmxParameterTxt.getText().isEmpty()
        if (listmodel.isEmpty() || outputText.getText().isEmpty() || projectNameText.getText().isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "É necessário informar um nome para seu projeto, pelo menos uma fonte de dados como origem (imagem ou diretório) e uma pasta de destino");
        } else {
            nome_item = projectNameText.getText();
            destino = " -o \"" + outputText.getText() + "\"";
            String item;
            for (int i = 0; i < tam; i++) {
                item = inputsJList.getModel().getElementAt(i);
                // item.indexOf("@") != -1

                // if (item.contains(".")){
                // //é uma imagem
                // String[] split = new String[item.length()];
                // split = item.split(".");
                // String filename = split[0];
                // JOptionPane.showMessageDialog(null, filename);
                // }else{
                // // é uma pasta
                // JOptionPane.showMessageDialog(null, "é uma pasta");
                // }

                if (item.contains("@")) {

                    String[] split = new String[item.length()];

                    split = item.split("@");

                    origem = " -d \"" + split[0] + "\"";

                    if (split.length > 2) {
                        dname = " -dname \"" + split[1] + "\"";
                        log = " -log \"" + outputText.getText() + File.separator + split[2] + "\"";
                        nome_item = split[1];
                    }
                    if (split.length > 1) {
                        if (split[1].contains(".log")) {
                            log = " -log \"" + outputText.getText() + File.separator + split[1] + "\"";
                        } else {
                            dname = " -dname \"" + split[1] + "\"";
                            nome_item = split[1];
                        }
                    }
                } else {
                    origem = " -d \"" + inputsJList.getModel().getElementAt(i) + "\"";
                }
                if (PortableCheckBox.isSelected()) {
                    portable = " --portable";
                }
                if ((i > 0) || FAppendCheckBox.isSelected()) {
                    append = " --append";
                }
                antes = "@echo OFF\n" + "set INICIO=%date% - %time%\n"
                        + "@echo ==========================================\n" + "@echo Processamento: \"" + nome_item
                        + "\" \n" + "@echo ==========================================\n";

                depois = "\nset FIM=%date% - %time%\n"
                        + "@echo ========================================================================================================================\n"
                        + "@echo O processamento: \"" + nome_item + "\" iniciou em: %INICIO% e finalizou em: %FIM%\n"
                        + "@echo ========================================================================================================================\n";
                command[i] = antes + java + xmx + iped + log + profile + expressList + origem + dname + destino
                        + asapFile + portable + append + depois;
                dname = "";
                log = "";
                antes = "";
                depois = "";
                // JOptionPane.showMessageDialog(null, command[i].toString());

            }
            String nameFile = projectNameText.getText();
            String outputfolder = outputText.getText();

            try {
                this.createScript(nameFile, outputfolder, command);
            } catch (IOException ex) {
                Logger.getLogger(FrontIPED.class.getName()).log(Level.SEVERE, null, ex);
            }
            this.Command = command;
        }
    }// GEN-LAST:event_btnSaveProjectActionPerformed

    private void btnListExpressActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnListExpressActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(pathListaExpressText, fc);
    }// GEN-LAST:event_btnListExpressActionPerformed

    private void jcProfilesItemStateChanged(java.awt.event.ItemEvent evt) {// GEN-FIRST:event_jcProfilesItemStateChanged
        if (jcProfiles.getSelectedItem() != "Default") {
            jtpPrincipal.setEnabledAt(1, false);
        } else {
            jtpPrincipal.setEnabledAt(1, true);
        }
    }// GEN-LAST:event_jcProfilesItemStateChanged

    private void jcProfilesFocusLost(java.awt.event.FocusEvent evt) {// GEN-FIRST:event_jcProfilesFocusLost

    }// GEN-LAST:event_jcProfilesFocusLost

    private void jcProfilesInputMethodTextChanged(java.awt.event.InputMethodEvent evt) {// GEN-FIRST:event_jcProfilesInputMethodTextChanged

    }// GEN-LAST:event_jcProfilesInputMethodTextChanged

    private void btnASAPActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnASAPActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(pathAsapFileText, fc);
    }// GEN-LAST:event_btnASAPActionPerformed

    private void LogAlternativoCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_LogAlternativoCheckBoxActionPerformed

    }// GEN-LAST:event_LogAlternativoCheckBoxActionPerformed

    private void LogAlternativoCheckBoxMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_LogAlternativoCheckBoxMouseClicked

    }// GEN-LAST:event_LogAlternativoCheckBoxMouseClicked

    private void LogAlternativoCheckBoxStateChanged(javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_LogAlternativoCheckBoxStateChanged
        if (LogAlternativoCheckBox.isSelected()) {
            LogFileLabel.setEnabled(LogAlternativoCheckBox.isSelected());
            nameLogFileText.setEnabled(LogAlternativoCheckBox.isSelected());
        } else {
            LogFileLabel.setEnabled(LogAlternativoCheckBox.isSelected());
            nameLogFileText.setEnabled(LogAlternativoCheckBox.isSelected());
        }
    }// GEN-LAST:event_LogAlternativoCheckBoxStateChanged

    private void DNameCheckBoxStateChanged(javax.swing.event.ChangeEvent evt) {// GEN-FIRST:event_DNameCheckBoxStateChanged
        if (DNameCheckBox.isSelected()) {
            DNameLabel.setEnabled(DNameCheckBox.isSelected());
            imageNameText.setEnabled(DNameCheckBox.isSelected());
        } else {
            DNameLabel.setEnabled(DNameCheckBox.isSelected());
            imageNameText.setEnabled(DNameCheckBox.isSelected());
        }
    }// GEN-LAST:event_DNameCheckBoxStateChanged

    private void DNameCheckBoxMouseClicked(java.awt.event.MouseEvent evt) {// GEN-FIRST:event_DNameCheckBoxMouseClicked
        // TODO add your handling code here:
    }// GEN-LAST:event_DNameCheckBoxMouseClicked

    private void DNameCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_DNameCheckBoxActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_DNameCheckBoxActionPerformed

    private void btnSaveProfileActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnSaveProfileActionPerformed

        Properties propsIpedConfig = new Properties();

        String valores = jlHash.getSelectedValuesList().toString();
        String sub1 = valores.replace("[", "");
        String sub2 = sub1.replace("]", "");
        String sub3 = sub2.replace(",", ";");
        propsIpedConfig.setProperty("hash", sub3);
        // salva os outros valores dos checkboxes no arquivo IPEDConfig.txt
        propsIpedConfig.setProperty("excludeKffIgnorable",
                Boolean.toString(this.excludeKffIgnorableCheckBox.isSelected()));
        propsIpedConfig.setProperty("ignoreDuplicates", Boolean.toString(this.ignoreDuplicatesCheckBox.isSelected()));
        propsIpedConfig.setProperty("exportFileProps", Boolean.toString(this.exportFilePropsCheckBox.isSelected()));
        propsIpedConfig.setProperty("processFileSignatures",
                Boolean.toString(this.processFileSignaturesCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableFileParsing", Boolean.toString(this.enableFileParsingCheckBox.isSelected()));
        propsIpedConfig.setProperty("expandContainers", Boolean.toString(this.expandContainersCheckBox.isSelected()));
        propsIpedConfig.setProperty("indexFileContents", Boolean.toString(this.indexFileContentsCheckBox.isSelected()));
        propsIpedConfig.setProperty("indexUnknownFiles", Boolean.toString(this.indexUnknownFilesCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableOCR", Boolean.toString(this.enableOCRCheckBox.isSelected()));
        propsIpedConfig.setProperty("addFileSlacks", Boolean.toString(this.addFileSlacksCheckBox.isSelected()));
        propsIpedConfig.setProperty("addUnallocated", Boolean.toString(this.addUnallocatedCheckBox.isSelected()));
        propsIpedConfig.setProperty("indexUnallocated", Boolean.toString(this.indexUnallocatedCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableCarving", Boolean.toString(this.enableCarvingCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableKFFCarving", Boolean.toString(this.enableKFFCarvingCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableKnownMetCarving",
                Boolean.toString(this.enableKnownMetCarvingCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableImageThumbs", Boolean.toString(this.enableImageThumbsCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableVideoThumbs", Boolean.toString(this.enableVideoThumbsCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableHTMLReport", Boolean.toString(this.enableHTMLReportCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableRegexSearch", Boolean.toString(this.enableRegexSearchCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableLanguageDetect",
                Boolean.toString(this.enableLanguageDetectCheckBox.isSelected()));
        propsIpedConfig.setProperty("enableNamedEntityRecogniton",
                Boolean.toString(this.enableNamedEntityRecognitonCheckBox.isSelected()));
        propsIpedConfig.setProperty("indexCorruptedFiles",
                Boolean.toString(this.indexCorruptedFilesCheckBox.isSelected()));

        savePropertiesIPEDConfig(propsIpedConfig);
        JOptionPane.showMessageDialog(null, "Perfil Personalizado Salvo!");
        // carregaConfiguracoes();
        this.carregaIPEDConfig();
    }// GEN-LAST:event_btnSaveProfileActionPerformed

    private void enableHTMLReportCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableHTMLReportCheckBoxActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_enableHTMLReportCheckBoxActionPerformed

    private void btnRegripperFolderPathActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnRegripperFolderPathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(regripperFolderPathText, fc);
    }// GEN-LAST:event_btnRegripperFolderPathActionPerformed

    private void regripperFolderPathTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_regripperFolderPathTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_regripperFolderPathTextActionPerformed

    private void enableRegripperFolderPathCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableRegripperFolderPathCheckBoxActionPerformed
        btnRegripperFolderPath.setEnabled(enableRegripperFolderPathCheckBox.isSelected());
        regripperFolderPathText.setEnabled(enableRegripperFolderPathCheckBox.isSelected());
        if (!enableRegripperFolderPathCheckBox.isSelected())
            regripperFolderPathText.setBackground(new java.awt.Color(240, 240, 240));
        else
            regripperFolderPathText.setBackground(Color.WHITE);
    }// GEN-LAST:event_enableRegripperFolderPathCheckBoxActionPerformed

    private void btnOptionalJarsPathActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnOptionalJarsPathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(optionalJarsPathText, fc);
    }// GEN-LAST:event_btnOptionalJarsPathActionPerformed

    private void optionalJarsPathTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_optionalJarsPathTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_optionalJarsPathTextActionPerformed

    private void enableOptionalJarsPathCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableOptionalJarsPathCheckBoxActionPerformed
        btnOptionalJarsPath.setEnabled(enableOptionalJarsPathCheckBox.isSelected());
        optionalJarsPathText.setEnabled(enableOptionalJarsPathCheckBox.isSelected());
        if (!enableOptionalJarsPathCheckBox.isSelected())
            optionalJarsPathText.setBackground(new java.awt.Color(240, 240, 240));
        else
            optionalJarsPathText.setBackground(Color.WHITE);
    }// GEN-LAST:event_enableOptionalJarsPathCheckBoxActionPerformed

    private void btnMplayerPathActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnMplayerPathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(mplayerPathText, fc);
    }// GEN-LAST:event_btnMplayerPathActionPerformed

    private void mplayerPathTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_mplayerPathTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_mplayerPathTextActionPerformed

    private void enableMplayerPathCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableMplayerPathCheckBoxActionPerformed
        btnMplayerPath.setEnabled(enableMplayerPathCheckBox.isSelected());
        mplayerPathText.setEnabled(enableMplayerPathCheckBox.isSelected());
        if (!enableMplayerPathCheckBox.isSelected())
            mplayerPathText.setBackground(new java.awt.Color(240, 240, 240));
        else
            mplayerPathText.setBackground(Color.WHITE);
    }// GEN-LAST:event_enableMplayerPathCheckBoxActionPerformed

    private void enableTskJarPathCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableTskJarPathCheckBoxActionPerformed
        btnTskJarPath.setEnabled(enableTskJarPathCheckBox.isSelected());
        tskJarPathText.setEnabled(enableTskJarPathCheckBox.isSelected());
        if (!enableTskJarPathCheckBox.isSelected())
            tskJarPathText.setBackground(new java.awt.Color(240, 240, 240));
        else
            tskJarPathText.setBackground(Color.WHITE); // TODO add your handling code here:
    }// GEN-LAST:event_enableTskJarPathCheckBoxActionPerformed

    private void btnTskJarPathActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnTskJarPathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(ledDieText, fc);
    }// GEN-LAST:event_btnTskJarPathActionPerformed

    private void tskJarPathTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_tskJarPathTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_tskJarPathTextActionPerformed

    private void btnDiePathActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnDiePathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(ledDieText, fc);
    }// GEN-LAST:event_btnDiePathActionPerformed

    private void ledDieTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_ledDieTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_ledDieTextActionPerformed

    private void enableLedDieCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableLedDieCheckBoxActionPerformed
        btnDiePath.setEnabled(enableLedDieCheckBox.isSelected());
        ledDieText.setEnabled(enableLedDieCheckBox.isSelected());
        if (!enableLedDieCheckBox.isSelected())
            ledDieText.setBackground(new java.awt.Color(240, 240, 240));
        else
            ledDieText.setBackground(Color.WHITE);
    }// GEN-LAST:event_enableLedDieCheckBoxActionPerformed

    private void btnHashLEDPathActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnHashLEDPathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(ledWkffPathText, fc);
    }// GEN-LAST:event_btnHashLEDPathActionPerformed

    private void ledWkffPathTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_ledWkffPathTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_ledWkffPathTextActionPerformed

    private void enableLedWkffCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableLedWkffCheckBoxActionPerformed
        btnHashLEDPath.setEnabled(enableLedWkffCheckBox.isSelected());
        ledWkffPathText.setEnabled(enableLedWkffCheckBox.isSelected());
        if (!enableLedWkffCheckBox.isSelected())
            ledWkffPathText.setBackground(new java.awt.Color(240, 240, 240));
        else
            ledWkffPathText.setBackground(Color.WHITE);
    }// GEN-LAST:event_enableLedWkffCheckBoxActionPerformed

    private void btnKffPathActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_btnKffPathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setCurrentDirectory(this.iped_dir);
        abreJanela(kffDbText, fc);
    }// GEN-LAST:event_btnKffPathActionPerformed

    private void kffDbTextActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_kffDbTextActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_kffDbTextActionPerformed

    private void enableKffCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_enableKffCheckBoxActionPerformed
        btnKffPath.setEnabled(enableKffCheckBox.isSelected());
        kffDbText.setEnabled(enableKffCheckBox.isSelected());
        if (!enableKffCheckBox.isSelected())
            kffDbText.setBackground(new java.awt.Color(240, 240, 240));
        else
            kffDbText.setBackground(Color.WHITE);
    }// GEN-LAST:event_enableKffCheckBoxActionPerformed

    private void jcProfilesActionPerformed(java.awt.event.ActionEvent evt) {// GEN-FIRST:event_jcProfilesActionPerformed
        // TODO add your handling code here:
    }// GEN-LAST:event_jcProfilesActionPerformed

    /**
     * @param args
     *            the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String args[]) throws IOException {
        /* Set the Nimbus look and feel */
        // <editor-fold defaultstate="collapsed" desc=" Look and feel setting code
        // (optional) ">
        /*
         * If Nimbus (introduced in Java SE 6) is not available, stay with the default
         * look and feel. For details see
         * http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         * 
         * try { for (javax.swing.UIManager.LookAndFeelInfo info :
         * javax.swing.UIManager.getInstalledLookAndFeels()) { if
         * ("Nimbus".equals(info.getName())) {
         * javax.swing.UIManager.setLookAndFeel(info.getClassName()); break; } } } catch
         * (ClassNotFoundException ex) {
         * java.util.logging.Logger.getLogger(FrontIPED.class.getName()).log(java.util.
         * logging.Level.SEVERE, null, ex); } catch (InstantiationException ex) {
         * java.util.logging.Logger.getLogger(FrontIPED.class.getName()).log(java.util.
         * logging.Level.SEVERE, null, ex); } catch (IllegalAccessException ex) {
         * java.util.logging.Logger.getLogger(FrontIPED.class.getName()).log(java.util.
         * logging.Level.SEVERE, null, ex); } catch
         * (javax.swing.UnsupportedLookAndFeelException ex) {
         * java.util.logging.Logger.getLogger(FrontIPED.class.getName()).log(java.util.
         * logging.Level.SEVERE, null, ex); }
         */
        // </editor-fold>
        // </editor-fold>
        // </editor-fold>
        // </editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                new FrontIPED().setVisible(true);
            }
        });
    }

    private static void abreJanela(JTextField tf, JFileChooser fc) {
        int result = fc.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            tf.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox DNameCheckBox;
    private javax.swing.JLabel DNameLabel;
    private javax.swing.JCheckBox FAppendCheckBox;
    private javax.swing.JCheckBox LogAlternativoCheckBox;
    private javax.swing.JLabel LogFileLabel;
    private javax.swing.JPanel PainelDestino;
    private javax.swing.JPanel PainelMemoria;
    private javax.swing.JPanel PainelOrigem;
    private javax.swing.JPanel PainelPerfil;
    private javax.swing.JPanel PainelProjetoConfig;
    private javax.swing.JCheckBox PortableCheckBox;
    private javax.swing.JCheckBox addFileSlacksCheckBox;
    private javax.swing.JCheckBox addUnallocatedCheckBox;
    private javax.swing.JButton btnASAP;
    private javax.swing.JButton btnAddInput;
    private javax.swing.JButton btnDestino;
    private javax.swing.JButton btnDiePath;
    private javax.swing.JButton btnDirTemp;
    private javax.swing.JButton btnHashLEDPath;
    private javax.swing.JButton btnIniciarProcesso;
    private javax.swing.JButton btnKffPath;
    private javax.swing.JButton btnListExpress;
    private javax.swing.JButton btnMplayerPath;
    private javax.swing.JButton btnOptionalJarsPath;
    private javax.swing.JButton btnOrigem;
    private javax.swing.JButton btnRegripperFolderPath;
    private javax.swing.JButton btnRemInput;
    private javax.swing.JButton btnSalvar;
    private javax.swing.JButton btnSaveProfile;
    private javax.swing.JButton btnSaveProject;
    private javax.swing.JButton btnTskJarPath;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JCheckBox enableCarvingCheckBox;
    private javax.swing.JCheckBox enableFileParsingCheckBox;
    private javax.swing.JCheckBox enableHTMLReportCheckBox;
    private javax.swing.JCheckBox enableImageThumbsCheckBox;
    private javax.swing.JCheckBox enableKFFCarvingCheckBox;
    private javax.swing.JCheckBox enableKffCheckBox;
    private javax.swing.JCheckBox enableKnownMetCarvingCheckBox;
    private javax.swing.JCheckBox enableLanguageDetectCheckBox;
    private javax.swing.JCheckBox enableLedDieCheckBox;
    private javax.swing.JCheckBox enableLedWkffCheckBox;
    private javax.swing.JCheckBox enableMplayerPathCheckBox;
    private javax.swing.JCheckBox enableNamedEntityRecognitonCheckBox;
    private javax.swing.JCheckBox enableOCRCheckBox;
    private javax.swing.JCheckBox enableOptionalJarsPathCheckBox;
    private javax.swing.JCheckBox enableRegexSearchCheckBox;
    private javax.swing.JCheckBox enableRegripperFolderPathCheckBox;
    private javax.swing.JCheckBox enableTskJarPathCheckBox;
    private javax.swing.JCheckBox enableVideoThumbsCheckBox;
    private javax.swing.JCheckBox excludeKffIgnorableCheckBox;
    private javax.swing.JCheckBox expandContainersCheckBox;
    private javax.swing.JCheckBox exportFilePropsCheckBox;
    private javax.swing.JTextField hashText;
    private javax.swing.JCheckBox ignoreDuplicatesCheckBox;
    private javax.swing.JTextField imageNameText;
    private javax.swing.JCheckBox indexCorruptedFilesCheckBox;
    private javax.swing.JCheckBox indexFileContentsCheckBox;
    private javax.swing.JCheckBox indexTempOnSSDCheckBox;
    private javax.swing.JTextField indexTempText;
    private javax.swing.JCheckBox indexUnallocatedCheckBox;
    private javax.swing.JCheckBox indexUnknownFilesCheckBox;
    private javax.swing.JTextField inputText;
    private javax.swing.JList<String> inputsJList;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JComboBox<String> jcLocale;
    private javax.swing.JComboBox<String> jcProfiles;
    private javax.swing.JList<String> jlHash;
    private javax.swing.JPanel jpFerramentasExternas;
    private javax.swing.JPanel jpIPEDConfig;
    private javax.swing.JPanel jpOutrasConfiguracoes;
    private javax.swing.JPanel jpParametros;
    private javax.swing.JPanel jpProjeto;
    private javax.swing.JPanel jpSobre;
    private javax.swing.JTabbedPane jtpPrincipal;
    private javax.swing.JTextField kffDbText;
    private javax.swing.JTextField ledDieText;
    private javax.swing.JTextField ledWkffPathText;
    private javax.swing.JTextField mplayerPathText;
    private javax.swing.JTextField nameLogFileText;
    private javax.swing.JTextField numThreadsText;
    private javax.swing.JTextField optionalJarsPathText;
    private javax.swing.JCheckBox outputOnSSDCheckBox;
    private javax.swing.JTextField outputText;
    private javax.swing.JTextField pathAsapFileText;
    private javax.swing.JTextField pathListaExpressText;
    private javax.swing.JCheckBox processFileSignaturesCheckBox;
    private javax.swing.JTextField projectNameText;
    private javax.swing.JTextField regripperFolderPathText;
    private javax.swing.JTextField tskJarPathText;
    private javax.swing.JTextField xmxParameterTxt;
    // End of variables declaration//GEN-END:variables
}
