package iped.app.ui.controls;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.dnd.DropTarget;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.VetoableChangeListener;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

public class IPEDSearchList<E> extends JPanel {
        protected RSyntaxTextArea txFilter;
        Predicate<E> availablePredicate;
        protected List<E> availableItems;
        protected JScrollPane listScrollPanel;
        protected JList<E> list;
        protected int paintRef;
        protected int paintSize;
        private ListModel<E> defaultListModel;

        protected IPEDSearchList() {
            this(null);
        }

        protected IPEDSearchList(Predicate<E> availablePredicate) {
            super(new BorderLayout());
            this.availablePredicate=availablePredicate;
        }
        
        public void createGUI(List<E> m) {
            list = new JList(m.toArray());
            defaultListModel = list.getModel();
            createGUI();
        }

        public void createGUI(ListModel<E> m) {
            list = new JList<E>(m);
            createGUI();
        }
        
        class FilteredList implements Future<DefaultListModel>{
            DefaultListModel result = new DefaultListModel<>();
            Thread thread;

            public FilteredList(String text) {
                Predicate checkContains = new Predicate<E>() {
                    @Override
                    public boolean test(E t) {
                        if(!text.equals("")) {
                            return t.toString().contains(text);
                        }else {
                            return true;
                        }
                            
                    }
                };

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        for(int i=0; i<availableItems.size(); i++) {
                            Object o = availableItems.get(i);
                            if(checkContains.test(o)) {
                                synchronized (this) {
                                    if(result==null) {
                                        return;//thread was canceled
                                    }
                                    result.addElement(o);
                                }
                            }
                        }
                    }
                };
                thread = new Thread(r);
                thread.start();
            }
            
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                synchronized (this) {
                    result = null;
                }
                return false;
            }

            @Override
            public boolean isCancelled() {
                return result == null;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public DefaultListModel<Object> get() throws InterruptedException, ExecutionException {
                return result;
            }

            @Override
            public DefaultListModel<Object> get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                return result;
            }            
        }

        public void createGUI() {
            list.setFixedCellWidth(100);
            list.setFixedCellHeight(16);
            
            txFilter = new RSyntaxTextArea(1,20);
            txFilter.setHighlightCurrentLine(false);
            txFilter.addKeyListener(new KeyListener() {
                private IPEDSearchList<E>.FilteredList fl;
                @Override
                public void keyTyped(KeyEvent e) {
                }
                @Override public void keyReleased(KeyEvent e) {
                    String text = txFilter.getText();
                    if(text.length()>0) {
                        if(fl!=null) {
                            fl.cancel(true);
                        }
                        fl = new FilteredList(text);
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                DefaultListModel lm;
                                try {
                                    lm = fl.get();
                                    if(lm!=null) {
                                        list.setModel(lm);
                                        list.clearSelection();
                                        list.updateUI();
                                    }
                                } catch (InterruptedException | ExecutionException e1) {
                                    e1.printStackTrace();
                                }
                            }
                        });
                    }else {
                        list.setModel(defaultListModel);
                        list.clearSelection();
                        list.updateUI();
                    }
                }
                @Override public void keyPressed(KeyEvent e) {}
            });
            
            RTextScrollPane tsFilter = new RTextScrollPane(txFilter);
            tsFilter.setBackground(this.getBackground());
            tsFilter.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            tsFilter.setLineNumbersEnabled(false);
            this.add(tsFilter,BorderLayout.NORTH);
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
            if(paintRef>=1000000) {
                paintRef=1;
            }
            paintSize=list.getModel().getSize();
            super.paintImmediately(x, y, w, h);
        }

        @Override
        protected void paintChildren(Graphics g) {
            paintRef++;
            if(paintRef>=1000000) {
                paintRef=1;
            }
            paintSize=list.getModel().getSize();
            super.paintChildren(g);
        }
}
