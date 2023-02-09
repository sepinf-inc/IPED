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
package iped.app.processing.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
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

import iped.app.ui.App;
import iped.app.ui.AppMain;
import iped.data.IItem;
import iped.engine.Version;
import iped.engine.core.Statistics;
import iped.engine.core.Worker;
import iped.engine.core.Worker.STATE;
import iped.engine.localization.Messages;
import iped.engine.task.AbstractTask;
import iped.engine.task.ExportFileTask;
import iped.engine.task.ParsingTask;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.util.UIPropertyListenerProvider;
import iped.parsers.standard.StandardParser;
import iped.utils.IconUtil;
import iped.utils.LocalizedFormat;

/**
 * Dialog de progresso do processamento, fornecendo previsão de término,
 * velocidade e lista dos itens sendo processados.
 */
public class ProgressFrame extends JFrame implements PropertyChangeListener, ActionListener {

    private static final long serialVersionUID = -1130342847618772236L;
    private JProgressBar progressBar;
    private JButton pause, openApp;
    private JLabel tasks, itens, stats, parsers;
    int indexed = 0, discovered = 0;
    long rate = 0, instantRate;
    int volume, taskSize;
    long secsToEnd;
    private UIPropertyListenerProvider task;
    private Date indexStart;
    private Worker[] workers;
    private NumberFormat sizeFormat = LocalizedFormat.getNumberInstance();
    private boolean paused = false;
    private String decodingDir = null;

    private class RestrictedSizeLabel extends JLabel {

        private static final long serialVersionUID = 1L;

		public Dimension getMaximumSize() {
            return this.getPreferredSize();
        }

        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            super.paintComponent(g2);
        }
    }

    public ProgressFrame(UIPropertyListenerProvider task) {
        super(Version.APP_NAME);
        setIconImages(IconUtil.getIconImages("process", "/iped/app/icon"));
        
        this.setBounds(0, 0, 800, 400);
        this.setLocationRelativeTo(null);
        this.task = task;

        progressBar = new JProgressBar(0, 1);
        progressBar.setPreferredSize(new Dimension(600, 40));
        progressBar.setStringPainted(true);
        progressBar.setString(Messages.getString("ProgressFrame.Starting")); //$NON-NLS-1$

        pause = new JButton(Messages.getString("ProgressFrame.Pause")); //$NON-NLS-1$
        pause.addActionListener(this);

        openApp = new JButton(Messages.getString("ProgressFrame.OpenApp")); //$NON-NLS-1$
        openApp.addActionListener(this);
        openApp.setEnabled(false);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(openApp);
        buttonPanel.add(pause);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));

        tasks = new RestrictedSizeLabel();
        itens = new RestrictedSizeLabel();
        stats = new RestrictedSizeLabel();
        parsers = new RestrictedSizeLabel();
        int sz = 8;
        tasks.setBorder(BorderFactory.createEmptyBorder(sz, sz, sz, sz));
        stats.setBorder(BorderFactory.createEmptyBorder(sz, sz, sz, sz));
        itens.setBorder(BorderFactory.createEmptyBorder(sz, sz, sz, sz));
        parsers.setBorder(BorderFactory.createEmptyBorder(sz, sz, sz, sz));
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
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent arg0) {
                task.cancel(true);
            }
        });
    }

    private void updateString() {
        String msg = progressBar.getString();
        if (indexed > 0) {
            msg = Messages.getString("ProgressFrame.Processing") + indexed + " / " + discovered; //$NON-NLS-1$ //$NON-NLS-2$
        } else if (discovered > 0) {
            msg = Messages.getString("ProgressFrame.Found") + discovered + Messages.getString("ProgressFrame.items"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        if (taskSize != 0 && indexStart != null) {
            secsToEnd = ((long) taskSize - (long) volume) * ((new Date()).getTime() - indexStart.getTime())
                    / (((long) volume + 1) * 1000);
            msg += Messages.getString("ProgressFrame.FinishIn") + secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 + "m " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    + secsToEnd % 60 + "s"; //$NON-NLS-1$
        } else if (decodingDir != null) {
            msg += " - " + decodingDir;
        }
        progressBar.setString(msg);

    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (indexStart == null) {
            indexStart = new Date();
        }

        if ("processed".equals(evt.getPropertyName())) { //$NON-NLS-1$
            indexed = (Integer) evt.getNewValue();
            updateString();
            tasks.setText(getTaskTimes());
            itens.setText(getItemList());
            stats.setText(getStats());
            parsers.setText(getParsersTime());
            if (indexed > 0)
                openApp.setEnabled(true);

        } else if ("taskSize".equals(evt.getPropertyName())) { //$NON-NLS-1$
            taskSize = (Integer) evt.getNewValue();
            progressBar.setMaximum(taskSize);

        } else if ("discovered".equals(evt.getPropertyName())) { //$NON-NLS-1$
            discovered = (Integer) evt.getNewValue();
            updateString();

        } else if ("decodingDir".equals(evt.getPropertyName())) { //$NON-NLS-1$
            decodingDir = (String) evt.getNewValue();

        } else if ("mensagem".equals(evt.getPropertyName())) { //$NON-NLS-1$
            progressBar.setString((String) evt.getNewValue());
            tasks.setText(getTaskTimes());
            itens.setText(getItemList());
            stats.setText(getStats());
            parsers.setText(getParsersTime());

        } else if ("progresso".equals(evt.getPropertyName())) { //$NON-NLS-1$
            long prevVolume = volume;
            volume = (Integer) evt.getNewValue();
            if (taskSize != 0) {
                progressBar.setValue((volume));
            }

            Date now = new Date();
            long interval = (now.getTime() - indexStart.getTime()) / 1000 + 1;
            rate = (long) volume * 1000000L * 3600L / ((1 << 30) * interval);
            instantRate = (long) (volume - prevVolume) * 1000000L * 3600L / (1 << 30) + 1;

        } else if ("workers".equals(evt.getPropertyName())) { //$NON-NLS-1$
            workers = (Worker[]) evt.getNewValue();
        }

    }

    private String getItemList() {
        if (workers == null) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder msg = new StringBuilder();
        msg.append(Messages.getString("ProgressFrame.CurrentItems")); //$NON-NLS-1$
        msg.append("<table cellspacing=0 cellpadding=1 border=1>"); //$NON-NLS-1$
        boolean hasWorkerAlive = false;
        for (int i = 0; i < workers.length; i++) {
            if (!workers[i].isAlive()) {
                continue;
            }
            hasWorkerAlive = true;
            msg.append("<tr><td>"); //$NON-NLS-1$
            msg.append(workers[i].getName());
            msg.append("</td><td>"); //$NON-NLS-1$
            AbstractTask task = workers[i].runningTask;
            if (workers[i].state == STATE.PAUSED) {
                msg.append(Messages.getString("ProgressFrame.Paused")); //$NON-NLS-1$
            } else if (workers[i].state == STATE.PAUSING) {
                msg.append(Messages.getString("ProgressFrame.Pausing")); //$NON-NLS-1$
            } else if (task != null) {
                msg.append(task.getName());
            } else {
                msg.append("  -  "); //$NON-NLS-1$
            }
            msg.append("</td><td>"); //$NON-NLS-1$
            IItem evidence = workers[i].evidence;
            if (evidence != null) {
                String len = ""; //$NON-NLS-1$
                if (evidence.getLength() != null) {
                    len = " (" + sizeFormat.format(evidence.getLength()) + " bytes)"; //$NON-NLS-1$ //$NON-NLS-2$
                }
                msg.append(evidence.getPath() + len);
            } else {
                msg.append(Messages.getString("ProgressFrame.WaitingItem")); //$NON-NLS-1$
            }
            msg.append("</td></tr>"); //$NON-NLS-1$
        }
        msg.append("</table>"); //$NON-NLS-1$
        if (!hasWorkerAlive) {
            return ""; //$NON-NLS-1$
        }
        return msg.toString();

    }

    private String getTaskTimes() {
        if (workers == null) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder msg = new StringBuilder();
        msg.append(Messages.getString("ProgressFrame.TaskTimes")); //$NON-NLS-1$
        msg.append("<table cellspacing=0 cellpadding=1 border=1>"); //$NON-NLS-1$
        long totalTime = 0;
        long[] taskTimes = new long[workers[0].tasks.size()];
        for (Worker worker : workers) {
            for (int i = 0; i < taskTimes.length; i++) {
                taskTimes[i] += worker.tasks.get(i).getTaskTime();
                totalTime += worker.tasks.get(i).getTaskTime();
            }
        }
        totalTime = totalTime / (1000000 * workers.length);
        if (totalTime == 0) {
            totalTime = 1;
        }
        for (int i = 0; i < taskTimes.length; i++) {
            AbstractTask task = workers[0].tasks.get(i);
            long sec = taskTimes[i] / (1000000 * workers.length);
            msg.append("<tr><td>"); //$NON-NLS-1$
            msg.append(task.getName());
            msg.append("</td><td>"); //$NON-NLS-1$
            msg.append(task.isEnabled() ? sec + "s (" + (100 * sec) / totalTime + "%)" : "-"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            msg.append("</td></tr>"); //$NON-NLS-1$
        }
        msg.append("</table>"); //$NON-NLS-1$
        return msg.toString();
    }

    private String getParsersTime() {
        if (ParsingTask.times.isEmpty())
            return ""; //$NON-NLS-1$
        StringBuilder msg = new StringBuilder();
        msg.append(Messages.getString("ProgressFrame.ParserTimes")); //$NON-NLS-1$
        msg.append("<table cellspacing=0 cellpadding=1 border=1>"); //$NON-NLS-1$
        long totalTime = 0;
        for (Worker worker : workers)
            for (AbstractTask task : worker.tasks)
                if (task.getClass().equals(ParsingTask.class))
                    totalTime += task.getTaskTime();
        totalTime = totalTime / (1000000 * workers.length);
        if (totalTime == 0)
            totalTime = 1;
        for (Object o : ParsingTask.times.entrySet().toArray()) {
            Entry<String, AtomicLong> e = (Entry<String, AtomicLong>) o;
            msg.append("<tr><td>"); //$NON-NLS-1$
            msg.append(e.getKey());
            msg.append("</td><td>"); //$NON-NLS-1$
            long sec = e.getValue().get() / (1000000 * workers.length);
            msg.append(sec + "s (" + (100 * sec) / totalTime + "%)"); //$NON-NLS-1$ //$NON-NLS-2$
            msg.append("</td></tr>"); //$NON-NLS-1$
        }
        msg.append("</table>"); //$NON-NLS-1$
        return msg.toString();
    }

    private String getStats() {
        if (Statistics.get() == null) {
            return "";
        }
        StringBuilder msg = new StringBuilder();
        startTable(msg);
        addTitle(msg, 2, Messages.getString("ProgressFrame.Statistics"));
        
        long time = (System.currentTimeMillis() - indexStart.getTime()) / 1000;
        startRow(msg, Messages.getString("ProgressFrame.ProcessingTime"));
        finishRow(msg, time / 3600 + "h " + (time / 60) % 60 + "m " + time % 60 + "s", Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.EstimatedEnd"));
        finishRow(msg, secsToEnd == 0 ? "-" : secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 + "m " + secsToEnd % 60 + "s", Align.RIGHT);
        
        startRow(msg,  Messages.getString("ProgressFrame.MeanSpeed"));
        finishRow(msg, rate + " GB/h", Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.CurrentSpeed"));
        finishRow(msg, instantRate + " GB/h", Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.VolumeFound")); 
        finishRow(msg, sizeFormat.format(Statistics.get().getCaseData().getDiscoveredVolume() >>> 20) + " MB", Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.VolumeProcessed"));
        finishRow(msg, sizeFormat.format(Statistics.get().getVolume() >>> 20) + " MB", Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.ItemsFound"));
        finishRow(msg, Statistics.get().getCaseData().getDiscoveredEvidences(), Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.ItemsProcessed"));
        finishRow(msg, Statistics.get().getProcessed(), Align.RIGHT);
        
        startRow(msg,  Messages.getString("ProgressFrame.ActiveProcessed"));
        finishRow(msg, Statistics.get().getActiveProcessed(), Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.SubitemsProcessed"));
        finishRow(msg, Statistics.get().getSubitemsDiscovered(), Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.Carved"));
        finishRow(msg, BaseCarveTask.getItensCarved(), Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.CarvedDiscarded"));
        finishRow(msg, Statistics.get().getCorruptCarveIgnored(), Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.Exported"));
        finishRow(msg, ExportFileTask.getItensExtracted(), Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.Ignored"));
        finishRow(msg, Statistics.get().getIgnored(), Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.ParsingErrors"));
        finishRow(msg, StandardParser.parsingErrors, Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.ReadErrors"));
        finishRow(msg, Statistics.get().getIoErrors(), Align.RIGHT);
        
        startRow(msg, Messages.getString("ProgressFrame.Timeouts"));
        finishRow(msg, Statistics.get().getTimeouts(), Align.RIGHT);
        
        finishTable(msg);
        return msg.toString();
    }
    
    private void startTable(StringBuilder sb) {
        String borderColor = "#DDDDDD";
        String cellColor = "#FAFAFC";
        String titleColor = "#99AADD";
        sb.append("<html><head><style> ");
        sb.append("td { ");
        sb.append("border-bottom: 1px solid ").append(borderColor).append("; ");
        sb.append("border-right: 1px solid ").append(borderColor).append("; ");
        sb.append("border-top: 0px; ");
        sb.append("border-left: 0px; ");
        sb.append("border-spacing: 0px; ");
        sb.append("background-color: ").append(cellColor).append("; ");
        sb.append("padding: 3px; ");
        sb.append("} ");
        sb.append("td.t { ");
        sb.append("border-top: 1px solid ").append(borderColor).append("; ");
        sb.append("border-left: 1px solid ").append(borderColor).append("; ");
        sb.append("text-align: center; ");
        sb.append("background-color: ").append(titleColor).append("; ");
        sb.append("} ");
        sb.append("td.s { ");
        sb.append("border-left: 1px solid ").append(borderColor).append("; ");
        sb.append("} ");
        sb.append("td.c { ");
        sb.append("text-align: center; ");
        sb.append("} ");
        sb.append("td.r { ");
        sb.append("text-align: right; ");
        sb.append("} ");
        sb.append("table, tr { ");
        sb.append("border-spacing: 0px; ");
        sb.append("} ");
        sb.append("</style></head><body>");
        sb.append("<table>");
    }

    private void addTitle(StringBuilder sb, int colSpan, String title) {
        sb.append("<tr><td class=t colspan=").append(colSpan);
        sb.append(">").append(title).append("</td></tr>");
    }

    private void startRow(StringBuilder sb, Object content) {
        sb.append("<tr>");
        addCell(sb, content, Align.LEFT);
    }
    
    private void finishRow(StringBuilder sb, Object content) {
        finishRow(sb, content, Align.LEFT);
        sb.append("</tr>");
    }

    private void finishRow(StringBuilder sb, Object content, Align align) {
        addCell(sb, content, align);
        sb.append("</tr>");
    }
    
    private void addCell(StringBuilder sb, Object content) {
        addCell(sb, content, Align.LEFT);
    }

    private void addCell(StringBuilder sb, Object content, Align align) {
        sb.append("<td");
        if (align == Align.CENTER)
            sb.append(" class=c");
        else if (align == Align.RIGHT)
            sb.append(" class=r");
        sb.append(">").append(content).append("</td>");
    }

    private void finishTable(StringBuilder sb) {
        sb.append("</body></table></html>");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource().equals(pause)) {
            paused = !paused;
            if (paused)
                pause.setText(Messages.getString("ProgressFrame.Continue")); //$NON-NLS-1$
            else
                pause.setText(Messages.getString("ProgressFrame.Pause")); //$NON-NLS-1$

            for (Worker worker : workers) {
                synchronized (worker) {
                    worker.state = paused ? Worker.STATE.PAUSING : Worker.STATE.RUNNING;
                }
            }
        }

        if (e.getSource().equals(openApp)) {
            if (!App.get().isVisible()) {
                JOptionPane.showMessageDialog(this, Messages.getString("ProgressFrame.IncompleteProcessing")); //$NON-NLS-1$
                new AppMain().start(workers[0].output.getParentFile(), workers[0].manager, null);
            } else
                JOptionPane.showMessageDialog(this, Messages.getString("ProgressFrame.AlreadyOpen")); //$NON-NLS-1$
        }

    }

    enum Align {
        LEFT, CENTER, RIGHT;
    }
}
