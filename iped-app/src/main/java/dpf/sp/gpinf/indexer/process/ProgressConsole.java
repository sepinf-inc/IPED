package dpf.sp.gpinf.indexer.process;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.Date;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dpf.sp.gpinf.indexer.localization.Messages;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.util.LocalizedFormat;
import iped3.IItem;

public class ProgressConsole implements PropertyChangeListener {

    private static Logger LOGGER = LogManager.getLogger(ProgressConsole.class);

    private static final int LOG_ITEMS_INTERVAL_MILLIS = 60000;

    private final Level MSG = Level.getLevel("MSG"); //$NON-NLS-1$

    private int indexed = 0, discovered = 0;
    private long rate = 0, instantRate;
    private int volume, taskSize;
    private long secsToEnd;
    private Date indexStart;
    private Worker[] workers;
    private long lastTime = 0;
    private NumberFormat sizeFormat = LocalizedFormat.getNumberInstance();

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (indexStart == null) {
            indexStart = new Date();
        }

        if ("processed".equals(evt.getPropertyName())) { //$NON-NLS-1$
            indexed = (Integer) evt.getNewValue();
            long now = System.currentTimeMillis();
            if (now - lastTime >= LOG_ITEMS_INTERVAL_MILLIS) {
                if (lastTime != 0) {
                    logItemList();
                }
                lastTime = now;
            }

        } else if ("taskSize".equals(evt.getPropertyName())) { //$NON-NLS-1$
            taskSize = (Integer) evt.getNewValue();

        } else if ("discovered".equals(evt.getPropertyName())) { //$NON-NLS-1$
            discovered = (Integer) evt.getNewValue();
            if (volume == 0)
                updateString();

        } else if ("mensagem".equals(evt.getPropertyName())) { //$NON-NLS-1$
            LOGGER.log(MSG, (String) evt.getNewValue());

        } else if ("progresso".equals(evt.getPropertyName())) { //$NON-NLS-1$
            long prevVolume = volume;
            volume = (Integer) evt.getNewValue();

            Date now = new Date();
            long interval = (now.getTime() - indexStart.getTime()) / 1000 + 1;
            rate = (long) volume * 1000000L * 3600L / ((1 << 30) * interval);
            instantRate = (long) (volume - prevVolume) * 1000000L * 3600L / (1 << 30) + 1;
            updateString();

        } else if ("workers".equals(evt.getPropertyName())) { //$NON-NLS-1$
            workers = (Worker[]) evt.getNewValue();
        }

    }

    private void updateString() {
        String msg = Messages.getString("ProgressConsole.Starting"); //$NON-NLS-1$
        if (indexed > 0) {
            msg = Messages.getString("ProgressConsole.Processing") + indexed + "/" + discovered; //$NON-NLS-1$ //$NON-NLS-2$
            int percent = (taskSize != 0) ? (volume * 100 / taskSize) : 0;
            msg += " (" + percent + "%)" + " " + rate + "GB/h"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        } else if (discovered > 0) {
            msg = Messages.getString("ProgressConsole.Found") + discovered //$NON-NLS-1$
                    + Messages.getString("ProgressConsole.files"); //$NON-NLS-1$
        }

        if (taskSize != 0 && indexStart != null) {
            secsToEnd = ((long) taskSize - (long) volume) * ((new Date()).getTime() - indexStart.getTime())
                    / (((long) volume + 1) * 1000);
            msg += Messages.getString("ProgressConsole.FinishIn") + secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 //$NON-NLS-1$ //$NON-NLS-2$
                    + "m " + secsToEnd % 60 + "s"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        LOGGER.log(MSG, msg);
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
                msg.append(" [no task]"); //$NON-NLS-1$
            }
            IItem evidence = workers[i].evidence;
            if (evidence != null) {
                msg.append(" [" + evidence.getPath() + "]");
                if (evidence.getLength() != null) {
                    msg.append(" [" + sizeFormat.format(evidence.getLength()) + " bytes]"); //$NON-NLS-1$ //$NON-NLS-2$
                }
            } else {
                msg.append(" [no item]");
            }
            LOGGER.log(MSG, msg);
        }
    }

}
