package iped.app.home.configurables;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.EventObject;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.CellEditorListener;
import javax.swing.tree.TreePath;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import iped.app.home.configurables.ParsersTreeModel.ParserElementName;

public class ParserTreeCellEditor extends JPanel implements javax.swing.tree.TreeCellEditor {

    private JComboBox<Boolean> booleanCombo;
    private JTextField textEditor;
    private JLabel label;
    TreePath tp;
    private Field f;

    public ParserTreeCellEditor() {
        booleanCombo = new JComboBox<Boolean>();
        booleanCombo.addItem(true);
        booleanCombo.addItem(false);
        textEditor = new JTextField();
        label = new JLabel();

        textEditor.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == e.VK_ENTER) {
                    JTree tree = (JTree) e.getComponent().getParent().getParent();
                    tree.stopEditing();
                }
                if (e.getKeyCode() == e.VK_ESCAPE) {
                    JTree tree = (JTree) e.getComponent().getParent().getParent();
                    tree.cancelEditing();
                }
            }
        });
    }

    @Override
    public Object getCellEditorValue() {
        return null;
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
        if (anEvent instanceof MouseEvent) {
            JTree tree = (JTree) anEvent.getSource();
            MouseEvent me = (MouseEvent) anEvent;

            tp = tree.getPathForLocation(me.getX(), me.getY());

            if (tp != null) {
                Object value = tp.getLastPathComponent();
                return value instanceof Field;
            } else {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
        return false;
    }

    @Override
    public boolean stopCellEditing() {
        Element element = ((ParserElementName) tp.getParentPath().getLastPathComponent()).getElement();
        NodeList nlParamss = element.getElementsByTagName("params");
        if (nlParamss != null) {
            Element el = (Element) nlParamss.item(0);
            NodeList nlParams = el.getElementsByTagName("param");
            for (int i = 0; i < nlParams.getLength(); i++) {
                Element param = (Element) nlParams.item(i);
                if (param.getAttribute("name").equals(f.getName())) {
                    param.getFirstChild().setNodeValue(textEditor.getText());
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public void cancelCellEditing() {

    }

    @Override
    public void addCellEditorListener(CellEditorListener l) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeCellEditorListener(CellEditorListener l) {
        // TODO Auto-generated method stub

    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
        if (value instanceof Field) {
            f = (Field) value;

            Object tpValue = tree.getPathForRow(row).getParentPath().getLastPathComponent();
            if (!(tpValue instanceof ParserElementName))
                return null;

            this.removeAll();
            this.setLayout(new FlowLayout());

            label.setText(f.getName() + ":");
            this.add(label);

            String fieldValue = null;
            Element element = ((ParserElementName) tpValue).getElement();
            NodeList nlParamss = element.getElementsByTagName("params");
            if (nlParamss != null) {
                Element el = (Element) nlParamss.item(0);
                NodeList nlParams = el.getElementsByTagName("param");
                for (int i = 0; i < nlParams.getLength(); i++) {
                    Element param = (Element) nlParams.item(i);
                    if (param.getAttribute("name").equals(f.getName())) {
                        fieldValue = param.getFirstChild().getNodeValue();
                        break;
                    }
                }
            }

            if (f.getType() == Boolean.class) {
                this.removeAll();
                this.add(booleanCombo);
            } else {
                textEditor.setPreferredSize(new Dimension(200, 32));
                if (fieldValue != null) {
                    textEditor.setText(fieldValue);
                } else {
                    textEditor.setText("");
                }
                this.add(textEditor);
            }

            return this;
        }
        return null;
    }

}
