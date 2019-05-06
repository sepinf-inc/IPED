/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3;

import java.io.Serializable;
import java.util.List;

/**
 *
 * @author WERNECK
 */
public interface CaseInfo extends Serializable {

  /**
   * Adiciona propriedade.
   *
   * @param property propriedade a ser adicionada
   */
  void addProperty(Property property);

  /**
   * Obtem o nome da imagem a ser utilizado no cabeçalho.
   *
   * @return nome da imagem do cabeçalho
   */
  String getHeaderImage();

  /**
   * Obtem texto do cabeçalho.
   *
   * @return texto do cabeçalho
   */
  String getHeaderText();

  /**
   * Obtém lista de propriedades.
   *
   * @return lista não modificável de propriedades.
   */
  List<Property> getProperties();

  /**
   * Altera o nome e camimho da imagem do cabeçalho.
   *
   * @param headerImage novo nome e caminho da imagem
   */
  void setHeaderImage(String headerImage);

  /**
   * Altera o texto do cabeçalho.
   *
   * @param headerText novo texto
   */
  void setHeaderText(String headerText);

  /**
   * Retorna String com os dados contidos no objeto.
   *
   * @return String listando as propriedades e dados do cabeçalho.
   */
  @Override
  String toString();
  
}
