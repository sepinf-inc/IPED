package dpf.sp.gpinf.indexer.parsers.jdbc;

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
import java.sql.SQLException;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;

public class TableReportGenerator {

    private ResultSet data;
    private int cols, rows, totRows;

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    private JDBCTableReader tableReader;

    private static final String newCol(String value, String tag) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(tag).append(">");
        if (value != null) {
            sb.append(SimpleHTMLEncoder.htmlEncode(value));
        }
        sb.append("</").append(tag).append(">");
        return sb.toString();
    }

    private static final String newCol(String value) {
        return newCol(value, "td");
    }

    public TableReportGenerator(JDBCTableReader tableReader) {
        this.data = tableReader.getTableData();
        this.tableReader = tableReader;
    }

    public InputStream createHtmlReport(int maxRows, ContentHandler handler,
            ParseContext context)
            throws IOException, SQLException, SAXException {
        rows = 0;
        try (TemporaryResources tmp = new TemporaryResources()) {
            Path path = tmp.createTempFile();
            try (OutputStream os = Files.newOutputStream(path);
                    Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                    PrintWriter out = new PrintWriter(writer);) {
                cols = tableReader.getHeaders().size();

                out.print("<head>"); //$NON-NLS-1$
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

                while (data != null && data.next() && rows < maxRows) {
                    rows++;
                    out.print("<tr>");
                    for (int i = 1; i <= cols; i++) {
                        String text = tableReader.handleCell(data, data.getMetaData(), i, handler, context, false,
                                rows);
                        out.print(newCol(text));
                    }
                    out.print("</tr>");
                }
                totRows += rows;
                out.print("</tbody>");
                out.print("</table>");
                out.print("</body>");
                out.close();

            }
            return Files.newInputStream(path);
        }

    }

    public int getTotRows() {
        return totRows;
    }
}
