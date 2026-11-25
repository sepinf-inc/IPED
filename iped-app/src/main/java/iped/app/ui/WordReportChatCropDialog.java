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
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

import javax.swing.SwingWorker;

import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.utils.ImageUtil;
import iped.viewers.HtmlViewerCapture;

/** Review the visible chat pixels before opening the document/insertion screen. */
class WordReportChatCropDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private final HtmlViewerCapture capture;
    private final CropPanel canvas = new CropPanel();
    private final JPanel choices = new JPanel();
    private final Set<Integer> omittedTranscripts = new HashSet<>();
    private Map<String, WordReportEvidence.ChatImage> files = Collections.emptyMap();
    private WordReportEvidence.ChatImage selectedImage;
    private boolean loadingImages;
    private boolean imageSearchFailed;
    private WordReportChatSelection.Result preview;
    private final JLabel count = new JLabel();
    private final JButton accept = new JButton(message("UseChatCrop"));
    private WordReportChatSelection selection;
    private Rectangle crop;
    private WordReportChatSelection.Result result;

    static WordReportChatSelection.Result show(HtmlViewerCapture capture, IPEDMultiSource source, IItemId chat) {
        WordReportChatCropDialog dialog = new WordReportChatCropDialog(capture, false);
        dialog.loadImages(source, chat);
        dialog.setVisible(true);
        return dialog.result;
    }

    static WordReportChatSelection.Result showDocument(HtmlViewerCapture capture) {
        WordReportChatCropDialog dialog = new WordReportChatCropDialog(capture, true);
        dialog.setVisible(true);
        return dialog.result;
    }

    private static String message(String key, Object... arguments) {
        return Messages.getString("WordReport." + key, arguments);
    }

    private WordReportChatCropDialog(HtmlViewerCapture capture, boolean document) {
        super(App.get(), message(document ? "DocumentCropTitle" : "ChatCropTitle"), true);
        this.capture = capture;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1120, screen.width), Math.min(900, screen.height));
        setMinimumSize(new Dimension(Math.min(720, screen.width), Math.min(500, screen.height)));
        setLocationRelativeTo(App.get());
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel(message(document ? "DocumentCropHint" : "ChatCropHint")), BorderLayout.NORTH);
        canvas.setPreferredSize(new Dimension(650, 600));
        canvas.setMinimumSize(new Dimension(240, 200));
        if (document) {
            content.add(canvas, BorderLayout.CENTER);
        } else {
            choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
            JPanel list = new JPanel(new BorderLayout());
            list.add(choices, BorderLayout.PAGE_START);
            JScrollPane text = new JScrollPane(list);
            text.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            JPanel options = new JPanel(new BorderLayout(8, 8));
            options.setMinimumSize(new Dimension(230, 200));
            options.add(text, BorderLayout.CENTER);
            options.add(count, BorderLayout.SOUTH);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, options);
            split.setResizeWeight(0.72);
            content.add(split, BorderLayout.CENTER);
        }
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
            preview.keepTranscripts(omittedTranscripts);
            preview.attachment = selectedImage;
            result = preview;
            dispose();
        });
        actions.add(accept);
        content.add(actions, BorderLayout.SOUTH);
        add(content);
        if (document) {
            selection = WordReportChatSelection.document(capture.image);
            resetCrop();
        } else {
            rebuild();
        }
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
        preview = selection.crop(crop);
        if (preview.document) {
            canvas.repaint();
            return;
        }
        choices.removeAll();
        JPanel transcripts = group("ChatCropTranscripts");
        if (preview.transcripts.isEmpty()) {
            transcripts.add(new JLabel(message("ChatCropNoTranscript")));
        }
        for (int i = 0; i < preview.transcripts.size(); i++) {
            int index = preview.transcriptIndices.get(i);
            JCheckBox include = new JCheckBox(message("ChatAudioNumber", index + 1), !omittedTranscripts.contains(index));
            include.addActionListener(e -> {
                if (include.isSelected()) {
                    omittedTranscripts.remove(index);
                } else {
                    omittedTranscripts.add(index);
                }
            });
            JPanel option = new JPanel(new BorderLayout());
            option.add(include, BorderLayout.NORTH);
            option.add(detail(preview.transcripts.get(i).text), BorderLayout.CENTER);
            transcripts.add(option);
        }
        if (!preview.images.isEmpty()) {
            JPanel images = group("ChatSideImage");
            ButtonGroup radios = new ButtonGroup();
            JRadioButton none = new JRadioButton(message("ChatNoSideImage"), true);
            none.addActionListener(e -> selectedImage = null);
            radios.add(none);
            images.add(none);
            Set<WordReportEvidence.ChatImage> visible = new HashSet<>();
            for (HtmlViewerCapture.Attachment image : preview.images) {
                WordReportEvidence.ChatImage file = files.get(image.key());
                if (file == null || !visible.add(file)) {
                    continue;
                }
                JRadioButton radio = new JRadioButton();
                radio.getAccessibleContext().setAccessibleName(file.name);
                radio.getAccessibleContext().setAccessibleDescription(file.path);
                radio.addActionListener(e -> selectedImage = file);
                radios.add(radio);
                BufferedImage thumbnail = file.thumbnail;
                ImageIcon icon = new ImageIcon(ImageUtil.resizeImage(thumbnail,
                        Math.min(144, thumbnail.getWidth()), Math.min(96, thumbnail.getHeight())));
                JButton picture = new JButton(icon);
                if (file.id == null) {
                    picture.setToolTipText(message("ChatEmbeddedImage"));
                    radio.setText(message("ChatEmbeddedImage"));
                }
                picture.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
                picture.setContentAreaFilled(false);
                picture.setFocusable(false);
                picture.getAccessibleContext().setAccessibleName(file.name);
                picture.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                picture.addActionListener(e -> radio.doClick(0));
                JPanel option = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 2));
                option.add(radio);
                option.add(picture);
                option.setMaximumSize(new Dimension(Integer.MAX_VALUE, option.getPreferredSize().height));
                images.add(option);
                if (file == selectedImage) {
                    radio.setSelected(true);
                }
            }
            if (!visible.contains(selectedImage)) {
                selectedImage = null;
            }
            if (visible.isEmpty()) {
                images.add(detail(message(loadingImages ? "ChatImagesLoading"
                        : imageSearchFailed ? "ChatImageSearchFailed" : "ChatNoOriginalImage")));
            }
        } else {
            selectedImage = null;
        }
        choices.revalidate();
        choices.repaint();
        count.setText(capture.messages.isEmpty() ? message("ChatVisualCrop")
                : message("ChatCropCount", preview.messageCount, capture.messages.size()));
        accept.setEnabled(preview.messageCount > 0 || capture.messages.isEmpty());
        canvas.repaint();
    }

    private JPanel group(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(message(title)));
        choices.add(panel);
        return panel;
    }

    private JTextArea detail(String value) {
        JTextArea field = new JTextArea(value, 0, 18);
        field.setEditable(false);
        field.setLineWrap(true);
        field.setWrapStyleWord(true);
        field.setOpaque(false);
        return field;
    }

    private void loadImages(IPEDMultiSource source, IItemId chat) {
        if (selection.images.isEmpty()) {
            return;
        }
        loadingImages = true;
        updateCrop();
        new SwingWorker<Map<String, WordReportEvidence.ChatImage>, Void>() {
            @Override
            protected Map<String, WordReportEvidence.ChatImage> doInBackground() throws Exception {
                return WordReportEvidence.findChatImages(source, chat, selection.images);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) {
                    return;
                }
                loadingImages = false;
                try {
                    files = get();
                } catch (Exception e) {
                    imageSearchFailed = true;
                }
                updateCrop();
            }
        }.execute();
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
