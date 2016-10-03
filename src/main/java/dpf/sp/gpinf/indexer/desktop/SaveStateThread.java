package dpf.sp.gpinf.indexer.desktop;

import java.io.File;
import java.io.IOException;

import dpf.sp.gpinf.indexer.search.Marcadores;
import dpf.sp.gpinf.indexer.util.Util;

public class SaveStateThread extends Thread{
	
	private static SaveStateThread instance = new SaveStateThread();
	
	static{
		instance.setDaemon(true);
		instance.start();
	}
	
	private File file;
	private Marcadores state;
	
	private SaveStateThread(){
	}
	
	public static SaveStateThread getInstance(){
		return instance;
	}
	
	public synchronized void saveState(Marcadores state, File file){
		this.state = state;
		this.file = file;
	}
	
	public void run(){
		while(!Thread.interrupted()){
			File file;
			Marcadores state;
			synchronized(this){
				file = this.file;
				state = this.state;
				this.file = null;
			}
			if(file != null)
				try {
					File tmp = new File(file.getAbsolutePath() + ".tmp");
					if(tmp.exists())
						tmp.delete();
					Util.writeObject(state, tmp.getAbsolutePath());
					File bkp = new File(file.getAbsolutePath() + ".bkp");
					if(!file.exists() || file.renameTo(bkp)){
						if(tmp.renameTo(file))
							bkp.delete();
						else
							bkp.renameTo(file);
					}
					
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				break;
			}
		}
	}
}
