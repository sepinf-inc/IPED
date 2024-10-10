package iped.app.ui.controls.textarea;

import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

public class JSONViewFactory implements ViewFactory {

    @Override
    public View create(Element elem) {
        return new JSONView(elem);
    }

}
