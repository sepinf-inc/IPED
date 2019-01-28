package dpf.sp.gpinf.indexer.process;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Date;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dpf.sp.gpinf.indexer.Messages;


public class ProgressConsole implements PropertyChangeListener{
	
	private static Logger LOGGER = LogManager.getLogger(ProgressConsole.class);
	
	private final Level MSG = Level.getLevel("MSG"); //$NON-NLS-1$
	
	int indexed = 0, discovered = 0;
	long rate = 0, instantRate;
	int volume, taskSize;
	long secsToEnd;
	private Date indexStart;

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
			if (indexStart == null) {
			  indexStart = new Date();
			}

		    if ("processed".equals(evt.getPropertyName())) { //$NON-NLS-1$
		      indexed = (Integer) evt.getNewValue();

		    } else if ("taskSize".equals(evt.getPropertyName())) { //$NON-NLS-1$
		      taskSize = (Integer) evt.getNewValue();

		    } else if ("discovered".equals(evt.getPropertyName())) { //$NON-NLS-1$
		      discovered = (Integer) evt.getNewValue();
		      if(volume == 0)
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
		    }
		
	}
	
	private void updateString() {
	    String msg = Messages.getString("ProgressConsole.Starting"); //$NON-NLS-1$
	    if (indexed > 0) {
	      msg = Messages.getString("ProgressConsole.Processing") + indexed + "/" + discovered; //$NON-NLS-1$ //$NON-NLS-2$
	      int percent = (taskSize != 0) ? (volume * 100 / taskSize) : 0;
	      msg += " (" + percent + "%)" + " " + rate + "GB/h"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    } else if (discovered > 0) {
	      msg = Messages.getString("ProgressConsole.Found") + discovered + Messages.getString("ProgressConsole.files"); //$NON-NLS-1$ //$NON-NLS-2$
	    }

	    if (taskSize != 0 && indexStart != null) {
	      secsToEnd = ((long) taskSize - (long) volume) * ((new Date()).getTime() - indexStart.getTime()) / (((long) volume + 1) * 1000);
	      msg += Messages.getString("ProgressConsole.FinishIn") + secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 + "m " + secsToEnd % 60 + "s"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    }
	    LOGGER.log(MSG, msg);
	}

}
