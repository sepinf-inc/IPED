package iped.app.ui.controls.textarea;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.PlainDocument;
import javax.swing.text.PlainView;
import javax.swing.text.Segment;
import javax.swing.text.Utilities;

public abstract class RegexView extends PlainView {
    protected HashMap<Pattern, Color> patternColors;

    public RegexView(Element elem) {
        super(elem);
        getDocument().putProperty(PlainDocument.tabSizeAttribute, 4);
    }

    abstract protected void configPatterns();

    private TreeMap<Integer, Integer> startMap;
    private TreeMap<Integer, Color> colorMap;

    @Override
    protected int drawUnselectedText(Graphics graphics, int x, int y, int p0,
            int p1) throws BadLocationException {
        Document doc = getDocument();
        String text = doc.getText(p0, p1 - p0);

        Segment segment = getLineBuffer();

        startMap = new TreeMap<Integer, Integer>();
        colorMap = new TreeMap<Integer, Color>();
        
        if(patternColors!=null) {
            // Match all regexes on this snippet, store positions
            for (Map.Entry<Pattern, Color> entry : patternColors.entrySet()) {

                Matcher matcher = entry.getKey().matcher(text);

                while (matcher.find()) {
                    startMap.put(matcher.start(1), matcher.end());
                    colorMap.put(matcher.start(1), entry.getValue());
                }
            }
        }

        // TODO: check the map for overlapping parts

        int i = 0;

        Font originalFont = graphics.getFont();
        Font taggedFont = new Font(graphics.getFont().getName(), Font.ITALIC + Font.BOLD, graphics.getFont().getSize());
        // Colour the parts
        for (Map.Entry<Integer, Integer> entry : startMap.entrySet()) {
            int start = entry.getKey();
            int end = entry.getValue();

            graphics.setFont(originalFont);
            if (i < start) {
                graphics.setColor(Color.black);
                doc.getText(p0 + i, start - i, segment);
                x = Utilities.drawTabbedText(segment, x, y, graphics, this, i);
            }

            graphics.setColor(colorMap.get(start));
            i = end;
            doc.getText(p0 + start, i - start, segment);
            graphics.setFont(taggedFont);
            x = Utilities.drawTabbedText(segment, x, y, graphics, this, start);
        }

        // Paint possible remaining text black
        if (i < text.length()) {
            graphics.setColor(Color.black);
            doc.getText(p0 + i, text.length() - i, segment);
            x = Utilities.drawTabbedText(segment, x, y, graphics, this, i);
        }

        return x;
    }

}
