package iped.app.home.newcase.tabs.process;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import iped.app.home.MainFrame;
import iped.app.ui.Messages;
import iped.configuration.IConfigurationDirectory;
import iped.engine.config.TaskInstallerConfig;
import iped.engine.task.IScriptTask;
import iped.engine.task.PythonTask;
import iped.engine.task.ScriptTask;
import iped.engine.task.ScriptTaskComplianceException;
import iped.utils.IOUtil;

public class ScriptEditPanel extends JPanel implements DocumentListener{
    protected RSyntaxTextArea textArea;
    protected RTextScrollPane txtAreaScroll;
    IScriptTask scriptTask;
    IScriptTask scriptTaskToSave;
    File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.SCRIPT_BASE).toFile();
    private JPanel titlePanel;
    private JTextField titleText;
    private JButton fileButton;
    private JRadioButton rbJavascript;
    private JRadioButton rbPython;
    boolean insertionMode = false;
    private JPanel templatePanel;
    private JComboBox<File> cbTemplate;
    private JButton btSelectTemplate;
    private JPanel scriptLanguagePanel;
    static final String TEMPLATE_SCRIPT_NAME = "ExampleScriptTask.js";

    public ScriptEditPanel(MainFrame mainFrame, IScriptTask scriptTask) {
        super();
        this.scriptTask = scriptTask;
        if(scriptTask.getScriptFileName().equals(TEMPLATE_SCRIPT_NAME)) {
            insertionMode=true;            
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        
    }

    private void setScriptTask(File destFile) throws ScriptTaskComplianceException {
        if (destFile.getName().endsWith(".py")) {
            scriptTask = new PythonTask(destFile);
            textArea.setSyntaxEditingStyle(RSyntaxTextArea.SYNTAX_STYLE_PYTHON);
        } else {
            scriptTask = new ScriptTask(destFile);
            textArea.setSyntaxEditingStyle(RSyntaxTextArea.SYNTAX_STYLE_JAVASCRIPT);
        }
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(destFile), "UTF-8")); //$NON-NLS-1$
            StringBuffer sb = new StringBuffer();
            String str;
            while ((str = in.readLine()) != null) {
                sb.append(str+"\n");
            }
            in.close();
            textArea.setText(sb.toString());
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    protected void createAndShowGUI() {
        titlePanel = new JPanel(new BorderLayout());

        titleText = new JTextField();
        fileButton = new JButton("Import script");
        fileButton.setToolTipText("Load script from file");

        ScriptEditPanel self = this;
        fileButton.addActionListener(new ActionListener() {
            private JFileChooser scriptChooser = new JFileChooser();

            @Override
            public void actionPerformed(ActionEvent e) {
                int result = scriptChooser.showOpenDialog(self);
                if(result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = scriptChooser.getSelectedFile();

                    try {
                        File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.SCRIPT_BASE).toFile();
                        File destFile = new File(scriptDir,selectedFile.getName());

                        if(destFile.exists()) {
                            JOptionPane.showMessageDialog(self, Messages.get("Home.ProcOptions.ScriptAlreadyExists"));
                        }else {
                            try {
                                setScriptTask(destFile);
                            }catch (Exception ex) {
                                IOUtil.deleteDirectory(destFile);
                                throw ex;
                            }
                        }
                    }catch(Exception ex) {
                        JOptionPane.showMessageDialog(self, ex.getLocalizedMessage());
                    }
                }
            }
        });

        titlePanel.add(new JLabel("File:"), BorderLayout.WEST);
        titlePanel.add(titleText, BorderLayout.CENTER);

        templatePanel = new JPanel(new BorderLayout());

        textArea = new RSyntaxTextArea(20, 60);

        if(insertionMode) {
            textArea.setEnabled(false);

            titlePanel.add(templatePanel, BorderLayout.SOUTH);
            titlePanel.setEnabled(true);//cannot modify existing script name

            ButtonGroup bg = new ButtonGroup();
            rbJavascript = new JRadioButton("Javascript");
            rbPython = new JRadioButton("Python");
            bg.add(rbJavascript);
            bg.add(rbPython);
            scriptLanguagePanel = new JPanel();
            scriptLanguagePanel.add(rbJavascript);
            scriptLanguagePanel.add(rbPython);

            rbJavascript.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    changeToJavascript();
                }
            });

            rbPython.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    changeToPython();
                }
            });

            changeToJavascript();
        }else {
            titlePanel.setEnabled(false);//cannot modify existing script name
        }

        try {
            if(scriptTask!=null) {
                String scriptName =scriptTask.getScriptFileName();

                File scriptFile = new File(scriptDir, scriptTask.getScriptFileName());
                titleText.setText(scriptName.substring(0,scriptName.lastIndexOf(".")));

                setScriptTask(scriptFile);
            }

            textArea.setAutoscrolls(true);
            textArea.getDocument().addDocumentListener(this);
            txtAreaScroll = new RTextScrollPane();
            txtAreaScroll.setViewportView(textArea);
            txtAreaScroll.setAutoscrolls(true);
            txtAreaScroll.setLineNumbersEnabled(true);
            this.setLayout(new BorderLayout());
            this.add(titlePanel,BorderLayout.NORTH);
            this.add(txtAreaScroll,BorderLayout.CENTER);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void changeToJavascript() {
        changeTo("/jstemplates");
    }

    private void changeToPython() {
        changeTo("/pythontemplates");
    }

    private void changeTo(String folderName) {
        templatePanel.removeAll();
        templatePanel.add(scriptLanguagePanel, BorderLayout.NORTH);
        File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.SCRIPT_BASE +folderName).toFile();            
        if(scriptDir!=null) {
            File[] files = scriptDir.listFiles();
            if(files!=null) {
                cbTemplate = new JComboBox<File>(files);
                cbTemplate.setSelectedIndex(-1);
                templatePanel.add(new JLabel("Select template:"), BorderLayout.WEST);
                templatePanel.add(cbTemplate, BorderLayout.CENTER);
                cbTemplate.setRenderer(new ListCellRenderer<File>() {
                    JLabel result = new JLabel();
                    public Component getListCellRendererComponent(JList<? extends File> list, File value, int index,
                            boolean isSelected, boolean cellHasFocus) {
                        if(value==null) {
                            result.setText("");
                        }else {
                            result.setText(value.getName());
                        }
                        return result;
                    }
                });

                cbTemplate.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        try {
                            setScriptTask((File) cbTemplate.getSelectedItem());
                        } catch (ScriptTaskComplianceException e1) {
                            e1.printStackTrace();
                        }
                    }
                });
            }
            templatePanel.add(new JLabel("Preview:"), BorderLayout.SOUTH);
            btSelectTemplate = new JButton("Start editing");
            btSelectTemplate.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    templatePanel.setVisible(false);
                    templatePanel.updateUI();
                    textArea.setEditable(true);
                    textArea.setEnabled(true);
                }
            });
            
            JPanel templateButtons = new JPanel();
            templateButtons.add(btSelectTemplate);
            templateButtons.add(fileButton);
            templatePanel.add(templateButtons, BorderLayout.EAST);

            templatePanel.updateUI();
        }
        if(textArea!=null) {
            textArea.setText("");
            textArea.clearParsers();
        }
    }

    public void applyChanges() throws ScriptTaskComplianceException {
        scriptTask.checkTaskCompliance(textArea.getText());
        
        File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.SCRIPT_BASE).toFile();
        String extension=".js";
        if(rbPython.isSelected()) {
            extension=".py";
        }
        File destFile = new File(scriptDir, titleText.getText()+extension);
        try {
            Files.write(destFile.toPath(),textArea.getText().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if(insertionMode) {
            if(rbPython.isSelected()) {
                scriptTask = new PythonTask(destFile);
            }else {
                scriptTask= new ScriptTask(destFile);
            }
        }
        
        scriptTaskToSave=scriptTask;
    }

    public IScriptTask getScriptTask() {
        return scriptTaskToSave;
    }
    
}
