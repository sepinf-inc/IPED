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
package dpf.sp.gpinf.indexer.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeoutException;

import javax.imageio.ImageIO;

import dpf.sp.gpinf.indexer.Configuration;

public class GraphicsMagicConverter {
	
	private static final String RESOLUTION = "resolution";
	private static final String TMP_DIR = "MAGICK_TMPDIR";
	private static final String[] CMD = {"gm", "convert", "-sample", RESOLUTION, "-", "bmp:-"};
	private static final String tmpDirName = "gm-im_temp";
	
	public static boolean USE_GM = true;
	public static int TIMEOUT = 10;
	public static boolean enabled = true;
	public static String toolPathWin = "";
	
	private static File tmpDir;
	
	private BufferedImage result;
	
	static{
		try{
			if(!System.getProperty("os.name").startsWith("Windows"))
				toolPathWin = "";
			
			tmpDir = new File(Configuration.indexerTemp, tmpDirName);
			tmpDir.mkdirs();
			startTmpDirCleaner();
				
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private static void startTmpDirCleaner(){
		Thread t = new Thread(){
			public void run(){
				while(true){
					File[] subFiles = tmpDir.listFiles();
					if(subFiles != null)
						for(File tmp : subFiles)
							tmp.delete();
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		};
		t.setDaemon(true);
		t.start();
	}
		
	private String[] getCmd(int resolution){
		int cmdOffset = 0;
	    if(!USE_GM) cmdOffset = 1;
	    String[] cmd = new String[CMD.length - cmdOffset];
	    for(int i = 0; i < cmd.length; i++){
	        cmd[i] = CMD[i + cmdOffset];
	        if(!toolPathWin.isEmpty() && i == 0)
	        	cmd[0] = toolPathWin + "/" + cmd[0];
	        if(cmd[i].equals(RESOLUTION))
	        	cmd[i] = String.valueOf(resolution);
	    }
	    return cmd;
	}
	
	public BufferedImage getImage(final InputStream in,final int resolution){
		try {
			return getImage(in, resolution, false);
			
		} catch (TimeoutException e) {
			return null;
		}
	}
	
	public BufferedImage getImage(InputStream in, int resolution, boolean throwTimeout) throws TimeoutException{
		
		if(!enabled)
			return null;
	    
	    ProcessBuilder pb = new ProcessBuilder();
	    pb.environment().put(TMP_DIR, tmpDir.getAbsolutePath());
	    pb.command(getCmd(resolution));
	    Process p = null;
	    try {
			p = pb.start();
		} catch (IOException e1) {
			Log.error("GraphicsMagicConverter", "Erro ao executar graphicsMagick/imageMagick. "
        			+ "Verifique se está instalado ou se o caminho está configurado corretamente!");
		}
	    if(p != null){
	    	sendInputStream(in, p);
            ignoreErrorStream(p);
            Thread t = getResultThread(p);
            t.start();
            try {
				t.join(TIMEOUT * 1000);
				
				if(result == null && t.isAlive() && throwTimeout)
	                throw new TimeoutException();
	            else
	            	Log.warning("GraphicsMagicConverter", "Timeout while converting image to BMP.");
				
			} catch (InterruptedException e) {
				
			}finally{
	        	p.destroy();
	        }
	    }   
	    
	    return result;
	}
	
	private Thread getResultThread(final Process p){
	    return new Thread(){
            @Override
            public void run(){
            	InputStream in = p.getInputStream();
                try {
                    result = ImageIO.read(in);
                    
                } catch (IOException e) {
                    //e.printStackTrace();
                }finally{
                	IOUtil.closeQuietly(in);
                }
            }
        };
	}
	
	private void ignoreErrorStream(final Process p){
	    new Thread(){
	        @Override
	        public void run(){
	        	InputStream in = p.getErrorStream();
	            int i = 0;
	            byte[] buf = new byte[8192];
	            try {
	                while(i != -1)
	                	i = in.read(buf);
	                
                } catch (IOException e) {
                    //e.printStackTrace();
                } finally{
                	IOUtil.closeQuietly(in);
                }
	        }
	        
	    }.start();
	}
	
	
	private void sendInputStream(final InputStream in, final Process p){
	    new Thread(){
	        @Override
	        public void run(){
	            OutputStream out = p.getOutputStream();
	            int i = 0;
	            byte[] buf = new byte[64 * 1024];
	            try {
	                while(i != -1){
	                    out.write(buf, 0, i);
	                    out.flush();
	                    i = in.read(buf);
	                }
                } catch (IOException e) {
                    //e.printStackTrace();
                }finally{
                	IOUtil.closeQuietly(out);
                }
	        }
	        
	    }.start();
	}
	
}
