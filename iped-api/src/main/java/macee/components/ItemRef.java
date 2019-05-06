/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.components;

import java.io.Serializable;

/**
 * @author WERNECK
 */
public interface ItemRef extends Serializable {

    int compareTo(ItemRef other);

    String getCaseId();

    int getId();

    String getSourceId();

}
