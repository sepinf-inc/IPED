package dpf.mt.gpinf.indexer.search.kml;

import java.awt.Dialog.ModalityType;
import java.awt.FileDialog;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;
import javax.swing.RowSorter.SortKey;

import org.apache.commons.lang.ArrayUtils;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.desktop.ColumnsManager;
import dpf.sp.gpinf.indexer.parsers.util.ExtraProperties;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.util.DateUtil;
import dpf.sp.gpinf.indexer.util.ProgressDialog;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;

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
	    	  w.write(getResultsKML(App.get(), cols, false));
		      w.close();
		      f=null;
	      } catch (IOException e) {
	    	  // TODO Auto-generated catch block
	    	  e.printStackTrace();
	      }
	  }
	
	  public static String getResultsKML(App app) throws IOException{
		  return getResultsKML(app, new String[]{IndexItem.ID}, true);
	  }
	  
	  public static String getResultsKML(App app, String[] colunas, boolean showProgress) throws IOException{
		  
		  ProgressDialog progress = null;
		  if(showProgress)
			  progress = new ProgressDialog(app, null, false, 1000, ModalityType.APPLICATION_MODAL);
		  
		  GetResultsKML getKML = new GetResultsKML(app, colunas, progress);
		  getKML.execute();
		  
		  if(showProgress)
			  progress.setVisible();
		  try {
			String kml = getKML.get();
			if(showProgress && getKML.itemsWithGPS == 0)
				  JOptionPane.showMessageDialog(null, "Nenhum item georeferenciado encontrado!");
			
			return kml;
			
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}  
		return "";
		  
	  }
	  
	  static class GetResultsKML extends dpf.sp.gpinf.indexer.util.CancelableWorker<String, Integer>{
		  
		  App app;
		  String[] colunas;
		  ProgressDialog progress;
		  int contSemCoordenadas=0, itemsWithGPS = 0;
		  
		  GetResultsKML(App app, String[] colunas, ProgressDialog progress){
			this.app = app;
			this.colunas = colunas;
			this.progress = progress;
		  }
		  
		  @Override
		  public void done() {
			  if(progress != null)
				  progress.close();
		  }

		@Override
		protected String doInBackground() throws Exception {
			
			  StringBuilder tourPlayList = new StringBuilder("");
			  StringBuilder kml= new StringBuilder("");

			  String coluna = null;
			  SortKey ordem = null;
			  boolean descendingOrder = false;
			  try{
				  ordem = app.getResultsTable().getRowSorter().getSortKeys().get(0);
				  coluna = ColumnsManager.getInstance().getLoadedCols()[ordem.getColumn()-2];
				  descendingOrder = ordem.getSortOrder().equals(ordem.getSortOrder().DESCENDING);
			  }catch(Exception ex){
				  coluna = IndexItem.ID;
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

			  MultiSearchResult results = app.getResults();
			  org.apache.lucene.document.Document doc;
			  
			  SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			  df.setTimeZone(TimeZone.getTimeZone("GMT"));
			  
			  if(progress != null){
				  progress.setNote("Obtendo coordenadas" + "...");
				  progress.setMaximum(results.getLength()); 
			  }
			  
			  String metaPrefix = ExtraProperties.IMAGE_META_PREFIX.replace(":", "\\:");
			  String query = metaPrefix + "GPS\\ Latitude:* AND " + metaPrefix + "GPS\\ Longitude:*";
			  IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, query);
			  MultiSearchResult multiResult = searcher.multiSearch();
			  
			  HashSet<ItemId> gpsItems = new HashSet<ItemId>();
			  for(ItemId item : multiResult.getIterator())
				  gpsItems.add(item);

			  for (int row = 0; row < results.getLength(); row++) {
				  
				  if(progress != null){
					  progress.setProgress(row + 1);
					  if(progress.isCanceled())
						  break; 
				  }

				  ItemId item = results.getItem(app.getResultsTable().convertRowIndexToModel(row));
				  
				  if(!gpsItems.contains(item))
				  	  continue;
				  
				  int luceneId = app.get().appCase.getLuceneId(item);
				  doc =  app.appCase.getSearcher().doc(luceneId);
				  
				  String lat = doc.get(ExtraProperties.IMAGE_META_PREFIX + "GPS Latitude");
				  String longit = doc.get(ExtraProperties.IMAGE_META_PREFIX + "GPS Longitude");
				  
				  if(lat != null && longit != null){
					  
					  if(progress != null) progress.setNote("Obtendo coordenadas" + ": " + (++itemsWithGPS));
					  
					  //necessário para múltiplos casos carregados, pois ids se repetem
					  String gid = item.getSourceId() + "-" + item.getId();
					  
					  kml.append("<Placemark>");
					  //kml+="<styleUrl>#basico</styleUrl>";
					  kml.append("<id>" + gid + "</id>");
					  kml.append("<name>" + htmlFormat(doc.get(IndexItem.NAME)) + "</name>");
				      if(!IndexItem.ID.equals(coluna))
						  kml.append("<description>"+htmlFormat(coluna)+":"+htmlFormat(doc.get(coluna))+"</description>");
					  else
						  kml.append("<description>"+htmlFormat(coluna)+":"+htmlFormat(gid)+"</description>");
					  
					  lat = converteCoordFormat(lat);
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
					  		+ "	<Placemark targetId=\"" + gid + "\"><gx:balloonVisibility>1</gx:balloonVisibility></Placemark>"
					  		+ "	</Change></Update></gx:AnimatedUpdate>"
					  		+ "<gx:Wait><gx:duration>1.0</gx:duration></gx:Wait>"
					  		+ "<gx:AnimatedUpdate><gx:duration>0.0</gx:duration><Update><targetHref/><Change>"
				  			+ "	<Placemark targetId=\"" + gid + "\"><gx:balloonVisibility>0</gx:balloonVisibility></Placemark>"
				  			+ "	</Change></Update></gx:AnimatedUpdate>");

					  kml.append("<ExtendedData>");
					  
					  for(int j = 0; j < colunas.length; j++){
						  if(!IndexItem.ID.equals(colunas[j]))
							  kml.append("<Data name=\""+htmlFormat(colunas[j])+"\"><value>"+ htmlFormat(doc.get(colunas[j])) +"</value></Data>");
						  else
							  kml.append("<Data name=\""+IndexItem.ID+"\"><value>"+ gid +"</value></Data>");
					  }
					  
					  boolean checked = App.get().appCase.getMultiMarcadores().isSelected(item);
					  kml.append("<Data name=\"checked\"><value>"+checked+"</value></Data>");
					  
					  boolean selected  = App.get().getResultsTable().isRowSelected(row);
					  kml.append("<Data name=\"selected\"><value>"+selected+"</value></Data>");
					  kml.append("</ExtendedData>");

					  String dataCriacao = doc.get(IndexItem.CREATED);
					  if(dataCriacao != null && !dataCriacao.isEmpty())
						try {
							dataCriacao = df.format(DateUtil.stringToDate(dataCriacao)) + "Z";
						} catch (ParseException e) {
							dataCriacao = "";
						} 
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
		  
	  }
	  
	  static public String htmlFormat(String html){
		  if(html==null){
			  return "";
		  }
		  return SimpleHTMLEncoder.htmlEncode(html);
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
