package iped.viewers;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Pixels and visible text positions from one unchanged HTML viewport. */
public class HtmlViewerCapture {

    public final BufferedImage image;
    public final List<Message> messages = new ArrayList<>();

    public static class Message {
        public Rectangle2D bounds;
        public String label = "";
        public final List<Attachment> images = new ArrayList<>();
        public final List<Transcript> transcripts = new ArrayList<>();
    }

    public static class Attachment {
        public final String hash;
        public final Rectangle2D bounds;

        public Attachment(String hash, Rectangle2D bounds) {
            this.hash = hash;
            this.bounds = bounds;
        }
    }

    public static class Transcript {
        public String hash;
        public final List<Character> characters = new ArrayList<>();
    }

    public static class Character {
        public final String text;
        public final Rectangle2D bounds;

        public Character(String text, Rectangle2D bounds) {
            this.text = text;
            this.bounds = bounds;
        }
    }

    public HtmlViewerCapture(BufferedImage image) {
        this.image = image;
    }

    static HtmlViewerCapture capture(WebView view) throws IOException {
        String script;
        try (InputStream in = HtmlViewerCapture.class.getResourceAsStream("/iped/viewers/chat-viewport.js")) {
            if (in == null) {
                throw new IOException("Missing viewport capture script");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JSObject data = (JSObject) view.getEngine().executeScript(script);
        int width = Math.min((int) Math.floor(view.getWidth()), number(data, "width"));
        int height = Math.min((int) Math.floor(view.getHeight()), number(data, "height"));
        if (width <= 0 || height <= 0) {
            throw new IOException("Empty HTML viewport");
        }
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setViewport(new javafx.geometry.Rectangle2D(0, 0, width, height));
        HtmlViewerCapture capture = new HtmlViewerCapture(SwingFXUtils.fromFXImage(
                view.snapshot(parameters, new WritableImage(width, height)), null));
        JSObject messages = (JSObject) data.getMember("messages");
        for (int i = 0; i < number(messages, "length"); i++) {
            JSObject source = (JSObject) messages.getSlot(i);
            Message message = new Message();
            message.bounds = rectangle((JSObject) source.getMember("bounds"));
            message.label = String.valueOf(source.getMember("label"));
            JSObject images = (JSObject) source.getMember("images");
            for (int j = 0; j < number(images, "length"); j++) {
                JSObject image = (JSObject) images.getSlot(j);
                message.images.add(new Attachment(String.valueOf(image.getMember("hash")),
                        rectangle((JSObject) image.getMember("bounds"))));
            }
            JSObject transcripts = (JSObject) source.getMember("transcripts");
            for (int t = 0; t < number(transcripts, "length"); t++) {
                JSObject text = (JSObject) transcripts.getSlot(t);
                Transcript transcript = new Transcript();
                transcript.hash = String.valueOf(text.getMember("hash"));
                JSObject characters = (JSObject) text.getMember("characters");
                for (int c = 0; c < number(characters, "length"); c++) {
                    JSObject character = (JSObject) characters.getSlot(c);
                    transcript.characters.add(new Character(String.valueOf(character.getMember("text")),
                            rectangle((JSObject) character.getMember("bounds"))));
                }
                message.transcripts.add(transcript);
            }
            capture.messages.add(message);
        }
        if (!capture.messages.isEmpty() && isBlank(capture.image)) {
            throw new IOException("The HTML preview has not been painted");
        }
        return capture;
    }

    private static boolean isBlank(BufferedImage image) {
        int first = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != first) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int number(JSObject object, String name) {
        return ((Number) object.getMember(name)).intValue();
    }

    private static Rectangle2D rectangle(JSObject array) {
        double x = ((Number) array.getSlot(0)).doubleValue();
        double y = ((Number) array.getSlot(1)).doubleValue();
        double width = ((Number) array.getSlot(2)).doubleValue();
        double height = ((Number) array.getSlot(3)).doubleValue();
        return new Rectangle2D.Double(x, y, width, height);
    }
}
