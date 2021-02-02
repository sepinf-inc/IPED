package dpf.inc.sepinf.UsnJrnl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class ReportGenerator {
	
	private static final String NEW_ROW = "<TR><TD>"; //$NON-NLS-1$
    private static final String CLOSE_ROW = "</TD></TR>"; //$NON-NLS-1$
    private static final String NEW_COL = "</TD><TD>"; //$NON-NLS-1$
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$

    public void startDocument(PrintWriter out) {
        out.println("<!DOCTYPE html>"); //$NON-NLS-1$
        out.println("<HTML>"); //$NON-NLS-1$
        out.println("<HEAD>"); //$NON-NLS-1$
        out.print("<meta charset=\"UTF-8\">"); //$NON-NLS-1$
        out.println("</HEAD>"); //$NON-NLS-1$
        out.println("<BODY>"); //$NON-NLS-1$
        out.println("<h2>Usn Journal</h2>"); //$NON-NLS-1$
    }
    
    
    public void endDocument(PrintWriter out) {
        out.println("</BODY></HTML>"); //$NON-NLS-1$
    }
    
    
    public byte[] createHTMLReport(ArrayList<UsnJrnlEntry> entries) {
    	 ByteArrayOutputStream bout = new ByteArrayOutputStream();
         PrintWriter out = new PrintWriter(new OutputStreamWriter(bout,StandardCharsets.UTF_8)); //$NON-NLS-1$
         
         startDocument(out);
         
         out.print("<table border=1>");
         
         out.print(NEW_ROW+"FileName");
         out.print(NEW_COL+"USN");
         out.print(NEW_COL+"TimeStamp");
         out.print(NEW_COL+"Reasons");
         out.print(NEW_COL+"MTF Ref.");
         out.print(NEW_COL+"MTF parent Ref");
         out.print(NEW_COL+"File attr");
         out.print(NEW_COL+"Source Info");
         out.print(NEW_COL+"Security Id");
         out.print(CLOSE_ROW);
         for(UsnJrnlEntry u:entries) {
        	 out.print(NEW_ROW+u.getFileName());
             out.print(NEW_COL+u.getUSN());
             out.print(NEW_COL+timeFormat.format(u.getFileTime()));
             out.print(NEW_COL+u.getReasons());
             out.print(NEW_COL+u.getMftRef());
             out.print(NEW_COL+u.getParentMftRef());
             out.print(NEW_COL+u.getHumanAttributes());
             out.print(NEW_COL+u.getSourceInformation());
             out.print(NEW_COL+u.getSecurityId());
             out.print(CLOSE_ROW);
         }        
         
         out.print("</table>");
         
         
         endDocument(out);
                  
         out.flush();
         return bout.toByteArray();
    	
    }


}
