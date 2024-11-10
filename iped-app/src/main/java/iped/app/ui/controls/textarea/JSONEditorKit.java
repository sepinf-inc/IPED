package iped.app.ui.controls.textarea;

import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;

public class JSONEditorKit extends StyledEditorKit {

    private ViewFactory jsonViewFactory;

    public JSONEditorKit() {
        jsonViewFactory = new JSONViewFactory();
    }

    @Override
    public ViewFactory getViewFactory() {
        return jsonViewFactory;
    }

    @Override
    public String getContentType() {
        return "text/xml";
    }
}
