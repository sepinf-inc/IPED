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

import dpf.sp.gpinf.indexer.Messages;
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
		  if(fDialog==null)	 fDialog = new FileDialog(App.get(), Messages.getString("KMLResult.Save"), FileDialog.SAVE); //$NON-NLS-1$
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
				  JOptionPane.showMessageDialog(null, Messages.getString("KMLResult.NoGPSItem")); //$NON-NLS-1$
			
			return kml;
			
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}  
		return ""; //$NON-NLS-1$
		  
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
			
			  StringBuilder tourPlayList = new StringBuilder(""); //$NON-NLS-1$
			  StringBuilder kml= new StringBuilder(""); //$NON-NLS-1$

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

			  kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"); //$NON-NLS-1$
			  kml.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\" >"); //$NON-NLS-1$
			  kml.append("<Document>"); //$NON-NLS-1$
			  kml.append("<name>" + Messages.getString("KMLResult.SearchResults") + "</name>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			  kml.append("<open>1</open>"); //$NON-NLS-1$
			  kml.append("<description>" + Messages.getString("KMLResult.SearchResultsDescription") + "</description>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			  kml.append("<Style id=\"basico\"><BalloonStyle><![CDATA[" //$NON-NLS-1$
			  		+ " $[name] <br/> $[description] <br/> " + Messages.getString("KMLResult.ShowInTree") //$NON-NLS-1$ //$NON-NLS-2$
			  		+ "]]>" //$NON-NLS-1$
			  		+ "</BalloonStyle></Style>"); //$NON-NLS-1$

			  kml.append("<Folder>"); //$NON-NLS-1$
			  kml.append("<name>" + Messages.getString("KMLResult.Results") + "</name>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			  MultiSearchResult results = app.getResults();
			  org.apache.lucene.document.Document doc;
			  
			  SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"); //$NON-NLS-1$
			  df.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
			  
			  if(progress != null){
				  progress.setNote(Messages.getString("KMLResult.LoadingGPSData") + "..."); //$NON-NLS-1$ //$NON-NLS-2$
				  progress.setMaximum(results.getLength()); 
			  }
			  
			  String metaPrefix = ExtraProperties.IMAGE_META_PREFIX.replace(":", "\\:"); //$NON-NLS-1$ //$NON-NLS-2$
			  String ufedPrefix = ExtraProperties.UFED_META_PREFIX.replace(":", "\\:"); //$NON-NLS-1$ //$NON-NLS-2$
			  String query = "(" + metaPrefix + "GPS\\ Latitude:* AND " + metaPrefix + "GPS\\ Longitude:*) " + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			                  "(" + ufedPrefix + "Latitude:[-90 TO 90] AND " + ufedPrefix + "Longitude:[-180 TO 180])";  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
				  
				  String lat = doc.get(ExtraProperties.IMAGE_META_PREFIX + "geo:lat"); //$NON-NLS-1$
				  if(lat == null)
                      lat = doc.get(ExtraProperties.UFED_META_PREFIX + "Latitude"); //$NON-NLS-1$
				  String longit = doc.get(ExtraProperties.IMAGE_META_PREFIX + "geo:long"); //$NON-NLS-1$
				  if(longit == null)
                      longit = doc.get(ExtraProperties.UFED_META_PREFIX + "Longitude"); //$NON-NLS-1$
				  
				  if(lat != null && longit != null){
					  
					  if(progress != null) progress.setNote(Messages.getString("KMLResult.LoadingGPSData") + ": " + (++itemsWithGPS)); //$NON-NLS-1$ //$NON-NLS-2$
					  
					  //necessário para múltiplos casos carregados, pois ids se repetem
					  String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
					  
					  kml.append("<Placemark>"); //$NON-NLS-1$
					  //kml+="<styleUrl>#basico</styleUrl>";
					  kml.append("<id>" + gid + "</id>"); //$NON-NLS-1$ //$NON-NLS-2$
					  kml.append("<name>" + htmlFormat(doc.get(IndexItem.NAME)) + "</name>"); //$NON-NLS-1$ //$NON-NLS-2$
				      if(!IndexItem.ID.equals(coluna))
						  kml.append("<description>"+htmlFormat(coluna)+":"+htmlFormat(doc.get(coluna))+"</description>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					  else
						  kml.append("<description>"+htmlFormat(coluna)+":"+htmlFormat(gid)+"</description>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					  
					  kml.append("<Point><coordinates>"+longit+","+lat+",0</coordinates></Point>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					  
					  tourPlayList.append("<gx:FlyTo>" //$NON-NLS-1$
					  		+ "<gx:duration>5.0</gx:duration>" //$NON-NLS-1$
					  		+ "<gx:flyToMode>bounce</gx:flyToMode>" //$NON-NLS-1$
					  		+ "<LookAt>" //$NON-NLS-1$
					  		+ "<longitude>"+longit+"</longitude>" //$NON-NLS-1$ //$NON-NLS-2$
					  		+ "<latitude>"+lat+"</latitude>" //$NON-NLS-1$ //$NON-NLS-2$
					  		+ "<altitude>300</altitude>" //$NON-NLS-1$
					  		+ "<altitudeMode>relativeToGround</altitudeMode>" //$NON-NLS-1$
					  		+ "</LookAt>"						   //$NON-NLS-1$
					  		+ "</gx:FlyTo>" //$NON-NLS-1$
					  		+ "<gx:AnimatedUpdate><gx:duration>0.0</gx:duration><Update><targetHref/><Change>" //$NON-NLS-1$
					  		+ "	<Placemark targetId=\"" + gid + "\"><gx:balloonVisibility>1</gx:balloonVisibility></Placemark>" //$NON-NLS-1$ //$NON-NLS-2$
					  		+ "	</Change></Update></gx:AnimatedUpdate>" //$NON-NLS-1$
					  		+ "<gx:Wait><gx:duration>1.0</gx:duration></gx:Wait>" //$NON-NLS-1$
					  		+ "<gx:AnimatedUpdate><gx:duration>0.0</gx:duration><Update><targetHref/><Change>" //$NON-NLS-1$
				  			+ "	<Placemark targetId=\"" + gid + "\"><gx:balloonVisibility>0</gx:balloonVisibility></Placemark>" //$NON-NLS-1$ //$NON-NLS-2$
				  			+ "	</Change></Update></gx:AnimatedUpdate>"); //$NON-NLS-1$

					  kml.append("<ExtendedData>"); //$NON-NLS-1$
					  
					  for(int j = 0; j < colunas.length; j++){
						  if(!IndexItem.ID.equals(colunas[j]))
							  kml.append("<Data name=\""+htmlFormat(colunas[j])+"\"><value>"+ htmlFormat(doc.get(colunas[j])) +"</value></Data>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						  else
							  kml.append("<Data name=\""+IndexItem.ID+"\"><value>"+ gid +"</value></Data>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					  }
					  
					  boolean checked = App.get().appCase.getMultiMarcadores().isSelected(item);
					  kml.append("<Data name=\"checked\"><value>"+checked+"</value></Data>"); //$NON-NLS-1$ //$NON-NLS-2$
					  
					  boolean selected  = App.get().getResultsTable().isRowSelected(row);
					  kml.append("<Data name=\"selected\"><value>"+selected+"</value></Data>"); //$NON-NLS-1$ //$NON-NLS-2$
					  kml.append("</ExtendedData>"); //$NON-NLS-1$

					  String dataCriacao = doc.get(IndexItem.CREATED);
					  if(dataCriacao != null && !dataCriacao.isEmpty())
						try {
							dataCriacao = df.format(DateUtil.stringToDate(dataCriacao)) + "Z"; //$NON-NLS-1$
						} catch (ParseException e) {
							dataCriacao = ""; //$NON-NLS-1$
						} 
					  kml.append("<TimeSpan><begin>"+dataCriacao+"</begin></TimeSpan>"); //$NON-NLS-1$ //$NON-NLS-2$

					  kml.append("</Placemark>"); //$NON-NLS-1$
				  }else{
					  contSemCoordenadas++;
				  }
				  
			  }
			  kml.append("</Folder>"); //$NON-NLS-1$

			  kml.append("<gx:Tour>"); //$NON-NLS-1$
			  if(descendingOrder){
				  kml.append("  <name>"+coluna+"-DESC</name>"); //$NON-NLS-1$ //$NON-NLS-2$
			  }else{
				  kml.append("  <name>"+coluna+"</name>"); //$NON-NLS-1$ //$NON-NLS-2$
			  }
			  kml.append("  <gx:Playlist>"); //$NON-NLS-1$
			  kml.append(tourPlayList);
			  kml.append("  </gx:Playlist>"); //$NON-NLS-1$
			  kml.append("</gx:Tour>"); //$NON-NLS-1$
			  
			  kml.append("</Document>"); //$NON-NLS-1$
			  kml.append("</kml>"); //$NON-NLS-1$
			  
			  return kml.toString();
			
		}
		  
	  }
	  
	  static public String htmlFormat(String html){
		  if(html==null){
			  return ""; //$NON-NLS-1$
		  }
		  return SimpleHTMLEncoder.htmlEncode(html);
	  }

	  static public String converteCoordFormat(String coord){
		  StringTokenizer st = new StringTokenizer(coord, "°\"'"); //$NON-NLS-1$
		  String result = ""; //$NON-NLS-1$
		  
		  int grau = Integer.parseInt(st.nextToken());
		  String min = st.nextToken().trim();
		  String resto = st.nextToken().trim();
		  double dec = (double)Integer.parseInt(min)*(double)60 + Double.parseDouble(resto.replace(",", ".")); //$NON-NLS-1$ //$NON-NLS-2$
		  if(grau>0){
			  result += Double.toString( ((double)grau) + (dec/(double)3600) );
		  }else{
			  result += Double.toString( ((double)grau) - (dec/(double)3600) );
		  }
		  
		  return result;	  
	  }
}
