package iped.app.timelinegraph;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

public class IpedGraphicsWrapper extends Graphics2D {
    Graphics2D wrapped;
    FontMetrics fm;

    public IpedGraphicsWrapper(Graphics2D g) {
        this.wrapped = g;
    }

    public int hashCode() {
        return wrapped.hashCode();
    }

    public boolean equals(Object obj) {
        return wrapped.equals(obj);
    }

    public Graphics create() {
        return wrapped.create();
    }

    public Graphics create(int x, int y, int width, int height) {
        return wrapped.create(x, y, width, height);
    }

    public void translate(int x, int y) {
        wrapped.translate(x, y);
    }

    public Color getColor() {
        return wrapped.getColor();
    }

    public void setColor(Color c) {
        wrapped.setColor(c);
    }

    public void setPaintMode() {
        wrapped.setPaintMode();
    }

    public void setXORMode(Color c1) {
        wrapped.setXORMode(c1);
    }

    public Font getFont() {
        return wrapped.getFont();
    }

    public void setFont(Font font) {
        wrapped.setFont(font);
    }

    public FontMetrics getFontMetrics() {
        if (fm == null) {
            fm = new IpedFontMetricsWrapper(wrapped.getFontMetrics());
        }
        return fm;
    }

    public FontMetrics getFontMetrics(Font f) {
        return new IpedFontMetricsWrapper(wrapped.getFontMetrics(f));
    }

    public Rectangle getClipBounds() {
        return wrapped.getClipBounds();
    }

    public void clipRect(int x, int y, int width, int height) {
        wrapped.clipRect(x, y, width, height);
    }

    public void setClip(int x, int y, int width, int height) {
        wrapped.setClip(x, y, width, height);
    }

    public Shape getClip() {
        return wrapped.getClip();
    }

    public void setClip(Shape clip) {
        wrapped.setClip(clip);
    }

    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        wrapped.copyArea(x, y, width, height, dx, dy);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        wrapped.drawLine(x1, y1, x2, y2);
    }

    public void fillRect(int x, int y, int width, int height) {
        wrapped.fillRect(x, y, width, height);
    }

    public void drawRect(int x, int y, int width, int height) {
        wrapped.drawRect(x, y, width, height);
    }

    public void clearRect(int x, int y, int width, int height) {
        wrapped.clearRect(x, y, width, height);
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        wrapped.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        wrapped.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void draw3DRect(int x, int y, int width, int height, boolean raised) {
        wrapped.draw3DRect(x, y, width, height, raised);
    }

    public void fill3DRect(int x, int y, int width, int height, boolean raised) {
        wrapped.fill3DRect(x, y, width, height, raised);
    }

    public void drawOval(int x, int y, int width, int height) {
        wrapped.drawOval(x, y, width, height);
    }

    public void fillOval(int x, int y, int width, int height) {
        wrapped.fillOval(x, y, width, height);
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        wrapped.drawArc(x, y, width, height, startAngle, arcAngle);
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        wrapped.fillArc(x, y, width, height, startAngle, arcAngle);
    }

    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        wrapped.drawPolyline(xPoints, yPoints, nPoints);
    }

    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        wrapped.drawPolygon(xPoints, yPoints, nPoints);
    }

    public void drawPolygon(Polygon p) {
        wrapped.drawPolygon(p);
    }

    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        wrapped.fillPolygon(xPoints, yPoints, nPoints);
    }

    public void fillPolygon(Polygon p) {
        wrapped.fillPolygon(p);
    }

    public void drawString(String str, int x, int y) {
        for (String line : str.split("\n"))
            wrapped.drawString(line, x, y += wrapped.getFontMetrics().getHeight());
    }

    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        wrapped.drawString(iterator, x, y);
    }

    public void drawChars(char[] data, int offset, int length, int x, int y) {
        wrapped.drawChars(data, offset, length, x, y);
    }

    public void drawBytes(byte[] data, int offset, int length, int x, int y) {
        wrapped.drawBytes(data, offset, length, x, y);
    }

    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        return wrapped.drawImage(img, x, y, observer);
    }

    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        return wrapped.drawImage(img, x, y, width, height, observer);
    }

    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        return wrapped.drawImage(img, x, y, bgcolor, observer);
    }

    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        return wrapped.drawImage(img, x, y, width, height, bgcolor, observer);
    }

    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
        return wrapped.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }

    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color bgcolor, ImageObserver observer) {
        return wrapped.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
    }

    public void dispose() {
        wrapped.dispose();
    }

    public void finalize() {
        wrapped.finalize();
    }

    public String toString() {
        return wrapped.toString();
    }

    public Rectangle getClipRect() {
        return wrapped.getClipRect();
    }

    public boolean hitClip(int x, int y, int width, int height) {
        return wrapped.hitClip(x, y, width, height);
    }

    public Rectangle getClipBounds(Rectangle r) {
        return wrapped.getClipBounds(r);
    }

    public void draw(Shape s) {
        wrapped.draw(s);
    }

    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        return wrapped.drawImage(img, xform, obs);
    }

    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        wrapped.drawImage(img, op, x, y);
    }

    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        wrapped.drawRenderedImage(img, xform);
    }

    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
        wrapped.drawRenderableImage(img, xform);
    }

    public void drawString(String str, float x, float y) {
        for (String line : str.split("\n"))
            wrapped.drawString(line, x, y += wrapped.getFontMetrics().getHeight());
    }

    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        wrapped.drawString(iterator, x, y);
    }

    public void drawGlyphVector(GlyphVector g, float x, float y) {
        wrapped.drawGlyphVector(g, x, y);
    }

    public void fill(Shape s) {
        wrapped.fill(s);
    }

    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        return wrapped.hit(rect, s, onStroke);
    }

    public GraphicsConfiguration getDeviceConfiguration() {
        return wrapped.getDeviceConfiguration();
    }

    public void setComposite(Composite comp) {
        wrapped.setComposite(comp);
    }

    public void setPaint(Paint paint) {
        wrapped.setPaint(paint);
    }

    public void setStroke(Stroke s) {
        wrapped.setStroke(s);
    }

    public void setRenderingHint(Key hintKey, Object hintValue) {
        wrapped.setRenderingHint(hintKey, hintValue);
    }

    public Object getRenderingHint(Key hintKey) {
        return wrapped.getRenderingHint(hintKey);
    }

    public void setRenderingHints(Map<?, ?> hints) {
        wrapped.setRenderingHints(hints);
    }

    public void addRenderingHints(Map<?, ?> hints) {
        wrapped.addRenderingHints(hints);
    }

    public RenderingHints getRenderingHints() {
        return wrapped.getRenderingHints();
    }

    public void translate(double tx, double ty) {
        wrapped.translate(tx, ty);
    }

    public void rotate(double theta) {
        wrapped.rotate(theta);
    }

    public void rotate(double theta, double x, double y) {
        wrapped.rotate(theta, x, y);
    }

    public void scale(double sx, double sy) {
        wrapped.scale(sx, sy);
    }

    public void shear(double shx, double shy) {
        wrapped.shear(shx, shy);
    }

    public void transform(AffineTransform Tx) {
        wrapped.transform(Tx);
    }

    public void setTransform(AffineTransform Tx) {
        wrapped.setTransform(Tx);
    }

    public AffineTransform getTransform() {
        return wrapped.getTransform();
    }

    public Paint getPaint() {
        return wrapped.getPaint();
    }

    public Composite getComposite() {
        return wrapped.getComposite();
    }

    public void setBackground(Color color) {
        wrapped.setBackground(color);
    }

    public Color getBackground() {
        return wrapped.getBackground();
    }

    public Stroke getStroke() {
        return wrapped.getStroke();
    }

    public void clip(Shape s) {
        wrapped.clip(s);
    }

    public FontRenderContext getFontRenderContext() {
        return wrapped.getFontRenderContext();
    };

}
