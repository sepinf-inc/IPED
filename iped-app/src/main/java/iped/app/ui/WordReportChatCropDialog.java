package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

import iped.viewers.HtmlViewerCapture;

/** Review the visible chat pixels before opening the document/insertion screen. */
class WordReportChatCropDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private final HtmlViewerCapture capture;
    private final CropPanel canvas = new CropPanel();
    private final JTextArea transcription = new JTextArea();
    private final JLabel count = new JLabel();
    private final JButton accept = new JButton(message("UseChatCrop"));
    private WordReportChatSelection selection;
    private Rectangle crop;
    private WordReportChatSelection.Result result;

    static WordReportChatSelection.Result show(HtmlViewerCapture capture) {
        WordReportChatCropDialog dialog = new WordReportChatCropDialog(capture);
        dialog.setVisible(true);
        return dialog.result;
    }

    private static String message(String key, Object... arguments) {
        return Messages.getString("WordReport." + key, arguments);
    }

    private WordReportChatCropDialog(HtmlViewerCapture capture) {
        super(App.get(), message("ChatCropTitle"), true);
        this.capture = capture;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1120, screen.width), Math.min(900, screen.height));
        setMinimumSize(new Dimension(Math.min(720, screen.width), Math.min(500, screen.height)));
        setLocationRelativeTo(App.get());
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel(message("ChatCropHint")), BorderLayout.NORTH);
        canvas.setPreferredSize(new Dimension(650, 600));
        canvas.setMinimumSize(new Dimension(240, 200));
        transcription.setEditable(false);
        transcription.setLineWrap(true);
        transcription.setWrapStyleWord(true);
        JScrollPane text = new JScrollPane(transcription);
        text.setBorder(BorderFactory.createTitledBorder(message("ChatCropTranscripts")));
        JPanel options = new JPanel(new BorderLayout(8, 8));
        options.setMinimumSize(new Dimension(230, 200));
        options.add(text, BorderLayout.CENTER);
        options.add(count, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, options);
        split.setResizeWeight(0.72);
        content.add(split, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton normal = new JButton(message("ChatCropNormal"));
        normal.addActionListener(e -> resetCrop());
        actions.add(normal);
        JButton reset = new JButton(message("ChatCropReset"));
        reset.addActionListener(e -> resetCrop());
        actions.add(reset);
        JButton cancel = new JButton(message("ChatCropCancel"));
        cancel.addActionListener(e -> dispose());
        actions.add(cancel);
        accept.addActionListener(e -> {
            result = selection.crop(crop);
            dispose();
        });
        actions.add(accept);
        content.add(actions, BorderLayout.SOUTH);
        add(content);
        rebuild();
    }

    private void rebuild() {
        boolean[] selected = new boolean[capture.messages.size()];
        Arrays.fill(selected, true);
        selection = WordReportChatSelection.select(capture, selected);
        resetCrop();
    }

    private void resetCrop() {
        crop = new Rectangle(0, 0, selection.image.getWidth(), selection.image.getHeight());
        updateCrop();
    }

    private void updateCrop() {
        WordReportChatSelection.Result preview = selection.crop(crop);
        StringBuilder text = new StringBuilder();
        for (WordReportDocument.Evidence audio : preview.transcripts) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(audio.text);
            if (audio.metadata.containsKey("Hash")) {
                text.append("\nHash: ").append(audio.metadata.get("Hash"));
            }
        }
        transcription.setText(text.length() == 0 ? message("ChatCropNoTranscript") : text.toString());
        transcription.setCaretPosition(0);
        count.setText(message("ChatCropCount", preview.messageCount, capture.messages.size()));
        accept.setEnabled(preview.messageCount > 0);
        canvas.repaint();
    }

    private class CropPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private Point start;
        private double scale;
        private int left;
        private int top;

        CropPanel() {
            setBackground(Color.DARK_GRAY);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    start = position(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (start != null) {
                        Point end = position(e);
                        crop = new Rectangle(Math.min(start.x, end.x), Math.min(start.y, end.y),
                                Math.max(1, Math.abs(end.x - start.x)), Math.max(1, Math.abs(end.y - start.y)));
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (start != null) {
                        if (crop.width < 3 || crop.height < 3) {
                            resetCrop();
                        } else {
                            updateCrop();
                        }
                        start = null;
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private Point position(MouseEvent e) {
            return new Point(Math.max(0, Math.min(selection.image.getWidth(), (int) ((e.getX() - left) / scale))),
                    Math.max(0, Math.min(selection.image.getHeight(), (int) ((e.getY() - top) / scale))));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (selection == null) {
                return;
            }
            scale = Math.min((getWidth() - 16.0) / selection.image.getWidth(), (getHeight() - 16.0) / selection.image.getHeight());
            scale = Math.max(0.001, scale);
            int width = (int) (selection.image.getWidth() * scale);
            int height = (int) (selection.image.getHeight() * scale);
            left = (getWidth() - width) / 2;
            top = (getHeight() - height) / 2;
            Graphics2D graphics = (Graphics2D) g.create();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(selection.image, left, top, width, height, null);
                int x = left + (int) (crop.x * scale);
                int y = top + (int) (crop.y * scale);
                int w = (int) (crop.width * scale);
                int h = (int) (crop.height * scale);
                graphics.setColor(new Color(0, 0, 0, 130));
                graphics.fillRect(left, top, width, y - top);
                graphics.fillRect(left, y + h, width, top + height - y - h);
                graphics.fillRect(left, y, x - left, h);
                graphics.fillRect(x + w, y, left + width - x - w, h);
                graphics.setColor(Color.YELLOW);
                graphics.drawRect(x, y, w, h);
            } finally {
                graphics.dispose();
            }
        }
    }
}
