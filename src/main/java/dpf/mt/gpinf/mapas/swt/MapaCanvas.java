package dpf.mt.gpinf.mapas.swt;

import java.awt.Component;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import dpf.mt.gpinf.mapas.AbstractMapaCanvas;

/**
 * A simple canvas that encapsulates a SWT Browser instance.
 * Add it to a AWT or Swing container and call "connect()" after
 * the container has been made visible.
 */
public class MapaCanvas extends AbstractMapaCanvas {

  private Thread swtThread;
  private Browser swtBrowser;
  
  boolean connected = false;
  SaveKMLFunction savef = null;
 
  public void addSaveKmlFunction(Runnable save){
	  this.saveRunnable = save;
	  final MapaCanvas canvas = this;
	  if(connected){
		  swtBrowser.getDisplay().syncExec(new Runnable() {
				@Override
				public void run() {
					savef = new SaveKMLFunction(canvas.saveRunnable, swtBrowser, "exportarKmlBF");
				}
			});
	  }
  }
  
  /**
   * Connect this canvas to a SWT shell with a Browser component
   * and starts a background thread to handle SWT events. This method
   * waits until the browser component is ready.
   */
  public void connect() {
    if (this.swtThread == null) {
      final MapaCanvas canvas = this;
      this.swtThread = new Thread() {
        @Override
        public void run() {
          try {
            Display display = new Display();
            Shell shell = SWT_AWT.new_Shell(display, canvas);
            shell.setLayout(new FillLayout());

            synchronized (this) {
              swtBrowser = new Browser(shell, SWT.NONE);
              swtBrowser.setJavascriptEnabled(true);
              this.notifyAll();
            }
            
            new MapSelectFunction(canvas, swtBrowser, "selecionaMarcadorBF");
            new MarkerMouseClickedFunction(canvas, swtBrowser, "markerMouseClickedBF");
            new MarkerMouseClickedFunction(canvas, swtBrowser, "markerMouseDblClickedBF");
            new MarkerMousePressedFunction(canvas, swtBrowser, "markerMousePressedBF");
            new MarkerMouseReleasedFunction(canvas, swtBrowser, "markerMouseReleasedBF");
            new MarkerMouseEnteredFunction(canvas, swtBrowser, "markerMouseEnteredBF");
            new MarkerMouseExitedFunction(canvas, swtBrowser, "markerMouseExitedBF");
            new SelecionaItemFunction(canvas, swtBrowser, "marcaMarcadorBF");
            
            if((savef==null)&&(canvas.saveRunnable!=null)){
				savef = new SaveKMLFunction(canvas.saveRunnable, swtBrowser, "exportarKmlBF");
            }
            
            shell.open();
            connected = true;
            
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
  
  public void setText(final String html){
	  getBrowser().getDisplay().asyncExec(new Runnable() {
		  public void run(){
			  getBrowser().setText(html);
		  }
	  });
  }
  
  public void setKML(String kml){
	  try {
		  
		  String html = IOUtils.toString(getClass().getResourceAsStream("main.html"), "UTF-8");
		  String js = IOUtils.toString(getClass().getResourceAsStream("geoxmlfull_v3.js"), "UTF-8");
		  String js2 = IOUtils.toString(getClass().getResourceAsStream("keydragzoom.js"), "UTF-8");
		  String js3 = IOUtils.toString(getClass().getResourceAsStream("extensions.js"), "UTF-8");
		  String js4 = IOUtils.toString(getClass().getResourceAsStream("ext_geoxml3.js"), "UTF-8");

		  String b64_selecionado = "data:image/png;base64," + Base64.getEncoder().encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado.png")));
		  String b64_selecionado_m = "data:image/png;base64," + Base64.getEncoder().encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado_m.png")));
		  String b64_normal = "data:image/png;base64," + Base64.getEncoder().encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_normal.png")));
		  String b64_marcado = "data:image/png;base64," + Base64.getEncoder().encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_marcado.png")));
		  
		  html = html.replace("{{load_geoxml3}}", js);
		  html = html.replace("{{load_keydragzoom}}", js2);
		  html = html.replace("{{load_extensions}}", js3);
		  html = html.replace("{{load_geoxml3_ext}}", js4);
		  html = html.replace("{{icone_selecionado_base64}}", b64_selecionado);
		  html = html.replace("{{icone_base64}}", b64_normal);
		  html = html.replace("{{icone_selecionado_m_base64}}", b64_selecionado_m);
		  html = html.replace("{{icone_m_base64}}", b64_marcado);		  
		  html = html.replace("{{kml}}", kml.replace("\n", "").replace("\r", ""));
		  
		  setText(html);		  
	  } catch (IOException e) {
		  e.printStackTrace();
	  }
  }

  public boolean isConnected() {
	return connected;
 }

public void redesenha(){
	
	if(this.selecoesAfazer!=null){
		//repinta selecoes alteradas
		final String[] marks = new String[this.selecoesAfazer.keySet().size()]; 
		this.selecoesAfazer.keySet().toArray(marks);
		final HashMap <String, Boolean> selecoesAfazerCopy = selecoesAfazer;

		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				for(int i = 0; i<marks.length; i++){
					Boolean b = selecoesAfazerCopy.get(marks[i]);
					swtBrowser.execute("gxml.seleciona("+marks[i]+",'"+b+"');");
				}
			}
		});
		this.selecoesAfazer = null;
	}
}

@Override
public Component getContainer() {
	return this;
}

}