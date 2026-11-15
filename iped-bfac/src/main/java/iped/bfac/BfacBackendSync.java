package iped.bfac;

import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import iped.bfac.api.BfacApiClient;
import iped.bfac.api.Category;
import iped.bfac.api.Submission;
import iped.bfac.api.ValidationResult;

/**
 * Background synchronization with the BFAC backend: session validation,
 * categories, and open submissions.
 */
public class BfacBackendSync {

    public interface Listener {
        void onSessionValid();
        void onSessionExpired();
        void onConnectionError(String detailMessage);
        void onCategoriesLoaded(List<Category> categories);
        void onSubmissionsLoadStarted();
        void onSubmissionsLoadFinished();
        void onSubmissionsLoaded(List<Submission> submissions);
    }

    private final BfacApiClient apiClient;
    private Listener listener;
    private SwingWorker<ValidationResult, Void> sessionValidationWorker;

    public BfacBackendSync(BfacApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void validateSession() {
        if (sessionValidationWorker != null && !sessionValidationWorker.isDone()) {
            return;
        }

        sessionValidationWorker = new SwingWorker<ValidationResult, Void>() {
            @Override
            protected ValidationResult doInBackground() {
                return apiClient.validateSession();
            }

            @Override
            protected void done() {
                try {
                    ValidationResult result = get();
                    if (result.isValid()) {
                        notifySessionValid();
                        loadCategories();
                        loadOpenSubmissions();
                    } else if (result.getStatusCode() == 401) {
                        notifySessionExpired();
                    } else if (result.getStatusCode() == -1 || apiClient.wasLastCallConnectionError()) {
                        notifyConnectionError(result.getMessage());
                    } else {
                        notifySessionValid();
                        loadCategories();
                        loadOpenSubmissions();
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    notifyConnectionError(cause.getMessage());
                }
            }
        };
        sessionValidationWorker.execute();
    }

    public void loadCategories() {
        new SwingWorker<List<Category>, Void>() {
            @Override
            protected List<Category> doInBackground() {
                return apiClient.getCategories();
            }

            @Override
            protected void done() {
                try {
                    if (apiClient.wasLastCallUnauthorized()) {
                        notifySessionExpired();
                        return;
                    }
                    if (apiClient.wasLastCallConnectionError()) {
                        notifyConnectionError(null);
                        return;
                    }
                    notifyCategoriesLoaded(get());
                } catch (Exception e) {
                    if (apiClient.wasLastCallConnectionError()) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        notifyConnectionError(cause.getMessage());
                    }
                }
            }
        }.execute();
    }

    public void loadOpenSubmissions() {
        notifySubmissionsLoadStarted();

        new SwingWorker<List<Submission>, Void>() {
            @Override
            protected List<Submission> doInBackground() {
                return apiClient.getOpenSubmissions();
            }

            @Override
            protected void done() {
                try {
                    if (apiClient.wasLastCallUnauthorized()) {
                        notifySessionExpired();
                        return;
                    }
                    if (apiClient.wasLastCallConnectionError()) {
                        notifyConnectionError(null);
                        return;
                    }
                    notifySubmissionsLoaded(get());
                } catch (Exception e) {
                    if (apiClient.wasLastCallConnectionError()) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        notifyConnectionError(cause.getMessage());
                    }
                } finally {
                    notifySubmissionsLoadFinished();
                }
            }
        }.execute();
    }

    private void notifySessionValid() {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onSessionValid());
        }
    }

    private void notifySessionExpired() {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onSessionExpired());
        }
    }

    private void notifyConnectionError(String detailMessage) {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onConnectionError(detailMessage));
        }
    }

    private void notifyCategoriesLoaded(List<Category> categories) {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onCategoriesLoaded(categories));
        }
    }

    private void notifySubmissionsLoadStarted() {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onSubmissionsLoadStarted());
        }
    }

    private void notifySubmissionsLoadFinished() {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onSubmissionsLoadFinished());
        }
    }

    private void notifySubmissionsLoaded(List<Submission> submissions) {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onSubmissionsLoaded(submissions));
        }
    }
}
