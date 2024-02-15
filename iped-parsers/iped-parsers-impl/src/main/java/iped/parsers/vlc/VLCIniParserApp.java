package iped.parsers.vlc;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class VLCIniParserApp extends Application {

    private ListView<String> recentFilesListView;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        setupUI(primaryStage);
    }

    private void setupUI(Stage primaryStage) {
        primaryStage.setTitle("VLC INI Parser");

        recentFilesListView = new ListView<>();

        Button loadButton = createLoadButton();

        VBox layout = createLayout();
        layout.getChildren().addAll(recentFilesListView, loadButton);

        Scene scene = new Scene(layout, 900, 300);
        primaryStage.setScene(scene);

        primaryStage.show();
    }

    private Button createLoadButton() {
        Button loadButton = new Button("Load File");
        loadButton.setOnAction(e -> loadFile());
        return loadButton;
    }

    private VBox createLayout() {
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(10, 10, 10, 10));
        return layout;
    }

    private void loadFile() {
        File selectedFile = showFileChooser();
        if (selectedFile != null) {
            try {
                List<String> recentFiles = VLCIniParser.parseRecentFiles(selectedFile.getAbsolutePath());
                updateListView(recentFiles);
            } catch (IOException e) {
                handleException(e);
            }
        }
    }

    private File showFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose your file:");
        return fileChooser.showOpenDialog(null);
    }

    private void updateListView(List<String> recentFiles) {
        recentFilesListView.getItems().clear();
        if (recentFiles != null && !recentFiles.isEmpty()) {
            recentFilesListView.getItems().addAll(recentFiles);
        }
    }

    private void handleException(Exception e) {
        e.printStackTrace();

        // Lógica para exibir uma mensagem de erro ao usuário, se necessário.
        String errorMessage = "Ocorreu um erro. Detalhes: " + e.getMessage();

        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(null);
        alert.setContentText(errorMessage);
        alert.showAndWait();
    }
}
