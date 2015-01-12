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
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.JOptionPane;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.core.Stream2BufferedImage;
import org.im4java.process.Pipe;
import org.im4java.process.ProcessStarter;

import dpf.sp.gpinf.indexer.search.App;

public class GraphicsMagicConverter {
	
	//static PooledGMService service;
	public static boolean USE_GM = true;
	public static int TIMEOUT = 5;
	
	static String ERROR_MSG = "FileNotFoundException: gm";
	static boolean errorDisplayed = false;
	
	 
	static{
		try{
			if(System.getProperty("os.name").startsWith("Windows")){
				String path = App.get().codePath + "/gm";
				ProcessStarter.setGlobalSearchPath(path);
			}
			
			//ProcessStarter.setGlobalSearchPath(path);
			/*GMConnectionPoolConfig config = new GMConnectionPoolConfig();
			config.setGMPath(path + "\\gm.exe");
			service = new PooledGMService(config);
			*/
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
	public BufferedImage getImage(final InputStream in,final int resolution){
	
		
		if(!errorDisplayed)
			{
				final Stream2BufferedImage s2b = new Stream2BufferedImage();
	
				FutureTask<Integer> convertTask = new FutureTask<Integer>(new Callable<Integer>() {
					public Integer call() throws Exception {
						
						IMOperation op = new IMOperation();
						op.addImage("-");
						if(resolution > 0)
							op.resize(resolution);
						op.addImage("bmp:-");
				
						Pipe pipeIn  = new Pipe(in, null);
						
						
						ConvertCmd convert = new ConvertCmd(USE_GM);
						convert.setInputProvider(pipeIn);
						convert.setOutputConsumer(s2b);
						convert.run(op);
						
						return 1;
					}
				});
				
				 Thread convertThread = new Thread(convertTask);
				 convertThread.start();
				           
				 try {
					 convertTask.get(TIMEOUT, TimeUnit.SECONDS);
			               
			         } catch (InterruptedException e) {
			        	 convertThread.interrupt();
			           	
			         } catch (ExecutionException e) {
			        	 
			        	 synchronized(ERROR_MSG){
								if(!errorDisplayed  && e.toString().contains(ERROR_MSG)){
									e.printStackTrace();
									JOptionPane.showMessageDialog(null, "Não foi possível executar GraphicsMagick. Verifique se está instalado!");
									errorDisplayed = true;
								}
							}
			 				
			 		} catch (TimeoutException e) {
			 			System.out.println("Timeout running GM.");
			 			convertThread.interrupt();
			 			
			 		}
				//System.out.println("GM running");
				
				return s2b.getImage();
				
			}
		
		return null;
		
	}
}
