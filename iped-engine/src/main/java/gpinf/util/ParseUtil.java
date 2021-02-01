package gpinf.util;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Reúne métodos auxiliares para interpretação de Strings.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class ParseUtil {

    /**
     * Interpreta uma String contendo data no formato DD/MM/YYYY HH:MI:SS
     *
     * @param strDateTime
     *            String com data e hora
     * @return Date correspondente.
     * @throws DateFormatException
     *             Data inválida.
     */
    public static final Date parseDate(String strDateTime) throws DateFormatException {
        Calendar calendar = new GregorianCalendar();
        // 01 34 6789 12 45 78
        // DD/MM/YYYY HH:MI:SS
        try {
            calendar.set(Integer.parseInt(strDateTime.substring(6, 10)),
                    Integer.parseInt(strDateTime.substring(3, 5)) - 1, Integer.parseInt(strDateTime.substring(0, 2)),
                    Integer.parseInt(strDateTime.substring(11, 13)), Integer.parseInt(strDateTime.substring(14, 16)),
                    Integer.parseInt(strDateTime.substring(17, 19)));
        } catch (Exception ex) {
            throw new DateFormatException("Invalid date format: " + strDateTime + ".\nExpected: DD/MM/YYYY HH:MI:SS."); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return calendar.getTime();
    }

    /**
     * Interpreta uma String contendo um número utilizando pontos como separador de
     * milhar. Apenas elimina os pontos, ou seja, se houver outros caracteres não
     * numéricos uma exceção será disparada.
     *
     * @param strNumber
     *            String com o número, por exemplo "1.234.567".
     * @return número correspondente.
     * @throws NumberFormatException
     *             Número inválido.
     */
    public static final long parseLong(String strNumber) throws NumberFormatException {
        char[] c = new char[strNumber.length()];
        int pos = 0;
        for (int i = 0; i < strNumber.length(); i++) {
            char ci = strNumber.charAt(i);
            if (ci == '.') {
                continue;
            }
            c[pos++] = ci;
        }
        try {
            return Long.parseLong(new String(c, 0, pos));
        } catch (NumberFormatException nfe) {
            throw new NumberFormatException(
                    "Invalid number: " + strNumber + "\n.Only dots are permited as thousand separator."); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }
}
