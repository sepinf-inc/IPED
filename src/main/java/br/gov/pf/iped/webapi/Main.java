package br.gov.pf.iped.webapi;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class.
 *
 */
public class Main {
    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     * @throws IOException 
     */
    public static HttpServer startServer(String host, int port, String urlToAskSources) throws IOException {
        // create a resource config that scans for JAX-RS resources and providers
        // in gpinf.api package
        final ResourceConfig rc = new ResourceConfig().packages("br.gov.pf.iped.webapi");
        
        Sources.init(urlToAskSources);
        
        //https://stackoverflow.com/questions/26546373/showing-grizzly-exceptions-in-eclipse-console
        Logger l = Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
        l.setLevel(Level.FINE);
        l.setUseParentHandlers(false);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.ALL);
        l.addHandler(ch);
        
        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        return GrizzlyHttpServerFactory.createHttpServer(URI.create("http://"+host+":"+port), rc);
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
    	String host = "0.0.0.0";
    	int port = 8080;
    	String urlToAskSources = null;
    	
    	for (String arg : args) {
			if (arg.startsWith("--host=")){
				host = arg.substring("--host=".length());
				
			}else if (arg.startsWith("--port=")){
				port = Integer.parseInt(arg.substring("--port=".length()));
				
			}else if (arg.startsWith("--sources=")){
				urlToAskSources = arg.substring("--sources=".length());

			}else{
				printHelp();
				System.exit(-1);
			}
		}
    	if (urlToAskSources == null){
    		System.err.println("missing --sources option");
    		printHelp();
			System.exit(-1);
    	}
        startServer(host,port,urlToAskSources);
        System.out.println(String.format(
        	"Jersey app started with WADL available at \n%sapplication.wadl\n", 
        	"http://"+host+":"+port+"/"));
    }
    public static void printHelp(){
    	System.out.println("--sources=(URL|Path)\tfile or url with json: [{path}...]");
    	System.out.println("--host=\t\tdefault:0.0.0.0");
    	System.out.println("--port=\t\tdefault:8080");
    }
}
