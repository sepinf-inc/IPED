package dpf.sp.gpinf.indexer.util;

import java.net.ServerSocket;
import java.net.Socket;

public class ProxySever {
	
	static volatile ServerSocket serverSocket;
	
	public static void start(){
		
		if(serverSocket != null)
			return;
		
		try {
			serverSocket = new ServerSocket(0);
			String port = Integer.toString(serverSocket.getLocalPort());
			
			System.setProperty("http.proxyHost","127.0.0.1");
			System.setProperty("http.proxyPort",port);
			System.setProperty("https.proxyHost","127.0.0.1");
			System.setProperty("https.proxyPort",port);
			System.setProperty("ftp.proxyHost","127.0.0.1");
			System.setProperty("ftp.proxyPort",port);
			//System.setProperty("socksProxyHost","127.0.0.1");
			//System.setProperty("socksProxyPort",port);
		
		} catch (Exception e1) {
			e1.printStackTrace();
			System.setSecurityManager(new AppSecurityManager());
		}
		
		if(serverSocket != null)
			new Thread(){
				public void run(){
					while(true){
						try{
							Socket clientSocket = serverSocket.accept();
						    clientSocket.getOutputStream().close();
						    clientSocket.close();
						    
						}catch(Exception e){
							e.printStackTrace();
						}
					}
				}
			}.start();
			
		
		
	}
	
}
