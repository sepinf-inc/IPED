
package iped.parsers.misc;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

/**
 * Extract OFC info to xls file
 * 
 * @author guilherme.dutra
 * 
 *         Reference: http://moneymvps.org/downloads/files/moneyofc.doc
 * 
 **/

public class OFCParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
    private static Set<MediaType> SUPPORTED_MIMES = MediaType.set("application/x-ofc");

    private static Logger LOGGER = LoggerFactory.getLogger(OFCParser.class);

    public final String dateStringDefault = "yyyy/MM/dd hh:mm:ss";
    Charset cs;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_MIMES;
    }

    public String getBaseName(String fileName) {

        if (fileName.indexOf(".") > 0) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        } else {
            return fileName;
        }
    }

    public boolean isValidNumber(String number) {

        String value = number;
        if (value == null || value.isEmpty())
            return false;

        try {
            new BigInteger(value, 10);
        } catch (NumberFormatException nfe) {
            return false;
        }

        return true;
    }

    public void setNumericCellValue(HSSFCell cell, Object number) throws Exception {

        if (cell != null) {

            if (number != null) {

                if (number instanceof Double) {
                    cell.setCellType(CellType.NUMERIC);
                    cell.setCellValue((double) number);
                } else if (number instanceof Float) {
                    cell.setCellType(CellType.NUMERIC);
                    cell.setCellValue((float) number);
                } else if (number instanceof Integer) {
                    cell.setCellType(CellType.NUMERIC);
                    cell.setCellValue((int) number);
                } else {

                    if (isValidNumber(number.toString())) {
                        cell.setCellType(CellType.NUMERIC);
                        cell.setCellValue(number.toString());
                    } else {
                        cell.setCellType(CellType.STRING);
                        cell.setCellValue(number.toString());
                    }
                }
            } else {
                cell.setCellType(CellType.STRING);
                cell.setCellValue("");
            }
        }

    }

    public void setStringCellValue(HSSFCell cell, Object value) throws Exception {

        if (cell != null) {
            if (value != null) {
                cell.setCellType(CellType.STRING);
                String decodedValue;
                cell.setCellValue(value.toString());
            } else {
                cell.setCellType(CellType.STRING);
                cell.setCellValue("");
            }
        }

    }

    public void setDateCellValue(HSSFCell cell, String value, CellStyle cellStyle) throws Exception {

        Date date = null;
        if (cell != null) {
            if (value != null) {

                try {
                    date = new SimpleDateFormat("yyyyMMddhhmmss").parse(value);
                } catch (ParseException pe1) {
                    try {
                        date = new SimpleDateFormat("yyyyMMdd").parse(value);
                    } catch (ParseException pe2) {
                        throw pe2;
                    }
                }
                if (date != null) {
                    cell.setCellValue(date);
                    cell.setCellStyle(cellStyle);
                } else {
                    cell.setCellType(CellType.STRING);
                    cell.setCellValue("");
                }
            } else {
                cell.setCellType(CellType.STRING);
                cell.setCellValue("");
            }
        }

    }

    public void decodeBank(OFC ofc, HSSFWorkbook workbook) throws Exception {

        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        short accountCount = 1;
        HSSFSheet sheetBank = workbook.createSheet("Account_" + accountCount);

        short cnb = 0;
        short rnb = 0;

        short cnt = 0;
        short rnt = 0;

        HSSFRow rowheadBank = sheetBank.createRow(rnb++);
        rowheadBank.createCell(cnb++).setCellValue("Document Type");
        rowheadBank.createCell(cnb++).setCellValue("Code Page");
        rowheadBank.createCell(cnb++).setCellValue("Bank ID");
        rowheadBank.createCell(cnb++).setCellValue("Branch ID");
        rowheadBank.createCell(cnb++).setCellValue("Account Number");
        rowheadBank.createCell(cnb++).setCellValue("Account Type");
        rowheadBank.createCell(cnb++).setCellValue("Transaction Start Date");
        rowheadBank.createCell(cnb++).setCellValue("Transaction End Date");
        rowheadBank.createCell(cnb++).setCellValue("Ledger Balance Amount");

        cnb = 0;
        HSSFRow rowBank = sheetBank.createRow(rnb++);

        cell = rowBank.createCell(cnb++);
        setStringCellValue(cell, ofc.DTD);

        cell = rowBank.createCell(cnb++);
        setStringCellValue(cell, ofc.CPAGE);

        for (ACCTSTMT as : ofc.ACCTSTMT) {

            for (ACCTFROM af : as.ACCTFROM) {

                cell = rowBank.createCell(cnb++);
                setStringCellValue(cell, af.BANKID);

                cell = rowBank.createCell(cnb++);
                setStringCellValue(cell, af.BRANCHID);

                cell = rowBank.createCell(cnb++);
                setStringCellValue(cell, af.ACCTID);

                cell = rowBank.createCell(cnb++);
                setStringCellValue(cell, af.getACCTTYPEString());

            }

            for (STMTRS strs : as.STMTRS) {

                cell = rowBank.createCell(cnb++);
                setDateCellValue(cell, strs.DTSTART, cellStyle);

                cell = rowBank.createCell(cnb++);
                setDateCellValue(cell, strs.DTEND, cellStyle);

                cell = rowBank.createCell(cnb++);
                setStringCellValue(cell, strs.LEDGER);

                for (int i = 0; i < cnb; i++) {
                    sheetBank.autoSizeColumn(i);
                }

                HSSFSheet sheetTrans = workbook.createSheet("Transactions_" + accountCount);

                HSSFRow rowheadTrans = sheetTrans.createRow(rnt++);
                rowheadTrans.createCell(cnt++).setCellValue("Type");
                rowheadTrans.createCell(cnt++).setCellValue("Date Posted");
                rowheadTrans.createCell(cnt++).setCellValue("Ammount");
                rowheadTrans.createCell(cnt++).setCellValue("FIT ID");
                rowheadTrans.createCell(cnt++).setCellValue("Check Number");
                rowheadTrans.createCell(cnt++).setCellValue("Memo");
                rowheadTrans.createCell(cnt++).setCellValue("Client ID");
                rowheadTrans.createCell(cnt++).setCellValue("Server transaction ID");
                rowheadTrans.createCell(cnt++).setCellValue("Standard Industrial Code");
                rowheadTrans.createCell(cnt++).setCellValue("Payee ID");
                rowheadTrans.createCell(cnt++).setCellValue("Name");
                rowheadTrans.createCell(cnt++).setCellValue("Payee");
                rowheadTrans.createCell(cnt++).setCellValue("Account to aggregate");

                for (STMTTRN strn : strs.STMTTRN) {

                    cnt = 0;
                    HSSFRow rowTrans = sheetTrans.createRow(rnt++);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.getTRNTYPEString());

                    cell = rowTrans.createCell(cnt++);
                    setDateCellValue(cell, strn.DTPOSTED, cellStyle);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.TRNAMT);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.FITID);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.CHKNUM);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.MEMO);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.CLTID);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.SRVRTID);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.SIC);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.PAYEEID);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.NAME);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.PAYEE);

                    cell = rowTrans.createCell(cnt++);
                    setStringCellValue(cell, strn.ACCTTO);

                }

                for (int i = 0; i < cnt; i++) {
                    sheetTrans.autoSizeColumn(i);
                }

            }

            accountCount++;

        }

        cell = null;
        cellStyle = null;

    }

    public Charset findCharset(File file) throws IOException {
        /* discover charset */
        FileInputStream inputStream = new FileInputStream(file);
        Charset result = Charset.defaultCharset();
        try {
            Reader reader = new InputStreamReader(inputStream);
            BufferedReader rd = new BufferedReader(reader);
            Pattern pattern = Pattern.compile("\\<CPAGE\\>(.*)\\<\\/CPAGE\\>");
            Matcher matcher = pattern.matcher("\\D");

            String line = null;
            while ((line = rd.readLine()) != null) {
                matcher.reset(line);
                if (matcher.find()) {
                    String cpage = matcher.group(1);
                    try {
                        return Charset.forName(cpage);
                    } catch (Exception e) {
                        try {
                            return Charset.forName("windows-" + cpage);
                        } catch (Exception e2) {
                            // TODO: handle exception
                        }
                    }
                }
            }
        } finally {
            inputStream.close();
        }

        return result;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        TemporaryResources tmp = null;
        OFC OFC = null;

        try {

            tmp = new TemporaryResources();
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            File file = tis.getFile();

            FileInputStream inputStream = new FileInputStream(file);
            tmp.addResource(inputStream);// adds this resource to be closed when tmp is closed
            Reader reader = new InputStreamReader(inputStream, findCharset(file));

            JAXBContext jaxbContext = JAXBContext.newInstance(OFC.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();

            OFC = (OFC) jaxbUnmarshaller.unmarshal(reader);
            reader.close();

            Metadata meta = new Metadata();
            meta.set(StandardParser.INDEXER_CONTENT_TYPE, MediaType.parse("application/vnd.ms-excel").toString());
            meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, getBaseName(file.getName()) + ".xls");
            meta.set(TikaCoreProperties.TITLE, getBaseName(file.getName()) + " XLS Parserd");
            meta.set(BasicProps.LENGTH, "");
            meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

            HSSFWorkbook workbook = new HSSFWorkbook();

            if (OFC != null) {
                try {
                    Integer.parseInt(OFC.CPAGE);
                    this.cs = Charset.forName("windows-" + OFC.CPAGE);
                } catch (Exception e) {
                    // TODO: ignore
                }
                decodeBank(OFC, workbook);
            }

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            workbook.write(bout);
            workbook.close();
            ByteArrayInputStream is1 = new ByteArrayInputStream(bout.toByteArray());

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

            if (extractor.shouldParseEmbedded(meta)) {
                extractor.parseEmbedded(is1, handler, meta, true);
            }

        } catch (Exception ex) {
            String fileName = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            ItemInfo itemInfo = context.get(ItemInfo.class);
            if (itemInfo != null)
                fileName = itemInfo.getPath();
            LOGGER.error("Error parsing OFC file {}: {}", fileName, ex.toString());
        } finally {
            if (tmp != null)
                tmp.close();
            if (OFC != null)
                OFC.clear();
        }

    }

}

@XmlRootElement(name = "OFC")
@XmlAccessorType(XmlAccessType.FIELD)
class OFC implements Serializable {

    private static final long serialVersionUID = 1L;

    public String DTD;
    public String CPAGE;
    public Vector<ACCTSTMT> ACCTSTMT = new Vector<ACCTSTMT>();

    public OFC() {
        super();
    }

    @Override
    public String toString() {

        String tab = "\t";
        String ret = "OFC" + "\n";
        ret += tab + "DTD:" + this.DTD + "\n";
        ret += tab + "CPAGE:" + this.CPAGE + "\n";

        for (ACCTSTMT o : this.ACCTSTMT) {
            ret += tab + "ACCTSTMT" + "\n";
            ret += o.toString();
        }

        return ret;
    }

    public void clear() {
        if (ACCTSTMT != null) {
            for (ACCTSTMT o : this.ACCTSTMT)
                o.clear();
            ACCTSTMT.clear();
        }
        ACCTSTMT = null;
    }

}

class ACCTSTMT {

    public Vector<ACCTFROM> ACCTFROM = new Vector<ACCTFROM>();
    public Vector<STMTRS> STMTRS = new Vector<STMTRS>();

    @Override
    public String toString() {

        String ret = "";
        String tab = "\t\t";
        for (ACCTFROM o1 : this.ACCTFROM) {
            ret += tab + "ACCTFROM" + "\n";
            ret += o1.toString();
        }
        for (STMTRS o2 : this.STMTRS) {
            ret += tab + "STMTRS" + "\n";
            ret += o2.toString();
        }
        return ret;
    }

    public void clear() {
        if (ACCTFROM != null) {
            for (ACCTFROM o1 : this.ACCTFROM)
                o1.clear();
            ACCTFROM.clear();
        }
        ACCTFROM = null;
        if (STMTRS != null) {
            for (STMTRS o2 : this.STMTRS)
                o2.clear();
            STMTRS.clear();
        }
        STMTRS = null;

    }

}

class ACCTFROM {
    public String BANKID;
    public String BRANCHID;
    public String ACCTID;
    public Integer ACCTTYPE;

    @Override
    public String toString() {

        String ret = "";
        String tab = "\t\t\t";
        ret += tab + "BANKID:" + this.BANKID + "\n";
        ret += tab + "BRANCHID:" + this.BRANCHID + "\n";
        ret += tab + "ACCTID:" + this.ACCTID + "\n";
        ret += tab + "ACCTTYPE:" + this.ACCTTYPE + "\n";

        return ret;

    }

    public String getACCTTYPEString() {

        String ret = "UNKNOWN";

        switch (this.ACCTTYPE) {

            case 0:
                ret = "Checking";
                break;
            case 1:
                ret = "Savings";
                break;
            case 2:
                ret = "Credit card";
                break;
            case 3:
                ret = "Money market";
                break;
            case 4:
                ret = "Line of credit";
                break;
            case 5:
                ret = "Loan";
                break;
            case 6:
                ret = "Interbank transfer payee";
                break;
            case 7:
                ret = "Other";
                break;

        }

        return ret;

    }

    public void clear() {

    }

}

class STMTRS {
    public String DTSTART;
    public String DTEND;
    public String LEDGER;
    public Vector<STMTTRN> STMTTRN = new Vector<STMTTRN>();

    @Override
    public String toString() {

        String ret = "";
        String tab = "\t\t\t";
        ret += tab + "DTSTART:" + this.DTSTART + "\n";
        ret += tab + "DTEND:" + this.DTEND + "\n";
        ret += tab + "LEDGER:" + this.LEDGER + "\n";
        for (STMTTRN o : this.STMTTRN) {
            ret += tab + "STMTTRN" + "\n";
            ret += o.toString();
        }
        return ret;

    }

    public void clear() {
        if (STMTTRN != null) {
            for (STMTTRN o : this.STMTTRN)
                o.clear();
            STMTTRN.clear();
        }
        STMTTRN = null;
    }

}

class STMTTRN {
    public Integer TRNTYPE;
    public String DTPOSTED;
    public String TRNAMT;
    public String FITID;
    public String CLTID;
    public String SRVRTID;
    public String CHKNUM;
    public String SIC;
    public String PAYEEID;
    public String NAME;
    public String PAYEE;
    public String ACCTTO;
    public String MEMO;

    public String getTRNTYPEString() {

        String ret = "UNKNOWN";

        switch (this.TRNTYPE) {

            case 0:
                ret = "Credit";
                break;
            case 1:
                ret = "Debit";
                break;
            case 2:
                ret = "Interest";
                break;
            case 3:
                ret = "Dividend";
                break;
            case 4:
                ret = "Service charge";
                break;
            case 5:
                ret = "Deposit";
                break;
            case 6:
                ret = "ATM withdrawal";
                break;
            case 7:
                ret = "Transfer";
                break;
            case 8:
                ret = "Check";
                break;
            case 9:
                ret = "Electronic payment";
                break;
            case 10:
                ret = "Cash withdrawal";
                break;
            case 11:
                ret = "Electronic payroll deposit";
                break;
            case 12:
                ret = "Other";
                break;

        }

        return ret;

    }

    @Override
    public String toString() {

        String ret = "";
        String tab = "\t\t\t\t";
        ret += tab + "TRNTYPE:" + this.TRNTYPE + "\n";
        ret += tab + "DTPOSTED:" + this.DTPOSTED + "\n";
        ret += tab + "TRNAMT:" + this.TRNAMT + "\n";
        ret += tab + "FITID:" + this.FITID + "\n";
        ret += tab + "SRVRTID:" + this.SRVRTID + "\n";
        ret += tab + "CHKNUM:" + this.CHKNUM + "\n";
        ret += tab + "SIC:" + this.SIC + "\n";
        ret += tab + "PAYEEID:" + this.PAYEEID + "\n";
        ret += tab + "NAME:" + this.NAME + "\n";
        ret += tab + "PAYEE:" + this.PAYEE + "\n";
        ret += tab + "ACCTTO:" + this.ACCTTO + "\n";
        ret += tab + "MEMO:" + this.MEMO + "\n";

        return ret;

    }

    public void clear() {

    }

}
