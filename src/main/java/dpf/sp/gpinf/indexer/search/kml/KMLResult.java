package dpf.sp.gpinf.indexer.search.kml;

import java.awt.FileDialog;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
		  String tourPlayList="";
		  String kml="";

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
		  
		  
		  kml+="<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
		  kml+="<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\" >";
		  kml+="<Document>";
		  kml+="<name>Resultados da pesquisa</name>";
		  kml+="<open>1</open>";
		  kml+="<description>Resultados da pesquisa georeferenciados.</description>";
		  kml+="<Style id=\"basico\"><BalloonStyle><![CDATA["
		  		+ " $[name] <br/> $[description] <br/> Mostra na árvore."
		  		+ "]]>"
		  		+ "</BalloonStyle></Style>";

		  kml+="<Folder>";
		  kml+="<name>Resultados</name>";

		  int contSemCoordenadas=0;
		  SearchResult results = app.getResults();		  
		  for (int i = 0; i < results.docs.length; i++) {
			  org.apache.lucene.document.Document doc = app.searcher.doc(results.docs[app.getResultsTable().convertRowIndexToModel(i)]);
			  
			  String lat = doc.get("GPS Latitude");
			  if(lat!=null){
				  kml+="<Placemark>";
				  //kml+="<styleUrl>#basico</styleUrl>";
				  kml+="<id>"+doc.get("id")+"</id>";
				  kml+="<name>"+doc.get("id")+"-"+doc.get("nome")+"</name>";
				  kml+="<description>"+htmlFormat(coluna)+":"+htmlFormat(doc.get(coluna))+"</description>";
				  lat = converteCoordFormat(lat);
				  String longit = doc.get("GPS Longitude");
				  longit = converteCoordFormat(longit);
				  kml+="<Point><coordinates>"+longit+","+lat+",0</coordinates></Point>";
				  
				  tourPlayList+="<gx:FlyTo>"
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
			  			+ "	</Change></Update></gx:AnimatedUpdate>";

				  kml+="<ExtendedData>";
				  
				  for(int j=0; j<colunas.length; j++){
					  kml+="<Data name=\""+colunas[j]+"\"><value>"+doc.get(colunas[j])+"</value></Data>";
				  }
				  
				  boolean checked  = ((Boolean)App.get().getResultsTable().getValueAt(i, 1));
				  kml+="<Data name=\"checked\"><value>"+checked+"</value></Data>";
				  boolean selected  = ((Boolean)App.get().getResultsTable().isRowSelected(i));
				  kml+="<Data name=\"selected\"><value>"+selected+"</value></Data>";
				  kml+="</ExtendedData>";
				  

				  String dataCriacao = doc.get("criacao").replace(".", "-");
				  dataCriacao = dataCriacao.substring(0, 10)+"T"+dataCriacao.replace(".",":").substring(11, 19)+"Z"; 
				  kml+="<TimeSpan><begin>"+dataCriacao+"</begin></TimeSpan>";

				  kml+="</Placemark>";
			  }else{
				  contSemCoordenadas++;
			  }
			  
		  }
		  kml+="</Folder>";

		  kml+="<gx:Tour>";
		  if(descendingOrder){
			  kml+="  <name>"+coluna+"-DESC</name>";
		  }else{
			  kml+="  <name>"+coluna+"</name>";
		  }
		  kml+="  <gx:Playlist>";
		  kml+=tourPlayList;
		  kml+="  </gx:Playlist>";
		  kml+="</gx:Tour>";
		  
		  kml+="</Document>";
		  kml+="</kml>";

		  return kml;
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
