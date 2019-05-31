/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3;

import java.io.Serializable;

/**
 *
 * @author WERNECK
 */
public interface IProperty extends Serializable {

    /**
     * Obtém nome da propriedade.
     *
     * @return nome da propriedade
     */
    String getName();

    /**
     * Obtém valor da propriedade.
     *
     * @return valor correspondente da propriedade
     */
    String getValue();

    /**
     * Retorna representação em texto da propriedade.
     */
    @Override
    String toString();

}
