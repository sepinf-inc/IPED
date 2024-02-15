package iped.app.timelinegraph;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.text.CharacterIterator;

public class IpedFontMetricsWrapper extends FontMetrics {

    protected IpedFontMetricsWrapper(Font font) {
        super(font);
    }

    public IpedFontMetricsWrapper(FontMetrics fontMetrics) {
        super(fontMetrics.getFont());
        this.fm = fontMetrics;
    }

    FontMetrics fm;

    public int hashCode() {
        return fm.hashCode();
    }

    public boolean equals(Object obj) {
        return fm.equals(obj);
    }

    public Font getFont() {
        return fm.getFont();
    }

    public FontRenderContext getFontRenderContext() {
        return fm.getFontRenderContext();
    }

    public int getLeading() {
        return fm.getLeading();
    }

    public int getAscent() {
        return fm.getAscent();
    }

    public int getDescent() {
        return fm.getDescent();
    }

    public int getHeight() {
        return fm.getHeight();
    }

    public int getMaxAscent() {
        return fm.getMaxAscent();
    }

    public int getMaxDescent() {
        return fm.getMaxDescent();
    }

    public int getMaxDecent() {
        return fm.getMaxDecent();
    }

    public int getMaxAdvance() {
        return fm.getMaxAdvance();
    }

    public int charWidth(int codePoint) {
        return fm.charWidth(codePoint);
    }

    public int charWidth(char ch) {
        return fm.charWidth(ch);
    }

    public int stringWidth(String str) {
        for (String line : str.split("\n"))
            return fm.stringWidth(line);
        return 0;
    }

    public int charsWidth(char[] data, int off, int len) {
        return fm.charsWidth(data, off, len);
    }

    public int bytesWidth(byte[] data, int off, int len) {
        return fm.bytesWidth(data, off, len);
    }

    public int[] getWidths() {
        return fm.getWidths();
    }

    public boolean hasUniformLineMetrics() {
        return fm.hasUniformLineMetrics();
    }

    public LineMetrics getLineMetrics(String str, Graphics context) {
        return fm.getLineMetrics(str, context);
    }

    public LineMetrics getLineMetrics(String str, int beginIndex, int limit, Graphics context) {
        return fm.getLineMetrics(str, beginIndex, limit, context);
    }

    public LineMetrics getLineMetrics(char[] chars, int beginIndex, int limit, Graphics context) {
        return fm.getLineMetrics(chars, beginIndex, limit, context);
    }

    public LineMetrics getLineMetrics(CharacterIterator ci, int beginIndex, int limit, Graphics context) {
        return fm.getLineMetrics(ci, beginIndex, limit, context);
    }

    public Rectangle2D getStringBounds(String str, Graphics context) {
        return fm.getStringBounds(str, context);
    }

    public Rectangle2D getStringBounds(String str, int beginIndex, int limit, Graphics context) {
        return fm.getStringBounds(str, beginIndex, limit, context);
    }

    public Rectangle2D getStringBounds(char[] chars, int beginIndex, int limit, Graphics context) {
        return fm.getStringBounds(chars, beginIndex, limit, context);
    }

    public Rectangle2D getStringBounds(CharacterIterator ci, int beginIndex, int limit, Graphics context) {
        return fm.getStringBounds(ci, beginIndex, limit, context);
    }

    public Rectangle2D getMaxCharBounds(Graphics context) {
        return fm.getMaxCharBounds(context);
    }

    public String toString() {
        return fm.toString();
    }

}
