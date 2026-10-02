package iped.bfac.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.LoginResult;
import iped.bfac.localization.Messages;

/**
 * Login card for BFAC: server URL display, credentials, and background login.
 */
public class LoginPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public interface Listener {
        void onLoginSuccess(String username);
    }

    private final BfacApiClient apiClient;
    private final JLabel serverUrlLabel;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JButton loginButton;
    private final JLabel loginStatusLabel;

    private Listener listener;

    public LoginPanel(BfacApiClient apiClient) {
        super(new GridBagLayout());
        this.apiClient = apiClient;
        setBorder(new EmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel(Messages.getString("BfacDialog.LoginTitle"), SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 20, 0);
        add(titleLabel, gbc);

        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        add(new JLabel(Messages.getString("BfacDialog.ServerUrl")), gbc);

        serverUrlLabel = new JLabel(apiClient.getBaseUrl());
        serverUrlLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        serverUrlLabel.setForeground(Color.DARK_GRAY);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        add(serverUrlLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        add(new JLabel(Messages.getString("BfacDialog.Username")), gbc);

        usernameField = new JTextField(25);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        add(new JLabel(Messages.getString("BfacDialog.Password")), gbc);

        passwordField = new JPasswordField(25);
        passwordField.addActionListener(e -> onLogin());
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        add(passwordField, gbc);

        loginButton = new JButton(Messages.getString("BfacDialog.Login"));
        loginButton.addActionListener(e -> onLogin());
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(20, 5, 5, 5);
        add(loginButton, gbc);

        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setForeground(Color.RED);
        gbc.gridy = 5;
        gbc.insets = new Insets(10, 5, 5, 5);
        add(loginStatusLabel, gbc);

        String storedUsername = apiClient.getStoredUsername();
        if (storedUsername != null && !storedUsername.isEmpty()) {
            usernameField.setText(storedUsername);
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setServerUrl(String url) {
        serverUrlLabel.setText(url);
    }

    public void setUsername(String username) {
        usernameField.setText(username);
    }

    public void clearPassword() {
        passwordField.setText("");
    }

    public void showSessionExpiredMessage() {
        loginStatusLabel.setForeground(Color.ORANGE);
        loginStatusLabel.setText(Messages.getString("BfacDialog.SessionExpiredLoginAgain"));
    }

    public void resetStatus() {
        loginStatusLabel.setText(" ");
        loginStatusLabel.setForeground(Color.RED);
    }

    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            loginStatusLabel.setForeground(Color.RED);
            loginStatusLabel.setText(Messages.getString("BfacDialog.EnterCredentials"));
            return;
        }

        loginButton.setEnabled(false);
        usernameField.setEnabled(false);
        passwordField.setEnabled(false);
        java.awt.Component root = getTopLevelAncestor();
        if (root != null) {
            root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        loginStatusLabel.setForeground(Color.BLUE);
        loginStatusLabel.setText(Messages.getString("BfacDialog.LoggingIn"));

        new SwingWorker<LoginResult, Void>() {
            @Override
            protected LoginResult doInBackground() {
                return apiClient.login(username, password);
            }

            @Override
            protected void done() {
                try {
                    LoginResult result = get();
                    if (result.isSuccess()) {
                        passwordField.setText("");
                        loginStatusLabel.setText(" ");
                        if (listener != null) {
                            listener.onLoginSuccess(username);
                        }
                    } else {
                        loginStatusLabel.setForeground(Color.RED);
                        loginStatusLabel.setText(result.getMessage());
                    }
                } catch (Exception e) {
                    loginStatusLabel.setForeground(Color.RED);
                    loginStatusLabel.setText(Messages.getString("BfacDialog.Error", e.getMessage()));
                } finally {
                    loginButton.setEnabled(true);
                    usernameField.setEnabled(true);
                    passwordField.setEnabled(true);
                    if (root != null) {
                        root.setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        }.execute();
    }
}
