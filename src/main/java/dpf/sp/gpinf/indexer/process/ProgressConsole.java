package dpf.sp.gpinf.indexer.process;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Date;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ProgressConsole implements PropertyChangeListener{
	
	private static Logger LOGGER = LogManager.getLogger(ProgressConsole.class);
	
	private final Level MSG = Level.getLevel("MSG");
	
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

		    if ("processed".equals(evt.getPropertyName())) {
		      indexed = (Integer) evt.getNewValue();

		    } else if ("taskSize".equals(evt.getPropertyName())) {
		      taskSize = (Integer) evt.getNewValue();

		    } else if ("discovered".equals(evt.getPropertyName())) {
		      discovered = (Integer) evt.getNewValue();
		      if(volume == 0)
		    	  updateString();

		    } else if ("mensagem".equals(evt.getPropertyName())) {
		    	LOGGER.log(MSG, (String) evt.getNewValue());

		    } else if ("progresso".equals(evt.getPropertyName())) {
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
	    String msg = "Inicializando...";
	    if (indexed > 0) {
	      msg = "Processando " + indexed + "/" + discovered;
	      int percent = (taskSize != 0) ? (volume * 100 / taskSize) : 0;
	      msg += " (" + percent + "%)" + " " + rate + "GB/h";
	    } else if (discovered > 0) {
	      msg = "Localizados " + discovered + " arquivos";
	    }

	    if (taskSize != 0 && indexStart != null) {
	      secsToEnd = ((long) taskSize - (long) volume) * ((new Date()).getTime() - indexStart.getTime()) / (((long) volume + 1) * 1000);
	      msg += " Termino em " + secsToEnd / 3600 + "h " + (secsToEnd / 60) % 60 + "m " + secsToEnd % 60 + "s";
	    }
	    LOGGER.log(MSG, msg);
	}

}
