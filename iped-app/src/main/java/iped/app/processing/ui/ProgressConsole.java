package iped.app.processing.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import iped.data.IItem;
import iped.engine.core.Statistics;
import iped.engine.core.Worker;
import iped.engine.localization.Messages;
import iped.engine.task.AbstractTask;
import iped.utils.LocalizedFormat;

public class ProgressConsole implements PropertyChangeListener {

    private static Logger LOGGER = LogManager.getLogger(ProgressConsole.class);

    private static final int LOG_ITEMS_INTERVAL_MILLIS = 60000;

    private final Level MSG = Level.getLevel("MSG"); //$NON-NLS-1$

    private boolean discoverEnded;
    private Worker[] workers;
    private long lastTime;
    private long processingStart;
    private final NumberFormat sizeFormat = LocalizedFormat.getNumberInstance();

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (processingStart == 0) {
            processingStart = System.currentTimeMillis();
        }

        if ("discoverEnded".equals(evt.getPropertyName())) {
            discoverEnded = true;
            update();

        } else if ("update".equals(evt.getPropertyName())) {
            update();

        } else if ("mensagem".equals(evt.getPropertyName())) { //$NON-NLS-1$
            LOGGER.log(MSG, (String) evt.getNewValue());

        } else if ("workers".equals(evt.getPropertyName())) { //$NON-NLS-1$
            workers = (Worker[]) evt.getNewValue();
        }
    }

    private void update() {
        Statistics s = Statistics.get();
        if (s == null) {
            return;
        }
        // Get volume/item processed/total
        int totalVolume = (int) (Statistics.get().getCaseData().getDiscoveredVolume() >>> 20); // Converted to MB
        int totalItems = Statistics.get().getCaseData().getDiscoveredEvidences();
        int processedVolume = (int) (Statistics.get().getVolume() >>> 20); // Converted to MB
        int processedItems = Statistics.get().getProcessed();

        long interval = (System.currentTimeMillis() - processingStart) / 1000 + 1;
        long rate = processedVolume * 3600L / ((1 << 10) * interval);

        String msg = Messages.getString("ProgressConsole.Starting");
        if (processedItems > 0) {
            msg = Messages.getString("ProgressConsole.Processing") + processedItems + "/" + totalItems;
            int percent = discoverEnded && totalVolume != 0 ? (int) Math.round(processedVolume * 100.0 / totalVolume)
                    : 0;
            msg += " (" + percent + "%)" + " " + rate + "GB/h";
        } else if (totalItems > 0) {
            msg = Messages.getString("ProgressConsole.Found") + totalItems
                    + Messages.getString("ProgressConsole.files");
        }

        if (discoverEnded && processingStart != 0) {
            long secsToEnd = (totalVolume - processedVolume) * (System.currentTimeMillis() - processingStart)
                    / ((processedVolume + 1) * 1000L);
            msg += Messages.getString("ProgressConsole.FinishIn") + secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60
                    + "m " + secsToEnd % 60 + "s";
        }
        LOGGER.log(MSG, msg);

        if (System.currentTimeMillis() - lastTime >= LOG_ITEMS_INTERVAL_MILLIS) {
            if (lastTime != 0) {
                logItemList();
            }
            lastTime = System.currentTimeMillis();
        }
    }

    private void logItemList() {
        if (workers == null)
            return;
        for (int i = 0; i < workers.length; i++) {
            if (!workers[i].isAlive())
                continue;
            StringBuilder msg = new StringBuilder();
            msg.append(workers[i].getName());
            AbstractTask task = workers[i].runningTask;
            if (task != null) {
                msg.append(" [" + task.getName() + "]");
            } else {
                msg.append(" [no task]");
            }
            IItem evidence = workers[i].evidence;
            if (evidence != null) {
                msg.append(" [" + evidence.getPath() + "]");
                if (evidence.getLength() != null) {
                    msg.append(" [" + sizeFormat.format(evidence.getLength()) + " bytes]");
                }
            } else {
                msg.append(" [no item]");
            }
            LOGGER.log(MSG, msg);
        }
    }
}
