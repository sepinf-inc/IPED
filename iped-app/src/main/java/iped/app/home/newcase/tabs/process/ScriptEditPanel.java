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
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

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

public class ScriptEditPanel extends JPanel implements DocumentListener {
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
    private static final String JAVASCRIPT_TEMPLATE_DIR = "/jstemplates";
    private static final String PYTHON_TEMPLATE_DIR = "/pythontemplates";
    private boolean scriptChanged = false;
    boolean isScriptEditable = false;

    public ScriptEditPanel(MainFrame mainFrame, IScriptTask scriptTask) {
        super();
        this.scriptTask = scriptTask;
        File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.SCRIPT_BASE).toFile();
        File exampleScriptFile = new File(scriptDir, ScriptEditPanel.TEMPLATE_SCRIPT_NAME);
        File customScriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.CUSTOM_SCRIPT_BASE).toFile();
        if (scriptTask.getScriptFileName().equals(exampleScriptFile.getAbsolutePath())) {
            insertionMode = true;
            isScriptEditable = true;
        } else {
            // is script file is in custom script path, enable edition
            if (scriptTask.getScriptFileName().startsWith(customScriptDir.getAbsolutePath())) {
                isScriptEditable = true;
            }
        }
    }

    public ScriptEditPanel(MainFrame mainFrame, IScriptTask scriptTask, boolean isScriptEditable) {
        this(mainFrame, scriptTask);
        this.isScriptEditable = isScriptEditable;
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        changedUpdate(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        changedUpdate(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        scriptChanged = true;
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
                sb.append(str + "\n");
            }
            in.close();
            textArea.setText(sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void createAndShowGUI() {
        titlePanel = new JPanel(new BorderLayout());

        titleText = new JTextField();
        fileButton = new JButton(Messages.get("ScriptEditPanel.ImportScript"));
        fileButton.setToolTipText(Messages.get("ScriptEditPanel.ImportScript.tooltip"));

        ScriptEditPanel self = this;
        fileButton.addActionListener(new ActionListener() {
            private JFileChooser scriptChooser = new JFileChooser();

            @Override
            public void actionPerformed(ActionEvent e) {
                int result = scriptChooser.showOpenDialog(self);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = scriptChooser.getSelectedFile();

                    try {
                        File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.SCRIPT_BASE).toFile();
                        File destFile = new File(scriptDir, selectedFile.getName());

                        if (destFile.exists()) {
                            JOptionPane.showMessageDialog(self, Messages.get("Home.ProcOptions.ScriptAlreadyExists"));
                        } else {
                            try {
                                setScriptTask(destFile);
                            } catch (Exception ex) {
                                IOUtil.deleteDirectory(destFile);
                                throw ex;
                            }
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(self, ex.getLocalizedMessage());
                    }
                }
            }
        });

        titlePanel.add(new JLabel(Messages.get("ScriptEditPanel.File")), BorderLayout.WEST);
        titlePanel.add(titleText, BorderLayout.CENTER);

        templatePanel = new JPanel(new BorderLayout());

        textArea = new RSyntaxTextArea(20, 60);
        textArea.setEditable(isScriptEditable);

        if (insertionMode) {
            textArea.setEnabled(false);

            titlePanel.add(templatePanel, BorderLayout.SOUTH);
            titlePanel.setEnabled(true);// cannot modify existing script name
            titleText.setEnabled(true);// cannot modify existing script name

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
        } else {
            titlePanel.setEnabled(false);// cannot modify existing script name
            titleText.setEnabled(false);// cannot modify existing script name
        }

        try {
            if (scriptTask != null) {
                String scriptName = scriptTask.getScriptFileName();

                File scriptFile = new File(scriptName);
                titleText.setText(scriptName.substring(0, scriptName.lastIndexOf(".")).substring(scriptName.lastIndexOf(File.separator) + 1));

                setScriptTask(scriptFile);
            }

            textArea.setAutoscrolls(true);
            textArea.getDocument().addDocumentListener(this);
            ((AbstractDocument) textArea.getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                    if (scriptChanged || confirmChange()) {
                        super.remove(fb, offset, length);
                    }
                }

                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                    if (scriptChanged || confirmChange()) {
                        super.insertString(fb, offset, string, attr);
                    }
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                    if (scriptChanged || confirmChange()) {
                        super.replace(fb, offset, length, text, attrs);
                    }
                }
            });
            txtAreaScroll = new RTextScrollPane();
            txtAreaScroll.setViewportView(textArea);
            txtAreaScroll.setAutoscrolls(true);
            txtAreaScroll.setLineNumbersEnabled(true);
            this.setLayout(new BorderLayout());
            this.add(titlePanel, BorderLayout.NORTH);
            this.add(txtAreaScroll, BorderLayout.CENTER);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void changeToJavascript() {
        changeTo(ScriptEditPanel.JAVASCRIPT_TEMPLATE_DIR);
    }

    private void changeToPython() {
        changeTo(ScriptEditPanel.PYTHON_TEMPLATE_DIR);
    }

    private void changeTo(String folderName) {
        templatePanel.removeAll();
        templatePanel.add(scriptLanguagePanel, BorderLayout.NORTH);
        File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.SCRIPT_BASE + folderName).toFile();
        if (scriptDir != null) {
            File[] files = scriptDir.listFiles();
            if (files != null) {
                cbTemplate = new JComboBox<File>(files);
                cbTemplate.setSelectedIndex(-1);
                templatePanel.add(new JLabel(Messages.get("ScriptEditPanel.SelectTemplateLabel")), BorderLayout.WEST);
                templatePanel.add(cbTemplate, BorderLayout.CENTER);
                cbTemplate.setRenderer(new ListCellRenderer<File>() {
                    JLabel result = new JLabel();

                    public Component getListCellRendererComponent(JList<? extends File> list, File value, int index, boolean isSelected, boolean cellHasFocus) {
                        if (value == null) {
                            result.setText("");
                        } else {
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
            templatePanel.add(new JLabel(Messages.get("ScriptEditPanel.Preview")), BorderLayout.SOUTH);
            btSelectTemplate = new JButton(Messages.get("ScriptEditPanel.StartEditing"));
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
        if (textArea != null) {
            textArea.setText("");
            textArea.clearParsers();
        }
    }

    public void applyChanges() throws ScriptTaskComplianceException {
        if (scriptChanged) {

            File scriptDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), TaskInstallerConfig.CUSTOM_SCRIPT_BASE).toFile();
            String extension = ".js";
            if (scriptTask instanceof PythonTask) {
                extension = ".py";
            }
            File destFile = new File(scriptDir, titleText.getText() + extension);

            if (insertionMode) {
                if (rbPython.isSelected()) {
                    scriptTask = new PythonTask(destFile);
                    scriptTask.checkTaskCompliance(textArea.getText());

                    try {
                        Files.write(destFile.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                } else {
                    scriptTask.checkTaskCompliance(textArea.getText());

                    try {
                        Files.write(destFile.toPath(), textArea.getText().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                    scriptTask = new ScriptTask(destFile);
                }
            }

            scriptTaskToSave = scriptTask;
        }
    }

    public IScriptTask getScriptTask() {
        return scriptTaskToSave;
    }

    public boolean confirmChange() {
        int result = JOptionPane.showConfirmDialog(this, Messages.get("ScriptEditPanel.scriptScopeWarn"));
        return result == JOptionPane.YES_OPTION;
    }

}
