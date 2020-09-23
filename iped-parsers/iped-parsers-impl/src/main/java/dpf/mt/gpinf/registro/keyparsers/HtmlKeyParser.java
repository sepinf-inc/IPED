package dpf.mt.gpinf.registro.keyparsers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.script.Invocable;
import javax.script.ScriptException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;

import dpf.mt.gpinf.registro.model.KeyNode;
import dpf.mt.gpinf.registro.model.KeyValue;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.EmbeddedParent;
import iped3.util.BasicProps;

public class HtmlKeyParser implements RegistryKeyParser {

    KeyPathPatternMap<String> valueNameDecoders = new KeyPathPatternMap<String>();
    KeyPathPatternMap<List<String>> valueDataDecoders = new KeyPathPatternMap<List<String>>();

    static final String FUNCTION_MARKER = "function\\#2=";

    boolean hasDecoders = false;

    @Override
    public void parse(KeyNode kn, String title, boolean hasChildren, String keyPath, EmbeddedParent parent,
            ContentHandler handler, Metadata metadata, ParseContext context) throws TikaException {
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        if (extractor.shouldParseEmbedded(metadata)) {
            try {
                ByteArrayInputStream keyStream = new ByteArrayInputStream(generateKeyNodeHtml(kn, keyPath));

                Metadata kmeta = new Metadata();
                kmeta.set(TikaCoreProperties.MODIFIED, kn.getLastWrittenAsDate());
                kmeta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "text/html");
                if (hasChildren) {
                    kmeta.set(BasicProps.HASCHILD, "true");
                }
                if (keyPath.equals("ROOT")) {
                    kmeta.set(TikaCoreProperties.TITLE, "ROOT");
                } else {
                    kmeta.set(TikaCoreProperties.TITLE, title);
                }

                context.set(EmbeddedParent.class, parent);
                extractor.parseEmbedded(keyStream, handler, kmeta, false);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private byte[] generateKeyNodeHtml(KeyNode kn, String keyPath)
            throws UnsupportedEncodingException, NoSuchMethodException, ScriptException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));

        startDocument(out);

        out.println("<TABLE>");
        out.println("<TR>");
        String lastWrittenString = dateFormat.get().format(kn.getLastWrittenAsDate());
        out.println("<TD>Última modificação:</td><td>" + lastWrittenString + "</td>");
        out.println("</TR>");
        out.println("<TABLE>");
        out.println("<br/>");

        List<String> valueNamesToDecode = valueDataDecoders.getPatternMatch(keyPath);
        String valueNameDecoderFunction = valueNameDecoders.getPatternMatch(keyPath);

        out.println("<TABLE>");
        out.println("<TR>");
        out.println("<TH>Atributo</TH>");
        if (valueNameDecoderFunction != null) {
            out.println("<TH>Atributo Decodificado</TH>");
        }
        out.println("<TH>Tipo</TH>");
        out.println("<TH>Valor</TH>");
        if (valueNamesToDecode != null) {
            out.println("<TH>Decodificado</TH>");
        }
        out.println("</TR>");

        KeyValue[] kvs = kn.getValues();

        if (kvs != null) {
            for (int i = 0; i < kvs.length; i++) {
                KeyValue keyValue = kvs[i];

                out.print("<TR>");

                out.print("<TD>" + keyValue.getValueName() + "</TD>");
                if (valueNameDecoderFunction != null) {
                    out.print("<TD>" + execute(valueNameDecoderFunction, keyValue.getValueName()) + "</TD>");
                }

                out.print("<TD>" + getDatatypeString(keyValue.getValueDatatype()) + "</TD>");
                out.println("<TD>" + keyValue.getValueDataAsString() + "</TD>");
                if (valueNamesToDecode != null) {
                    if ((valueNamesToDecode.size() == 1)// if there is no include tag process all values
                            || valueNamesToDecode.contains(keyValue.getValueName())) {
                        String function = valueNamesToDecode.get(0).substring(FUNCTION_MARKER.length());
                        out.println("<TD>" + execute(function, keyValue) + "</TD>");
                    } else {
                        out.println("<TD>(*)</TD>");
                    }
                }
                out.println("</TR>");
            }
        }

        out.println("</TABLE>");

        out.println("<p font=\"-2\">(*) conteúdo não decodificado, ou sem decodificador encontrado. </p>");

        endDocument(out);

        out.flush();
        return bout.toByteArray();
    }

    private String execute(String function, String keyValueName) throws NoSuchMethodException, ScriptException {
        Invocable inv = RegistryKeyParserManager.getRegistryKeyParserManager().getInv();

        return (String) inv.invokeFunction(function, keyValueName);
    }

    private String execute(String function, KeyValue keyValue) throws NoSuchMethodException, ScriptException {
        Invocable inv = RegistryKeyParserManager.getRegistryKeyParserManager().getInv();

        return (String) inv.invokeFunction(function, keyValue);
    }

    private boolean hasDecoderForKey(KeyNode kn) {
        // TODO Auto-generated method stub
        return false;
    }

    public static String getDatatypeString(int type) {
        switch (type) {
            case KeyValue.REG_NONE:
                return "REG_NONE";
            case KeyValue.REG_SZ:
                return "REG_SZ";
            case KeyValue.REG_EXPAND_SZ:
                return "REG_EXPAND_SZ";
            case KeyValue.REG_BINARY:
                return "REG_BINARY";
            case KeyValue.REG_DWORD:
                return "REG_DWORD";
            case KeyValue.REG_DWORD_BIGENDIAN:
                return "REG_DWORD_BIGENDIAN";
            case KeyValue.REG_LINK:
                return "REG_LINK";
            case KeyValue.REG_MULTI_SZ:
                return "REG_MULTI_SZ";
            case KeyValue.REG_RESOURCE_LIST:
                return "REG_RESOURCE_LIST";
            case KeyValue.REG_FULL_RESOURCE_DESCRIPTOR:
                return "REG_FULL_RESOURCE_DESCRIPTOR";
            case KeyValue.REG_RESOURCE_REQUIREMENTS_LIST:
                return "REG_RESOURCE_REQUIREMENTS_LIST";
            case KeyValue.REG_QWORD:
                return "REG_QWORD";
            default:
                return Integer.toString(type);
        }
    }

    public void startDocument(PrintWriter out) {
        out.println("<!DOCTYPE html>");
        out.println("<HTML>");
        out.println("<HEAD>");
        out.print("<meta charset=\"UTF-8\">");
        out.println("<style>" + "TABLE {  border-collapse: collapse; font-family: Arial, sans-serif; } "
                + "TH { border: solid; font-weight: bold; text-align: center; background-color:#AAAAAA; foreground-color:#FFFFFF; } "
                + "TR { vertical-align: middle; } " + ".rb { background-color:#E7E7E7; vertical-align: middle; } "
                + ".rr {  background-color:#FFFFFF; vertical-align: middle; } "
                + "TD { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; } "
                + ".e { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 150px; font-family: monospace; } "
                + ".a { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; } "
                + ".b { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 450px; } "
                + ".z { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 160px; } "
                + ".c { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word;  width: 110px; } "
                + ".h { display: table-cell; border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; }"
                + " TD:hover[onclick]{background-color:#F0F0F0; cursor:pointer} " + "</style>");
        out.println("</HEAD>");
        out.println("<BODY>");
    }

    public void endDocument(PrintWriter out) {
        out.println("</BODY></HTML>");
    }

    /** Formatador de datas. */
    protected static final ThreadLocal<DateFormat> dateFormat = new ThreadLocal<DateFormat>() {
        protected DateFormat initialValue() {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        }
    };

    @Override
    public void decodeAllValueDataDecoderFunction(String pattern, String function) {
        List<String> valueNames = new ArrayList<String>();
        valueNames.add(FUNCTION_MARKER + function);
        valueDataDecoders.put(pattern, valueNames);
    }

    @Override
    public void addValueDataDecoderFunction(String pattern, String valueName, String function) {
        List<String> valueNames = valueDataDecoders.get(pattern);

        if (valueNames == null) {
            valueNames = new ArrayList<String>();
            valueNames.add(FUNCTION_MARKER + function);
            valueDataDecoders.put(pattern, valueNames);
        }

        valueNames.add(valueName);
    }

    @Override
    public void addValueNameDecoderFunction(String pattern, String function) {
        valueNameDecoders.put(pattern, function);
    }

}
