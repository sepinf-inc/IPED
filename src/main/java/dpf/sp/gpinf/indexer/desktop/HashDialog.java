package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class HashDialog extends JDialog{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public HashDialog(String hash) {
        this(hash, "");
    }
    
    public HashDialog(String hash, String absolutePath) {
        super();
        this.setModal(true);
        this.setTitle(Messages.getString("HashDialog.MD5Title")); //$NON-NLS-1$
        this.setBounds(0, 0, 600, 150);
        this.setLocationRelativeTo(null);
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("<html><body>" + Messages.getString("HashDialog.MD5Title")+ ":<br>" + absolutePath + "</body></html>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        JTextField text = new JTextField(hash.toUpperCase());
        text.setEditable(false);
        text.setHorizontalAlignment(JTextField.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 30, 30));
        text.setFont(text.getFont().deriveFont(20f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(text, BorderLayout.CENTER);
        this.getContentPane().add(panel);
        this.pack();
    }
    
}
