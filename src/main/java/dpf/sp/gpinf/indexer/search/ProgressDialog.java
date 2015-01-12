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
package dpf.sp.gpinf.indexer.search;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.util.CancelableWorker;

public class ProgressDialog extends JDialog implements ActionListener, Runnable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static long millisToPopup = 500;
	private float scale = 0;

	private JLabel msg;
	private JProgressBar progressBar;
	private JButton button;
	private Component parent;

	private volatile boolean canceled = false, closed = false;

	private CancelableWorker task;

	public ProgressDialog(Component parent, CancelableWorker task, boolean indeterminate) {
		this(parent, task);
		progressBar.setIndeterminate(indeterminate);
	}

	public void setIndeterminate(boolean indeterminate) {
		progressBar.setIndeterminate(indeterminate);
	}

	public ProgressDialog(Component parent, CancelableWorker task) {
		super(SwingUtilities.windowForComponent(parent), Dialog.ModalityType.MODELESS);
		this.setAlwaysOnTop(true);
		this.setBounds(0, 0, 260, 140);
		this.setTitle("Progresso");
		this.parent = parent;
		this.task = task;

		msg = new JLabel("Pesquisando...");
		progressBar = new JProgressBar();
		button = new JButton("Cancelar");

		msg.setBounds(20, 10, 200, 20);
		progressBar.setBounds(20, 30, 200, 25);
		button.setBounds(140, 60, 80, 30);

		this.getContentPane().add(msg);
		this.getContentPane().add(progressBar);
		this.getContentPane().add(button);
		this.getContentPane().add(new JLabel());

		button.addActionListener(this);

		this.setFocusableWindowState(false);
		new Thread(this).start();
	}

	public void setNote(String note) {
		msg.setText(note);
	}

	public void setProgress(long progress) {
		progressBar.setValue((int) (progress * scale));
	}

	public void setMaximum(long max) {
		scale = 1000f / max;
		progressBar.setMaximum(1000);
	}

	public boolean isCanceled() {
		return canceled;
	}

	synchronized public void close() {
		closed = true;
		this.dispose();
	}

	@Override
	public void actionPerformed(ActionEvent evt) {
		if (evt.getSource() == button) {
			canceled = true;
			task.doCancel(false);
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
		final ProgressDialog dialog = this;

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				synchronized (dialog) {
					if (!closed) {
						dialog.setVisible(true);
						dialog.setLocationRelativeTo(parent);
					}
				}
			}
		});

	}
}
