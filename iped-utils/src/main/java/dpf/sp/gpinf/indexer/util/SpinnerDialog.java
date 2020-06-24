package dpf.sp.gpinf.indexer.util;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;

public class SpinnerDialog extends JDialog{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private JSpinner spinner;
    private JButton button;
    
    public SpinnerDialog(Frame parent, String title, String label, int val, int minVal, int maxVal) {
        super(parent);
        this.setModal(true);
        this.setTitle(title);
        
        SpinnerNumberModel model = new SpinnerNumberModel(val, minVal, maxVal, 1);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        JPanel labelPanel = new JPanel();
        JLabel jLabel = new JLabel(label);
        labelPanel.add(jLabel);
        
        JPanel spinnerPanel = new JPanel();
        spinner = new JSpinner(model);
        spinnerPanel.add(spinner);
        
        JPanel okPanel = new JPanel();
        button = new JButton("OK"); //$NON-NLS-1$
        okPanel.add(button);
        
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SpinnerDialog.this.dispose();
            }
        });
        
        panel.add(labelPanel, BorderLayout.WEST);
        panel.add(spinnerPanel, BorderLayout.EAST);
        panel.add(okPanel, BorderLayout.SOUTH);

        this.getContentPane().add(panel);
        
        this.pack();
        
        this.setLocationRelativeTo(parent);
    }
    
    public void addChangeListener(ChangeListener l) {
        spinner.addChangeListener(l);
    }
    
    public int getSelectedValue() {
        return (Integer) spinner.getValue();
    }

}
