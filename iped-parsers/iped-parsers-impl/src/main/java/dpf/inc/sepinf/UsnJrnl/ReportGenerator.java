package dpf.inc.sepinf.UsnJrnl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class ReportGenerator {

    private static final String NEW_ROW = "<TR>"; //$NON-NLS-1$
    private static final String CLOSE_ROW = "</TR>"; //$NON-NLS-1$

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX"); //$NON-NLS-1$

    private static final String newCol(String value, String tag) {

        return "<" + tag + ">" + value + "</" + tag + ">";
    }

    private static final String newCol(String value) {
        return newCol(value, "TD");
    }

    public void startDocument(PrintWriter out) {
        out.println("<!DOCTYPE html>"); //$NON-NLS-1$
        out.println("<HTML>"); //$NON-NLS-1$
        out.println("<HEAD>"); //$NON-NLS-1$
        out.print("<meta charset=\"UTF-8\">"); //$NON-NLS-1$
        out.println("<style>\r\n" + "table {\r\n" + "  border-collapse: collapse;\r\n" + "  width: 100%;\r\n"
                + "  border: 1px solid #ddd;\r\n" + "}\r\n" + "\r\n" + "th, td {\r\n" + "  text-align: left;\r\n"
                + "  padding: 16px;\r\n" + "}\r\n" + "\r\n" + "tr:nth-child(even) {\r\n"
                + "  background-color: #f2f2f2;\r\n" + "}\r\n" + "</style>");
        out.println("</HEAD>"); //$NON-NLS-1$
        out.println("<BODY>"); //$NON-NLS-1$
        out.println("<h2>Usn Journal</h2>"); //$NON-NLS-1$
    }

    public void endDocument(PrintWriter out) {
        out.println("</BODY></HTML>"); //$NON-NLS-1$
    }

    public byte[] createHTMLReport(ArrayList<UsnJrnlEntry> entries) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, StandardCharsets.UTF_8)); // $NON-NLS-1$

        startDocument(out);

        out.print("<table border=1>");

        out.print(NEW_ROW);
        out.print(newCol("FileName", "TH"));
        out.print(newCol("USN", "TH"));
        out.print(newCol("TimeStamp", "TH"));
        out.print(newCol("Reasons", "TH"));
        out.print(newCol("MTF Ref.", "TH"));
        out.print(newCol("MTF parent Ref", "TH"));
        out.print(newCol("File attr", "TH"));
        out.print(newCol("Source Info", "TH"));
        out.print(newCol("Security Id", "TH"));
        out.print(CLOSE_ROW);
        for (UsnJrnlEntry u : entries) {
            out.print(NEW_ROW);
            out.print(newCol(u.getFileName()));
            out.print(newCol(u.getUSN() + ""));
            out.print(newCol(timeFormat.format(u.getFileTime())));
            out.print(newCol(u.getReasons()));
            out.print(newCol(u.getMftRef() + ""));
            out.print(newCol(u.getParentMftRef() + ""));
            out.print(newCol(u.getHumanAttributes()));
            out.print(newCol(u.getSourceInformation() + ""));
            out.print(newCol(u.getSecurityId() + ""));
            out.print(CLOSE_ROW);
        }

        out.print("</table>");

        endDocument(out);

        out.flush();
        return bout.toByteArray();

    }

}
