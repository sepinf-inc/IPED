package macee.annotations.filter;

/**
 * Available filter operations are include only or exclude.
 *
 * COMENTÁRIO (Werneck): Melhorar a nomenclatura. INCLUDE_ONLY é incluir
 * se a condição for atendida. Faria sentido criar um INCLUDE_ELSE para incluir
 * se a condição não for atendida ou ficaria muito confuso?
 * @author WERNECK
 */
public enum FilterOperation {

    INCLUDE_ONLY, EXCLUDE

}
