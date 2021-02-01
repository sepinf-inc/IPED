package dpf.mt.gpinf.skype.parser;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;

/**
 * Reúne métodos auxiliares para formatação de dados.
 * 
 * @author Patrick Dalla Bernardina
 */
public class FormatUtil {
    /** Formatador de números que utiliza separador de milhares. */
    private static final ThreadLocal<NumberFormat> numberFormat = new ThreadLocal<NumberFormat>() {
        protected NumberFormat initialValue() {
            return new DecimalFormat("#,##0"); //$NON-NLS-1$
        }
    };

    /** Formatador de números decimais com até duas casas decimais. */
    private static final ThreadLocal<NumberFormat> decimalFormat = new ThreadLocal<NumberFormat>() {
        protected NumberFormat initialValue() {
            return new DecimalFormat("0.###"); //$NON-NLS-1$
        }
    };

    /** Formatador de datas. */
    private static final ThreadLocal<DateFormat> dateFormat = new ThreadLocal<DateFormat>() {
        protected DateFormat initialValue() {
            return new SimpleDateFormat(Messages.getString("SkypeParser.DateFormat")); //$NON-NLS-1$
        }
    };

    /**
     * Formata uma data no formato "dd/MM/yyyy HH:mm:ss".
     * 
     * @param date
     *            Objeto da class Date a ser formatado.
     * @return String com a data formatada. No caso da data ser nula ou inválida
     *         retorna uma String vazia.
     */
    public static final String format(Date date) {
        if (date != null)
            try {
                return dateFormat.get().format(date);
            } catch (Exception e) {
            }
        return " - "; //$NON-NLS-1$
    }

    /**
     * Formata um número inteiro (da classe Long) no formato "1.234.567".
     * 
     * @param number
     *            Número a ser formatado.
     * @return String com o número formatado. No caso do número ser nulo retorna uma
     *         String vazia.
     */
    public static final String format(Long number) {
        if (number == null)
            return " - "; //$NON-NLS-1$
        return numberFormat.get().format(number);
    }

    /**
     * Formata um número decimal.
     * 
     * @param d
     *            Número a ser formatado.
     * @return String com o número formatado. No caso do número ser nulo retorna uma
     *         String vazia.
     */
    public static final String format(Double d) {
        if ((d == null) || (d == 0))
            return " - "; //$NON-NLS-1$
        return decimalFormat.get().format(d);
    }

    public static String format(String value) {
        if (value == null || value.trim().isEmpty())
            return " - "; //$NON-NLS-1$
        else {
            // substitui caracteres do HTML por caracteres de escape
            value = SimpleHTMLEncoder.htmlEncode(value);
            return value.trim();
        }
    }
}
