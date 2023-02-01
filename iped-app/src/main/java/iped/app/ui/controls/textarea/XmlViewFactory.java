package iped.app.ui.controls.textarea;

import java.net.URL;

import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

public class XmlViewFactory implements ViewFactory {

    URL xsdFile=null;
    
    /**
     * @see javax.swing.text.ViewFactory#create(javax.swing.text.Element)
     */
    public View create(Element element) {
        XmlView xv = new XmlView(element, xsdFile);
        xv.configPatterns();
        return xv;
    }

    public void setSchema(URL xsdFile) {
        this.xsdFile = xsdFile;        
    }

}
