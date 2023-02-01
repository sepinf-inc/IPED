package iped.app.ui.controls.textarea;

import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.InputMap;
import javax.swing.JTextPane;
import javax.swing.text.StyledEditorKit;

public class RegexTextPane extends JTextPane {

    public RegexTextPane() {
        // Set editor kit
        this.setContentType("text/xml");
        this.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        RegexTextPane self = this;        
        InputMap inputMap = getInputMap();
        this.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                
            }
            
            @Override
            public void keyReleased(KeyEvent e) {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public void keyPressed(KeyEvent e) {
                
                int key = e.getKeyCode();
                
                if(key == KeyEvent.VK_UP) {
                    String text = self.getText();
                    int pos = text.indexOf("\n", 0);
                    int lastpos=-1;
                    int priorLineWidth=0;
                    while(pos > -1 && pos < self.getCaretPosition()) {
                        priorLineWidth=pos-lastpos;
                        lastpos=pos;
                        pos = text.indexOf("\n", pos+1);
                    }
                    if(lastpos!=-1) {
                        self.setCaretPosition(Math.min(self.getCaretPosition()-priorLineWidth,lastpos));
                    }
                }

                if(key == KeyEvent.VK_END) {
                    String text = self.getText();
                    int pos = text.indexOf("\n", 0);
                    int lastpos=-1;
                    while(pos > -1 && pos < self.getCaretPosition()) {
                        pos = text.indexOf("\n", pos+1);
                    }
                    if(pos>-1) {
                        self.setCaretPosition(pos);
                    }
                    
                }

                if(key == KeyEvent.VK_HOME) {
                    String text = self.getText();
                    int pos = text.indexOf("\n", 0);
                    int lastpos=0;
                    while(pos > -1 && pos < self.getCaretPosition()) {
                        lastpos=pos;
                        pos = text.indexOf("\n", pos+1);
                    }
                    if(lastpos>-1) {
                        self.setCaretPosition(lastpos+1);
                    }
                    
                }
                
                if(key == KeyEvent.VK_DOWN) {
                    String text = self.getText();
                    int pos = text.indexOf("\n", self.getCaretPosition());
                    if(pos>-1) {
                        int currentLineWidth=pos - text.substring(0,pos).lastIndexOf("\n");
                        int nextlinewidth = text.substring(pos+1).indexOf("\n");
                        if(nextlinewidth==-1)nextlinewidth=text.substring(pos+1).length();
                        self.setCaretPosition(Math.min(self.getCaretPosition()+currentLineWidth,pos+1+nextlinewidth));
                    }
                }
            }
        });
    }
}
