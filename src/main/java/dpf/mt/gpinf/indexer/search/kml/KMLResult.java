package dpf.mt.gpinf.indexer.search.kml;

import java.awt.FileDialog;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.RowSorter;
import javax.swing.RowSorter.SortKey;

import org.apache.commons.lang.ArrayUtils;

import com.healthmarketscience.jackcess.impl.ColumnImpl.SortOrder;

import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.ColumnsManager;
import dpf.sp.gpinf.indexer.search.SearchResult;

public class KMLResult {
	  static FileDialog fDialog;
	  public static void saveKML(){
		  if(fDialog==null)	 fDialog = new FileDialog(App.get(), "Save", FileDialog.SAVE);
	      fDialog.setVisible(true);
	      String path = fDialog.getDirectory() + fDialog.getFile();
	      File f = new File(path);
	      
	      FileWriter w;
	      try {
	    	  w = new FileWriter(f);
	    	  String[] cols = ColumnsManager.getInstance().getLoadedCols();
	    	  cols = (String[]) ArrayUtils.subarray(cols, 2, cols.length);
	    	  w.write(getResultsKML(App.get(), cols));
		      w.close();
		      f=null;
	      } catch (IOException e) {
	    	  // TODO Auto-generated catch block
	    	  e.printStackTrace();
	      }
	  }
	
	  public static String getResultsKML(App app) throws IOException{
		  return getResultsKML(app, new String[]{"id"});
	  }

	  public static String getResultsKML(App app, String[] colunas) throws IOException{
		  StringBuffer tourPlayList = new StringBuffer("");
		  StringBuffer kml= new StringBuffer("");

		  String coluna = null;
		  SortKey ordem = null;
		  boolean descendingOrder = false;
		  try{
			  ordem = app.getResultsTable().getRowSorter().getSortKeys().get(0);
			  coluna = ColumnsManager.getInstance().getLoadedCols()[ordem.getColumn()-2];
			  descendingOrder = ordem.getSortOrder().equals(ordem.getSortOrder().DESCENDING);
		  }catch(Exception ex){
			  coluna = "id";
			  descendingOrder = false;
		  }

		  kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		  kml.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\" >");
		  kml.append("<Document>");
		  kml.append("<name>Resultados da pesquisa</name>");
		  kml.append("<open>1</open>");
		  kml.append("<description>Resultados da pesquisa georeferenciados.</description>");
		  kml.append("<Style id=\"basico\"><BalloonStyle><![CDATA["
		  		+ " $[name] <br/> $[description] <br/> Mostra na árvore."
		  		+ "]]>"
		  		+ "</BalloonStyle></Style>");

		  kml.append("<Folder>");
		  kml.append("<name>Resultados</name>");

		  int contSemCoordenadas=0;
		  SearchResult results = app.getResults();
		  org.apache.lucene.document.Document doc;

		  for (int i = 0; i < results.docs.length; i++) {
			  doc =  app.searcher.doc(results.docs[app.getResultsTable().convertRowIndexToModel(i)]);
			  
			  String lat = doc.get("GPS Latitude");
			  if(lat!=null){
				  kml.append("<Placemark>");
				  //kml+="<styleUrl>#basico</styleUrl>";
				  kml.append("<id>"+doc.get("id")+"</id>");
				  kml.append("<name>"+doc.get("id")+"-"+doc.get("nome")+"</name>");
				  kml.append("<description>"+htmlFormat(coluna)+":"+htmlFormat(doc.get(coluna))+"</description>");
				  lat = converteCoordFormat(lat);
				  String longit = doc.get("GPS Longitude");
				  longit = converteCoordFormat(longit);
				  kml.append("<Point><coordinates>"+longit+","+lat+",0</coordinates></Point>");
				  
				  tourPlayList.append("<gx:FlyTo>"
				  		+ "<gx:duration>5.0</gx:duration>"
				  		+ "<gx:flyToMode>bounce</gx:flyToMode>"
				  		+ "<LookAt>"
				  		+ "<longitude>"+longit+"</longitude>"
				  		+ "<latitude>"+lat+"</latitude>"
				  		+ "<altitude>300</altitude>"
				  		+ "<altitudeMode>relativeToGround</altitudeMode>"
				  		+ "</LookAt>"						  
				  		+ "</gx:FlyTo>"
				  		+ "<gx:AnimatedUpdate><gx:duration>0.0</gx:duration><Update><targetHref/><Change>"
				  		+ "	<Placemark targetId=\""+doc.get("id")+"\"><gx:balloonVisibility>1</gx:balloonVisibility></Placemark>"
				  		+ "	</Change></Update></gx:AnimatedUpdate>"
				  		+ "<gx:Wait><gx:duration>1.0</gx:duration></gx:Wait>"
				  		+ "<gx:AnimatedUpdate><gx:duration>0.0</gx:duration><Update><targetHref/><Change>"
			  			+ "	<Placemark targetId=\""+doc.get("id")+"\"><gx:balloonVisibility>0</gx:balloonVisibility></Placemark>"
			  			+ "	</Change></Update></gx:AnimatedUpdate>");

				  kml.append("<ExtendedData>");
				  
				  for(int j=0; j<colunas.length; j++){
					  kml.append("<Data name=\""+colunas[j]+"\"><value>"+doc.get(colunas[j])+"</value></Data>");
				  }
				  
				  boolean checked  = ((Boolean)App.get().getResultsTable().getValueAt(i, 1));
				  kml.append("<Data name=\"checked\"><value>"+checked+"</value></Data>");
				  boolean selected  = ((Boolean)App.get().getResultsTable().isRowSelected(i));
				  kml.append("<Data name=\"selected\"><value>"+selected+"</value></Data>");
				  kml.append("</ExtendedData>");
				  

				  String dataCriacao = doc.get("criacao").replace(".", "-");
				  dataCriacao = dataCriacao.substring(0, 10)+"T"+dataCriacao.replace(".",":").substring(11, 19)+"Z"; 
				  kml.append("<TimeSpan><begin>"+dataCriacao+"</begin></TimeSpan>");

				  kml.append("</Placemark>");
			  }else{
				  contSemCoordenadas++;
			  }
			  
		  }
		  kml.append("</Folder>");

		  kml.append("<gx:Tour>");
		  if(descendingOrder){
			  kml.append("  <name>"+coluna+"-DESC</name>");
		  }else{
			  kml.append("  <name>"+coluna+"</name>");
		  }
		  kml.append("  <gx:Playlist>");
		  kml.append(tourPlayList);
		  kml.append("  </gx:Playlist>");
		  kml.append("</gx:Tour>");
		  
		  kml.append("</Document>");
		  kml.append("</kml>");

		  return kml.toString();
	  }
	  
	  static public String htmlFormat(String html){
		  if(html==null){
			  return "";
		  }
		  return html.replace("/", "&#47;").replace("\\", "&#92;").replace("@", "&#64;");			
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
}
