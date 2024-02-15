package iped.parsers.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.Messages;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;
import iped.utils.SimpleHTMLEncoder;
import iped.utils.tika.IpedMetadata;

public class TableReportGenerator {

    private ResultSet data;
    private int cols, rows, totRows;
    private Boolean hasNext;

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    private JDBCTableReader tableReader;

    private static final String newCol(String value, String tag) {
    	return newCol(value, tag, true);
    }
    
    private static final String newCol(String value, String tag, boolean encodeHTML) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(tag).append(">");
        if (value != null) {
        	if(encodeHTML) {
                sb.append(SimpleHTMLEncoder.htmlEncode(value));
        	}else {
                sb.append(value);
        	}
        }
        sb.append("</").append(tag).append(">");
        return sb.toString();
    }

    private static final String newCol(String value) {
        return newCol(value, "td", true);
    }

    private static final String newCol(String value, boolean encodeHTML) {
        return newCol(value, "td", encodeHTML);
    }

    public TableReportGenerator(JDBCTableReader tableReader) {
        this.data = tableReader.getTableData();
        this.tableReader = tableReader;
    }

    public InputStream createHtmlReport(int maxRows, ContentHandler handler,
            ParseContext context, TemporaryResources tmp, IpedMetadata tableM)
            throws IOException, SQLException, SAXException {
        rows = 0;
        Path path = tmp.createTempFile();
        try (OutputStream os = Files.newOutputStream(path);
                Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                PrintWriter out = new PrintWriter(writer);) {
            cols = tableReader.getHeaders().size();

            out.print("<html>");
            out.print("<head>"); //$NON-NLS-1$
            out.print("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />");
            out.print("<style>"); //$NON-NLS-1$
            out.print("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            out.print("</style>"); //$NON-NLS-1$
            out.print("</head>"); //$NON-NLS-1$

            out.print("<body>");
            out.print("<b>");
            out.print(Messages.getString("AbstractDBParser.Table") + tableReader.getTableName());
            out.print("</b>");

            out.print("<table name=\"" + tableReader.getTableName() + "\" >");
            out.print("<thead>");
            out.print("<tr>"); //$NON-NLS-1$
            for (String header : tableReader.getHeaders()) {
                out.print(newCol(header, "th"));
            }
            out.print("</tr>"); //$NON-NLS-1$

            out.print("</thead>");

            out.print("<tbody>");

            if (totRows == 0) {
                next();
            }else {
            	hasNext=true;
            }
            
            ArrayList<String> locations = new ArrayList<String>();
            ArrayList<String> lats = new ArrayList<String>();
            ArrayList<String> longs = new ArrayList<String>();
            
            LngColumnExtractor lngExtractor = new LngColumnExtractor();
            LatColumnExtractor latExtractor = new LatColumnExtractor();
            HashMap<String, ArrayList<String>> dates = new HashMap<String, ArrayList<String>>();

            if (hasNext) {
                do {
                    rows++;
                    out.print("<tr>");
                	ResultSetMetaData rsmd = data.getMetaData();
                	Boolean latExtracted = null;
                	Boolean lngExtracted = null;
                    for (int i = 1; i <= cols; i++) {
                        String text = tableReader.handleCell(data, rsmd, i, handler, context, rows);
                        
                        boolean isTime = false;
                        if(text!=null) {
                            try {
                                int datePos = text.indexOf(AbstractDBParser.DATETIME_MARKUP_START);
                                if(datePos!=-1) {
                                	String datetext = text.substring(datePos+AbstractDBParser.DATETIME_MARKUP_START.length());
                                	datetext = datetext.substring(0,datetext.indexOf("\">"));
                                	String colName = AbstractDBParser.DATABASEDATECOLUMN_PREFIX + rsmd.getTableName(i) + ":"
                                            + rsmd.getColumnName(i);
                                	ArrayList<String> valueList = dates.get(colName);
                                	if(valueList==null) {
                                        valueList = new ArrayList<>();
                                        dates.put(colName, valueList);
                                	}
                                    valueList.add(datetext);
                                	isTime=true;
                                }
                            }catch(Exception e) {
                            	e.printStackTrace();
                            }
                            
                            if(latExtractor.extractMetadata(data, i)) {
                            	latExtracted=true;
                            }
                            if(lngExtractor.extractMetadata(data, i)) {
                            	lngExtracted=true;
                            }
                            
                            if(latExtracted!=null && lngExtracted!=null) {
                            	locations.add(latExtractor.getLastExtraction() + ";" + lngExtractor.getLastExtraction());
                            	latExtracted=null;
                            	lngExtracted=null;
                            }
                        }
                        
                        if(isTime) {
                            out.print(newCol(text,false));//does not encode html
                        }else {
                            out.print(newCol(text));
                        }
                    }
                    out.print("</tr>");

                    if(!(latExtracted!=null && lngExtracted!=null)) {
                    	if(latExtracted!=null) {
                    		latExtractor.cancelLastExtraction();
                    	}
                    	if(lngExtracted!=null) {
                    		lngExtractor.cancelLastExtraction();
                    	}
                    }
                    
                } while (next() && rows < maxRows);
            }

            if (!dates.isEmpty()) {
                for (Entry<String, ArrayList<String>> entry : dates.entrySet()) {
                    MetadataUtil.setMetadataType(entry.getKey(), Date.class);
                    tableM.set(entry.getKey(), entry.getValue());
                }
            }

            if(locations.size()>0) {
            	latExtractor.applyExtractedMetadatas(tableM);
            	lngExtractor.applyExtractedMetadatas(tableM);
            	tableM.set(ExtraProperties.LOCATIONS, locations);
            }

            totRows += rows;
            out.print("</tbody>");
            out.print("</table>");

            if (tableReader.hasDateGuessed()) {
                out.print(Messages.getString("AbstractDBParser.ProbableDate")); //$NON-NLS-1$
            }

            out.print("</body>");
            out.print("</html>");
            out.close();

        }
        return Files.newInputStream(path);

    }

    public int getTotRows() {
        return totRows;
    }

    public Boolean hasNext() {
        return hasNext;
    }

    private boolean next() throws SQLException {
        return hasNext = data != null && data.next();
    }

}
