package iped.app.ui.controls;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.dnd.DropTarget;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.VetoableChangeListener;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.DropMode;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

public class IPEDConfigSearchList<E> extends JPanel {
    protected RSyntaxTextArea txFilter;
    protected Predicate<E> availablePredicate;
    protected List<E> availableItems;
    protected JScrollPane listScrollPanel;
    protected JList<E> list;
    protected Predicate<E> checkTypedContent;
    protected int paintRef;
    protected int paintSize;

    protected IPEDConfigSearchList() {
        this(null);
    }

    protected IPEDConfigSearchList(Predicate<E> availablePredicate) {
            super(new BorderLayout());
            this.availablePredicate=availablePredicate;
            checkTypedContent = new Predicate<E>() {
                @Override
                public boolean test(E t) {
                    if(!txFilter.getText().trim().equals("")) {
                        return !t.toString().contains(txFilter.getText());
                    }else {
                        return false;
                    }
                        
                }
            };
        }

    public void createGUI(IPEDFilterableListModel m) {
        list = new JList<E>(m);

        m.setFilter(checkTypedContent);

        txFilter = new RSyntaxTextArea(1, 20);
        txFilter.setHighlightCurrentLine(false);
        txFilter.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                list.clearSelection();
                list.updateUI();
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }
        });

        RTextScrollPane tsFilter = new RTextScrollPane(txFilter);
        tsFilter.setBackground(this.getBackground());
        tsFilter.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        tsFilter.setLineNumbersEnabled(false);
        this.add(tsFilter, BorderLayout.NORTH);
        listScrollPanel = new JScrollPane();
        listScrollPanel.setBackground(this.getBackground());
        listScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScrollPanel.setViewportView(list);
        listScrollPanel.setAutoscrolls(true);
        list.setDropMode(DropMode.ON);
        this.add(listScrollPanel, BorderLayout.CENTER);
    }

    @Override
    public void setTransferHandler(TransferHandler newHandler) {
        list.setTransferHandler(newHandler);
    }

    public Color getSelectionForeground() {
        return list.getSelectionForeground();
    }

    public void setSelectionForeground(Color selectionForeground) {
        list.setSelectionForeground(selectionForeground);
    }

    public Color getSelectionBackground() {
        return list.getSelectionBackground();
    }

    public void setSelectionBackground(Color selectionBackground) {
        list.setSelectionBackground(selectionBackground);
    }

    public int getVisibleRowCount() {
        return list.getVisibleRowCount();
    }

    public void setDropTarget(DropTarget dt) {
        list.setDropTarget(dt);
    }

    public void setDragEnabled(boolean b) {
        list.setDragEnabled(b);
    }

    public final void setDropMode(DropMode dropMode) {
        list.setDropMode(dropMode);
    }

    public ListSelectionModel getSelectionModel() {
        return list.getSelectionModel();
    }

    public ListSelectionListener[] getListSelectionListeners() {
        return list.getListSelectionListeners();
    }

    public void setSelectionMode(int selectionMode) {
        list.setSelectionMode(selectionMode);
    }

    public int getSelectionMode() {
        return list.getSelectionMode();
    }

    public void clearSelection() {
        list.clearSelection();
    }

    public int[] getSelectedIndices() {
        return list.getSelectedIndices();
    }

    public void setSelectedIndex(int index) {
        list.setSelectedIndex(index);
    }

    public void setSelectedIndices(int[] indices) {
        list.setSelectedIndices(indices);
    }

    public Object[] getSelectedValues() {
        return list.getSelectedValues();
    }

    public List<E> getSelectedValuesList() {
        return list.getSelectedValuesList();
    }

    public int getSelectedIndex() {
        return list.getSelectedIndex();
    }

    public E getSelectedValue() {
        return list.getSelectedValue();
    }

    public void setSelectedValue(Object anObject, boolean shouldScroll) {
        list.setSelectedValue(anObject, shouldScroll);
    }

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        list.addVetoableChangeListener(listener);
    }

    public JList<E> getListComponent() {
        return list;
    }

    @Override
    public void paintImmediately(int x, int y, int w, int h) {
        paintRef++;
        if (paintRef >= 1000000) {
            paintRef = 1;
        }
        paintSize = list.getModel().getSize();
        super.paintImmediately(x, y, w, h);
    }

    @Override
    protected void paintChildren(Graphics g) {
        paintRef++;
        if (paintRef >= 1000000) {
            paintRef = 1;
        }
        paintSize = list.getModel().getSize();
        super.paintChildren(g);
    }
}
