package gpinf.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Reúne métodos auxiliares para manipulação de Strings contendo trechos HTMLs.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class HtmlUtil {

  /**
   * Extrai células de uma tabela HTML.
   *
   * @param table String com o trecho HTML contendo a tabela a ser processada
   * @return Células da tabela, organizadas por linha e depois por coluna.
   */
  public static final List<List<String>> extractCellsFromTable(String table) {
    List<List<String>> lines = new ArrayList<List<String>>();
    String s = table.toLowerCase();
    int p1 = 0;
    while (true) {
      p1 = s.indexOf("<tr", p1);
      if (p1 < 0) {
        break;
      }
      p1 = s.indexOf(">", p1 + 3);
      if (p1 < 0) {
        break;
      }
      int p2 = s.indexOf("</tr>", p1);
      int p3 = s.indexOf("<tr", p1);
      if (p2 < 0) {
        p2 = s.length();
      }
      if (p3 < 0) {
        p3 = s.length();
      }
      int skip = p2 < p3 ? 5 : 0;
      if (p3 < p2) {
        p2 = p3;
      }
      lines.add(extractCellsFromRow(table.substring(p1 + 1, p2)));
      p1 = p2 + skip;
    }
    return lines;
  }

  /**
   * Extrai células de uma linha de uma tabela HTML.
   *
   * @param line String com o trecho HTML contendo uma linha da tabela a ser processada
   * @return Células da linha, na ordem que estão na String.
   */
  private static final List<String> extractCellsFromRow(String line) {
    List<String> cells = new ArrayList<String>();
    String s = line.toLowerCase();
    int p1 = 0;
    while (true) {
      p1 = s.indexOf("<td", p1);
      if (p1 < 0) {
        break;
      }
      p1 = s.indexOf(">", p1 + 3);
      if (p1 < 0) {
        break;
      }
      int p2 = s.indexOf("</td>", p1);
      if (p2 < 0) {
        p2 = s.length();
      }
      cells.add(line.substring(p1 + 1, p2));
      p1 = p2 + 5;
    }
    return cells;
  }

  /**
   * Extrai links de um trecho HTML.
   *
   * @param str trecho HTML a ser processado
   * @return Array com links encontrados.
   */
  public static final String[] getLinks(String str) {
    List<String> links = new ArrayList<String>();
    String s = str.toLowerCase();
    int p1 = 0;
    while (true) {
      p1 = s.indexOf("href=\"", p1);
      if (p1 < 0) {
        break;
      }
      p1 += 6;
      int p2 = s.indexOf("\"", p1);
      if (p2 < 0) {
        break;
      }
      links.add(str.substring(p1, p2));
      p1 = p2 + 1;
    }
    return links.toArray(new String[0]);
  }

  public static final String getAttachName(String str) {
    int p1 = str.indexOf(">>");
    int p2 = str.indexOf("</a>", p1);
    if (p1 > 0 && p2 > 0) {
      return str.substring(p1 + 2, p2);
    } else {
      return "";
    }
  }

  /**
   * Converte caracteres especiais html; para os valores correspondentes.
   *
   * @param str String a ser processada.
   * @return String Após a retirada de caracteres especiais.
   */
  public static final String removeSpecialChars(String str) {
    if (str.indexOf("&#") < 0) {
      return str;
    }
    StringBuilder ret = new StringBuilder(str);
    int p1 = 0;
    while (true) {
      p1 = ret.indexOf("&#", p1);
      if (p1 < 0) {
        break;
      }
      int p2 = ret.indexOf(";", p1);
      if (p2 < p1 + 3 || p2 > p1 + 5) {
        break;
      }
      int v = Integer.parseInt(ret.substring(p1 + 2, p2));
      ret.delete(p1, p2 + 1);
      ret.insert(p1, (char) v);
      p1++;
    }
    return ret.toString();
  }
}
