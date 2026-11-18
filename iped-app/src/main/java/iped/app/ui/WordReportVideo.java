package iped.app.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import iped.data.IItem;
import iped.engine.config.Configuration;
import iped.engine.config.VideoThumbsConfig;
import iped.engine.task.video.VideoProcessResult;
import iped.engine.task.video.VideoThumbTask;
import iped.engine.task.video.VideoThumbsMaker;
import iped.engine.task.video.VideoThumbsOutputConfig;
import iped.utils.ImageUtil;

/** Reuses IPED video frames, generating a private preview only when needed. */
class WordReportVideo {

    static List<BufferedImage> read(IItem item, Path work) throws Exception {
        List<BufferedImage> frames = null;
        if (item.hasPreview()) {
            try (InputStream in = item.getPreviewSeekeableInputStream()) {
                frames = ImageUtil.getFrames(in);
            } catch (IOException e) {
                // A thumbnail without frame metadata cannot be used as a frame sequence.
            }
        }
        if ((frames == null || frames.isEmpty()) && item.getViewFile() != null && item.getViewFile().isFile()) {
            try {
                frames = ImageUtil.getFrames(item.getViewFile());
            } catch (IOException e) {
                // Try the original video with the same generator used by IPED.
            }
        }
        if (frames == null || frames.size() < 20) {
            Path directory = Files.createTempDirectory(work, "video-");
            VideoThumbsOutputConfig output = new VideoThumbsOutputConfig(directory.resolve("frames.jpg").toFile(), new VideoThumbsConfig(), 2);
            output.setRows(4);
            output.setColumns(5);
            output.setThumbSize(480);
            VideoThumbsMaker maker = new VideoThumbsMaker();
            String root = Configuration.getInstance().appRoot;
            if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
                maker.setMPlayer(Path.of(root, VideoThumbTask.MPLAYER_WIN_PATH).toString());
            } else {
                maker.setMPlayer("mplayer");
            }
            maker.setTimeoutFirstCall(45000);
            maker.setTimeoutInfo(15000);
            maker.setTimeoutProcess(45000);
            try (VideoProcessResult result = maker.createThumbs(item.getTempFile(), directory.toFile(), Collections.singletonList(output), 0)) {
                if (!result.isSuccess()) {
                    throw new IOException(Messages.getString("WordReport.VideoFramesUnavailable"));
                }
                frames = ImageUtil.getFrames(output.getOutFile());
            }
        }
        if (frames == null || frames.isEmpty()) {
            throw new IOException(Messages.getString("WordReport.VideoFramesUnavailable"));
        }
        return sample(frames, 20);
    }

    /** Preserves chronological order and never repeats a frame to fill the grid. */
    static List<BufferedImage> sample(List<BufferedImage> frames, int count) {
        List<BufferedImage> selected = new ArrayList<>();
        int size = Math.min(count, frames.size());
        for (int i = 0; i < size; i++) {
            int index = size == 1 ? 0 : (int) Math.round(i * (frames.size() - 1.0) / (size - 1));
            selected.add(frames.get(index));
        }
        return selected;
    }

    static BufferedImage contactSheet(List<BufferedImage> frames) {
        int columns = Math.min(5, frames.size());
        int rows = (frames.size() + columns - 1) / columns;
        BufferedImage first = frames.get(0);
        int width = Math.min(320, first.getWidth());
        int height = Math.max(1, first.getHeight() * width / first.getWidth());
        BufferedImage sheet = new BufferedImage(columns * (width + 2) + 2, rows * (height + 2) + 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                double scale = Math.min(width / (double) frame.getWidth(), height / (double) frame.getHeight());
                int w = Math.max(1, (int) (frame.getWidth() * scale));
                int h = Math.max(1, (int) (frame.getHeight() * scale));
                int x = 2 + (i % columns) * (width + 2) + (width - w) / 2;
                int y = 2 + (i / columns) * (height + 2) + (height - h) / 2;
                graphics.drawImage(frame, x, y, w, h, null);
            }
        } finally {
            graphics.dispose();
        }
        return sheet;
    }
}
