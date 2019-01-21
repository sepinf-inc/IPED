package br.gov.pf.labld.cases;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class ChooseOutputAction extends AbstractAction implements FocusListener {

  private static final long serialVersionUID = -3545374838608476977L;

  private Component parent;
  private JTextField target;
  private Component focusTarget;

  private final SimpleDateFormat sdf = new SimpleDateFormat("'iped-'yyyy-MM-dd-HH-mm-ss");

  public ChooseOutputAction(Component parent, JTextField target, Component focusTarget) {
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
    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

    File currentDir = getCurrentDir();
    if (currentDir != null && currentDir.exists() && currentDir.isDirectory()) {
      fileChooser.setCurrentDirectory(currentDir);
    }

    int returnVal = fileChooser.showOpenDialog(parent);

    if (returnVal == JFileChooser.APPROVE_OPTION) {
      File file = fileChooser.getSelectedFile();

      if (file.list().length > 0) {
        String newDir = sdf.format(new Date());
        String message = Messages.getString("Case.NonEmptyDirectoryAlert", file.getAbsolutePath(), newDir);
        int confirmationVal = JOptionPane.showConfirmDialog(parent, message, "", JOptionPane.YES_NO_OPTION);
        if (confirmationVal == JOptionPane.YES_OPTION) {
          file = new File(file, newDir);
          target.setText(file.getAbsolutePath());
        }
      } else {
        target.setText(file.getAbsolutePath());
      }

    }
  }

  private File getCurrentDir() {
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
