package iped.parsers.misc;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.List;

import org.apache.poi.hpsf.Property;
import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.hpsf.Section;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;

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
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.webcohesion.ofx4j.domain.data.MessageSetType;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.ResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponse;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.banking.BankingResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.common.BalanceInfo;
import com.webcohesion.ofx4j.domain.data.common.TransactionList;
import com.webcohesion.ofx4j.domain.data.signon.SignonResponse;
import com.webcohesion.ofx4j.domain.data.common.Transaction;
import com.webcohesion.ofx4j.domain.data.signon.SignonResponse;
import com.webcohesion.ofx4j.domain.data.signon.FinancialInstitution;
import com.webcohesion.ofx4j.domain.data.common.Status;
import com.webcohesion.ofx4j.io.DefaultStringConversion;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import com.webcohesion.ofx4j.io.OFXParseException;

import iped.parsers.standard.RawStringParser;
import iped.parsers.standard.StandardParser;
import iped.utils.IOUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

import org.apache.tika.parser.microsoft.OfficeParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.FileOutputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import java.util.Date;
import java.util.TimeZone;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Extract OFX info to xls file
 * 
 * @author guilherme.dutra
 * 
 * Reference:
 * http://moneymvps.org/faq/article/8.aspx
 * 
**/

public class OFXParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
    private static Set<MediaType> SUPPORTED_MIMES = MediaType.set("application/x-ofx-v1","application/x-ofx-v2");

    private static Logger LOGGER = LoggerFactory.getLogger(OFXParser.class);

    public final String dateStringDefault = "yyyy/MM/dd hh:mm:ss";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_MIMES;
    }

    public String getBaseName(String fileName){

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

    public void setNumericCellValue(HSSFCell cell, Object number)throws Exception{

        if (cell != null){

            if (number != null){

                if (number instanceof Double){
                    cell.setCellType(CellType.NUMERIC);    
                    cell.setCellValue((double)number); 
                }else if(number instanceof Float){
                    cell.setCellType(CellType.NUMERIC);    
                    cell.setCellValue((float)number);    
                }else if(number instanceof Integer){
                    cell.setCellType(CellType.NUMERIC);    
                    cell.setCellValue((int)number);
                }else{

                    if (isValidNumber(number.toString())){
                        cell.setCellType(CellType.NUMERIC);    
                        cell.setCellValue(number.toString());            
                    }else{
                        cell.setCellType(CellType.STRING);    
                        cell.setCellValue(number.toString());
                    }
                }
            }else{
                cell.setCellType(CellType.STRING);    
                cell.setCellValue("");            
            }
        }


    }

    public void setStringCellValue(HSSFCell cell, Object value)throws Exception{

        if (cell != null){
            if (value != null){
                cell.setCellType(CellType.STRING);    
                cell.setCellValue(value.toString());            
            }else{
                cell.setCellType(CellType.STRING);    
                cell.setCellValue("");
            }
        }

    }    

    public void setDateCellValue(HSSFCell cell, Date value, CellStyle cellStyle)throws Exception{

        if (cell != null){
            if (value != null){
                cell.setCellValue(value);
                cell.setCellStyle(cellStyle);              
            }else{
                cell.setCellType(CellType.STRING);    
                cell.setCellValue("");
            }
        }

    }    


    public void decodeSignon(ResponseEnvelope re, HSSFWorkbook workbook) throws Exception{

        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        SignonResponse sr = re.getSignonResponse();

        if (sr != null){

            short cnb = 0;
            short rnb = 0;
            HSSFSheet sheetSig = workbook.createSheet("Signon");

            HSSFRow rowheadSig = sheetSig.createRow(rnb++);
            rowheadSig.createCell(cnb++).setCellValue("Status Code");
            rowheadSig.createCell(cnb++).setCellValue("Status Serverity");
            rowheadSig.createCell(cnb++).setCellValue("Language");
            rowheadSig.createCell(cnb++).setCellValue("Financial Institution Id");
            rowheadSig.createCell(cnb++).setCellValue("Financial Institution Organization");
            rowheadSig.createCell(cnb++).setCellValue("Timestamp Response");
            rowheadSig.createCell(cnb++).setCellValue("User Key");
            rowheadSig.createCell(cnb++).setCellValue("Expiration User Key");
            rowheadSig.createCell(cnb++).setCellValue("Profile Last Update");
            rowheadSig.createCell(cnb++).setCellValue("Account Last Update");
            rowheadSig.createCell(cnb++).setCellValue("Session Id");
            rowheadSig.createCell(cnb++).setCellValue("Access Key");
            
            
            cnb = 0;
            HSSFRow rowSig = sheetSig.createRow(rnb++);


            Status s = sr.getStatus();
            if (s != null){

                cell = rowSig.createCell(cnb++);
                setNumericCellValue(cell,s.getCode());

                cell = rowSig.createCell(cnb++);
                setStringCellValue(cell,s.getSeverity());

            }else{
                cnb += 2;
            }


            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell,sr.getLanguage());
        

            FinancialInstitution fi = sr.getFinancialInstitution();
            if (fi != null){

                cell = rowSig.createCell(cnb++);
                setStringCellValue(cell,fi.getId());

                cell = rowSig.createCell(cnb++);
                setStringCellValue(cell,fi.getOrganization());


            }else{

                cnb += 2;
            }

            cell = rowSig.createCell(cnb++);
            setDateCellValue(cell,sr.getTimestamp(),cellStyle);    

            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell,sr.getUserKey());

            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell,sr.getUserKeyExpiration());

            cell = rowSig.createCell(cnb++);
            setDateCellValue(cell,sr.getProfileLastUpdated(),cellStyle);   

            cell = rowSig.createCell(cnb++);
            setDateCellValue(cell,sr.getAccountLastUpdated(),cellStyle);   

            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell,sr.getSessionId());
            
            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell,sr.getAccessKey());

            for (int i=0 ; i < cnb; i++)
                sheetSig.autoSizeColumn(i);    

        }
        cell = null;
        cellStyle = null;
        sr = null;



    }

    public void decodeBank(ResponseEnvelope re, HSSFWorkbook workbook) throws Exception{

        
        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));


        ResponseMessageSet message = re.getMessageSet(MessageSetType.banking);
        if (message != null){

            List<BankStatementResponseTransaction> bank = ((BankingResponseMessageSet) message).getStatementResponses();

            if (bank != null){

                short bankCount = 1;
                for (BankStatementResponseTransaction b : bank) {
               
                    short cnb = 0;
                    short rnb = 0;
                    BankStatementResponse bsr = b.getMessage();

                    HSSFSheet sheetBank = workbook.createSheet("Bank_"+bankCount);

                    HSSFRow rowheadBank = sheetBank.createRow(rnb++);
                    rowheadBank.createCell(cnb++).setCellValue("Bank Id");
                    rowheadBank.createCell(cnb++).setCellValue("Account Number");
                    rowheadBank.createCell(cnb++).setCellValue("Branch Id");
                    rowheadBank.createCell(cnb++).setCellValue("Account Type");    
                    rowheadBank.createCell(cnb++).setCellValue("Account Key");    
                    rowheadBank.createCell(cnb++).setCellValue("Currency Code");                          
                    rowheadBank.createCell(cnb++).setCellValue("Ledger Balance Amount");
                    rowheadBank.createCell(cnb++).setCellValue("Ledger Balance Date");                          
                    rowheadBank.createCell(cnb++).setCellValue("Available Balance Amount");
                    rowheadBank.createCell(cnb++).setCellValue("Available Balance Date");
                    rowheadBank.createCell(cnb++).setCellValue("Transaction Start Date");
                    rowheadBank.createCell(cnb++).setCellValue("Transaction End Date");
                
                    if (bsr != null ){

                        cnb = 0;
                        HSSFRow rowBank = sheetBank.createRow(rnb++);

                        BankAccountDetails bad = bsr.getAccount();
                        if (bad != null){

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell,bad.getBankId());

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell,bad.getAccountNumber());

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell,bad.getBranchId());

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell,bad.getAccountType());

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell,bad.getAccountKey());


                        }else{
                            cnb += 5;
                        }

                        cell = rowBank.createCell(cnb++);
                        setStringCellValue(cell,bsr.getCurrencyCode());


                        BalanceInfo bil = bsr.getLedgerBalance();
                        if (bil != null) {

                            cell = rowBank.createCell(cnb++);
                            setNumericCellValue(cell, bil.getAmount());

                            cell = rowBank.createCell(cnb++);
                            setDateCellValue(cell,bil.getAsOfDate(),cellStyle);                            


                        }else{
                            cnb += 2;
                        }

                        BalanceInfo bia = bsr.getAvailableBalance();
                        if (bia != null) {

                            cell = rowBank.createCell(cnb++);
                            setNumericCellValue(cell, bia.getAmount());

                            cell = rowBank.createCell(cnb++);
                            setDateCellValue(cell,bia.getAsOfDate(),cellStyle);                            

                        }else{
                            cnb += 2;
                        }
                   
                        TransactionList tl = bsr.getTransactionList();                   

                        Date ds = null;
                        Date de = null;
                        if ( tl != null) {                
                            ds = tl.getStart();
                            de = tl.getEnd();                
                        }                

                        if ( ds != null){
                            cell = rowBank.createCell(cnb++);
                            setDateCellValue(cell,ds,cellStyle);       

                        }else{
                            cnb += 1;
                        }


                        if ( de != null){
                            cell = rowBank.createCell(cnb++);
                            setDateCellValue(cell,de,cellStyle);                                   

                        }else{
                            cnb += 1;
                        }

                        for (int i=0 ; i < cnb; i++)
                            sheetBank.autoSizeColumn(i);

                        ds = null;
                        de = null;


                        if ( tl != null) {

                            List<Transaction> list = tl.getTransactions();
                            
                            short cnt = 0;
                            short rnt = 0;
                       
                            HSSFSheet sheetTrans = workbook.createSheet("Bank_"+bankCount+" Transactions");

                            HSSFRow rowheadTrans = sheetTrans.createRow(rnt++);
                            rowheadTrans.createCell(cnt++).setCellValue("Type");
                            rowheadTrans.createCell(cnt++).setCellValue("Id");
                            rowheadTrans.createCell(cnt++).setCellValue("Date Posted");
                            rowheadTrans.createCell(cnt++).setCellValue("Ammount");           
                            rowheadTrans.createCell(cnt++).setCellValue("Memo");        
                            rowheadTrans.createCell(cnt++).setCellValue("Check Number");    
                            rowheadTrans.createCell(cnt++).setCellValue("Date Initiated");           
                            rowheadTrans.createCell(cnt++).setCellValue("Date Available");           
                            rowheadTrans.createCell(cnt++).setCellValue("Correction Id");    
                            rowheadTrans.createCell(cnt++).setCellValue("Server-Assigned Temporary Id");    
                            rowheadTrans.createCell(cnt++).setCellValue("Reference Number");    
                            rowheadTrans.createCell(cnt++).setCellValue("Standard Industrial Code");
                            rowheadTrans.createCell(cnt++).setCellValue("Payee Id");
                            rowheadTrans.createCell(cnt++).setCellValue("Name");                            
                            rowheadTrans.createCell(cnt++).setCellValue("Payee"); 
                            rowheadTrans.createCell(cnt++).setCellValue("Currency Code"); 
                            rowheadTrans.createCell(cnt++).setCellValue("Currency Exchange Rate"); 
                            rowheadTrans.createCell(cnt++).setCellValue("Original Currency Code"); 
                            rowheadTrans.createCell(cnt++).setCellValue("Original Currency Exchange Rate"); 
                       
                            for (Transaction transaction : list) {
                                
                                cnt = 0;
                                HSSFRow rowTrans = sheetTrans.createRow(rnt++);

                                if (transaction.getTransactionType()!= null){
                                    cell = rowTrans.createCell(cnt++);                                    
                                    setStringCellValue(cell,transaction.getTransactionType().name());
                                }else{
                                    cnt += 1;
                                }

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getId());
                                
                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell,transaction.getDatePosted(),cellStyle);
                                
                                cell = rowTrans.createCell(cnt++);
                                setNumericCellValue(cell, transaction.getAmount());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getMemo());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getCheckNumber());

                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell,transaction.getDateInitiated(),cellStyle);
                                
                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell,transaction.getDateAvailable(),cellStyle);                                

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getCorrectionId());                                

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getTempId());    

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getReferenceNumber());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getStandardIndustrialCode());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getPayeeId());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getName());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell,transaction.getPayee());

                                if (transaction.getCurrency()!= null){

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell,transaction.getCurrency().getCode());

                                    cell = rowTrans.createCell(cnt++);
                                    setNumericCellValue(cell, transaction.getCurrency().getExchangeRate());
                                }else{
                                    cnt += 2;
                                }
                                
                                if (transaction.getOriginalCurrency()!= null){
                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell,transaction.getOriginalCurrency().getCode());

                                    cell = rowTrans.createCell(cnt++);
                                    setNumericCellValue(cell, transaction.getOriginalCurrency().getExchangeRate());
                                }else{
                                    cnt += 2;
                                }

                            }
                            
                            for (int i=0 ; i < cnt; i++)
                                sheetTrans.autoSizeColumn(i);               
                         }

                    }
                    bankCount++;   
                }
            }

        }

        cell = null;
        cellStyle = null;
        message = null;

    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {


        TemporaryResources tmp = null;

        try{

            tmp = new TemporaryResources();
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            File file = tis.getFile();

            FileInputStream inputStream = new FileInputStream(file);                            
            Reader reader = new InputStreamReader(inputStream);

            AggregateUnmarshaller aggregate = new AggregateUnmarshaller(ResponseEnvelope.class);

            //Fix Timezone to the current system settings instead of GMT defalt
            DefaultStringConversion  conv = new DefaultStringConversion (TimeZone.getDefault().getID()); 
             aggregate.setConversion(conv);

            ResponseEnvelope re = (ResponseEnvelope) aggregate.unmarshal(reader);

            Metadata meta = new Metadata();
            meta.set(StandardParser.INDEXER_CONTENT_TYPE, MediaType.parse("application/vnd.ms-excel").toString());
            meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, getBaseName(file.getName()) + ".xls");
            meta.set(TikaCoreProperties.TITLE, getBaseName(file.getName()) + " XLS Parserd");
            meta.set(BasicProps.LENGTH, "");
            meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());
                   
            HSSFWorkbook workbook = new HSSFWorkbook();

            if ( re != null){
                decodeSignon(re, workbook);
                decodeBank(re, workbook);
            }


            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            workbook.write(bout);
            workbook.close();                
            ByteArrayInputStream is1 = new ByteArrayInputStream(bout.toByteArray());

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,new ParsingEmbeddedDocumentExtractor(context));

            if (extractor.shouldParseEmbedded(meta)){
                extractor.parseEmbedded(is1, handler, meta, true);
            }

        } catch (Exception ex) {
            LOGGER.error("Error parsing OFX file {}", ex.toString());
        } finally {
            if (tmp != null)
                tmp.close();
        } 



    }

}
