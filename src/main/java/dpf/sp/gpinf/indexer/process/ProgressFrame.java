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
package dpf.sp.gpinf.indexer.process;

import gpinf.dev.data.EvidenceFile;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;

/**
 * Dialog de progresso do processamento, fornecendo previsão de término, velocidade e lista dos itens sendo processados.
 */
public class ProgressFrame extends JFrame implements PropertyChangeListener, WindowListener {

	private static final long serialVersionUID = -1130342847618772236L;
	private JProgressBar progressBar;
	private JLabel list;
	int indexed = 0, discovered = 0;
	long rate = 0;
	int volume, taskSize;
	private SwingWorker task;
	private Date indexStart;
	private HashMap<Integer, String> itens = new HashMap<Integer, String>();
	private Worker[] workers;
	private NumberFormat sizeFormat = NumberFormat.getNumberInstance();

	public ProgressFrame(SwingWorker task) {
		super(Versao.APP_NAME);

		this.setBounds(0, 0, 600, 300);
		this.setLocationRelativeTo(null);
		this.task = task;

		progressBar = new JProgressBar(0, 1);
		progressBar.setPreferredSize(new Dimension(600, 50));
		progressBar.setStringPainted(true);
		progressBar.setString("Inicializando...");

		list = new JLabel();
		list.setVerticalAlignment(SwingConstants.TOP);
		JScrollPane scrollPane = new JScrollPane(list);

		this.getContentPane().add(progressBar, BorderLayout.NORTH);
		this.getContentPane().add(scrollPane, BorderLayout.CENTER);
		this.addWindowListener(this);
	}

	private void updateString() {
		String msg = progressBar.getString();
		if (indexed > 0)
			msg = "Processando " + indexed + " / " + discovered;
		else if (discovered > 0)
			msg = "Localizados " + discovered + " arquivos";

		if (taskSize != 0 && indexStart != null) {
			long secsToEnd = ((long) taskSize - (long) volume) * ((new Date()).getTime() - indexStart.getTime()) / (((long) volume + 1) * 1000);
			msg += " - Término em " + secsToEnd / 60 + "m " + secsToEnd % 60 + "s";
		}
		progressBar.setString(msg);

	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		
		if ("processed".equals(evt.getPropertyName())) {
			indexed = (Integer) evt.getNewValue();
			updateString();
			list.setText(getItemList());
			
		} else if ("taskSize".equals(evt.getPropertyName())) {
			taskSize = (Integer) evt.getNewValue() ;
			progressBar.setMaximum(taskSize);
			
		} else if ("discovered".equals(evt.getPropertyName())) {
			discovered = (Integer) evt.getNewValue();
			updateString();
			
		} else if ("mensagem".equals(evt.getPropertyName())) {
			progressBar.setString((String) evt.getNewValue());
			list.setText("");
			
		} else if ("progresso".equals(evt.getPropertyName())) {
			if (indexStart == null)
				indexStart = new Date();
			volume = (Integer) evt.getNewValue();
			if (taskSize != 0)
				progressBar.setValue((volume));
			
			Date now = new Date();
			long interval = (now.getTime() - indexStart.getTime()) / 1000 + 1;
			rate = (long)volume * 1000000L * 3600L / (1024L * 1024L * 1024L * interval);
			
		} else if ("workers".equals(evt.getPropertyName())) {
			workers = (Worker[]) evt.getNewValue();
		}

	}

	private String getItemList() {
		if (workers == null)
			return "";

		String msg = "<html>Processando: " + rate + " GB/h<br>";
		for (int i = 0; i < workers.length; i++) {
			EvidenceFile evidence = workers[i].evidence;
			if (evidence != null){
				AbstractTask task = workers[i].runningTask;
				String taskName = "";
				if(task != null)
					taskName = task.getClass().getSimpleName() + ": ";
				String len = "";
				if(evidence.getLength() != null)
					len = " (" + sizeFormat.format(evidence.getLength()) + " bytes)";
				msg += workers[i].getName() + " - " + taskName + evidence.getPath() +  len  + "<br>";
			}
				
		}

		return msg;

	}

	@Override
	public void windowActivated(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowClosed(WindowEvent arg0) {

	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		task.cancel(true);

	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		// TODO Auto-generated method stub

	}

}