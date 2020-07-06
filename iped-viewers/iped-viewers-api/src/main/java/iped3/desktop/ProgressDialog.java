/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped3.desktop;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dialog.ModalityType;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import iped3.desktop.util.Messages;

public class ProgressDialog implements ActionListener, Runnable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static long DEFAULT_MILLIS_TO_POPUP = 500;

    private long millisToPopup;
    private volatile float scale = 0;
    private volatile JLabel msg;
    private String note = Messages.getString("ProgressDialog.Searching"); //$NON-NLS-1$
    private volatile JProgressBar progressBar;
    private JButton button;
    private Component parent;
    private JDialog dialog;
    private boolean indeterminate;
    private Dialog.ModalityType modal;
    private Object lock = this;

    private volatile boolean canceled = false, closed = false;

    private CancelableWorker task;

    private int length = 0;
    private int extraWidth = 0;

    public void setTask(CancelableWorker task) {
        this.task = task;
    }

    public ProgressDialog(Component parent, CancelableWorker task) {
        this(parent, task, false);
    }

    public ProgressDialog(Component parent, CancelableWorker task, int lines) {
        this(parent, task, false);
        if (lines <= 0)
            lines = 1;
        this.length = 20 * lines;
    }

    public ProgressDialog(Component parent, CancelableWorker task, boolean indeterminate) {
        this(parent, task, indeterminate, DEFAULT_MILLIS_TO_POPUP, Dialog.ModalityType.MODELESS);
    }

    public void setIndeterminate(boolean value) {
        this.indeterminate = value;
        if (progressBar != null)
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    progressBar.setIndeterminate(indeterminate);
                }
            });
    }

    public ProgressDialog(Component parent, CancelableWorker task, boolean indeterminate, long millisToPopup,
            Dialog.ModalityType modal) {
        this.parent = parent;
        this.task = task;
        this.millisToPopup = millisToPopup;
        this.indeterminate = indeterminate;
        this.modal = modal;

        if (millisToPopup == 0)
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    createDialog();
                }
            });
        else
            new Thread(this).start();
    }

    public synchronized void setVisible() {
        if (dialog == null)
            createDialog();
    }

    private void createDialog() {
        if (parent instanceof Frame)
            dialog = new JDialog((Frame) parent, modal);
        else if (parent instanceof Dialog)
            dialog = new JDialog((Dialog) parent, modal);
        else if (parent != null)
            dialog = new JDialog(SwingUtilities.windowForComponent(parent), modal);
        else
            dialog = new JDialog(null, modal);

        dialog.setAlwaysOnTop(true);
        dialog.setBounds(0, 0, 260 + extraWidth, 140 + length);
        dialog.setTitle(Messages.getString("ProgressDialog.Progress")); //$NON-NLS-1$

        msg = new JLabel(note);
        progressBar = new JProgressBar();
        progressBar.setMaximum(100);
        button = new JButton(Messages.getString("ProgressDialog.Cancel")); //$NON-NLS-1$

        msg.setBounds(20, 10, 200 + extraWidth, 20 + length);
        progressBar.setBounds(20, 30 + length, 200 + extraWidth, 25);
        button.setBounds(140 + extraWidth, 60 + length, 80, 30);

        dialog.getContentPane().add(msg);
        dialog.getContentPane().add(progressBar);
        dialog.getContentPane().add(button);
        dialog.getContentPane().add(new JLabel());
        button.addActionListener(this);

        if (modal.equals(ModalityType.MODELESS))
            dialog.setFocusableWindowState(false);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        dialog.setLocationRelativeTo(parent);

        progressBar.setIndeterminate(indeterminate);
        if (indeterminate)
            progressBar.setValue(100);
    }

    public void setNote(final String note) {
        this.note = note;
        if (msg != null)
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    msg.setText(note);
                }
            });
    }

    public void setProgress(final long progress) {
        if (progressBar != null && (progress * scale - progressBar.getValue()) > 0)
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    progressBar.setValue((int) (progress * scale));
                }
            });
    }

    public void setMaximum(final long max) {
        scale = 100f / max;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void close() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                synchronized (lock) {
                    closed = true;
                    if (dialog != null) {
                        dialog.dispose();
                    }
                }
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() == button) {
            canceled = true;
            if (task != null)
                task.doCancel(true);
            this.close();
        }
    }

    @Override
    public void run() {
        try {
            Thread.sleep(millisToPopup);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                synchronized (lock) {
                    if (!closed && dialog == null) {
                        createDialog();
                    }
                }
            }
        });

    }

    public int getExtraWidth() {
        return extraWidth;
    }

    public void setExtraWidth(int extraWidth) {
        this.extraWidth = extraWidth;
    }
}
