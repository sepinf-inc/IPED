package dpf.sp.gpinf.indexer.search.maps;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.StringTokenizer;
import java.util.concurrent.Semaphore;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.commons.io.IOUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.SearchResult;


/**
 * Canvas que se conecta com um Browser SWT para integração com o Swing.
 *  
 * Implementa também métodos de transformação dos resultados das pesquisas em KML
 * a ser renderizado no GoogleMaps.
 * 
 *  adaptado do código: https://gist.github.com/r10r/2305091
 *
 * @author Patrick Dalla Bernardina <patrick.dalla@gmail.com>
 */

public class MapaCanvas extends Canvas {

  private Thread swtThread;
  private Browser swtBrowser;
  
  boolean desatualizado = true;
  boolean conectado = false;

  boolean primeiraentrada = true;
  
  public boolean isDesatualizado() {
	return desatualizado;
}

public void setDesatualizado(boolean des){
	  this.desatualizado = des;
  }

  public void connect() {
    if (this.swtThread == null) {
      final Canvas canvas = this;
      this.swtThread = new Thread() {
        @Override
        public void run() {
          try {

            Display display = new Display();
            Shell shell = SWT_AWT.new_Shell(display, canvas);
            shell.setLayout(new FillLayout());

            synchronized (this) {
              swtBrowser = new Browser(shell, SWT.NONE);
              this.notifyAll();
            }

            shell.open();
            conectado = true;
            
            //função de retorno, chamada quando um item é clicado no mapa.
            new MapItemClickFuncion (getBrowser(), "itemClick");

            
            while (!isInterrupted() && !shell.isDisposed()) {
              if (!display.readAndDispatch()) {
                display.sleep();
              }
            }
            shell.dispose();
            display.dispose();
          } catch (Exception e) {
            interrupt();
          }
        }
      };
      this.swtThread.start();
    }

    // Wait for the Browser instance to become ready
    synchronized (this.swtThread) {
      while (this.swtBrowser == null) {
        try {
          this.swtThread.wait(100);
        } catch (InterruptedException e) {
          this.swtBrowser = null;
          this.swtThread = null;
          break;
        }
      }
    }
  }

  /**
   * Returns the Browser instance. Will return "null"
   * before "connect()" or after "disconnect()" has
   * been called.
   */
  public Browser getBrowser() {
    return this.swtBrowser;
  }

  /**
   * Stops the swt background thread.
   */
  public void disconnect() {
    if (swtThread != null) {
      swtBrowser = null;
      swtThread.interrupt();
      swtThread = null;
    }
  }

  /**
   * Ensures that the SWT background thread
   * is stopped if this canvas is removed from
   * it's parent component (e.g. because the
   * frame has been disposed).
   */
  @Override
  public void removeNotify() {
    super.removeNotify();
    disconnect();
  }
  
  {
	  System.setProperty("sun.awt.xembedserver", "true");
  }
  
  /**
   * Renders html.
   *
   * @param html Html text
   */
  public void setText(final String html) {
      // This action must be executed on the SWT thread
	  Browser b = getBrowser();
	  Display d = b.getDisplay();
      d.asyncExec(new Runnable() {
          @Override
          public void run() {
              getBrowser().setText(html);
          }
      });
  }

  public void setKml(String kml) throws IOException{
	  setText(getHtmlFromKml(kml));
  }
  
  public String getHtmlFromKml(String kml) throws IOException{
	  String htmlString = "";
	  String gx = "";
	  
	  InputStream is = getClass().getResource("/dpf/sp/gpinf/indexer/search/maps/maps2.html").openStream();
	  StringWriter writer = new StringWriter();
	  IOUtils.copy(is, writer);
	  htmlString = writer.toString();
	  
	  InputStream is6 = getClass().getResource("/dpf/sp/gpinf/indexer/search/maps/geoxmlfull_v3.js").openStream();
	  StringWriter writer6 = new StringWriter();
	  IOUtils.copy(is6, writer6);
	  gx = writer6.toString();
	  
	  htmlString = htmlString.replace("{{data}}", kml.replace("\n", "").replace("\r", ""));
	  htmlString = htmlString.replace("{{javascript_geo}}", gx);
	  
	  return htmlString;
  }
  
  public String htmlFormat(String html){
	  return html.replace("/", "&#47;").replace("\\", "&#92;").replace("@", "&#64;");			
  }
  
  public String getResultsKML(App app) throws IOException{
	  String kml="<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

	  kml+="<kml xmlns=\"http://www.opengis.net/kml/2.2\">";
	  kml+="<Document>";
	  kml+="<name>Resultados da pesquisa</name>";
	  kml+="<open>1</open>";
	  kml+="<description>Resultados da pesquisa georeferenciados.</description>";
	  kml+="<Style id=\"basico\"><BalloonStyle><![CDATA[<text>$[name]</text><br/><text>$[description]</text>]]></BalloonStyle></Style>";

	  kml+="<Folder>";
	  kml+="<name>Resultados</name>";

	  SearchResult results = app.getResults();
	  for (int i = 0; i < results.docs.length; i++) {
		  org.apache.lucene.document.Document doc = app.searcher.doc(results.docs[i]);
		  
		  String lat = doc.get("GPS Latitude");
		  if(lat!=null){
			  kml+="<Placemark>";
			  kml+="<styleUrl>#basico</styleUrl>";
			  kml+="<id>"+doc.get("id")+"</id>";
			  kml+="<name>"+doc.get("id")+"-"+doc.get("nome")+"</name>";
			  kml+="<description>"+htmlFormat(doc.get("caminho"))+"</description>";
			  lat = converteCoordFormat(lat);
			  String longit = doc.get("GPS Longitude");
			  longit = converteCoordFormat(longit);
			  kml+="<Point><coordinates>"+longit+","+lat+",0</coordinates></Point>";

			  String dataCriacao = doc.get("criacao").replace(".", "-");
			  dataCriacao = dataCriacao.substring(0, 10)+"T"+dataCriacao.replace(".",":").substring(11, 19)+"Z"; 
			  kml+="<TimeSpan><begin>"+dataCriacao+"</begin></TimeSpan>";

			  kml+="</Placemark>";
		  }
		  
	  }
	  kml+="</Folder>";
	  kml+="</Document>";
	  kml+="</kml>";

	  return kml;
  }

  static public String converteCoordFormat(String coord){
	  StringTokenizer st = new StringTokenizer(coord, "°\"'");
	  String result = "";
	  
	  int grau = Integer.parseInt(st.nextToken());
	  String min = st.nextToken().trim();
	  String resto = st.nextToken().trim();
	  double dec = (double)Integer.parseInt(min)*(double)60 + Double.parseDouble(resto.replace(",", "."));
	  if(grau>0){
		  result += Double.toString( ((double)grau) + (dec/(double)3600) );
	  }else{
		  result += Double.toString( ((double)grau) - (dec/(double)3600) );
	  }
	  
	  return result;	  
  }
  
  public void redesenhaMapa(App app){
		if(isDesatualizado()){
			if(!conectado){
				app.setVisible(true);
				connect();
				
				//força a rederização do Mapa (resolvendo o bug da primeira renderização 
				app.treeSplitPane.setDividerLocation(app.treeSplitPane.getDividerLocation()-1);
				
			}
			
		    String kml = "";
		    try {
		    	kml = getResultsKML(app);
				setKml(kml);
			} catch (IOException e1) {
				e1.printStackTrace();
			}finally {
				setDesatualizado(false);
			}
		}
  }

}