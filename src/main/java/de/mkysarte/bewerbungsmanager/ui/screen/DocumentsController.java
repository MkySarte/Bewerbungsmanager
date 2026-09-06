package de.mkysarte.bewerbungsmanager.ui.screen;

import de.mkysarte.bewerbungsmanager.document.dto.DocumentMetaResponse;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentScope;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentType;
import de.mkysarte.bewerbungsmanager.document.service.DocumentService;
import de.mkysarte.bewerbungsmanager.ui.SpringFXMLLoader;
import de.mkysarte.bewerbungsmanager.ui.util.AsyncRunner;
import de.mkysarte.bewerbungsmanager.ui.util.OverlayAction;
import de.mkysarte.bewerbungsmanager.ui.util.OverlayRequest;
import javafx.beans.property.SimpleStringProperty;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.List;

/**
 * Controller für die Dokumentenverwaltung.
 *
 * Unterstützt Upload, Download und Löschen von globalen Dokumenten
 * (Lebenslauf, Zertifikate, Zeugnisse, Anschreiben).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentsController {

    private final DocumentService documentService;
    private final SpringFXMLLoader fxmlLoader;

    @FXML private TableView<DocumentMetaResponse> documentsTable;
    @FXML private TableColumn<DocumentMetaResponse, String> colName;
    @FXML private TableColumn<DocumentMetaResponse, String> colType;
    @FXML private TableColumn<DocumentMetaResponse, String> colScope;
    @FXML private TableColumn<DocumentMetaResponse, String> colSize;
    @FXML private TableColumn<DocumentMetaResponse, String> colDate;
    @FXML private ComboBox<String> typeFilter;
    @FXML private ComboBox<String> scopeFilter;
    @FXML private Label statusLabel;
    private Runnable onClose;
    private Runnable reopenDocuments;
    private BiConsumer<Long, String> previewHandler;
    private Consumer<OverlayRequest> overlayPresenter;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @FXML
    public void initialize() {
        // Spalten konfigurieren
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fileName()));
        colType.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().type() != null ? d.getValue().type().name() : ""));
        colScope.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().scope() != null ? d.getValue().scope().name() : ""));
        colSize.setCellValueFactory(d -> new SimpleStringProperty(
                formatSize(d.getValue().sizeBytes())));
        colDate.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().createdAt() != null ? d.getValue().createdAt().format(DATE_FMT) : ""));

        // Filter-Optionen
        typeFilter.setItems(FXCollections.observableArrayList(
                "Alle", "LEBENSLAUF", "ZERTIFIKATE", "ZEUGNISSE", "ANSCHREIBEN"));
        scopeFilter.setItems(FXCollections.observableArrayList("Alle", "GLOBAL", "APPLICATION"));

        documentsTable.setRowFactory(table -> {
            TableRow<DocumentMetaResponse> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    previewDocument(row.getItem());
                }
            });
            return row;
        });

        loadDocuments();
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setReopenDocuments(Runnable reopenDocuments) {
        this.reopenDocuments = reopenDocuments;
    }

    public void setOverlayPresenter(Consumer<OverlayRequest> overlayPresenter) {
        this.overlayPresenter = overlayPresenter;
    }

    public void setPreviewHandler(BiConsumer<Long, String> previewHandler) {
        this.previewHandler = previewHandler;
    }

    private void loadDocuments() {
        AsyncRunner.run(
                () -> documentService.list(null, null, null),
                docs -> {
                    documentsTable.setItems(FXCollections.observableArrayList(docs));
                    statusLabel.setText(docs.size() + " Dokument(e)");
                },
                error -> {
                    log.error("Fehler beim Laden der Dokumente", error);
                    statusLabel.setText("Fehler beim Laden");
                }
        );
    }

    @FXML
    public void handleFilter() {
        String selectedType = typeFilter.getValue();
        String selectedScope = scopeFilter.getValue();

        DocumentType type = (selectedType == null || selectedType.equals("Alle"))
                ? null : DocumentType.valueOf(selectedType);
        DocumentScope scope = (selectedScope == null || selectedScope.equals("Alle"))
                ? null : DocumentScope.valueOf(selectedScope);

        AsyncRunner.run(
                () -> documentService.list(scope, type, null),
                docs -> documentsTable.setItems(FXCollections.observableArrayList(docs)),
                error -> log.error("Filter-Fehler", error)
        );
    }

    @FXML
    public void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Dokument auswählen (PDF)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));

        File file = fileChooser.showOpenDialog(documentsTable.getScene().getWindow());
        if (file == null) return;

        ComboBox<String> typeSelect = new ComboBox<>(FXCollections.observableArrayList(
                "LEBENSLAUF", "ZERTIFIKATE", "ZEUGNISSE", "ANSCHREIBEN"));
        typeSelect.setValue("LEBENSLAUF");
        typeSelect.setMaxWidth(Double.MAX_VALUE);
        typeSelect.getStyleClass().add("form-input");

        Label inlineError = new Label();
        inlineError.getStyleClass().add("error-label");
        inlineError.setVisible(false);
        inlineError.setManaged(false);

        VBox form = new VBox(10,
                new Label("Dokumenttyp"),
                typeSelect,
                inlineError
        );
        form.getStyleClass().add("overlay-form-stack");

        if (overlayPresenter != null && reopenDocuments != null) {
            overlayPresenter.accept(OverlayRequest.builder()
                    .title("Dokument hochladen")
                    .subtitle("Waehlen Sie den Typ für \"" + file.getName() + "\".")
                    .customContent(form)
                    .actions(List.of(
                            OverlayAction.ghost("Abbrechen", reopenDocuments),
                            OverlayAction.primary("Hochladen", () -> {
                                String selectedType = typeSelect.getValue();
                                if (selectedType == null || selectedType.isBlank()) {
                                    inlineError.setText("Bitte zuerst einen Dokumenttyp wählen.");
                                    inlineError.setManaged(true);
                                    inlineError.setVisible(true);
                                    return;
                                }

                                DocumentType docType = DocumentType.valueOf(selectedType);
                                AsyncRunner.run(
                                        () -> {
                                            try {
                                                byte[] content = Files.readAllBytes(file.toPath());
                                                return documentService.upload(
                                                        content,
                                                        file.getName(),
                                                        "application/pdf",
                                                        docType,
                                                        DocumentScope.GLOBAL,
                                                        null
                                                );
                                            } catch (IOException e) {
                                                throw new RuntimeException("Datei konnte nicht gelesen werden: " + e.getMessage(), e);
                                            }
                                        },
                                        result -> {
                                            reopenDocuments.run();
                                            Platform.runLater(() -> statusLabel.setText("Dokument hochgeladen: " + result.fileName()));
                                        },
                                        error -> {
                                            log.error("Upload-Fehler", error);
                                            showDocumentsError("Upload fehlgeschlagen", "Das Dokument konnte nicht hochgeladen werden.", error.getMessage());
                                        }
                                );
                            }, false)
                    ))
                    .build());
            return;
        }

        statusLabel.setText("Bitte Dokumenttyp wählen: " + file.getName());
    }

    @FXML
    public void handlePreview() {
        DocumentMetaResponse selected = documentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showDocumentsNotice(
                    "Kein Dokument ausgewaehlt",
                    "Bitte wählen Sie zuerst ein Dokument aus.",
                    "Ohne Auswahl kann keine Vorschau geöffnet werden."
            );
            return;
        }
        previewDocument(selected);
    }

    @FXML
    public void handleDownload() {
        DocumentMetaResponse selected = documentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showDocumentsNotice(
                    "Kein Dokument ausgewaehlt",
                    "Bitte wählen Sie zuerst ein Dokument aus.",
                    "Ohne Auswahl kann kein Dokument heruntergeladen werden."
            );
            return;
        }

        AsyncRunner.run(
                () -> documentService.download(selected.documentId()),
                download -> {
                    try {
                        FileChooser chooser = new FileChooser();
                        chooser.setTitle("Dokument speichern");
                        chooser.setInitialFileName(download.fileName());
                        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));
                        File target = chooser.showSaveDialog(documentsTable.getScene().getWindow());
                        if (target == null) {
                            return;
                        }
                        Files.write(target.toPath(), download.content());
                        statusLabel.setText("Gespeichert: " + target.getName());
                    } catch (IOException e) {
                        log.error("Download-Fehler", e);
                        showDocumentsError("Speichern fehlgeschlagen", "Das Dokument konnte nicht gespeichert werden.", e.getMessage());
                    }
                },
                error -> {
                    log.error("Download-Fehler", error);
                    showDocumentsError("Download fehlgeschlagen", "Das Dokument konnte nicht heruntergeladen werden.", error.getMessage());
                }
        );
    }

    @FXML
    public void handleDelete() {
        DocumentMetaResponse selected = documentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showDocumentsNotice(
                    "Kein Dokument ausgewaehlt",
                    "Bitte wählen Sie zuerst ein Dokument aus.",
                    "Ohne Auswahl kann nichts gelöscht werden."
            );
            return;
        }

        if (overlayPresenter == null || reopenDocuments == null) {
            showDocumentsError("Löschbestätigung nicht verfügbar", "Die Löschbestätigung konnte nicht geöffnet werden.", null);
            return;
        }

        overlayPresenter.accept(OverlayRequest.danger(
                "Dokument löschen",
                "Dokument \"" + selected.fileName() + "\" wirklich löschen?",
                "Diese Aktion kann nicht rückgängig gemacht werden.",
                List.of(
                        OverlayAction.ghost("Abbrechen", reopenDocuments),
                        OverlayAction.danger("Dokument löschen", () -> AsyncRunner.run(
                                () -> { documentService.delete(selected.documentId()); return null; },
                                ignored -> reopenDocuments.run(),
                                error -> {
                                    log.error("Lösch-Fehler", error);
                                    showDocumentsError("Löschen fehlgeschlagen", "Das Dokument konnte nicht gelöscht werden.", error.getMessage());
                                }
                        ))
                )
        ));
    }

    private void showDocumentsNotice(String title, String subtitle, String warning) {
        if (overlayPresenter != null && reopenDocuments != null) {
            overlayPresenter.accept(OverlayRequest.info(
                    title,
                    subtitle,
                    warning,
                    OverlayAction.primary("Verstanden", reopenDocuments)
            ));
            return;
        }
        statusLabel.setText(subtitle);
    }

    private void showDocumentsError(String title, String subtitle, String details) {
        if (overlayPresenter != null && reopenDocuments != null) {
            String message = details == null || details.isBlank() ? subtitle : subtitle + "\n\n" + details;
            overlayPresenter.accept(OverlayRequest.danger(
                    title,
                    subtitle,
                    message,
                    List.of(OverlayAction.primary("Zurück", reopenDocuments))
            ));
            return;
        }
        statusLabel.setText(title + ": " + subtitle);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @FXML
    public void handleBack() {
        if (onClose != null) {
            onClose.run();
            return;
        }
    }

    private void previewDocument(DocumentMetaResponse selected) {
        if (previewHandler != null) {
            previewHandler.accept(selected.documentId(), selected.fileName());
            return;
        }
        AsyncRunner.run(
                () -> documentService.download(selected.documentId()),
                download -> {
                    try {
                        File tempFile = File.createTempFile("bewerbungsmanager_", "_" + download.fileName());
                        tempFile.deleteOnExit();
                        Files.write(tempFile.toPath(), download.content());

                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(tempFile);
                        }
                        statusLabel.setText("Geoeffnet: " + download.fileName());
                    } catch (IOException e) {
                        log.error("Preview-Fehler", e);
                        showDocumentsError("Vorschau fehlgeschlagen", "Das Dokument konnte nicht geöffnet werden.", e.getMessage());
                    }
                },
                error -> {
                    log.error("Preview-Fehler", error);
                    showDocumentsError("Vorschau fehlgeschlagen", "Die Vorschau konnte nicht geladen werden.", error.getMessage());
                }
        );
    }
}
