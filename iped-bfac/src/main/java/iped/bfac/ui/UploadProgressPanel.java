package iped.bfac.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import iped.bfac.localization.Messages;

/**
 * Progress panel for BFAC upload operations: percent bar, elapsed time,
 * byte counts, timestamped log, and Cancel/Close controls.
 */
public class UploadProgressPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter LOG_TIMESTAMP =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    public interface Listener {
        void onCancel();
        void onClose();
    }

    private final JProgressBar progressBar;
    private final JLabel elapsedLabel;
    private final JLabel bytesLabel;
    private final JTextArea logArea;
    private final JButton cancelButton;
    private final JButton closeButton;

    private Listener listener;
    private Timer elapsedTimer;
    private Instant operationStart;

    public UploadProgressPanel() {
        super(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel(Messages.getString("BfacDialog.UploadProgress"), SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setPreferredSize(new Dimension(500, 25));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        centerPanel.add(progressBar);
        centerPanel.add(Box.createVerticalStrut(8));

        JPanel statusPanel = new JPanel(new GridLayout(1, 2));
        elapsedLabel = new JLabel(Messages.getString("BfacDialog.ElapsedTime", "00:00:00"));
        bytesLabel = new JLabel(Messages.getString("BfacDialog.UploadBytesUnknown"), SwingConstants.RIGHT);
        statusPanel.add(elapsedLabel);
        statusPanel.add(bytesLabel);
        centerPanel.add(statusPanel);
        centerPanel.add(Box.createVerticalStrut(8));

        logArea = new JTextArea(12, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder(Messages.getString("BfacDialog.Log")));
        centerPanel.add(logScrollPane);

        add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cancelButton = new JButton(Messages.getString("BfacDialog.Cancel"));
        cancelButton.addActionListener(e -> {
            if (listener != null) {
                listener.onCancel();
            }
        });
        bottomPanel.add(cancelButton);

        closeButton = new JButton(Messages.getString("BfacDialog.Close"));
        closeButton.setEnabled(false);
        closeButton.addActionListener(e -> {
            if (listener != null) {
                listener.onClose();
            }
        });
        bottomPanel.add(closeButton);

        add(bottomPanel, BorderLayout.SOUTH);

        reset();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void reset() {
        stopTracking();
        logArea.setText("");
        setProgress(0);
        elapsedLabel.setText(Messages.getString("BfacDialog.ElapsedTime", "00:00:00"));
        bytesLabel.setText(Messages.getString("BfacDialog.UploadBytesUnknown"));
        setCancelEnabled(true);
        setCloseEnabled(false);
    }

    public void startTracking() {
        stopTracking();
        operationStart = Instant.now();
        updateElapsedLabel();
        elapsedTimer = new Timer(1000, e -> updateElapsedLabel());
        elapsedTimer.start();
    }

    public void stopTracking() {
        if (elapsedTimer != null) {
            elapsedTimer.stop();
            elapsedTimer = null;
        }
        if (operationStart != null) {
            updateElapsedLabel();
        }
    }

    public void appendLog(String message) {
        if (message == null || message.isEmpty()) {
            logArea.append("\n");
        } else {
            logArea.append(LOG_TIMESTAMP.format(Instant.now()) + " " + message + "\n");
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public void setProgress(int percent) {
        int value = Math.max(0, Math.min(percent, 100));
        progressBar.setValue(value);
        progressBar.setString(value + "%");
    }

    public void setUploadBytes(long uploaded, long total) {
        bytesLabel.setText(Messages.getString("BfacDialog.UploadBytes",
                formatBytes(uploaded), formatBytes(total)));
    }

    public void setCancelEnabled(boolean enabled) {
        cancelButton.setEnabled(enabled);
    }

    public void setCloseEnabled(boolean enabled) {
        closeButton.setEnabled(enabled);
    }

    private void updateElapsedLabel() {
        if (operationStart == null) {
            elapsedLabel.setText(Messages.getString("BfacDialog.ElapsedTime", "00:00:00"));
            return;
        }
        Duration duration = Duration.between(operationStart, Instant.now());
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        elapsedLabel.setText(Messages.getString("BfacDialog.ElapsedTime",
                String.format("%02d:%02d:%02d", hours, minutes, secs)));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
