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
import java.awt.RenderingHints.Key;
import java.awt.Taskbar;
import java.awt.Taskbar.State;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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
import iped.engine.util.Util;
import iped.parsers.standard.StandardParser;
import iped.utils.EmojiUtil;
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
    private int prevVolume;
    private boolean discoverEnded;
    private long rate, instantRate;
    private long secsToEnd;
    private long processingStart;
    private Worker[] workers;
    private String[] lastWorkerTaskItemId;
    private long[] lastWorkerTime;
    private static final NumberFormat nf = LocalizedFormat.getNumberInstance();
    private static final DecimalFormat pct = LocalizedFormat.getDecimalInstance("0.0%");
    private boolean paused = false;
    private String decodingDir = null;
    private long physicalMemory;
    private static final Map<String, Long> timesPerParser = new TreeMap<String, Long>();

    private static class RestrictedSizeLabel extends JLabel {

        private static final long serialVersionUID = 1L;

        private static final RenderingHints renderingHints;

        static {
            Map<Key, Object> hints = new HashMap<>();
            try {
                @SuppressWarnings("unchecked")
                Map<Key, Object> desktopHints = (Map<Key, Object>) Toolkit.getDefaultToolkit()
                        .getDesktopProperty("awt.font.desktophints");
                if (desktopHints != null) {
                    hints.putAll(desktopHints);
                }
            } catch (Exception e) {
            }
            hints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            renderingHints = new RenderingHints(hints);
        }

        public Dimension getMaximumSize() {
            return this.getPreferredSize();
        }

        public void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            RenderingHints saveHints = g2.getRenderingHints();
            g2.setRenderingHints(renderingHints);
            super.paintComponent(g2);
            g2.setRenderingHints(saveHints);
        }
    }

    public ProgressFrame(UIPropertyListenerProvider task) {
        super(Version.APP_NAME);
        setIconImages(IconUtil.getIconImages("process", "/iped/app/icon"));

        this.setBounds(0, 0, 800, 400);
        this.setLocationRelativeTo(null);

        progressBar = new JProgressBar(0, 1);
        progressBar.setPreferredSize(new Dimension(600, 40));
        progressBar.setStringPainted(true);
        progressBar.setString(Messages.getString("ProgressFrame.Starting")); //$NON-NLS-1$

        pause = new JButton(Messages.getString("ProgressFrame.Pause")); //$NON-NLS-1$
        pause.addActionListener(this);
        pause.setEnabled(false);

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

        int sz = 10;
        stats.setBorder(BorderFactory.createEmptyBorder(sz, sz, sz, sz));
        tasks.setBorder(BorderFactory.createEmptyBorder(sz, 0, sz, sz));
        parsers.setBorder(BorderFactory.createEmptyBorder(sz, 0, sz, sz));
        itens.setBorder(BorderFactory.createEmptyBorder(sz, 0, sz, sz));

        stats.setAlignmentY(TOP_ALIGNMENT);
        tasks.setAlignmentY(TOP_ALIGNMENT);
        parsers.setAlignmentY(TOP_ALIGNMENT);
        itens.setAlignmentY(TOP_ALIGNMENT);

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

    private void update() {
        Statistics s = Statistics.get();
        if (s == null) {
            return;
        }
        // Get volume/item processed/total 
        int totalVolume = (int)(Statistics.get().getCaseData().getDiscoveredVolume() >>> 20); // Converted to MB
        int totalItems = Statistics.get().getCaseData().getDiscoveredEvidences();
        int processedVolume = (int)(Statistics.get().getVolume() >>> 20); // Converted to MB
        int processedItems = Statistics.get().getProcessed();

        progressBar.setMaximum(totalVolume);
        
        tasks.setText(getTaskTimes());
        itens.setText(getItemList());
        stats.setText(getStats());
        parsers.setText(getParserTimes());
        if (processedItems > 0)
            openApp.setEnabled(true);

        if (discoverEnded) {
            progressBar.setValue(processedVolume);
        }

        long interval = (System.currentTimeMillis() - processingStart) / 1000 + 1;
        rate = processedVolume * 3600L / ((1 << 10) * interval);
        instantRate = (processedVolume - prevVolume) * 3600L / (1 << 10) + 1;

        String msg = progressBar.getString();
        if (processedItems > 0) {
            msg = Messages.getString("ProgressFrame.Processing") + processedItems + " / " + totalItems;
        } else if (totalItems > 0) {
            msg = Messages.getString("ProgressFrame.Found") + totalItems + Messages.getString("ProgressFrame.items");
        }

        if (discoverEnded && processingStart != 0) {
            secsToEnd = (totalVolume - processedVolume) * (System.currentTimeMillis() - processingStart)
                    / ((processedVolume + 1) * 1000L);
            msg += Messages.getString("ProgressFrame.FinishIn") + secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 + "m "
                    + secsToEnd % 60 + "s";
        } else if (decodingDir != null) {
            msg += " - " + decodingDir;
        }
        progressBar.setString(msg);
        updateTaskBar(totalVolume, processedVolume, discoverEnded);
        prevVolume = processedVolume;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (processingStart == 0) {
            processingStart = System.currentTimeMillis();
            physicalMemory = Util.getPhysicalMemorySize();
            updateTaskBar(0, 0, false);
        }

        if ("discoverEnded".equals(evt.getPropertyName())) {
            discoverEnded = true;
            update();
            
        } else if ("update".equals(evt.getPropertyName())) {
            update();

        } else if ("decodingDir".equals(evt.getPropertyName())) { //$NON-NLS-1$
            decodingDir = (String) evt.getNewValue();

        } else if ("mensagem".equals(evt.getPropertyName())) { //$NON-NLS-1$
            progressBar.setString((String) evt.getNewValue());
            tasks.setText(getTaskTimes());
            itens.setText(getItemList());
            stats.setText(getStats());
            parsers.setText(getParserTimes());

        } else if ("workers".equals(evt.getPropertyName())) { //$NON-NLS-1$
            workers = (Worker[]) evt.getNewValue();
            lastWorkerTaskItemId = new String[workers.length];
            lastWorkerTime = new long[workers.length];
            pause.setEnabled(true);
        }
    }

    private String getItemList() {
        if (workers == null)
            return "";
        StringBuilder msg = new StringBuilder();
        startTable(msg);
        addTitle(msg, 4, Messages.getString("ProgressFrame.CurrentItems"));

        long now = System.currentTimeMillis();
        boolean hasWorkerAlive = false;
        for (int i = 0; i < workers.length; i++) {
            Worker worker = workers[i];
            if (!worker.isAlive())
                continue;
            hasWorkerAlive = true;

            AbstractTask task = worker.runningTask;
            String taskName = task == null ? null : task.getName();

            IItem evidence = worker.evidence;

            String wt = "-";
            int pct = -1;
            if (taskName != null && evidence != null) {
                String taskId = taskName + evidence.getId();
                if (!taskId.equals(lastWorkerTaskItemId[i])) {
                    lastWorkerTime[i] = now;
                    lastWorkerTaskItemId[i] = taskId;
                } else if (worker.state != STATE.PAUSED) {
                    long t = (now - lastWorkerTime[i]) / 1000;
                    wt = t < 60 ? t + "s" : t / 60 + "m";
                    pct = (int) Math.min(t / 30, 60);
                }
            } else {
                lastWorkerTaskItemId[i] = null;
            }

            startRow(msg, worker.getName(), worker.state != STATE.PAUSED, pct);

            if (worker.state == STATE.PAUSED) {
                addCell(msg, Messages.getString("ProgressFrame.Paused"), Align.CENTER);
            } else if (worker.state == STATE.PAUSING) {
                addCell(msg, Messages.getString("ProgressFrame.Pausing"), Align.CENTER);
            } else if (task != null) {
                addCell(msg, taskName);
            } else {
                addCell(msg, "-", Align.CENTER);
            }

            addCell(msg, wt, wt.equals("-") ? Align.CENTER : Align.RIGHT);

            if (evidence != null) {
                String s = evidence.getPath();
                if (evidence.getLength() != null && evidence.getLength() > 0)
                    s += " (" + nf.format(evidence.getLength()) + " bytes)";
                finishRow(msg, clean(s));
            } else {
                finishRow(msg, Messages.getString("ProgressFrame.WaitingItem"));
            }
        }
        finishTable(msg);
        if (!hasWorkerAlive)
            return "";
        return msg.toString();
    }

    private String getTaskTimes() {
        if (workers == null) {
            return "";
        }
        StringBuilder msg = new StringBuilder();
        startTable(msg);
        addTitle(msg, 3, Messages.getString("ProgressFrame.TaskTimes"));

        long totalTime = 0;
        long[] taskTimes = new long[workers[0].tasks.size()];
        for (Worker worker : workers) {
            for (int i = 0; i < taskTimes.length; i++) {
                long time = worker.tasks.get(i).getTaskTime();
                taskTimes[i] += time;
                totalTime += time;
            }
        }
        if (totalTime < 1)
            totalTime = 1;

        for (int i = 0; i < taskTimes.length; i++) {
            AbstractTask task = workers[0].tasks.get(i);
            if (task.isEnabled()) {
                long time = taskTimes[i];
                long sec = time / (1000000 * workers.length);
                int pct = (int) ((100 * time + totalTime / 2) / totalTime);  // Round percentage

                startRow(msg, task.getName(), pct);
                addCell(msg, nf.format(sec) + "s", Align.RIGHT);
                finishRow(msg, pct + "%", Align.RIGHT);
            } else {
                startRow(msg, task.getName(), false);
                addCell(msg, "-", Align.CENTER);
                finishRow(msg, "-", Align.CENTER);
            }
        }

        finishTable(msg);
        return msg.toString();
    }

    private String getParserTimes() {
        ParsingTask.copyTimesPerParser(timesPerParser);
        if (timesPerParser.isEmpty())
            return "";
        StringBuilder msg = new StringBuilder();
        startTable(msg);
        addTitle(msg, 3, Messages.getString("ProgressFrame.ParserTimes"));

        long totalTime = 0;
        for (long parserTime : timesPerParser.values()) {
            totalTime += parserTime;
        }
        if (totalTime < 1)
            totalTime = 1;

        for (String parserName : timesPerParser.keySet()) {
            long time = timesPerParser.get(parserName);
            long sec = time / (1000000 * workers.length);
            int pct = (int) ((100 * time + totalTime / 2) / totalTime); // Round percentage

            startRow(msg, parserName, pct);
            addCell(msg, nf.format(sec) + "s", Align.RIGHT);
            finishRow(msg, pct + "%", Align.RIGHT);
        }

        finishTable(msg);
        return msg.toString();
    }

    private String getStats() {
        if (Statistics.get() == null)
            return "";
        StringBuilder msg = new StringBuilder();
        startTable(msg);
        addTitle(msg, 2, Messages.getString("ProgressFrame.Statistics"));

        long time = (System.currentTimeMillis() - processingStart) / 1000;
        startRow(msg, Messages.getString("ProgressFrame.ProcessingTime"));
        finishRow(msg, time / 3600 + "h " + (time / 60) % 60 + "m " + time % 60 + "s", Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.EstimatedEnd"));
        finishRow(msg,
                secsToEnd == 0 ? "-" : secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 + "m " + secsToEnd % 60 + "s",
                Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.MeanSpeed"));
        finishRow(msg, nf.format(rate) + " GB/h", Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.CurrentSpeed"));
        finishRow(msg, nf.format(instantRate) + " GB/h", Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.VolumeFound"));
        finishRow(msg, formatMB(Statistics.get().getCaseData().getDiscoveredVolume()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.VolumeProcessed"));
        finishRow(msg, formatMB(Statistics.get().getVolume()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.ItemsFound"));
        finishRow(msg, nf.format(Statistics.get().getCaseData().getDiscoveredEvidences()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.ItemsProcessed"));
        finishRow(msg, nf.format(Statistics.get().getProcessed()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.ActiveProcessed"));
        finishRow(msg, nf.format(Statistics.get().getActiveProcessed()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.SubitemsProcessed"));
        finishRow(msg, nf.format(Statistics.get().getSubitemsDiscovered()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.Carved"));
        finishRow(msg, nf.format(BaseCarveTask.getItensCarved()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.CarvedDiscarded"));
        finishRow(msg, nf.format(Statistics.get().getCorruptCarveIgnored()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.Exported"));
        finishRow(msg, nf.format(ExportFileTask.getItensExtracted()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.Ignored"));
        finishRow(msg, nf.format(Statistics.get().getIgnored()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.ParsingErrors"));
        finishRow(msg, nf.format(StandardParser.parsingErrors), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.ReadErrors"));
        finishRow(msg, nf.format(Statistics.get().getIoErrors()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.Timeouts"));
        finishRow(msg, nf.format(Statistics.get().getTimeouts()), Align.RIGHT);

        // Some environment information
        skipRow(msg, 2);
        addTitle(msg, 2, Messages.getString("ProgressFrame.Environment"));
        addEnvironmentInfo(msg);

        finishTable(msg);
        return msg.toString();
    }

    private void addEnvironmentInfo(StringBuilder msg) {
        startRow(msg, Messages.getString("ProgressFrame.JavaVersion"));
        finishRow(msg, Runtime.version(), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.FreeMemory"));
        finishRow(msg, formatMB(Runtime.getRuntime().freeMemory()), Align.RIGHT);

        startRow(msg, Messages.getString("ProgressFrame.TotalMemory"));
        finishRow(msg, formatMB(Runtime.getRuntime().totalMemory()), Align.RIGHT);

        long maxMemory = Runtime.getRuntime().maxMemory();
        if (maxMemory < Long.MAX_VALUE) {
            startRow(msg, Messages.getString("ProgressFrame.MaxMemory"));
            finishRow(msg, formatMB(maxMemory), Align.RIGHT);
        }

        if (physicalMemory != 0) {
            startRow(msg, Messages.getString("ProgressFrame.PhysicalMemory"));
            finishRow(msg, formatMB(physicalMemory), Align.RIGHT);
        }

        long freeMemory = Util.getFreeMemorySize();
        if (physicalMemory > 0 && freeMemory > 0) {
            double memoryUsage = (physicalMemory - freeMemory) / (double) physicalMemory;
            startRow(msg, Messages.getString("ProgressFrame.PhysicalMemoryUsage"));
            finishRow(msg, pct.format(memoryUsage), Align.RIGHT);
        }

        double cpuUsage = Util.getSystemCpuLoad();
        if (cpuUsage >= 0) {
            startRow(msg, Messages.getString("ProgressFrame.CPUUsage"));
            finishRow(msg, pct.format(cpuUsage), Align.RIGHT);
        }

        if (workers != null) {
            try {
                FileStore outputVolume = Files.getFileStore(workers[0].output.getCanonicalFile().toPath());
                FileStore tempVolume = Files
                        .getFileStore(new File(System.getProperty("java.io.tmpdir")).getCanonicalFile().toPath());

                if (outputVolume.equals(tempVolume)) {
                    startRow(msg, Messages.getString("ProgressFrame.OutputTempVolume"));
                    finishRow(msg, outputVolume.toString(), Align.RIGHT);

                    startRow(msg, Messages.getString("ProgressFrame.OutputTempFree"));
                    finishRow(msg,
                            formatGB(outputVolume.getUsableSpace()) + " ("
                                    + outputVolume.getUsableSpace() * 100 / outputVolume.getTotalSpace() + "%)",
                            Align.RIGHT);
                } else {
                    startRow(msg, Messages.getString("ProgressFrame.OutputVolume"));
                    finishRow(msg, outputVolume.toString(), Align.RIGHT);

                    startRow(msg, Messages.getString("ProgressFrame.OutputFree"));
                    finishRow(msg,
                            formatGB(outputVolume.getUsableSpace()) + " ("
                                    + outputVolume.getUsableSpace() * 100 / outputVolume.getTotalSpace() + "%)",
                            Align.RIGHT);

                    startRow(msg, Messages.getString("ProgressFrame.TempVolume"));
                    finishRow(msg, tempVolume.toString(), Align.RIGHT);

                    startRow(msg, Messages.getString("ProgressFrame.TempFree"));
                    finishRow(msg,
                            formatGB(tempVolume.getUsableSpace()) + " ("
                                    + tempVolume.getUsableSpace() * 100 / tempVolume.getTotalSpace() + "%)",
                            Align.RIGHT);
                }
            } catch (Exception e) {
            }
        }
    }

    private void startTable(StringBuilder sb) {
        // Table colors can be adjusted here
        String borderColor = "#CCCCCC";
        String cellColor = "#FCFCFC";
        String titleBackColor = "#557799";
        String titleTextColor = "#FFFFFF";
        String disabledColor = "#BBBBBB";

        sb.append("<html><head><style> ");
        sb.append("td { ");
        sb.append("border-bottom: 1px solid ").append(borderColor).append("; ");
        sb.append("border-right: 1px solid ").append(borderColor).append("; ");
        sb.append("border-top: 0px; ");
        sb.append("border-left: 0px; ");
        sb.append("border-spacing: 0px; ");
        sb.append("padding: 1px 3px 1px 3px; ");
        sb.append("} ");
        sb.append("td.e { ");
        sb.append("border-top: 0px; ");
        sb.append("border-right: 0px; ");
        sb.append("} ");
        sb.append("td.t { ");
        sb.append("border: 1px solid ").append(borderColor).append("; ");
        sb.append("padding: 2px 3px 2px 3px; ");
        sb.append("background-color: ").append(titleBackColor).append("; ");
        sb.append("color: ").append(titleTextColor).append("; ");
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
        sb.append("tr.a { ");
        sb.append("background-color: ").append(cellColor).append("; ");
        sb.append("} ");
        sb.append("tr.d { ");
        sb.append("background-color: ").append(cellColor).append("; ");
        sb.append("color: ").append(disabledColor).append("; ");
        sb.append("} ");
        sb.append("</style></head><body>");
        sb.append("<table>");
    }

    private void addTitle(StringBuilder sb, int colSpan, String title) {
        sb.append("<tr><td class=t colspan=").append(colSpan);
        sb.append(">").append(title).append("</td></tr>");
    }

    private void skipRow(StringBuilder sb, int colSpan) {
        sb.append("<tr><td class=e colspan=").append(colSpan);
        sb.append("> </td></tr>");
    }

    private void startRow(StringBuilder sb, Object content) {
        startRow(sb, content, true);
    }

    private void startRow(StringBuilder sb, Object content, boolean enabled) {
        startRow(sb, content, enabled, -1);
    }

    private void startRow(StringBuilder sb, Object content, int pct) {
        startRow(sb, content, true, pct);
    }

    private void startRow(StringBuilder sb, Object content, boolean enabled, int pct) {
        sb.append("<tr");
        if (pct >= 0) {
            // Color based on percentage can be adjusted here
            int c = pct == 0 ? 255 : 245 - Math.min(75, pct) * 3 / 2;
            sb.append(" bgcolor=#").append(String.format("%02X%02X%02X", c, c, 255));
        }
        sb.append(" class=").append(enabled ? "a" : "d");
        sb.append("><td class=s>");
        sb.append(content).append("</td>");
    }

    private void finishRow(StringBuilder sb, Object content) {
        finishRow(sb, content, Align.LEFT);
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
        if (e.getSource().equals(pause) && workers != null) {
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

    /**
     * This is a workaround to avoid weird line breaks caused by a JDK bug. It may
     * be removed when it is fixed there. See issue #2102.
     */
    private String clean(String s) {
        return EmojiUtil.clean(s, '?');
    }

    /**
     * Show the current progress and state in system task bar.
     */
    private void updateTaskBar(int totalVolume, int processedVolume, boolean discoverEnded) {
        if (Taskbar.isTaskbarSupported()) {
            Taskbar taskbar = Taskbar.getTaskbar();
            taskbar.setWindowProgressState(this,
                    paused ? State.PAUSED : discoverEnded ? State.INDETERMINATE : State.NORMAL);
            if (discoverEnded) {
                // Start from 10%, otherwise "paused" in earlier stages would be hard to see
                int pct = (int) Math.min(100, 10 + Math.round(90.0 * processedVolume / totalVolume));
                taskbar.setWindowProgressValue(this, pct);
            }
        }
    }

    private static String formatMB(long value) {
        return nf.format(value >>> 20) + " MB";
    }

    private static String formatGB(long value) {
        return nf.format(value >>> 30) + " GB";
    }

    private enum Align {
        LEFT, CENTER, RIGHT;
    }
}
