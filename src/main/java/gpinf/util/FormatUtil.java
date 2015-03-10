package gpinf.util;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Reúne métodos auxiliares para formatação de dados.
 * 
 * @author Wladimir Leite (GPINF/SP)
 */
public class FormatUtil {
	/** Formatador de números que utiliza separador de milhares. */
	private static final NumberFormat numberFormat = new DecimalFormat("#,##0");

	/** Formatador de números decimais com até duas casas decimais. */
	private static final NumberFormat decimalFormat = new DecimalFormat("0.###");

	/** Formatador de datas. */
	private static final DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

	/**
	 * Formata uma data no formato "dd/MM/yyyy HH:mm:ss".
	 * 
	 * @param date
	 *            Objeto da class Date a ser formatado.
	 * @return String com a data formatada. No caso da data ser nula ou inválida
	 *         retorna uma String vazia.
	 */
	public static final String format(Date date) {
		if (date == null)
			return "";
		try {
			return dateFormat.format(date);
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * Formata um número inteiro (da classe Long) no formato "1.234.567".
	 * 
	 * @param number
	 *            Número a ser formatado.
	 * @return String com o número formatado. No caso do número ser nulo retorna
	 *         uma String vazia.
	 */
	public static final String format(Long number) {
		if (number == null)
			return "";
		return numberFormat.format(number);
	}

	/**
	 * Formata um número decimal.
	 * 
	 * @param d
	 *            Número a ser formatado.
	 * @return String com o número formatado. No caso do número ser nulo retorna
	 *         uma String vazia.
	 */
	public static final String format(Double d) {
		if (d == null)
			return "";
		return decimalFormat.format(d);
	}
}
