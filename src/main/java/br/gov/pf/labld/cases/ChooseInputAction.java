package br.gov.pf.labld.cases;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JTextField;

public class ChooseInputAction extends AbstractAction implements FocusListener {

  private static final long serialVersionUID = 2873445511227416968L;

  private Component parent;
  private JTextField target;
  private Component focusTarget;

  public ChooseInputAction(Component parent, JTextField target, Component focusTarget) {
    this.parent = parent;
    this.target = target;
    this.focusTarget = focusTarget;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    showDialog();
  }

  private void showDialog() {
    final JFileChooser fileChooser = new JFileChooser();
    fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

    File currentFile = getCurrentFile();
    if (currentFile != null && currentFile.exists()) {
      if (currentFile.getParentFile() != null) {
        currentFile = currentFile.getParentFile();
      }
      fileChooser.setCurrentDirectory(currentFile);
    }

    int returnVal = fileChooser.showOpenDialog(parent);

    if (returnVal == JFileChooser.APPROVE_OPTION) {
      File file = fileChooser.getSelectedFile();
      String absolutePath = file.getAbsolutePath();
      target.setText(absolutePath);
    }
  }

  private File getCurrentFile() {
    File current = null;
    String text = target.getText().trim();
    if (!text.isEmpty()) {
      current = new File(text);
    }
    return current;
  }

  @Override
  public void focusGained(FocusEvent e) {
    focusTarget.requestFocusInWindow();
    showDialog();
  }

  @Override
  public void focusLost(FocusEvent e) {

  }

}
