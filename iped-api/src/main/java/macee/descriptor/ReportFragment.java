/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package macee.descriptor;

/**
 *
 * @author WERNECK
 */
public interface ReportFragment {

    int compareTo(Descriptor other);

    String getContent();

    int getOrder();

    String getParsedContent();

    boolean isParsed();

    void setOrder(int order);

    void setParsedContent(String parsedContent);

}
