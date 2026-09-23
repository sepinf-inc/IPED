package iped.viewers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import iped.io.IStreamSource;
import iped.utils.FileContentSource;
import iped.utils.SimpleHTMLEncoder;

public class NfeViewer extends HtmlViewer {

    private static Logger LOGGER = LoggerFactory.getLogger(NfeViewer.class);
    private File tmpFile = null;

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("application/x-nfe+xml") || contentType.equals("application/x-cte+xml");
    }

    @Override
    public String getName() {
        return "Notas Fiscais";
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {
        if (tmpFile != null) {
            tmpFile.delete();
            tmpFile = null;
        }

        if (content == null) {
            super.loadFile(null, null);
            return;
        }

        try {

            tmpFile = File.createTempFile("nfeHtmlFile", ".html");
            tmpFile.deleteOnExit();

            createNfeHtml(content, tmpFile);
            super.loadFile(new FileContentSource(tmpFile), highlightTerms);

        } catch (Exception e) {
            LOGGER.error("Error loading NFe/CTe", e);
            super.loadFile(null, null);
        }
    }

    private void createNfeHtml(IStreamSource source, File htmlFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head>");
        sb.append("<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\" />");
        sb.append("<style>");
        sb.append("body { font-family: Arial, sans-serif; font-size: 12px; }");
        sb.append(".box { border: 1px solid black; margin-bottom: 5px; padding: 5px; }");
        sb.append(".header { font-weight: bold; background-color: #f0f0f0; padding: 2px; }");
        sb.append(".row { display: flex; flex-wrap: wrap; }");
        sb.append(".field { margin-right: 15px; margin-bottom: 5px; }");
        sb.append(".label { font-size: 10px; color: #555; display: block; }");
        sb.append(".value { font-weight: bold; }");
        sb.append("table { width: 100%; border-collapse: collapse; margin-top: 5px; }");
        sb.append("th, td { border: 1px solid #ccc; padding: 4px; text-align: left; font-size: 11px; }");
        sb.append("th { background-color: #eee; }");
        sb.append("</style>");
        sb.append("</head><body>");

        try (InputStream is = source.getSeekableInputStream()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            String rootName = root.getNodeName();

            if (rootName.contains("nfeProc") || rootName.equals("NFe")) {
                parseNFe(doc, sb);
            } else if (rootName.contains("cteProc") || rootName.equals("CTe")) {
                parseCTe(doc, sb);
            } else {
                sb.append("<b>Formato não reconhecido: " + rootName + "</b>");
            }

        } catch (Exception e) {
            sb.append("<b>Erro ao ler arquivo XML: " + e.getMessage() + "</b>");
            LOGGER.warn("Erro parsing XML", e);
        }

        sb.append("</body></html>");
        Files.write(htmlFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String getTagValue(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList != null && nodeList.getLength() > 0) {
            NodeList childNodes = nodeList.item(0).getChildNodes();
            if (childNodes != null && childNodes.getLength() > 0) {
                return childNodes.item(0).getNodeValue();
            }
        }
        return "";
    }

    private void parseNFe(Document doc, StringBuilder sb) {
        Element infNFe = (Element) doc.getElementsByTagName("infNFe").item(0);
        if (infNFe == null)
            return;

        Element ide = (Element) infNFe.getElementsByTagName("ide").item(0);
        Element emit = (Element) infNFe.getElementsByTagName("emit").item(0);
        Element dest = (Element) infNFe.getElementsByTagName("dest").item(0);
        Element total = (Element) infNFe.getElementsByTagName("total").item(0);

        sb.append("<div class='box'>");
        sb.append("<div class='header'>DANFE - Documento Auxiliar da Nota Fiscal Eletrônica</div>");
        if (ide != null) {
            sb.append("<div class='row'>");
            addField(sb, "Natureza da Operação", getTagValue(ide, "natOp"));
            addField(sb, "Número", getTagValue(ide, "nNF"));
            addField(sb, "Série", getTagValue(ide, "serie"));
            addField(sb, "Data Emissão", getTagValue(ide, "dhEmi"));
            sb.append("</div>");
            sb.append("<div class='row'>");
            String chNFe = infNFe.getAttribute("Id");
            if (chNFe != null && chNFe.startsWith("NFe"))
                chNFe = chNFe.substring(3);
            addField(sb, "Chave de Acesso", chNFe);
            sb.append("</div>");
        }
        sb.append("</div>");

        if (emit != null) {
            sb.append("<div class='box'>");
            sb.append("<div class='header'>Emitente</div>");
            sb.append("<div class='row'>");
            addField(sb, "Nome / Razão Social", getTagValue(emit, "xNome"));
            addField(sb, "CNPJ", getTagValue(emit, "CNPJ"));
            addField(sb, "Inscrição Estadual", getTagValue(emit, "IE"));
            sb.append("</div>");

            Element enderEmit = (Element) emit.getElementsByTagName("enderEmit").item(0);
            if (enderEmit != null) {
                sb.append("<div class='row'>");
                addField(sb, "Endereço", getTagValue(enderEmit, "xLgr") + ", " + getTagValue(enderEmit, "nro"));
                addField(sb, "Bairro", getTagValue(enderEmit, "xBairro"));
                addField(sb, "Município", getTagValue(enderEmit, "xMun") + " - " + getTagValue(enderEmit, "UF"));
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        if (dest != null) {
            sb.append("<div class='box'>");
            sb.append("<div class='header'>Destinatário / Remetente</div>");
            sb.append("<div class='row'>");
            addField(sb, "Nome / Razão Social", getTagValue(dest, "xNome"));
            String docDest = getTagValue(dest, "CNPJ");
            if (docDest.isEmpty())
                docDest = getTagValue(dest, "CPF");
            addField(sb, "CNPJ/CPF", docDest);
            addField(sb, "Inscrição Estadual", getTagValue(dest, "IE"));
            sb.append("</div>");

            Element enderDest = (Element) dest.getElementsByTagName("enderDest").item(0);
            if (enderDest != null) {
                sb.append("<div class='row'>");
                addField(sb, "Endereço", getTagValue(enderDest, "xLgr") + ", " + getTagValue(enderDest, "nro"));
                addField(sb, "Bairro", getTagValue(enderDest, "xBairro"));
                addField(sb, "Município", getTagValue(enderDest, "xMun") + " - " + getTagValue(enderDest, "UF"));
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        if (total != null) {
            Element ICMSTot = (Element) total.getElementsByTagName("ICMSTot").item(0);
            if (ICMSTot != null) {
                sb.append("<div class='box'>");
                sb.append("<div class='header'>Cálculo do Imposto</div>");
                sb.append("<div class='row'>");
                addField(sb, "Base de Cálculo do ICMS", formatCurrency(getTagValue(ICMSTot, "vBC")));
                addField(sb, "Valor do ICMS", formatCurrency(getTagValue(ICMSTot, "vICMS")));
                addField(sb, "Base de Cálculo ICMS ST", formatCurrency(getTagValue(ICMSTot, "vBCST")));
                addField(sb, "Valor do ICMS ST", formatCurrency(getTagValue(ICMSTot, "vST")));
                addField(sb, "Valor Total dos Produtos", formatCurrency(getTagValue(ICMSTot, "vProd")));
                sb.append("</div>");
                sb.append("<div class='row'>");
                addField(sb, "Valor do Frete", formatCurrency(getTagValue(ICMSTot, "vFrete")));
                addField(sb, "Valor do Seguro", formatCurrency(getTagValue(ICMSTot, "vSeg")));
                addField(sb, "Desconto", formatCurrency(getTagValue(ICMSTot, "vDesc")));
                addField(sb, "Outras Despesas", formatCurrency(getTagValue(ICMSTot, "vOutro")));
                addField(sb, "Valor do IPI", formatCurrency(getTagValue(ICMSTot, "vIPI")));
                addField(sb, "Valor Total da Nota", formatCurrency(getTagValue(ICMSTot, "vNF")));
                sb.append("</div>");
                sb.append("</div>");
            }
        }

        sb.append("<div class='box'>");
        sb.append("<div class='header'>Dados do Produto/Serviço</div>");
        sb.append("<table>");
        sb.append(
                "<tr><th>Código</th><th>Descrição</th><th>NCM</th><th>CST</th><th>CFOP</th><th>Unid.</th><th>Qtd.</th><th>Vlr. Unit.</th><th>Vlr. Total</th></tr>");

        NodeList dets = infNFe.getElementsByTagName("det");
        for (int i = 0; i < dets.getLength(); i++) {
            Element det = (Element) dets.item(i);
            Element prod = (Element) det.getElementsByTagName("prod").item(0);
            if (prod != null) {
                sb.append("<tr>");
                sb.append("<td>").append(SimpleHTMLEncoder.htmlEncode(getTagValue(prod, "cProd"))).append("</td>");
                sb.append("<td>").append(SimpleHTMLEncoder.htmlEncode(getTagValue(prod, "xProd"))).append("</td>");
                sb.append("<td>").append(SimpleHTMLEncoder.htmlEncode(getTagValue(prod, "NCM"))).append("</td>");
                // CST might be inside imposto/ICMS/ICMSxx
                sb.append("<td>").append("-").append("</td>");
                sb.append("<td>").append(SimpleHTMLEncoder.htmlEncode(getTagValue(prod, "CFOP"))).append("</td>");
                sb.append("<td>").append(SimpleHTMLEncoder.htmlEncode(getTagValue(prod, "uCom"))).append("</td>");
                sb.append("<td>").append(SimpleHTMLEncoder.htmlEncode(getTagValue(prod, "qCom"))).append("</td>");
                sb.append("<td>").append(formatCurrency(getTagValue(prod, "vUnCom"))).append("</td>");
                sb.append("<td>").append(formatCurrency(getTagValue(prod, "vProd"))).append("</td>");
                sb.append("</tr>");
            }
        }
        sb.append("</table>");
        sb.append("</div>");
    }

    private void parseCTe(Document doc, StringBuilder sb) {
        Element infCte = (Element) doc.getElementsByTagName("infCte").item(0);
        if (infCte == null)
            return;

        Element ide = (Element) infCte.getElementsByTagName("ide").item(0);
        Element emit = (Element) infCte.getElementsByTagName("emit").item(0);
        Element dest = (Element) infCte.getElementsByTagName("dest").item(0);
        Element rem = (Element) infCte.getElementsByTagName("rem").item(0);
        Element vPrest = (Element) infCte.getElementsByTagName("vPrest").item(0);

        sb.append("<div class='box'>");
        sb.append("<div class='header'>DACTE - Documento Auxiliar do Conhecimento de Transporte Eletrônico</div>");
        if (ide != null) {
            sb.append("<div class='row'>");
            addField(sb, "Modelo", getTagValue(ide, "mod"));
            addField(sb, "Série", getTagValue(ide, "serie"));
            addField(sb, "Número", getTagValue(ide, "nCT"));
            addField(sb, "Data Emissão", getTagValue(ide, "dhEmi"));
            String tpServ = getTagValue(ide, "tpServ");
            addField(sb, "Tipo Serviço", tpServ.equals("0") ? "Normal" : "Outros");
            sb.append("</div>");
            sb.append("<div class='row'>");
            String chave = infCte.getAttribute("Id");
            if (chave != null && chave.startsWith("CTe"))
                chave = chave.substring(3);
            addField(sb, "Chave de Acesso", chave);
            sb.append("</div>");
            sb.append("<div class='row'>");
            addField(sb, "Início da Prestação", getTagValue(ide, "xMunIni") + " - " + getTagValue(ide, "UFIni"));
            addField(sb, "Término da Prestação", getTagValue(ide, "xMunFim") + " - " + getTagValue(ide, "UFFim"));
            sb.append("</div>");
        }
        sb.append("</div>");

        if (emit != null) {
            sb.append("<div class='box'>");
            sb.append("<div class='header'>Emitente</div>");
            sb.append("<div class='row'>");
            addField(sb, "Nome / Razão Social", getTagValue(emit, "xNome"));
            addField(sb, "CNPJ", getTagValue(emit, "CNPJ"));
            addField(sb, "IE", getTagValue(emit, "IE"));
            sb.append("</div>");
            Element enderEmit = (Element) emit.getElementsByTagName("enderEmit").item(0);
            if (enderEmit != null) {
                sb.append("<div class='row'>");
                addField(sb, "Endereço", getTagValue(enderEmit, "xLgr") + ", " + getTagValue(enderEmit, "nro"));
                addField(sb, "Município", getTagValue(enderEmit, "xMun") + " - " + getTagValue(enderEmit, "UF"));
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        if (rem != null) {
            sb.append("<div class='box'>");
            sb.append("<div class='header'>Remetente</div>");
            sb.append("<div class='row'>");
            addField(sb, "Nome / Razão Social", getTagValue(rem, "xNome"));
            String docRem = getTagValue(rem, "CNPJ");
            if (docRem.isEmpty())
                docRem = getTagValue(rem, "CPF");
            addField(sb, "CNPJ/CPF", docRem);
            addField(sb, "IE", getTagValue(rem, "IE"));
            sb.append("</div>");
            Element enderRem = (Element) rem.getElementsByTagName("enderReme").item(0);
            if (enderRem != null) {
                sb.append("<div class='row'>");
                addField(sb, "Endereço", getTagValue(enderRem, "xLgr") + ", " + getTagValue(enderRem, "nro"));
                addField(sb, "Município", getTagValue(enderRem, "xMun") + " - " + getTagValue(enderRem, "UF"));
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        if (dest != null) {
            sb.append("<div class='box'>");
            sb.append("<div class='header'>Destinatário</div>");
            sb.append("<div class='row'>");
            addField(sb, "Nome / Razão Social", getTagValue(dest, "xNome"));
            String docDest = getTagValue(dest, "CNPJ");
            if (docDest.isEmpty())
                docDest = getTagValue(dest, "CPF");
            addField(sb, "CNPJ/CPF", docDest);
            addField(sb, "IE", getTagValue(dest, "IE"));
            sb.append("</div>");
            Element enderDest = (Element) dest.getElementsByTagName("enderDest").item(0);
            if (enderDest != null) {
                sb.append("<div class='row'>");
                addField(sb, "Endereço", getTagValue(enderDest, "xLgr") + ", " + getTagValue(enderDest, "nro"));
                addField(sb, "Município", getTagValue(enderDest, "xMun") + " - " + getTagValue(enderDest, "UF"));
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        if (vPrest != null) {
            sb.append("<div class='box'>");
            sb.append("<div class='header'>Valores da Prestação de Serviço</div>");
            sb.append("<div class='row'>");
            addField(sb, "Valor Total do Serviço", formatCurrency(getTagValue(vPrest, "vTPrest")));
            addField(sb, "Valor a Receber", formatCurrency(getTagValue(vPrest, "vRec")));
            sb.append("</div>");

            sb.append("<div class='label'>Componentes do Valor</div>");
            sb.append("<table><tr><th>Nome</th><th>Valor</th></tr>");
            NodeList comps = vPrest.getElementsByTagName("Comp");
            for (int i = 0; i < comps.getLength(); i++) {
                Element comp = (Element) comps.item(i);
                sb.append("<tr>");
                sb.append("<td>").append(SimpleHTMLEncoder.htmlEncode(getTagValue(comp, "xNome"))).append("</td>");
                sb.append("<td>").append(formatCurrency(getTagValue(comp, "vComp"))).append("</td>");
                sb.append("</tr>");
            }
            sb.append("</table>");
            sb.append("</div>");
        }
    }

    private void addField(StringBuilder sb, String label, String value) {
        sb.append("<div class='field'>");
        sb.append("<span class='label'>").append(label).append("</span>");
        sb.append("<span class='value'>").append(SimpleHTMLEncoder.htmlEncode(value)).append("</span>");
        sb.append("</div>");
    }

    private String formatCurrency(String value) {
        if (value == null || value.isEmpty())
            return "";
        try {
            double v = Double.parseDouble(value);
            return String.format(new Locale("pt", "BR"), "R$ %.2f", v);
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
