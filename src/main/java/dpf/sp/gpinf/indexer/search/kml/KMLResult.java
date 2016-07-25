package dpf.sp.gpinf.indexer.search.kml;

import java.io.IOException;
import java.util.StringTokenizer;

import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.SearchResult;

public class KMLResult {
	  public static String getResultsKML(App app) throws IOException{
		  String kml="";
		  kml+="<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
		  kml+="<kml xmlns=\"http://www.opengis.net/kml/2.2\">";
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
			  org.apache.lucene.document.Document doc = app.searcher.doc(results.docs[i]);
			  
			  String lat = doc.get("GPS Latitude");
			  if(lat!=null){
				  kml+="<Placemark>";
				  //kml+="<styleUrl>#basico</styleUrl>";
				  kml+="<id>"+doc.get("id")+"</id>";
				  kml+="<name>"+doc.get("id")+"-"+doc.get("nome")+"</name>";
				  kml+="<description>"+htmlFormat(doc.get("caminho"))+"</description>";
				  lat = converteCoordFormat(lat);
				  String longit = doc.get("GPS Longitude");
				  longit = converteCoordFormat(longit);
				  kml+="<Point><coordinates>"+longit+","+lat+",0</coordinates></Point>";

				  kml+="<ExtendedData>";
				  kml+="<Data name=\"id\"><value>"+doc.get("id")+"</value></Data>";
				  boolean checked  = ((Boolean)App.get().getResultsTable().getValueAt(i, 1));
				  kml+="<Data name=\"checked\"><value>"+checked+"</value></Data>";
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
		  kml+="</Document>";
		  kml+="</kml>";

		  return kml;
	  }
	  
	  static public String htmlFormat(String html){
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
