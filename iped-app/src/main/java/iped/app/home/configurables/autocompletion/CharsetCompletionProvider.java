package iped.app.home.configurables.autocompletion;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.SortedMap;

import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;

public class CharsetCompletionProvider extends DefaultCompletionProvider {

    public CharsetCompletionProvider() {
        super();
        SortedMap charsets = Charset.availableCharsets();
        if (charsets != null) {
            for (Iterator iterator = charsets.keySet().iterator(); iterator.hasNext();) {
                String charsetName = (String) iterator.next();
                this.addCompletion(new BasicCompletion(this, charsetName));
            }
        }
    }

    @Override
    protected boolean isValidChar(char ch) {
        return super.isValidChar(ch) || ch == '/' || ch == '-' || ch == '.' || ch == '-' || ch == '=' || ch == '+' || ch == ';';
    }

}
