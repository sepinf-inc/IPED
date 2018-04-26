package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class HashDialog extends JDialog{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public HashDialog(String hash) {
        super();
        this.setModal(true);
        this.setTitle("Hash MD5 do ZIP exportado");
        this.setBounds(0, 0, 600, 150);
        this.setLocationRelativeTo(null);
        JPanel panel = new JPanel(new BorderLayout());
        JTextField text = new JTextField(hash.toUpperCase());
        text.setEditable(false);
        text.setHorizontalAlignment(JTextField.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        text.setFont(text.getFont().deriveFont(20f));
        panel.add(text, BorderLayout.CENTER);
        this.getContentPane().add(panel);
        this.pack();
    }
}
