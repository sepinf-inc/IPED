package iped.app.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import iped.engine.data.IPEDSource;
import iped.utils.UTF8Properties;
import iped.viewers.api.CancelableWorker;
import iped.viewers.util.ProgressDialog;

import java.util.*;

import java.nio.file.*;
import javax.swing.JDialog;

public class ExportCase extends CancelableWorker<String, Integer> {

	ArrayList<String> srcPaths;
	ArrayList<String> otherPaths;
	String dstPath;
	boolean copyImages;
	boolean estimateSizeOnly;
	Map<Long, List<String>> imgPaths;
	
	private static Logger LOGGER = LoggerFactory.getLogger(ExportCase.class);	

	protected ProgressDialog progressMonitor;

	boolean success = false;
  
	Values values = new Values();
	
	long free = 0;

	DecimalFormat decimal;  			

    JDialog dialog;

	public ExportCase(JDialog dialog, ArrayList<String> srcPaths, String dstPath, Map<Long, List<String>> imgPaths, ArrayList<String> otherPaths,boolean copyImages, boolean estimateSizeOnly){

		this.srcPaths = srcPaths;
		this.otherPaths = otherPaths;
		this.dstPath = dstPath;
		this.copyImages = copyImages;
		this.imgPaths = imgPaths;
		this.estimateSizeOnly = estimateSizeOnly;
        this.dialog = dialog;

		decimal = new DecimalFormat( "###.##");  	

		progressMonitor = new ProgressDialog(this.dialog, this,2);
        progressMonitor.setIndeterminate(true);	
		
		free = (new File(dstPath)).getUsableSpace();


	}
  
	@Override
	public void done() {
	  if(progressMonitor != null){
		  progressMonitor.close();
		  
		  if (estimateSizeOnly){
			  if(success){	
					JOptionPane.showMessageDialog(this.dialog, "Estimated Size: "+getEstimateSize(), "Export Case", JOptionPane.INFORMATION_MESSAGE);
                    dialog.setVisible(true);
			  }
		  }
		  else{
			  if(success){
					String msg = copyImages?"with":"without";
					LOGGER.info("Exported Case {} evidences on folder '{}' - Size: {}",msg, dstPath, getEstimateSize());		
					JOptionPane.showMessageDialog(this.dialog, "Case successfully exported to:\n"+dstPath, "Export Case", JOptionPane.INFORMATION_MESSAGE);
			  }
		  }
	  }
	}  
  
	public  long size(Path path) {

		final AtomicLong size = new AtomicLong(0);

		try {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

					size.addAndGet(attrs.size());
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new AssertionError("walkFileTree will not throw IOException if the FileVisitor does not");
		}

		return size.get();
	}
  
  protected void copyFiles(File src, File target) throws IOException { 
  
    InputStream in = null;
	BufferedOutputStream out = null;
  
	if (progressMonitor.isCanceled()) {
		return;
	}
	  
	if (src.isDirectory()){
		
		if (!target.exists()){
            target.mkdirs();
		} 		

        String files[] = src.list();

        for (String file : files){
            File srcFile = new File(src, file);
            File destFile = new File(target, file);
			
			if (progressMonitor.isCanceled()) {
				return;
			}
            copyFiles(srcFile, destFile);
        }
    } else{
		
		try {
        
			in = new FileInputStream(src);
			out = null;
			
			if(target.isDirectory())
				out = new BufferedOutputStream(new FileOutputStream(new File(target, src.getName())));
			else
				out = new BufferedOutputStream(new FileOutputStream(target));

			byte[] buf = new byte[8*1024];
			int len;				

			while ((len = in.read(buf)) >= 0 && !Thread.currentThread().isInterrupted()) {
				out.write(buf, 0, len);
				values.megas += len;
			}


			in.close();
			out.close();

        } catch (Exception e1) {
            System.out.println("Error on export:"+src);
            e1.printStackTrace();
        } finally {
                if (in != null) 
                    in.close();
                if (out != null) 
                    out.close();			
        }
	  
    }
  }


  protected String getEstimateSize(){
	  return formatBytes(values.totalMega);
  }

  protected boolean estimateSize(){
	  
	long size = 0;
	
	try{

		for (String path : this.srcPaths) {
			size += size(Paths.get(path));
		}
		if (copyImages){

            for (String path2 : this.otherPaths) {
				size += size(Paths.get(path2));
			}
			
		}				
		
		Thread.sleep(1000);

	} catch (Exception e) {
		e.printStackTrace();
	}

	values.totalMega = size;
	
	return true;
	  
  }




  @Override
  protected String doInBackground()  {
	  
	progressMonitor.setNote("Estimating total size");
	File target = null;
	File src = null;
	String folderName = "evidences";
	String root = "."+File.separator+folderName+File.separator;
		
	
	
	try {
	
		if (estimateSize()){
			
			if (estimateSizeOnly){
				success = true;
				return null;
			}

			progressMonitor.setIndeterminate(false);
            progressMonitor.reset();
            progressMonitor.setMaximum(values.totalMega);


			ArrayList<String> dstPaths = new ArrayList<String>();
			
			Timer timer = new Timer();
			timer.schedule(new RefreshProgress(progressMonitor, values), 0, 1000);
			
			target = new File(dstPath);
			if (target != null){	
				for (String path : this.srcPaths) {
					
					src = new File (path);
					
					if (src != null && src.exists()){
						copyFiles(src, target);
					}
				  
				}
			}

			if(copyImages){

				target = new File(dstPath,folderName);	
				if (!target.exists()){
					target.mkdirs();
				} 		
                
				IPEDSource iCase = new IPEDSource(new File(dstPath));
				for (Long idSleuthCase : imgPaths.keySet()) {
					List<String> pathsImage = imgPaths.get(idSleuthCase);
					dstPaths.clear();
					for(String path : pathsImage){
						File temp = new File(path);
						dstPaths.add(root+temp.getName());
					}													
					if (iCase!=null){
						iCase.getSleuthCase().setImagePaths(idSleuthCase,dstPaths);
					}
				}

				for (String path : this.otherPaths) {

					src = new File (path);
					
					if (src != null && src.exists()){
						if (src.isDirectory()){
							target = new File(dstPath,root+src.getName());		
						}else{
							target = new File(dstPath,root);	
						}
						
						copyFiles(src, target);
					}

                    
                    File newPath = new File(folderName,src.getName());
                    File oldPath;

                    if (src.isAbsolute())
                        oldPath = src;
                    else
                        oldPath = new File(dstPath,src.getPath());
                    
                    saveDataSourcePath(new File(dstPath,"iped"),oldPath, newPath);
				  
				}

				
			}

            
			
			timer.cancel();


			success = true;
		}
	}catch (Exception ex) {
			ex.printStackTrace();
			System.out.println(ex.getMessage());
		if (progressMonitor != null)
			JOptionPane.showMessageDialog(this.dialog, "Export Error! Verify aplication logs.", "Export Case", JOptionPane.ERROR_MESSAGE);	
	}

    return null;
  }
  
      private static void saveDataSourcePath(File caseModuleDir, File oldPath, File newPath) throws IOException {
        String NEW_DATASOURCE_PATH_FILE = "data/newDataSourceLocations.txt";
        File file = new File(caseModuleDir, NEW_DATASOURCE_PATH_FILE);
        UTF8Properties props = new UTF8Properties();
        if (file.exists())
            props.load(file);
        props.setProperty(oldPath.getPath(), newPath.getPath());
        try {
            props.store(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }  

	public String formatBytes(long soma){
	
		String resultado = "0 Bytes";
		
		try {
		
			if (soma >= 1125899906842624.0){
				resultado = decimal.format((soma/1125899906842624.0)) + " PB";
			} else if (soma >= 1099511627776.0){
				resultado = decimal.format((soma/1099511627776.0)) + " TB";
			} else if (soma >= 1073741824.0){
				resultado = decimal.format((soma/1073741824.0)) + " GB";
			} else if (soma >= 1048576.0) {
				resultado = decimal.format((soma/1048576.0)) + " MB";
			} else if (soma >= 1024.0) {
				resultado = decimal.format((soma/1024.0)) + " KB";
			} else if (soma >= 0.0) {
				resultado = decimal.format((soma)) + " B";
			}
		}
		catch (Exception ex){
			;
		}
		
		return resultado;
	
	}	  


}

class Values {

	public long megas = 0;
	public long totalMega = 0;
	
}


class RefreshProgress extends TimerTask {

	DecimalFormat decimal;  			
	long num = 0;
	Values valores = null;
	ProgressDialog progressMonitor = null;
	
	public RefreshProgress(ProgressDialog progressMonitor, Values valores){
		super();
		this.progressMonitor = progressMonitor;
		this.valores = valores;
		decimal = new DecimalFormat( "###.##");  	
	}
	
    public void run() {
		
		long timeLeft = 0;
		this.num++;
				
		if (progressMonitor.isCanceled()) {
			this.cancel();
			return;
		}		
						
		if (this.valores.megas <= 0)
			timeLeft = 359999;
		else
			timeLeft = (long)(((double)this.valores.totalMega - (double)this.valores.megas)/((double)this.valores.megas/(double)this.num));
		
		String timeLeftString = "Time left: "+formatTime(timeLeft);	
		
       	progressMonitor.setProgress(this.valores.megas);
		progressMonitor.setNote("<html><body>"+"Copying " + formatBytes(this.valores.megas) + " of " + formatBytes(this.valores.totalMega)+"<br>"+timeLeftString+"</body></html>");
    }
	public String formatTime(long tempo){

		long t1 = tempo/3600;
		long t2 = ((tempo % 3600) / 60);
		long t3 = tempo%60;
		String s1 = (t1<10)?("0"+t1):t1+"";
		String s2 = (t2<10)?("0"+t2):t2+"";
		String s3 = (t3<10)?("0"+t3):t3+"";
		return s1+":"+s2+":"+s3;

	}	
	public String formatBytes(long soma){
	
		String resultado = "0 Bytes";
		
		try {
		
			if (soma >= 1125899906842624.0){
				resultado = decimal.format((soma/1125899906842624.0)) + " PB";
			} else if (soma >= 1099511627776.0){
				resultado = decimal.format((soma/1099511627776.0)) + " TB";
			} else if (soma >= 1073741824.0){
				resultado = decimal.format((soma/1073741824.0)) + " GB";
			} else if (soma >= 1048576.0) {
				resultado = decimal.format((soma/1048576.0)) + " MB";
			} else if (soma >= 1024.0) {
				resultado = decimal.format((soma/1024.0)) + " KB";
			} else if (soma >= 0.0) {
				resultado = decimal.format((soma)) + " B";
			}
		}
		catch (Exception ex){
			;
		}
		
		return resultado;
	
	}	
	
}
