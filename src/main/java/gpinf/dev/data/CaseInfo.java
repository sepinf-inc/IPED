package gpinf.dev.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dpf.sp.gpinf.indexer.Messages;

/**
 * Informações de um caso.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class CaseInfo implements Serializable {

  /**
   * Identificador utilizado para serialização da classe.
   */
  private static final long serialVersionUID = 13141592654441L;

  /**
   * Propriedades do caso. É armazenada em uma lista para manter a ordem original.
   */
  private final List<Property> properties = new ArrayList<Property>();

  /**
   * Texto do cabeçalho.
   */
  private String headerText;

  /**
   * Nome e caminho do arquivo de imagem do cabeçalho.
   */
  private String headerImage;

  /**
   * Adiciona propriedade.
   *
   * @param property propriedade a ser adicionada
   */
  public void addProperty(Property property) {
    properties.add(property);
  }

  /**
   * Obtém lista de propriedades.
   *
   * @return lista não modificável de propriedades.
   */
  public List<Property> getProperties() {
    return Collections.unmodifiableList(properties);
  }

  /**
   * Retorna String com os dados contidos no objeto.
   *
   * @return String listando as propriedades e dados do cabeçalho.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Header:"); //$NON-NLS-1$
    sb.append("\n\t").append(headerImage).append(" : ").append(headerText); //$NON-NLS-1$ //$NON-NLS-2$
    sb.append("\n").append("Properties(").append(properties.size()).append("):"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    for (int i = 0; i < properties.size(); i++) {
      sb.append("\n\t").append(properties.get(i)); //$NON-NLS-1$
    }
    return sb.toString();
  }

  /**
   * Obtem o nome da imagem a ser utilizado no cabeçalho.
   *
   * @return nome da imagem do cabeçalho
   */
  public String getHeaderImage() {
    return headerImage;
  }

  /**
   * Altera o nome e camimho da imagem do cabeçalho.
   *
   * @param headerImage novo nome e caminho da imagem
   */
  public void setHeaderImage(String headerImage) {
    this.headerImage = headerImage;
  }

  /**
   * Obtem texto do cabeçalho.
   *
   * @return texto do cabeçalho
   */
  public String getHeaderText() {
    return headerText;
  }

  /**
   * Altera o texto do cabeçalho.
   *
   * @param headerText novo texto
   */
  public void setHeaderText(String headerText) {
    this.headerText = headerText;
  }
}
