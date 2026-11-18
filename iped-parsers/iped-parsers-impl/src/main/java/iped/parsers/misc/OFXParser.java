package iped.parsers.misc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
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
import com.webcohesion.ofx4j.domain.data.investment.transactions.InvestmentTransactionList;
import com.webcohesion.ofx4j.domain.data.signon.FinancialInstitution;
import com.webcohesion.ofx4j.domain.data.signon.SignonResponse;
import com.webcohesion.ofx4j.io.AggregateUnmarshaller;
import com.webcohesion.ofx4j.io.DefaultStringConversion;

import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.utils.IOUtil;

/**
 * Extract OFX info to xls file
 * 
 * @author guilherme.dutra
 * 
 *         Reference: http://moneymvps.org/faq/article/8.aspx
 * 
 **/

public class OFXParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
    private static Set<MediaType> SUPPORTED_MIMES = MediaType.set("application/x-ofx-v1", "application/x-ofx-v2");

    private static final String dateStringDefault = "yyyy/MM/dd hh:mm:ss";

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

    public void setNumericCellValue(HSSFCell cell, Object number) {

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

    public void setStringCellValue(HSSFCell cell, Object value) {

        if (cell != null) {
            if (value != null) {
                cell.setCellType(CellType.STRING);
                cell.setCellValue(value.toString());
            } else {
                cell.setCellType(CellType.STRING);
                cell.setCellValue("");
            }
        }

    }

    public void setDateCellValue(HSSFCell cell, Date value, CellStyle cellStyle) {

        if (cell != null) {
            if (value != null) {
                cell.setCellValue(value);
                cell.setCellStyle(cellStyle);
            } else {
                cell.setCellType(CellType.STRING);
                cell.setCellValue("");
            }
        }

    }

    public void decodeSignon(ResponseEnvelope re, HSSFWorkbook workbook) {

        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        SignonResponse sr = re.getSignonResponse();

        if (sr != null) {

            short cnb = 0;
            short rnb = 0;
            HSSFSheet sheetSig = workbook.createSheet(Messages.getString("OFXParser.Signon"));

            HSSFRow rowheadSig = sheetSig.createRow(rnb++);
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.StatusCode"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.StatusSeverity"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.Language"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.FinancialInstitutionId"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.FinancialInstitutionOrganization"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.TimestampResponse"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.UserKey"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.ExpirationUserKey"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.ProfileLastUpdate"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccountLastUpdate"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.SessionId"));
            rowheadSig.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccessKey"));

            cnb = 0;
            HSSFRow rowSig = sheetSig.createRow(rnb++);

            Status s = sr.getStatus();
            if (s != null) {

                cell = rowSig.createCell(cnb++);
                setNumericCellValue(cell, s.getCode());

                cell = rowSig.createCell(cnb++);
                setStringCellValue(cell, s.getSeverity());

            } else {
                cnb += 2;
            }

            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell, sr.getLanguage());

            FinancialInstitution fi = sr.getFinancialInstitution();
            if (fi != null) {

                cell = rowSig.createCell(cnb++);
                setStringCellValue(cell, fi.getId());

                cell = rowSig.createCell(cnb++);
                setStringCellValue(cell, fi.getOrganization());

            } else {

                cnb += 2;
            }

            cell = rowSig.createCell(cnb++);
            setDateCellValue(cell, sr.getTimestamp(), cellStyle);

            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell, sr.getUserKey());

            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell, sr.getUserKeyExpiration());

            cell = rowSig.createCell(cnb++);
            setDateCellValue(cell, sr.getProfileLastUpdated(), cellStyle);

            cell = rowSig.createCell(cnb++);
            setDateCellValue(cell, sr.getAccountLastUpdated(), cellStyle);

            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell, sr.getSessionId());

            cell = rowSig.createCell(cnb++);
            setStringCellValue(cell, sr.getAccessKey());

            for (int i = 0; i < cnb; i++)
                sheetSig.autoSizeColumn(i);

        }
        cell = null;
        cellStyle = null;
        sr = null;

    }

    public void decodeBank(ResponseEnvelope re, HSSFWorkbook workbook) {

        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        ResponseMessageSet message = re.getMessageSet(MessageSetType.banking);
        if (message != null) {

            List<BankStatementResponseTransaction> bank = ((BankingResponseMessageSet) message).getStatementResponses();

            if (bank != null) {

                short bankCount = 1;
                for (BankStatementResponseTransaction b : bank) {

                    short cnb = 0;
                    short rnb = 0;
                    BankStatementResponse bsr = b.getMessage();

                    HSSFSheet sheetBank = workbook.createSheet(Messages.getString("OFXParser.Bank") + "_" + bankCount);

                    HSSFRow rowheadBank = sheetBank.createRow(rnb++);
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.BankId"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccountNumber"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.BranchId"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccountType"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccountKey"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.CurrencyCode"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.LedgerBalanceAmount"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.LedgerBalanceDate"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AvailableBalanceAmount"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AvailableBalanceDate"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.TransactionStartDate"));
                    rowheadBank.createCell(cnb++).setCellValue(Messages.getString("OFXParser.TransactionEndDate"));

                    if (bsr != null) {

                        cnb = 0;
                        HSSFRow rowBank = sheetBank.createRow(rnb++);

                        BankAccountDetails bad = bsr.getAccount();
                        if (bad != null) {

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell, bad.getBankId());

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell, bad.getAccountNumber());

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell, bad.getBranchId());

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell, bad.getAccountType());

                            cell = rowBank.createCell(cnb++);
                            setStringCellValue(cell, bad.getAccountKey());

                        } else {
                            cnb += 5;
                        }

                        cell = rowBank.createCell(cnb++);
                        setStringCellValue(cell, bsr.getCurrencyCode());

                        BalanceInfo bil = bsr.getLedgerBalance();
                        if (bil != null) {

                            cell = rowBank.createCell(cnb++);
                            setNumericCellValue(cell, bil.getAmount());

                            cell = rowBank.createCell(cnb++);
                            setDateCellValue(cell, bil.getAsOfDate(), cellStyle);

                        } else {
                            cnb += 2;
                        }

                        BalanceInfo bia = bsr.getAvailableBalance();
                        if (bia != null) {

                            cell = rowBank.createCell(cnb++);
                            setNumericCellValue(cell, bia.getAmount());

                            cell = rowBank.createCell(cnb++);
                            setDateCellValue(cell, bia.getAsOfDate(), cellStyle);

                        } else {
                            cnb += 2;
                        }

                        TransactionList tl = bsr.getTransactionList();

                        Date ds = null;
                        Date de = null;
                        if (tl != null) {
                            ds = tl.getStart();
                            de = tl.getEnd();
                        }

                        if (ds != null) {
                            cell = rowBank.createCell(cnb++);
                            setDateCellValue(cell, ds, cellStyle);

                        } else {
                            cnb += 1;
                        }

                        if (de != null) {
                            cell = rowBank.createCell(cnb++);
                            setDateCellValue(cell, de, cellStyle);

                        } else {
                            cnb += 1;
                        }

                        for (int i = 0; i < cnb; i++)
                            sheetBank.autoSizeColumn(i);

                        ds = null;
                        de = null;

                        if (tl != null) {

                            List<Transaction> list = tl.getTransactions();
                            if (list == null) {
                                list = Collections.emptyList();
                            }

                            short cnt = 0;
                            short rnt = 0;

                            HSSFSheet sheetTrans = workbook.createSheet(Messages.getString("OFXParser.Bank") + "_" + bankCount + Messages.getString("OFXParser.Transactions"));

                            HSSFRow rowheadTrans = sheetTrans.createRow(rnt++);
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Type"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Id"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DatePosted"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Amount"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Memo"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CheckNumber"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DateInitiated"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DateAvailable"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CorrectionId"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.ServerAssignedTemporaryId"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.ReferenceNumber"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.StandardIndustrialCode"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.PayeeId"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Name"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Payee"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CurrencyCode"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CurrencyExchangeRate"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.OriginalCurrencyCode"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.OriginalCurrencyExchangeRate"));

                            for (Transaction transaction : list) {

                                cnt = 0;
                                HSSFRow rowTrans = sheetTrans.createRow(rnt++);

                                if (transaction.getTransactionType() != null) {
                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getTransactionType().name());
                                } else {
                                    cnt += 1;
                                }

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getId());

                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell, transaction.getDatePosted(), cellStyle);

                                cell = rowTrans.createCell(cnt++);
                                setNumericCellValue(cell, transaction.getAmount());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getMemo());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getCheckNumber());

                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell, transaction.getDateInitiated(), cellStyle);

                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell, transaction.getDateAvailable(), cellStyle);

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getCorrectionId());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getTempId());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getReferenceNumber());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getStandardIndustrialCode());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getPayeeId());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getName());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getPayee());

                                if (transaction.getCurrency() != null) {

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getCurrency().getCode());

                                    cell = rowTrans.createCell(cnt++);
                                    setNumericCellValue(cell, transaction.getCurrency().getExchangeRate());
                                } else {
                                    cnt += 2;
                                }

                                if (transaction.getOriginalCurrency() != null) {
                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getOriginalCurrency().getCode());

                                    cell = rowTrans.createCell(cnt++);
                                    setNumericCellValue(cell, transaction.getOriginalCurrency().getExchangeRate());
                                } else {
                                    cnt += 2;
                                }

                            }

                            for (int i = 0; i < cnt; i++)
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

    public void decodeCreditCard(ResponseEnvelope re, HSSFWorkbook workbook) {

        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        ResponseMessageSet message = re.getMessageSet(MessageSetType.creditcard);
        if (message != null) {

            List<CreditCardStatementResponseTransaction> cc = ((CreditCardResponseMessageSet) message).getStatementResponses();

            if (cc != null) {

                short TypeCount = 1;
                for (CreditCardStatementResponseTransaction b : cc) {

                    short cnb = 0;
                    short rnb = 0;
                    CreditCardStatementResponse bsr = b.getMessage();

                    HSSFSheet sheetType = workbook.createSheet(Messages.getString("OFXParser.CreditCard") + "_" + TypeCount);

                    HSSFRow rowheadType = sheetType.createRow(rnb++);
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccountNumber"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccountKey"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.CurrencyCode"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.LedgerBalanceAmount"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.LedgerBalanceDate"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AvailableBalanceAmount"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AvailableBalanceDate"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.TransactionStartDate"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.TransactionEndDate"));

                    if (bsr != null) {

                        cnb = 0;
                        HSSFRow rowType = sheetType.createRow(rnb++);

                        CreditCardAccountDetails bad = bsr.getAccount();
                        if (bad != null) {

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell, bad.getAccountNumber());

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell, bad.getAccountKey());

                        } else {
                            cnb += 2;
                        }

                        cell = rowType.createCell(cnb++);
                        setStringCellValue(cell, bsr.getCurrencyCode());

                        BalanceInfo bil = bsr.getLedgerBalance();
                        if (bil != null) {

                            cell = rowType.createCell(cnb++);
                            setNumericCellValue(cell, bil.getAmount());

                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell, bil.getAsOfDate(), cellStyle);

                        } else {
                            cnb += 2;
                        }

                        BalanceInfo bia = bsr.getAvailableBalance();
                        if (bia != null) {

                            cell = rowType.createCell(cnb++);
                            setNumericCellValue(cell, bia.getAmount());

                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell, bia.getAsOfDate(), cellStyle);

                        } else {
                            cnb += 2;
                        }

                        TransactionList tl = bsr.getTransactionList();

                        Date ds = null;
                        Date de = null;
                        if (tl != null) {
                            ds = tl.getStart();
                            de = tl.getEnd();
                        }

                        if (ds != null) {
                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell, ds, cellStyle);

                        } else {
                            cnb += 1;
                        }

                        if (de != null) {
                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell, de, cellStyle);

                        } else {
                            cnb += 1;
                        }

                        for (int i = 0; i < cnb; i++)
                            sheetType.autoSizeColumn(i);

                        ds = null;
                        de = null;

                        if (tl != null) {

                            List<Transaction> list = tl.getTransactions();

                            short cnt = 0;
                            short rnt = 0;

                            HSSFSheet sheetTrans = workbook.createSheet(Messages.getString("OFXParser.CreditCard") + "_" + TypeCount + Messages.getString("OFXParser.Transactions"));

                            HSSFRow rowheadTrans = sheetTrans.createRow(rnt++);
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Type"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Id"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DatePosted"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Amount"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Memo"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CheckNumber"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DateInitiated"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DateAvailable"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CorrectionId"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.ServerAssignedTemporaryId"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.ReferenceNumber"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.StandardIndustrialCode"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.PayeeId"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Name"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Payee"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CurrencyCode"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CurrencyExchangeRate"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.OriginalCurrencyCode"));
                            rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.OriginalCurrencyExchangeRate"));

                            for (Transaction transaction : list) {

                                cnt = 0;
                                HSSFRow rowTrans = sheetTrans.createRow(rnt++);

                                if (transaction.getTransactionType() != null) {
                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getTransactionType().name());
                                } else {
                                    cnt += 1;
                                }

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getId());

                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell, transaction.getDatePosted(), cellStyle);

                                cell = rowTrans.createCell(cnt++);
                                setNumericCellValue(cell, transaction.getAmount());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getMemo());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getCheckNumber());

                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell, transaction.getDateInitiated(), cellStyle);

                                cell = rowTrans.createCell(cnt++);
                                setDateCellValue(cell, transaction.getDateAvailable(), cellStyle);

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getCorrectionId());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getTempId());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getReferenceNumber());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getStandardIndustrialCode());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getPayeeId());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getName());

                                cell = rowTrans.createCell(cnt++);
                                setStringCellValue(cell, transaction.getPayee());

                                if (transaction.getCurrency() != null) {

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getCurrency().getCode());

                                    cell = rowTrans.createCell(cnt++);
                                    setNumericCellValue(cell, transaction.getCurrency().getExchangeRate());
                                } else {
                                    cnt += 2;
                                }

                                if (transaction.getOriginalCurrency() != null) {
                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getOriginalCurrency().getCode());

                                    cell = rowTrans.createCell(cnt++);
                                    setNumericCellValue(cell, transaction.getOriginalCurrency().getExchangeRate());
                                } else {
                                    cnt += 2;
                                }

                            }

                            for (int i = 0; i < cnt; i++)
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

    // TODO - Finish implementing
    public void decodeInvestimentWarn(ResponseEnvelope re, HSSFWorkbook workbook) {

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        boolean investmentExists = false;

        ResponseMessageSet message = re.getMessageSet(MessageSetType.investment);
        if (message != null) {
            List<InvestmentStatementResponseTransaction> cc = ((InvestmentStatementResponseMessageSet) message).getStatementResponses();
            investmentExists = cc != null && !cc.isEmpty();
        }

        if (investmentExists) {
            HSSFSheet sheetType = workbook.createSheet(Messages.getString("OFXParser.Investments"));

            HSSFRow rowheadType = sheetType.createRow(0);
            rowheadType.createCell(0).setCellValue("WARN: There are investment informations in this OFX, but this version of IPED does not parses it yet.");
        }
    }

    // TODO - Finish implementing
    public void decodeInvestiment(ResponseEnvelope re, HSSFWorkbook workbook) {

        HSSFCell cell;

        CellStyle cellStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat(dateStringDefault));

        ResponseMessageSet message = re.getMessageSet(MessageSetType.investment);
        if (message != null) {

            List<InvestmentStatementResponseTransaction> cc = ((InvestmentStatementResponseMessageSet) message).getStatementResponses();

            if (cc != null) {

                short TypeCount = 1;
                for (InvestmentStatementResponseTransaction b : cc) {

                    short cnb = 0;
                    short rnb = 0;
                    InvestmentStatementResponse bsr = b.getMessage();

                    HSSFSheet sheetType = workbook.createSheet(Messages.getString("OFXParser.Investment_") + TypeCount);

                    HSSFRow rowheadType = sheetType.createRow(rnb++);
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.DateOfStatement"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.BrokerId"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccountNumber"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.AccountKey"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.TransactionStartDate"));
                    rowheadType.createCell(cnb++).setCellValue(Messages.getString("OFXParser.TransactionEndDate"));

                    if (bsr != null) {

                        cnb = 0;
                        HSSFRow rowType = sheetType.createRow(rnb++);

                        cell = rowType.createCell(cnb++);
                        setDateCellValue(cell, bsr.getDateOfStatement(), cellStyle);

                        InvestmentAccountDetails bad = bsr.getAccount();
                        if (bad != null) {

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell, bad.getBrokerId());

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell, bad.getAccountNumber());

                            cell = rowType.createCell(cnb++);
                            setStringCellValue(cell, bad.getAccountKey());

                        } else {
                            cnb += 3;
                        }

                        InvestmentTransactionList tl = bsr.getInvestmentTransactionList();
                        /*
                         * TODO InvestmentPositionList positionList; InvestmentBalance accountBalance;
                         * FourOhOneKBalance fourOhOneKBalance; Inv401KInfo inv401KInfo;
                         */

                        Date ds = null;
                        Date de = null;
                        if (tl != null) {
                            ds = tl.getStart();
                            de = tl.getEnd();
                        }

                        if (ds != null) {
                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell, ds, cellStyle);

                        } else {
                            cnb += 1;
                        }

                        if (de != null) {
                            cell = rowType.createCell(cnb++);
                            setDateCellValue(cell, de, cellStyle);

                        } else {
                            cnb += 1;
                        }

                        for (int i = 0; i < cnb; i++)
                            sheetType.autoSizeColumn(i);

                        ds = null;
                        de = null;

                        if (tl != null) {

                            // Bank Transactions
                            List<InvestmentBankTransaction> listTrans = tl.getBankTransactions();

                            if (listTrans != null && listTrans.size() > 0) {

                                short cnt = 0;
                                short rnt = 0;

                                HSSFSheet sheetTrans = workbook.createSheet(Messages.getString("OFXParser.Investment") + "_" + TypeCount + " " + Messages.getString("OFXParser.BankTransactions"));

                                HSSFRow rowheadTrans = sheetTrans.createRow(rnt++);
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.SubAccountFund"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Type"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Id"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DatePosted"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Amount"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Memo"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CheckNumber"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DateInitiated"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.DateAvailable"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CorrectionId"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.ServerAssignedTemporaryId"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.ReferenceNumber"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.StandardIndustrialCode"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.PayeeId"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Name"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Payee"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CurrencyCode"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.CurrencyExchangeRate"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.OriginalCurrencyCode"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.OriginalCurrencyExchangeRate"));

                                for (InvestmentBankTransaction list : listTrans) {

                                    Transaction transaction = list.getTransaction();

                                    cnt = 0;
                                    HSSFRow rowTrans = sheetTrans.createRow(rnt++);

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, list.getSubAccountFund());

                                    if (transaction.getTransactionType() != null) {
                                        cell = rowTrans.createCell(cnt++);
                                        setStringCellValue(cell, transaction.getTransactionType().name());
                                    } else {
                                        cnt += 1;
                                    }

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getId());

                                    cell = rowTrans.createCell(cnt++);
                                    setDateCellValue(cell, transaction.getDatePosted(), cellStyle);

                                    cell = rowTrans.createCell(cnt++);
                                    setNumericCellValue(cell, transaction.getAmount());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getMemo());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getCheckNumber());

                                    cell = rowTrans.createCell(cnt++);
                                    setDateCellValue(cell, transaction.getDateInitiated(), cellStyle);

                                    cell = rowTrans.createCell(cnt++);
                                    setDateCellValue(cell, transaction.getDateAvailable(), cellStyle);

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getCorrectionId());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getTempId());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getReferenceNumber());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getStandardIndustrialCode());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getPayeeId());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getName());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, transaction.getPayee());

                                    if (transaction.getCurrency() != null) {

                                        cell = rowTrans.createCell(cnt++);
                                        setStringCellValue(cell, transaction.getCurrency().getCode());

                                        cell = rowTrans.createCell(cnt++);
                                        setNumericCellValue(cell, transaction.getCurrency().getExchangeRate());
                                    } else {
                                        cnt += 2;
                                    }

                                    if (transaction.getOriginalCurrency() != null) {
                                        cell = rowTrans.createCell(cnt++);
                                        setStringCellValue(cell, transaction.getOriginalCurrency().getCode());

                                        cell = rowTrans.createCell(cnt++);
                                        setNumericCellValue(cell, transaction.getOriginalCurrency().getExchangeRate());
                                    } else {
                                        cnt += 2;
                                    }

                                    for (int i = 0; i < cnt; i++)
                                        sheetTrans.autoSizeColumn(i);

                                }
                            }

                            // BaseInvestment
                            List<BaseInvestmentTransaction> listTransBase = tl.getInvestmentTransactions();

                            if (listTransBase != null && listTransBase.size() > 0) {

                                short cnt = 0;
                                short rnt = 0;

                                HSSFSheet sheetTrans = workbook.createSheet(Messages.getString("OFXParser.Investment_" + TypeCount + " BaseInvestment"));

                                HSSFRow rowheadTrans = sheetTrans.createRow(rnt++);
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Type"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.TransactionId"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.ServerId"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.TradeDate"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.SettlementDate"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.ReversalId"));
                                rowheadTrans.createCell(cnt++).setCellValue(Messages.getString("OFXParser.Memo"));

                                for (BaseInvestmentTransaction list : listTransBase) {

                                    // InvestmentTransaction transaction = list.getInvestmentTransaction();

                                    cnt = 0;
                                    HSSFRow rowTrans = sheetTrans.createRow(rnt++);

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, list.getTransactionType());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, list.getTransactionId());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, list.getServerId());

                                    cell = rowTrans.createCell(cnt++);
                                    setDateCellValue(cell, list.getTradeDate(), cellStyle);

                                    cell = rowTrans.createCell(cnt++);
                                    setDateCellValue(cell, list.getSettlementDate(), cellStyle);

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, list.getReversalTransactionId());

                                    cell = rowTrans.createCell(cnt++);
                                    setStringCellValue(cell, list.getMemo());

                                    for (int i = 0; i < cnt; i++)
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

    private Charset findCharset(InputStream is) throws IOException {
        /* discover charset */
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1));
            Pattern patternV1 = Pattern.compile("^CHARSET\\:(.*)");
            Matcher matcherV1 = patternV1.matcher("\\D");
            Pattern patternV11 = Pattern.compile("^ENCODING\\:(.*)");
            Matcher matcherV11 = patternV11.matcher("\\D");
            Pattern patternV2 = Pattern.compile("encoding=\"(.*)\"");
            Matcher matcherV2 = patternV2.matcher("\\D");

            String cpage = "";
            String cpage2 = "";
            String line = null;
            while ((line = reader.readLine()) != null) {
                matcherV1.reset(line);
                matcherV11.reset(line);
                matcherV2.reset(line);
                if (matcherV1.find()) {
                    cpage = matcherV1.group(1);
                    if (!"NONE".equalsIgnoreCase(cpage)) {
                        return getCharsetFromCodePage(cpage);
                    }
                } else if (matcherV2.find()) {
                    cpage = matcherV2.group(1);
                    return getCharsetFromCodePage(cpage);
                } else if (matcherV11.find()) {
                    cpage2 = matcherV11.group(1);
                }
            }
            if (!cpage2.isEmpty()) {
                return getCharsetFromCodePage(cpage2);
            }
        } finally {
            IOUtil.closeQuietly(reader);
        }
        return StandardCharsets.ISO_8859_1;
    }

    private static Charset getCharsetFromCodePage(String cpage) {
        try {
            return Charset.forName(cpage);
        } catch (Exception e1) {
            try {
                return Charset.forName("Windows-" + cpage);
            } catch (Exception e2) {
                return StandardCharsets.ISO_8859_1;
            }
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        byte[] orgBytes = stream.readNBytes(1 << 24); // Valid OFX files should be much smaller than 16 MB
        Charset cs = null;
        ByteArrayInputStream bis = null;
        try {
            bis = new ByteArrayInputStream(orgBytes);
            cs = findCharset(bis);
        } catch (Exception e) {
            throw new TikaException("Error decoding financial data.", e);
        } finally {
            IOUtil.closeQuietly(bis);
        }

        // Fix TimeZone to the current system settings instead of GMT default
        DefaultStringConversion conv = new DefaultStringConversion(TimeZone.getDefault().getID());
        AggregateUnmarshaller<ResponseEnvelope> aggregate = new AggregateUnmarshaller<>(ResponseEnvelope.class);
        aggregate.setConversion(conv);

        Reader reader = null;
        ResponseEnvelope re = null;
        try {
            byte[] cleanBytes = clean(orgBytes, cs);
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cleanBytes), cs));
            re = (ResponseEnvelope) aggregate.unmarshal(reader);

        } catch (Exception e1) {
            if (!StandardCharsets.UTF_8.equals(cs)) {
                try {
                    // Try again using UTF_8
                    IOUtil.closeQuietly(reader);
                    cs = StandardCharsets.UTF_8;
                    byte[] cleanBytes = clean(orgBytes, cs);
                    reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cleanBytes), cs));
                    re = (ResponseEnvelope) aggregate.unmarshal(reader);
                    e1 = null; // Success
                } catch (Exception e2) {
                }
            }
            if (e1 != null) {
                throw new TikaException("Error decoding financial data.", e1);
            }
        } finally {
            IOUtil.closeQuietly(reader);
        }

        if (re == null) {
            throw new TikaException("Error decoding financial data.");
        }

        Metadata meta = new Metadata();
        meta.set(StandardParser.INDEXER_CONTENT_TYPE, MediaType.parse("application/vnd.ms-excel").toString());
        IItemReader item = context.get(IItemReader.class);
        if (item != null) {
            String name = getBaseName(item.getName());
            meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, name + ".xls");
            meta.set(TikaCoreProperties.TITLE, name + " XLS Parsed");
        }
        meta.set(BasicProps.LENGTH, "");
        meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

        HSSFWorkbook workbook = new HSSFWorkbook();

        if (re != null) {
            decodeSignon(re, workbook);
            decodeBank(re, workbook);
            decodeCreditCard(re, workbook);
            decodeInvestimentWarn(re, workbook); // TODO - Finish implementing
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        workbook.write(bout);
        workbook.close();
        ByteArrayInputStream is1 = new ByteArrayInputStream(bout.toByteArray());

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        if (extractor.shouldParseEmbedded(meta)) {
            extractor.parseEmbedded(is1, handler, meta, true);
        }
    }

    private static byte[] clean(byte[] inBytes, Charset cs) {
        BufferedReader reader = null;
        BufferedWriter writer = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(inBytes.length);
        try {
            reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(inBytes), cs));
            writer = new BufferedWriter(new OutputStreamWriter(bos, cs));
            String line = null;
            while ((line = reader.readLine()) != null) {
                // Replace non-escaped ampersands 
                line = line.replaceAll("&(?![A-Za-z]+;|#[0-9]+;|#x[0-9a-fA-F]+;)", "&amp;");

                // Replace common non-standard transaction types (see #2430)
                line = line.replaceAll("<TRNTYPE>C<", "<TRNTYPE>CREDIT<");
                line = line.replaceAll("<TRNTYPE>IN<", "<TRNTYPE>CREDIT<");
                line = line.replaceAll("<TRNTYPE>D<", "<TRNTYPE>DEBIT<");
                line = line.replaceAll("<TRNTYPE>OUT<", "<TRNTYPE>DEBIT<");

                writer.write(line);
                writer.newLine();
            }
            writer.close();
            return bos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return inBytes;
        } finally {
            IOUtil.closeQuietly(reader);
            IOUtil.closeQuietly(writer);
            IOUtil.closeQuietly(bos);
        }
    }
}
