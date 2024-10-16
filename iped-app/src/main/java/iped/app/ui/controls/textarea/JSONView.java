package iped.app.ui.controls.textarea;

import java.awt.Color;
import java.util.HashMap;
import java.util.regex.Pattern;

import javax.swing.text.Element;

public class JSONView extends RegexView {

    private static String TAG_PATTERN = "(\"[^\"]*\"\\s*:)";
    private static String TAG_END_PATTERN = "(\"[^\"]*\"\\s*[,\\}\\]])";
    private static String TAG_COMMENT = "(<!--.*-->)";

    protected void configPatterns(){
        String TAG_PATTERN = "(\"[^\"]*\"\\s*:)";
        String TAG_END_PATTERN = "(\"[^\"]*\"\\s*[,\\}\\]])";
        String TAG_COMMENT = "(<!--.*-->)";
        
        // NOTE: the order is important!
        patternColors = new HashMap<Pattern, Color>();
        patternColors.put(Pattern.compile(TAG_PATTERN), new Color(63, 127, 127));
        patternColors.put(Pattern.compile(TAG_END_PATTERN), new Color(127, 63, 63));
    }
    public JSONView(Element elem) {
        super(elem);
        configPatterns();
    }

}
