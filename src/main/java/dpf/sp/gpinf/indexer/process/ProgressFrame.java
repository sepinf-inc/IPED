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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.Versao;
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.AppMain;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.Worker.STATE;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.process.task.BaseCarveTask;
import dpf.sp.gpinf.indexer.process.task.ExportFileTask;
import dpf.sp.gpinf.indexer.process.task.ParsingTask;
import gpinf.dev.data.EvidenceFile;

/**
 * Dialog de progresso do processamento, fornecendo previsão de término, velocidade e lista dos
 * itens sendo processados.
 */
public class ProgressFrame extends JFrame implements PropertyChangeListener, WindowListener, ActionListener {

  private static final long serialVersionUID = -1130342847618772236L;
  private JProgressBar progressBar;
  private JButton pause, openApp;
  private JLabel tasks, itens, stats, parsers;
  int indexed = 0, discovered = 0;
  long rate = 0, instantRate;
  int volume, taskSize;
  long secsToEnd;
  private SwingWorker task;
  private Date indexStart;
  private Worker[] workers;
  private NumberFormat sizeFormat = NumberFormat.getNumberInstance();
  private SimpleDateFormat df = new SimpleDateFormat("dd/MM HH:mm:ss");
  private boolean paused = false;

  private class RestrictedSizeLabel extends JLabel {

    public Dimension getMaximumSize() {
      return this.getPreferredSize();
    }
  }

  public ProgressFrame(SwingWorker task) {
    super(Versao.APP_NAME);

    this.setBounds(0, 0, 800, 400);
    this.setLocationRelativeTo(null);
    this.task = task;

    progressBar = new JProgressBar(0, 1);
    progressBar.setPreferredSize(new Dimension(600, 40));
    progressBar.setStringPainted(true);
    progressBar.setString("Inicializando...");
    
    pause = new JButton("Pausar");
    pause.addActionListener(this);
    
    openApp = new JButton("Abrir Aplicativo");
    openApp.addActionListener(this);
    openApp.setEnabled(false);
    
    JPanel buttonPanel = new JPanel();//new BorderLayout());
    buttonPanel.add(openApp);//, BorderLayout.WEST);
    buttonPanel.add(pause);//, BorderLayout.EAST);

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));

    tasks = new RestrictedSizeLabel();
    itens = new RestrictedSizeLabel();
    stats = new RestrictedSizeLabel();
    parsers = new RestrictedSizeLabel();
    tasks.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    stats.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    itens.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    parsers.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    stats.setAlignmentY(TOP_ALIGNMENT);
    itens.setAlignmentY(TOP_ALIGNMENT);
    tasks.setAlignmentY(TOP_ALIGNMENT);
    parsers.setAlignmentY(TOP_ALIGNMENT);

    panel.add(stats);
    panel.add(tasks);
    panel.add(parsers);
    panel.add(itens);
    JScrollPane scrollPane = new JScrollPane(panel);
    
    JPanel topPanel = new JPanel();
    topPanel.setLayout(new BorderLayout());
    topPanel.add(progressBar, BorderLayout.CENTER);
    topPanel.add(buttonPanel, BorderLayout.EAST);

    this.getContentPane().add(topPanel, BorderLayout.NORTH);
    this.getContentPane().add(scrollPane, BorderLayout.CENTER);
    this.addWindowListener(this);
  }

  private void updateString() {
    String msg = progressBar.getString();
    if (indexed > 0) {
      msg = "Processando " + indexed + " / " + discovered;
    } else if (discovered > 0) {
      msg = "Localizados " + discovered + " arquivos";
    }

    if (taskSize != 0 && indexStart != null) {
      secsToEnd = ((long) taskSize - (long) volume) * ((new Date()).getTime() - indexStart.getTime()) / (((long) volume + 1) * 1000);
      msg += " - Término em " + secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 + "m " + secsToEnd % 60 + "s";
    }
    progressBar.setString(msg);

  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (indexStart == null) {
      indexStart = new Date();
    }

    if ("processed".equals(evt.getPropertyName())) {
      indexed = (Integer) evt.getNewValue();
      updateString();
      tasks.setText(getTaskTimes());
      itens.setText(getItemList());
      stats.setText(getStats());
      parsers.setText(getParsersTime());
      if(indexed > 0)
    	  openApp.setEnabled(true);

    } else if ("taskSize".equals(evt.getPropertyName())) {
      taskSize = (Integer) evt.getNewValue();
      progressBar.setMaximum(taskSize);

    } else if ("discovered".equals(evt.getPropertyName())) {
      discovered = (Integer) evt.getNewValue();
      updateString();

    } else if ("mensagem".equals(evt.getPropertyName())) {
      progressBar.setString((String) evt.getNewValue());
      tasks.setText(getTaskTimes());
      itens.setText(getItemList());
      stats.setText(getStats());
      parsers.setText(getParsersTime());

    } else if ("progresso".equals(evt.getPropertyName())) {
      long prevVolume = volume;
      volume = (Integer) evt.getNewValue();
      if (taskSize != 0) {
        progressBar.setValue((volume));
      }

      Date now = new Date();
      long interval = (now.getTime() - indexStart.getTime()) / 1000 + 1;
      rate = (long) volume * 1000000L * 3600L / ((1 << 30) * interval);
      instantRate = (long) (volume - prevVolume) * 1000000L * 3600L / (1 << 30) + 1;

    } else if ("workers".equals(evt.getPropertyName())) {
      workers = (Worker[]) evt.getNewValue();
    }

  }

  private String getItemList() {
    if (workers == null) {
      return "";
    }
    StringBuilder msg = new StringBuilder();
    msg.append("<html>Itens em processamento:<br>");
    msg.append("<table cellspacing=0 cellpadding=1 border=1>");
    boolean hasWorkerAlive = false;
    for (int i = 0; i < workers.length; i++) {
      if (!workers[i].isAlive()) {
        continue;
      }
      hasWorkerAlive = true;
      msg.append("<tr><td>");
      msg.append(workers[i].getName());
      msg.append("</td><td>");
      AbstractTask task = workers[i].runningTask;
      if(workers[i].state == STATE.PAUSED){
    	  msg.append("[PAUSED]");
      }else if(workers[i].state == STATE.PAUSING){
    	  msg.append("[PAUSING...]");
      }else if (task != null) {
        msg.append(task.getClass().getSimpleName());
      } else {
        msg.append("  -  ");
      }
      msg.append("</td><td>");
      EvidenceFile evidence = workers[i].evidence;
      if (evidence != null) {
        String len = "";
        if (evidence.getLength() != null) {
          len = " (" + sizeFormat.format(evidence.getLength()) + " bytes)";
        }
        msg.append(evidence.getPath() + len);
      } else {
        msg.append("Aguardando item...");
      }
      msg.append("</td></tr>");
    }
    msg.append("</table>");
    if (!hasWorkerAlive) {
      return "";
    }
    return msg.toString();

  }

  private String getTaskTimes() {
    if (workers == null) {
      return "";
    }
    StringBuilder msg = new StringBuilder();
    msg.append("<html>Tempos de execução por tarefa:<br>");
    msg.append("<table cellspacing=0 cellpadding=1 border=1>");
    long totalTime = 0;
    long[] taskTimes = new long[workers[0].tasks.size()];
    for (Worker worker : workers) {
      for (int i = 0; i < taskTimes.length; i++) {
        taskTimes[i] += worker.tasks.get(i).getTaskTime();
        totalTime += worker.tasks.get(i).getTaskTime();
      }
    }
    totalTime = totalTime / (1000000 * Configuration.numThreads);
    if (totalTime == 0) {
      totalTime = 1;
    }
    for (int i = 0; i < taskTimes.length; i++) {
      AbstractTask task = workers[0].tasks.get(i);
      long sec = taskTimes[i] / (1000000 * Configuration.numThreads);
      msg.append("<tr><td>");
      msg.append(task.getClass().getSimpleName());
      msg.append("</td><td>");
      msg.append(task.isEnabled() ? sec + "s (" + (100 * sec) / totalTime + "%)" : "-");
      msg.append("</td></tr>");
    }
    msg.append("</table>");
    return msg.toString();
  }
  
  private String getParsersTime(){
	  if(ParsingTask.times.isEmpty())
		  return "";
	  StringBuilder msg = new StringBuilder();
	  msg.append("<html>Tempos por Parser:<br>");
	  msg.append("<table cellspacing=0 cellpadding=1 border=1>");
	  long totalTime = (System.currentTimeMillis() - indexStart.getTime()) / 1000 + 1;
	  for(Entry<String , AtomicLong> e : ParsingTask.times.entrySet()){
	    	msg.append("<tr><td>");
	        msg.append(e.getKey());
	        msg.append("</td><td>");
	        long sec = e.getValue().get() / (1000000 * Configuration.numThreads);
	        msg.append(sec + "s (" + (100 * sec) / totalTime + "%)");
	        msg.append("</td></tr>");
	    }
	  msg.append("</table>");
	  return msg.toString();
  }

  private String getStats() {
    if (Statistics.get() == null) {
      return "";
    }
    StringBuilder msg = new StringBuilder();
    msg.append("<html>Estatísticas:<br>");
    msg.append("<table cellspacing=0 cellpadding=1 border=1>");
    msg.append("<tr><td>");
    msg.append("Tempo decorrido");
    msg.append("</td><td>");
    long time = (System.currentTimeMillis() - indexStart.getTime()) / 1000;
    msg.append(time / 3600 + "h " + (time / 60) % 60 + "m " + time % 60 + "s");
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Término estimado");
    msg.append("</td><td>");
    msg.append(secsToEnd == 0 ? "-" : secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 + "m " + secsToEnd % 60 + "s");
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Velocidade média");
    msg.append("</td><td>");
    msg.append(rate + " GB/h");
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Velocidade atual");
    msg.append("</td><td>");
    msg.append(instantRate + " GB/h");
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Volume descoberto");
    msg.append("</td><td>");
    long discoveredVol = Statistics.get().caseData.getDiscoveredVolume() / (1 << 20);
    msg.append(NumberFormat.getNumberInstance().format(discoveredVol) + " MB");
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Volume processado");
    msg.append("</td><td>");
    msg.append(NumberFormat.getNumberInstance().format(Statistics.get().getVolume() / (1 << 20)) + " MB");
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Itens descobertos");
    msg.append("</td><td>");
    msg.append(Statistics.get().caseData.getDiscoveredEvidences());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Itens processados");
    msg.append("</td><td>");
    msg.append(Statistics.get().getProcessed());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Itens ativos processados");
    msg.append("</td><td>");
    msg.append(Statistics.get().getActiveProcessed());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Subitens extraídos");
    msg.append("</td><td>");
    msg.append(ParsingTask.getSubitensDiscovered());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Itens de carving");
    msg.append("</td><td>");
    msg.append(BaseCarveTask.getItensCarved());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Carvings ignorados");
    msg.append("</td><td>");
    msg.append(Statistics.get().getCorruptCarveIgnored());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Itens exportados");
    msg.append("</td><td>");
    msg.append(ExportFileTask.getItensExtracted());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Itens ignorados");
    msg.append("</td><td>");
    msg.append(Statistics.get().getIgnored());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Erros de parsing");
    msg.append("</td><td>");
    msg.append(IndexerDefaultParser.parsingErrors);
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Erros de Leitura");
    msg.append("</td><td>");
    msg.append(Statistics.get().getIoErrors());
    msg.append("</td></tr>");
    msg.append("<tr><td>");
    msg.append("Timeouts");
    msg.append("</td><td>");
    msg.append(Statistics.get().getTimeouts());
    msg.append("</td></tr>");
    msg.append("</table>");
    return msg.toString();
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

@Override
public void actionPerformed(ActionEvent e) {
	if(e.getSource().equals(pause)){
		paused = !paused;
		if(paused)
			pause.setText("Continuar");
		else
			pause.setText("Pausar");
		
		for (Worker worker : workers) {
			synchronized(worker){
				worker.state = paused ? Worker.STATE.PAUSING : Worker.STATE.RUNNING;
			}
		}
	}
	if(e.getSource().equals(openApp)){
		if(!App.get().isVisible()){
			JOptionPane.showMessageDialog(this, "A árvore de diretórios só estará completa ao fim do processamento!");
			new AppMain().start(workers[0].output.getParentFile(), workers[0].manager, null);
		}else
			JOptionPane.showMessageDialog(this, "O aplicativo de análise já está aberto!");
	}
}

}
