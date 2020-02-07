package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

public class GraphStatusBar extends JPanel {

  private static final long serialVersionUID = -3433655120498837041L;

  private JLabel statusLabel;
  private JProgressBar progressBar;

  public GraphStatusBar() {
    super(new BorderLayout());
    init();
  }

  public GraphStatusBar(boolean isDoubleBuffered) {
    super(new BorderLayout(), isDoubleBuffered);
    init();
  }

  private void init() {
    this.setBorder(new BevelBorder(BevelBorder.LOWERED));
    statusLabel = new JLabel("Status.");
    progressBar = new JProgressBar(SwingUtilities.HORIZONTAL, 0, 100);
    add(this.statusLabel, BorderLayout.WEST);
    add(this.progressBar, BorderLayout.EAST);
  }

  public void setStatus(String status) {
    this.statusLabel.setText(status);
    this.repaint();
  }

  public void setProgress(int value) {
    this.progressBar.setValue(value);
    this.repaint();
  }

  public void increaseProgress(int step) {
    int value = this.progressBar.getValue();
    value += step;
    if (value >= 100) {
      value = 0;
    }
    this.progressBar.setValue(value);
    this.repaint();
  }

}
