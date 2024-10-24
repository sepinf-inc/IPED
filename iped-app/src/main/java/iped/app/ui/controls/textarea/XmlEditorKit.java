package iped.app.ui.controls.textarea;

import java.net.URL;

import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;

public class XmlEditorKit extends StyledEditorKit {

    private XmlViewFactory xmlViewFactory;

    public XmlEditorKit() {
        xmlViewFactory = new XmlViewFactory();
    }

    @Override
    public ViewFactory getViewFactory() {
        return xmlViewFactory;
    }

    @Override
    public String getContentType() {
        return "text/xml";
    }

    public void setSchema(URL xsdFile) {
        xmlViewFactory.setSchema(xsdFile);
        
    }
}
