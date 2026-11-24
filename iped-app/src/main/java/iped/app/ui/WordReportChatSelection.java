package iped.app.ui;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import iped.data.IItemId;
import iped.viewers.HtmlViewerCapture;

/** Keeps message selection, pixels and transcription limited to the approved viewport crop. */
class WordReportChatSelection {

    final BufferedImage image;
    private final boolean document;
    final List<Rectangle2D> messages = new ArrayList<>();
    final List<HtmlViewerCapture.Transcript> transcripts = new ArrayList<>();

    final List<HtmlViewerCapture.Attachment> images = new ArrayList<>();

    static class Result {
        boolean document;
        IItemId attachment;
        final List<HtmlViewerCapture.Attachment> images = new ArrayList<>();
        final List<Integer> transcriptIndices = new ArrayList<>();
        BufferedImage image;
        int messageCount;
        final List<WordReportDocument.Evidence> transcripts = new ArrayList<>();

        void keepTranscripts(Set<Integer> omitted) {
            for (int i = transcripts.size() - 1; i >= 0; i--) {
                if (omitted.contains(transcriptIndices.get(i))) {
                    transcripts.remove(i);
                    transcriptIndices.remove(i);
                }
            }
        }
    }

    private WordReportChatSelection(BufferedImage image, boolean document) {
        this.image = image;
        this.document = document;
    }

    static WordReportChatSelection document(BufferedImage image) {
        return new WordReportChatSelection(image, true);
    }

    static WordReportChatSelection select(HtmlViewerCapture capture, boolean[] selected) {
        if (selected.length != capture.messages.size()) {
            throw new IllegalArgumentException("Invalid message selection");
        }
        List<Rectangle> bands = new ArrayList<>();
        int selectedCount = 0;
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                selectedCount++;
                Rectangle2D bounds = capture.messages.get(i).bounds;
                int top = Math.max(0, (int) Math.floor(bounds.getMinY()));
                int bottom = Math.min(capture.image.getHeight(), (int) Math.ceil(bounds.getMaxY()));
                if (bottom > top) {
                    if (!bands.isEmpty() && top <= bands.get(bands.size() - 1).getMaxY()) {
                        Rectangle last = bands.get(bands.size() - 1);
                        last.height = Math.max(last.height, bottom - last.y);
                    } else {
                        bands.add(new Rectangle(0, top, capture.image.getWidth(), bottom - top));
                    }
                }
            }
        }
        if (selectedCount == selected.length && selectedCount > 0) {
            bands.clear();
            bands.add(new Rectangle(0, 0, capture.image.getWidth(), capture.image.getHeight()));
        }
        int height = bands.stream().mapToInt(band -> band.height).sum();
        WordReportChatSelection selection = new WordReportChatSelection(new BufferedImage(
                capture.image.getWidth(), Math.max(1, height), BufferedImage.TYPE_INT_RGB), false);
        Graphics2D graphics = selection.image.createGraphics();
        try {
            int offset = 0;
            for (Rectangle band : bands) {
                graphics.drawImage(capture.image, 0, offset, band.width, offset + band.height,
                        0, band.y, band.width, band.y + band.height, null);
                for (int i = 0; i < selected.length; i++) {
                    HtmlViewerCapture.Message message = capture.messages.get(i);
                    if (!selected[i] || !band.intersects(message.bounds)) {
                        continue;
                    }
                    Rectangle2D visible = band.createIntersection(message.bounds);
                    selection.messages.add(new Rectangle2D.Double(visible.getX(), visible.getY() - band.y + offset,
                            visible.getWidth(), visible.getHeight()));
                    for (HtmlViewerCapture.Attachment image : message.images) {
                        Rectangle2D b = band.createIntersection(image.bounds);
                        if (!b.isEmpty()) {
                            selection.images.add(new HtmlViewerCapture.Attachment(image.hash, new Rectangle2D.Double(
                                    b.getX(), b.getY() - band.y + offset, b.getWidth(), b.getHeight())));
                        }
                    }
                    for (HtmlViewerCapture.Transcript transcript : message.transcripts) {
                        HtmlViewerCapture.Transcript copy = new HtmlViewerCapture.Transcript();
                        copy.hash = transcript.hash;
                        for (HtmlViewerCapture.Character character : transcript.characters) {
                            if (band.contains(character.bounds.getCenterX(), character.bounds.getCenterY())) {
                                Rectangle2D b = character.bounds;
                                copy.characters.add(new HtmlViewerCapture.Character(character.text,
                                        new Rectangle2D.Double(b.getX(), b.getY() - band.y + offset, b.getWidth(), b.getHeight())));
                            }
                        }
                        selection.transcripts.add(copy);
                    }
                }
                offset += band.height;
            }
        } finally {
            graphics.dispose();
        }
        return selection;
    }

    Result crop(Rectangle requested) {
        Rectangle crop = requested.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        if (crop.isEmpty()) {
            throw new IllegalArgumentException("Empty crop");
        }
        Result result = new Result();
        result.document = document;
        result.image = new BufferedImage(crop.width, crop.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.image.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, crop.width, crop.height, crop.x, crop.y, crop.x + crop.width, crop.y + crop.height, null);
        } finally {
            graphics.dispose();
        }
        result.messageCount = (int) messages.stream().filter(crop::intersects).count();
        for (HtmlViewerCapture.Attachment image : images) {
            if (crop.intersects(image.bounds)) {
                result.images.add(image);
            }
        }
        for (int i = 0; i < transcripts.size(); i++) {
            HtmlViewerCapture.Transcript transcript = transcripts.get(i);
            StringBuilder text = new StringBuilder();
            double previousY = Double.NaN;
            for (HtmlViewerCapture.Character character : transcript.characters) {
                Rectangle2D b = character.bounds;
                if (crop.contains(b.getCenterX(), b.getCenterY())) {
                    if (!Double.isNaN(previousY) && b.getCenterY() > previousY + b.getHeight() / 2) {
                        text.append('\n');
                    }
                    text.append(character.text);
                    previousY = b.getCenterY();
                }
            }
            String value = text.toString().trim();
            if (!value.isEmpty()) {
                WordReportDocument.Evidence audio = new WordReportDocument.Evidence();
                audio.text = value;
                if (transcript.hash != null && !transcript.hash.isEmpty()) {
                    audio.metadata.put("Hash", transcript.hash);
                }
                result.transcriptIndices.add(i);
                result.transcripts.add(audio);
            }
        }
        return result;
    }
}
