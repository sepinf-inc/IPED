package iped.app.ui;

import static org.junit.Assert.*;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.Test;

import iped.viewers.HtmlViewerCapture;

public class WordReportChatSelectionTest {

    private HtmlViewerCapture capture() {
        BufferedImage image = new BufferedImage(100, 300, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 100; x++) {
                image.setRGB(x, y, y * 256 + x);
            }
        }
        HtmlViewerCapture capture = new HtmlViewerCapture(image);
        for (int i = 0; i < 3; i++) {
            HtmlViewerCapture.Message message = new HtmlViewerCapture.Message();
            message.bounds = new Rectangle(5, i * 100 + 10, 90, 80);
            HtmlViewerCapture.Transcript transcript = new HtmlViewerCapture.Transcript();
            transcript.hash = "hash-" + i;
            String text = "audio" + i;
            for (int c = 0; c < text.length(); c++) {
                transcript.characters.add(new HtmlViewerCapture.Character(text.substring(c, c + 1),
                        new Rectangle2D.Double(10 + c * 10, i * 100 + 30, 10, 10)));
            }
            message.transcripts.add(transcript);
            capture.messages.add(message);
        }
        return capture;
    }

    @Test
    public void defaultKeepsTheVisiblePixelsWithoutScalingOrReadingOtherMessages() {
        HtmlViewerCapture source = capture();
        WordReportChatSelection.Result result = WordReportChatSelection.select(source, new boolean[] { true, true, true })
                .crop(new Rectangle(0, 0, 100, 300));
        assertEquals(3, result.messageCount);
        assertEquals(3, result.transcripts.size());
        assertEquals(300, result.image.getHeight());
        for (int y = 0; y < 300; y++) {
            assertEquals(source.image.getRGB(50, y), result.image.getRGB(50, y));
        }
    }

    @Test
    public void uncheckingMessagesRemovesTheirPixelsAndTranscripts() {
        HtmlViewerCapture source = capture();
        WordReportChatSelection.Result result = WordReportChatSelection.select(source, new boolean[] { false, true, false })
                .crop(new Rectangle(0, 0, 100, 80));
        assertEquals(1, result.messageCount);
        assertEquals(80, result.image.getHeight());
        assertEquals(source.image.getRGB(50, 110), result.image.getRGB(50, 0));
        assertEquals(source.image.getRGB(50, 189), result.image.getRGB(50, 79));
        assertEquals(1, result.transcripts.size());
        assertEquals("audio1", result.transcripts.get(0).text);
        assertEquals("hash-1", result.transcripts.get(0).metadata.get("Hash"));
    }

    @Test
    public void cropExcludesTranscriptionCharactersOutsideTheApprovedArea() {
        WordReportChatSelection.Result result = WordReportChatSelection.select(capture(), new boolean[] { true, true, true })
                .crop(new Rectangle(30, 125, 20, 20));
        assertEquals(1, result.messageCount);
        assertEquals(20, result.image.getWidth());
        assertEquals(1, result.transcripts.size());
        assertEquals("di", result.transcripts.get(0).text);
    }

    @Test
    public void nonAdjacentMessagesRemainInSourceOrderWithoutTheMiddleMessage() {
        HtmlViewerCapture source = capture();
        WordReportChatSelection.Result result = WordReportChatSelection.select(source, new boolean[] { true, false, true })
                .crop(new Rectangle(-20, -20, 150, 200));
        assertEquals(2, result.messageCount);
        assertEquals(160, result.image.getHeight());
        assertEquals(source.image.getRGB(50, 210), result.image.getRGB(50, 80));
        assertEquals("audio0", result.transcripts.get(0).text);
        assertEquals("audio2", result.transcripts.get(1).text);
    }

    @Test
    public void imageLinksAndIndividuallyApprovedTranscriptsFollowTheFinalCrop() {
        HtmlViewerCapture capture = capture();
        for (int i = 0; i < 3; i++) {
            capture.messages.get(i).images.add(new HtmlViewerCapture.Attachment("image-" + i,
                    new Rectangle2D.Double(10, i * 100 + 55, 25, 30)));
        }
        WordReportChatSelection selection = WordReportChatSelection.select(capture, new boolean[] { true, true, true });
        WordReportChatSelection.Result crop = selection.crop(new Rectangle(0, 100, 100, 145));
        assertEquals(1, crop.images.size());
        assertEquals("image-1", crop.images.get(0).hash);
        crop.keepTranscripts(Collections.singleton(1));
        assertEquals(1, crop.transcripts.size());
        assertEquals("audio2", crop.transcripts.get(0).text);
        crop.keepTranscripts(Collections.singleton(2));
        assertTrue(crop.transcripts.isEmpty());
    }

    @Test
    public void unrecognizedChatMarkupCanStillBeCroppedToTheVisiblePixels() {
        HtmlViewerCapture capture = capture();
        capture.messages.clear();
        WordReportChatSelection.Result crop = WordReportChatSelection.select(capture, new boolean[0])
                .crop(new Rectangle(20, 100, 30, 40));
        assertEquals(30, crop.image.getWidth());
        assertEquals(capture.image.getRGB(21, 101), crop.image.getRGB(1, 1));
        assertTrue(crop.transcripts.isEmpty());
    }

    @Test
    public void repeatedEmbeddedPhotosOfferOneThumbnailWithoutAnIndexedOriginal() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(capture().image, "png", out);
        String src = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        HtmlViewerCapture.Attachment first = new HtmlViewerCapture.Attachment("", new Rectangle(10, 10, 20, 20), src);
        HtmlViewerCapture.Attachment second = new HtmlViewerCapture.Attachment("", new Rectangle(10, 80, 20, 20), src);
        Map<String, WordReportEvidence.ChatImage> images = WordReportEvidence.findChatImages(null, null,
                Arrays.asList(first, second));
        assertEquals(1, images.size());
        assertNull(images.get(src).id);
        assertEquals(capture().image.getWidth(), images.get(src).thumbnail.getWidth());
    }

    @Test
    public void emptySelectionCannotIncludeAnyTranscription() {
        WordReportChatSelection.Result result = WordReportChatSelection.select(capture(), new boolean[] { false, false, false })
                .crop(new Rectangle(0, 0, 100, 1));
        assertEquals(0, result.messageCount);
        assertTrue(result.transcripts.isEmpty());
    }
}
