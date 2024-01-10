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
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.webcohesion.ofx4j.domain.data.MessageSetType;
import com.webcohesion.ofx4j.domain.data.ResponseEnvelope;
import com.webcohesion.ofx4j.domain.data.ResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.banking.BankAccountDetails;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponse;
import com.webcohesion.ofx4j.domain.data.banking.BankStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.banking.BankingResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.common.BalanceInfo;
import com.webcohesion.ofx4j.domain.data.common.Status;
import com.webcohesion.ofx4j.domain.data.common.Transaction;
import com.webcohesion.ofx4j.domain.data.common.TransactionList;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardAccountDetails;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardStatementResponse;
import com.webcohesion.ofx4j.domain.data.creditcard.CreditCardStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.investment.accounts.InvestmentAccountDetails;
import com.webcohesion.ofx4j.domain.data.investment.statements.InvestmentStatementResponse;
import com.webcohesion.ofx4j.domain.data.investment.statements.InvestmentStatementResponseMessageSet;
import com.webcohesion.ofx4j.domain.data.investment.statements.InvestmentStatementResponseTransaction;
import com.webcohesion.ofx4j.domain.data.investment.transactions.BaseInvestmentTransaction;
import com.webcohesion.ofx4j.domain.data.investment.transactions.InvestmentBankTransaction;
import com.webcohesion.ofx4j.domain.data.investment.transactions.InvestmentTransaction;
import com.webcohesion.ofx4j.domain.data.investment.transactions.InvestmentTransactionList;
import com.webcohesion.ofx4j.domain.data.signon.FinancialInstitution;
import com.webcohesion.ofx4j.domain.data.signon.SignonResponse;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import com.webcohesion.ofx4j.io.DefaultStringConversion;

import iped.parsers.standard.StandardParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

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

    public void decodeCreditCard(ResponseEnvelope re, HSSFWorkbook workbook) throws Exception{

        
        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));


        ResponseMessageSet message = re.getMessageSet(MessageSetType.creditcard);
        if (message != null){

            List<CreditCardStatementResponseTransaction> cc = ((CreditCardResponseMessageSet) message).getStatementResponses();

            if (cc != null){

                short TypeCount = 1;
                for (CreditCardStatementResponseTransaction b : cc) {
               
                    short cnb = 0;
                    short rnb = 0;
                    CreditCardStatementResponse bsr = b.getMessage();

                    HSSFSheet sheetType = workbook.createSheet("CreditCard_"+TypeCount);

                    HSSFRow rowheadType = sheetType.createRow(rnb++);
                    rowheadType.createCell(cnb++).setCellValue("Account Number");
                    rowheadType.createCell(cnb++).setCellValue("Account Key");    
                    rowheadType.createCell(cnb++).setCellValue("Currency Code");                          
                    rowheadType.createCell(cnb++).setCellValue("Ledger Balance Amount");
                    rowheadType.createCell(cnb++).setCellValue("Ledger Balance Date");                          
                    rowheadType.createCell(cnb++).setCellValue("Available Balance Amount");
                    rowheadType.createCell(cnb++).setCellValue("Available Balance Date");
                    rowheadType.createCell(cnb++).setCellValue("Transaction Start Date");
                    rowheadType.createCell(cnb++).setCellValue("Transaction End Date");
                
                    if (bsr != null ){

                        cnb = 0;
                        HSSFRow rowType = sheetType.createRow(rnb++);

                        CreditCardAccountDetails bad = bsr.getAccount();
                        if (bad != null){

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell,bad.getAccountNumber());

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell,bad.getAccountKey());


                        }else{
                            cnb += 2;
                        }

                        cell = rowType.createCell(cnb++);
                        setStringCellValue(cell,bsr.getCurrencyCode());


                        BalanceInfo bil = bsr.getLedgerBalance();
                        if (bil != null) {

                            cell = rowType.createCell(cnb++);
                            setNumericCellValue(cell, bil.getAmount());

                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell,bil.getAsOfDate(),cellStyle);                            


                        }else{
                            cnb += 2;
                        }

                        BalanceInfo bia = bsr.getAvailableBalance();
                        if (bia != null) {

                            cell = rowType.createCell(cnb++);
                            setNumericCellValue(cell, bia.getAmount());

                            cell = rowType.createCell(cnb++);
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
                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell,ds,cellStyle);       

                        }else{
                            cnb += 1;
                        }


                        if ( de != null){
                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell,de,cellStyle);                                   

                        }else{
                            cnb += 1;
                        }

                        for (int i=0 ; i < cnb; i++)
                            sheetType.autoSizeColumn(i);

                        ds = null;
                        de = null;


                        if ( tl != null) {

                            List<Transaction> list = tl.getTransactions();
                            
                            short cnt = 0;
                            short rnt = 0;
                       
                            HSSFSheet sheetTrans = workbook.createSheet("CreditCard_"+TypeCount+" Transactions");

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
                    TypeCount++;   
                }
            }

        }

        cell = null;
        cellStyle = null;
        message = null;

    }

    //TODO - Finish implementing
    public void decodeInvestimentWarn(ResponseEnvelope re, HSSFWorkbook workbook) throws Exception {
        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        boolean investmentExists = false;


        ResponseMessageSet message = re.getMessageSet(MessageSetType.investment);
        if (message != null){

            List<InvestmentStatementResponseTransaction> cc = ((InvestmentStatementResponseMessageSet) message).getStatementResponses();

            if (cc != null){

                short TypeCount = 1;
                for (InvestmentStatementResponseTransaction b : cc) {
                    investmentExists = true;
                    break;
                }
            }
        }

        if (investmentExists) {
            HSSFSheet sheetType = workbook.createSheet("Investments");

            HSSFRow rowheadType = sheetType.createRow(0);
            rowheadType.createCell(0).setCellValue(
                    "WARN: There are investment informations in this OFX, but this version of IPED does not parses it yet.");
        }
    }

    // TODO - Finish implementing
    public void decodeInvestiment(ResponseEnvelope re, HSSFWorkbook workbook) throws Exception {

        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        ResponseMessageSet message = re.getMessageSet(MessageSetType.investment);
        if (message != null) {

            List<InvestmentStatementResponseTransaction> cc = ((InvestmentStatementResponseMessageSet) message)
                    .getStatementResponses();

            if (cc != null) {

                short TypeCount = 1;
                for (InvestmentStatementResponseTransaction b : cc) {
               
                    short cnb = 0;
                    short rnb = 0;
                    InvestmentStatementResponse bsr = b.getMessage();

                    HSSFSheet sheetType = workbook.createSheet("Investment_"+TypeCount);

                    HSSFRow rowheadType = sheetType.createRow(rnb++);
                    rowheadType.createCell(cnb++).setCellValue("Date Of Statement");
                    rowheadType.createCell(cnb++).setCellValue("Broker Id");
                    rowheadType.createCell(cnb++).setCellValue("Account Number");
                    rowheadType.createCell(cnb++).setCellValue("Account Key");    
                    rowheadType.createCell(cnb++).setCellValue("Transaction Start Date");
                    rowheadType.createCell(cnb++).setCellValue("Transaction End Date");
                
                    if (bsr != null ){

                        cnb = 0;
                        HSSFRow rowType = sheetType.createRow(rnb++);


                        cell = rowType.createCell(cnb++);
                        setDateCellValue(cell,bsr.getDateOfStatement(),cellStyle);


                        InvestmentAccountDetails bad = bsr.getAccount();
                        if (bad != null){

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell,bad.getBrokerId());

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell,bad.getAccountNumber());

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell,bad.getAccountKey());


                        }else{
                            cnb += 3;
                        }
                   
                        InvestmentTransactionList tl = bsr.getInvestmentTransactionList();                   
                        /* TODO                        
                            InvestmentPositionList positionList;
                            InvestmentBalance accountBalance;
                            FourOhOneKBalance fourOhOneKBalance;
                            Inv401KInfo inv401KInfo;
                        */

                        Date ds = null;
                        Date de = null;
                        if ( tl != null) {                
                            ds = tl.getStart();
                            de = tl.getEnd();                
                        }                

                        if ( ds != null){
                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell,ds,cellStyle);       

                        }else{
                            cnb += 1;
                        }


                        if ( de != null){
                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell,de,cellStyle);                                   

                        }else{
                            cnb += 1;
                        }

                        for (int i=0 ; i < cnb; i++)
                            sheetType.autoSizeColumn(i);

                        ds = null;
                        de = null;


                        if ( tl != null) {

                            //Bank Transactions
                            List<InvestmentBankTransaction> listTrans = tl.getBankTransactions();

                            if (listTrans != null && listTrans.size() > 0){

                                short cnt = 0;
                                short rnt = 0;
                        
                                HSSFSheet sheetTrans = workbook.createSheet("Investment_"+TypeCount+" BankTransactions");

                                HSSFRow rowheadTrans = sheetTrans.createRow(rnt++);
                                rowheadTrans.createCell(cnt++).setCellValue("Sub Account Fund");
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

                                for (InvestmentBankTransaction list : listTrans){

                                    Transaction transaction = list.getTransaction();
                                    
                                    cnt = 0;
                                    HSSFRow rowTrans = sheetTrans.createRow(rnt++);

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell,list.getSubAccountFund());

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
                                
                                    for (int i=0 ; i < cnt; i++)
                                        sheetTrans.autoSizeColumn(i);   

                                }
                            }


                            //BaseInvestment
                            List<BaseInvestmentTransaction> listTransBase = tl.getInvestmentTransactions();

                            if (listTransBase != null && listTransBase.size() > 0){

                                short cnt = 0;
                                short rnt = 0;
                        
                                HSSFSheet sheetTrans = workbook.createSheet("Investment_"+TypeCount+" BaseInvestment");

                                HSSFRow rowheadTrans = sheetTrans.createRow(rnt++);
                                rowheadTrans.createCell(cnt++).setCellValue("Type");
                                rowheadTrans.createCell(cnt++).setCellValue("Transaction Id");
                                rowheadTrans.createCell(cnt++).setCellValue("Server Id");
                                rowheadTrans.createCell(cnt++).setCellValue("Trade Date");           
                                rowheadTrans.createCell(cnt++).setCellValue("Settlement Date");        
                                rowheadTrans.createCell(cnt++).setCellValue("Reversal Id");    
                                rowheadTrans.createCell(cnt++).setCellValue("Memo");           

                                for (BaseInvestmentTransaction list : listTransBase){

                                    InvestmentTransaction transaction = list.getInvestmentTransaction();
                                    
                                    cnt = 0;
                                    HSSFRow rowTrans = sheetTrans.createRow(rnt++);

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell,list.getTransactionType());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell,list.getTransactionId());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell,list.getServerId());

                                    cell = rowTrans.createCell(cnt++);
                                    setDateCellValue(cell,list.getTradeDate(),cellStyle);
                                    
                                    cell = rowTrans.createCell(cnt++);
                                    setDateCellValue(cell,list.getSettlementDate(),cellStyle);

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell,list.getReversalTransactionId());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell,list.getMemo());
                                
                                    for (int i=0 ; i < cnt; i++)
                                        sheetTrans.autoSizeColumn(i);   

                                }
                            }


                         }

                    }
                    TypeCount++;   
                }
            }

        }

        cell = null;
        cellStyle = null;
        message = null;

    }

    public Charset findCharset(File file) throws IOException {
        /* discover charset */
        FileInputStream inputStream = new FileInputStream(file);
        Charset result = Charset.defaultCharset();
        try{
            Reader reader = new InputStreamReader(inputStream);
            BufferedReader rd = new BufferedReader(reader);
            Pattern patternV1 = Pattern.compile("^CHARSET\\:(.*)");
            Matcher matcherV1 = patternV1.matcher("\\D");
            Pattern patternV2 = Pattern.compile("encoding=\"(.*)\"");
            Matcher matcherV2 = patternV2.matcher("\\D");
            String cpage = "";

            String line = null;
            while ((line = rd.readLine()) != null) {
                matcherV1.reset(line);
                matcherV2.reset(line);
                if (matcherV1.find()) {
                    cpage = matcherV1.group(1);
                    try {
                        return Charset.forName(cpage);
                    } catch (Exception e) {
                        try {
                            return Charset.forName("windows-" + cpage);
                        } catch (Exception e2) {
                            // TODO: handle exception
                        }
                    }
                }else if(matcherV2.find()){
                    cpage = matcherV2.group(1);
                    try {
                        return Charset.forName(cpage);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }                    
                }
            }
        }finally {
            inputStream.close();
        }

        return result;
    }


    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {


        TemporaryResources tmp = null;

        try{
            tmp = new TemporaryResources();
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            File file = tis.getFile();

            FileInputStream inputStream = new FileInputStream(file);                            
            Reader reader = new InputStreamReader(inputStream, findCharset(file));
            AggregateUnmarshaller aggregate = new AggregateUnmarshaller(ResponseEnvelope.class);

            // Fix Timezone to the current system settings instead of GMT defalt
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
                decodeCreditCard(re, workbook);
                decodeInvestimentWarn(re, workbook); // TODO - Finish implementing
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
