package de.mkysarte.bewerbungsmanager.ui.screen;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.CreateBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.PatchBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.service.BewerbungseintragService;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentScope;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentType;
import de.mkysarte.bewerbungsmanager.document.service.DocumentService;
import de.mkysarte.bewerbungsmanager.document.service.PdfBundleService;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
import de.mkysarte.bewerbungsmanager.ui.util.AsyncRunner;
import de.mkysarte.bewerbungsmanager.ui.util.OverlayAction;
import de.mkysarte.bewerbungsmanager.ui.util.OverlayRequest;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller für das Bewerbungs-Formular (Anlegen und Bearbeiten).
 */
@Slf4j
@Component
@org.springframework.context.annotation.Scope("prototype")
@RequiredArgsConstructor
public class ApplicationFormController {

    private final BewerbungseintragService bewerbungseintragService;
    private final StatusRepository statusRepository;
    private final DocumentService documentService;
    private final PdfBundleService pdfBundleService;
    private final EinstellungService einstellungService;

    // ── Sektion 1: Unternehmensinformationen ──
    @FXML private TextField firmaField;
    @FXML private TextField urlField;
    @FXML private TextField ansprechpartnerField;
    @FXML private TextField emailField;
    @FXML private TextField telefonField;
    @FXML private ComboBox<String> applicationTypeComboBox;
    @FXML private TextField ortField;
    @FXML private TextField stelleField;
    @FXML private Label errorLabel;
    @FXML private Label formTitleLabel;
    @FXML private Label formSubtitleLabel;

    // ── Sektion 2: Unterlagen-Checkliste ──
    @FXML private CheckBox lebenslaufCheck;
    @FXML private CheckBox anschreibenCheck;
    @FXML private CheckBox zertifikateCheck;
    @FXML private CheckBox zeugnisseCheck;
    @FXML private CheckBox pdfBundleCheck;
    @FXML private Button bundleDownloadBtn;
    @FXML private VBox anschreibenPanel;
    @FXML private FontIcon anschreibenIcon;
    @FXML private Label anschreibenLabel;
    @FXML private Button anschreibenPickBtn;
    @FXML private Button anschreibenDownloadBtn;

    // ── Sektion 3: Status & Rückmeldung ──
    @FXML private ComboBox<StatusItem> statusComboBox;
    @FXML private TextArea notizField;

    // ── Aktionen ──
    @FXML private Button deleteButton;
    @FXML private Button saveButton;

    private BewerbungseintragResponse currentApplication;
    private java.util.function.Consumer<Long> onSaved;
    private Runnable onClose;
    private Consumer<OverlayRequest> overlayPresenter;

    // Anschreiben-Datei (lokal gewählt)
    private File anschreibenFile = null;

    // Document-IDs für gesperrte Checkboxen (globale Dokumente)
    private Long globalLebenslaufId = null;
    private Long globalZertifikateId = null;
    private Long globalZeugnisseId = null;

    // Bereits gespeicherte Anschreiben-ID (beim Bearbeiten)
    private Long existingAnschreibenId = null;

    @FXML
    public void initialize() {
        List<StatusEntity> statuses = statusRepository.findAll();
        List<StatusItem> items = statuses.stream()
                .map(s -> new StatusItem(s.getStatusId(), s.getTitel()))
                .toList();
        statusComboBox.setItems(FXCollections.observableArrayList(items));
        statusComboBox.setCellFactory(param -> new StatusListCell());
        statusComboBox.setButtonCell(new StatusListCell());
        if (!items.isEmpty()) {
            statusComboBox.setValue(items.getFirst());
        }

        applicationTypeComboBox.setItems(FXCollections.observableArrayList(
                "Direktbewerbung", "Initiativbewerbung", "Recruiter"));

        loadGlobalDocuments();
    }

    private void loadGlobalDocuments() {
        AsyncRunner.run(
                () -> documentService.list(DocumentScope.GLOBAL, null, null),
                docs -> {
                    for (var doc : docs) {
                        if (doc.type() == DocumentType.LEBENSLAUF) {
                            globalLebenslaufId = doc.documentId();
                        } else if (doc.type() == DocumentType.ZERTIFIKATE) {
                            globalZertifikateId = doc.documentId();
                        } else if (doc.type() == DocumentType.ZEUGNISSE) {
                            globalZeugnisseId = doc.documentId();
                        }
                    }
                    applyLockedCheckboxes();
                },
                error -> log.warn("Globale Dokumente konnten nicht geladen werden: {}", error.getMessage())
        );
    }

    private void applyLockedCheckboxes() {
        if (globalLebenslaufId != null) {
            lebenslaufCheck.setSelected(true);
            lebenslaufCheck.setDisable(true);
            lebenslaufCheck.getStyleClass().add("attachment-linked");
        }
        if (globalZertifikateId != null) {
            zertifikateCheck.setSelected(true);
            zertifikateCheck.setDisable(true);
            zertifikateCheck.getStyleClass().add("attachment-linked");
        }
        if (globalZeugnisseId != null) {
            zeugnisseCheck.setSelected(true);
            zeugnisseCheck.setDisable(true);
            zeugnisseCheck.getStyleClass().add("attachment-linked");
        }
    }

    public void setApplication(BewerbungseintragResponse application) {
        this.currentApplication = application;

        if (application != null) {
            if (formTitleLabel != null) formTitleLabel.setText("Bewerbung bearbeiten");
            if (formSubtitleLabel != null) formSubtitleLabel.setText("Pflegen Sie alle relevanten Informationen zur Bewerbung.");
            firmaField.setText(nullToEmpty(application.firmaName()));
            firmaField.setEditable(false);
            stelleField.setText(nullToEmpty(application.stellenbezeichnung()));
            stelleField.setEditable(false);
            ortField.setText(nullToEmpty(application.location() != null ? application.location() : application.standort()));
            ansprechpartnerField.setText(nullToEmpty(application.ansprechpartner() != null ? application.ansprechpartner() : application.contactPerson()));
            emailField.setText(nullToEmpty(application.email()));
            telefonField.setText(nullToEmpty(application.telefon() != null ? application.telefon() : application.phone()));
            urlField.setText(nullToEmpty(application.url()));
            notizField.setText(nullToEmpty(application.notiz()));

            if (application.bewerbungscontainerName() != null) {
                String containerName = application.bewerbungscontainerName();
                if (applicationTypeComboBox.getItems().contains(containerName)) {
                    applicationTypeComboBox.setValue(containerName);
                }
            }

            if (application.statusId() != null) {
                statusComboBox.getItems().stream()
                        .filter(item -> item.id().equals(application.statusId()))
                        .findFirst()
                        .ifPresent(statusComboBox::setValue);
            }

            existingAnschreibenId = application.anschreibenId();
            if (application.anschreibenId() != null) {
                anschreibenCheck.setSelected(true);
                showAnschreibenPanel(true);
                anschreibenLabel.setText("Anschreiben bereits vorhanden");
                anschreibenIcon.setIconLiteral("far-check-circle");
                anschreibenIcon.getStyleClass().removeAll("upload-icon");
                anschreibenIcon.getStyleClass().add("upload-icon-done");
                anschreibenDownloadBtn.setVisible(true);
                anschreibenDownloadBtn.setManaged(true);
            }

            pdfBundleCheck.setSelected(application.pdfBundleGewuenscht());
            aktualisiereBundleKnopf();

            deleteButton.setVisible(false);
            deleteButton.setManaged(false);
            saveButton.setText("Aktualisieren");
        } else {
            if (formTitleLabel != null) formTitleLabel.setText("Neue Bewerbung anlegen");
            if (formSubtitleLabel != null) formSubtitleLabel.setText("Geben Sie alle relevanten Informationen zur Bewerbung ein.");
        }
    }

    /**
     * Der Bündel-Knopf erscheint nur, wenn er auch etwas zu tun hätte: Haken gesetzt und
     * mindestens eine Unterlage verknuepft.
     */
    @FXML
    public void aktualisiereBundleKnopf() {
        if (bundleDownloadBtn == null) {
            return;
        }
        boolean hatUnterlagen = lebenslaufCheck.isSelected()
                || zertifikateCheck.isSelected()
                || zeugnisseCheck.isSelected()
                || existingAnschreibenId != null
                || anschreibenFile != null;
        boolean sichtbar = pdfBundleCheck.isSelected() && hatUnterlagen;
        bundleDownloadBtn.setVisible(sichtbar);
        bundleDownloadBtn.setManaged(sichtbar);
    }

    /**
     * Führt die Unterlagen zu einer PDF zusammen und speichert sie, wohin der Nutzer möchte.
     *
     * <p>Es wird immer frisch aus den aktuellen Einzeldateien erzeugt — so kann das Bündel
     * nicht veralten. Ein noch nicht gespeichertes Anschreiben ist deshalb auch noch nicht
     * dabei; darauf wird hingewiesen.
     */
    @FXML
    public void handleDownloadBundle() {
        List<Long> reihenfolge = List.of();
        Long anschreibenId = existingAnschreibenId;
        // Reihenfolge der deutschen Bewerbungsmappe: Anschreiben zuerst.
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        if (anschreibenId != null) ids.add(anschreibenId);
        if (lebenslaufCheck.isSelected()) ids.add(globalLebenslaufId);
        if (zeugnisseCheck.isSelected()) ids.add(globalZeugnisseId);
        if (zertifikateCheck.isSelected()) ids.add(globalZertifikateId);
        reihenfolge = ids;

        if (anschreibenId == null && anschreibenFile != null) {
            showInfo("Das gewählte Anschreiben ist noch nicht gespeichert und daher nicht im "
                    + "Bündel. Bitte zuerst speichern.");
        }

        String firmaName = currentApplication != null ? currentApplication.firmaName() : firmaField.getText();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("PDF-Bündel speichern");
        chooser.setInitialFileName(pdfBundleService.buildFileName(firmaName));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));
        File target = chooser.showSaveDialog(saveButton.getScene().getWindow());
        if (target == null) {
            return;
        }

        List<Long> finalReihenfolge = reihenfolge;
        AsyncRunner.run(
                () -> pdfBundleService.merge(finalReihenfolge),
                bytes -> {
                    try {
                        Files.write(target.toPath(), bytes);
                        showInfo("Bündel gespeichert: " + target.getName());
                    } catch (IOException e) {
                        log.error("Bündel konnte nicht geschrieben werden", e);
                        showInfo("Das Bündel konnte nicht gespeichert werden.");
                    }
                },
                error -> {
                    log.error("Bündel konnte nicht erzeugt werden", error);
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    showInfo(cause.getMessage() != null
                            ? cause.getMessage()
                            : "Das Bündel konnte nicht erzeugt werden.");
                }
        );
    }

    public void setOnSaved(java.util.function.Consumer<Long> callback) {
        this.onSaved = callback;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void setOverlayPresenter(Consumer<OverlayRequest> overlayPresenter) {
        this.overlayPresenter = overlayPresenter;
    }

    @FXML
    public void handleAnschreibenToggle() {
        boolean checked = anschreibenCheck.isSelected();
        showAnschreibenPanel(checked);
        if (!checked) {
            anschreibenFile = null;
            existingAnschreibenId = null;
            anschreibenLabel.setText("Anschreiben für diese Firma");
            anschreibenIcon.setIconLiteral("far-file-alt");
            anschreibenDownloadBtn.setVisible(false);
            anschreibenDownloadBtn.setManaged(false);
        }
    }

    @FXML
    public void handlePickAnschreiben() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Anschreiben auswählen (PDF)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));
        File file = fc.showOpenDialog(saveButton.getScene().getWindow());
        if (file == null) return;

        if (file.length() > 5 * 1024 * 1024) {
            showError("Datei ist zu groß. Maximal 5 MB erlaubt.");
            return;
        }

        anschreibenFile = file;
        anschreibenLabel.setText(file.getName());
        anschreibenIcon.setIconLiteral("far-check-circle");
        anschreibenIcon.getStyleClass().removeAll("upload-icon");
        anschreibenIcon.getStyleClass().add("upload-icon-done");
        anschreibenDownloadBtn.setVisible(true);
        anschreibenDownloadBtn.setManaged(true);
        hideError();
    }

    @FXML
    public void handleDownloadAnschreiben() {
        if (anschreibenFile != null) {
            try {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(anschreibenFile);
                }
            } catch (IOException e) {
                log.error("Anschreiben konnte nicht geöffnet werden", e);
            }
        } else if (existingAnschreibenId != null) {
            Long id = existingAnschreibenId;
            AsyncRunner.run(
                    () -> documentService.download(id),
                    dl -> {
                        try {
                            File tmp = File.createTempFile("anschreiben_", ".pdf");
                            tmp.deleteOnExit();
                            Files.write(tmp.toPath(), dl.content());
                            if (java.awt.Desktop.isDesktopSupported()) {
                                java.awt.Desktop.getDesktop().open(tmp);
                            }
                        } catch (IOException e) {
                            log.error("Download fehlgeschlagen", e);
                        }
                    },
                    error -> showError("Download fehlgeschlagen.")
            );
        }
    }

    @FXML
    public void handleSave() {
        String firma = firmaField.getText().trim();
        String stelle = stelleField.getText().trim();
        String email = emailField.getText().trim();
        String ort = ortField.getText().trim();

        if (firma.isBlank()) { showError("Firmenname ist erforderlich."); return; }
        if (stelle.isBlank()) { showError("Position ist erforderlich."); return; }
        if (!email.isBlank() && (!email.contains("@") || !email.contains("."))) {
            showError("Bitte eine gültige E-Mail-Adresse eingeben."); return;
        }
        if (ort.isBlank()) { showError("Standort ist erforderlich."); return; }

        saveButton.setDisable(true);
        hideError();

        if (currentApplication == null) {
            createApplication(firma, stelle);
        } else {
            patchApplication();
        }
    }

    private void createApplication(String firma, String stelle) {
        StatusItem selectedStatus = statusComboBox.getValue();
        String containerName = applicationTypeComboBox.getValue();
        File fileToUpload = anschreibenFile;

        AsyncRunner.run(
                () -> {
                    // 1. Bewerbung zuerst anlegen (ohne Anschreiben)
                    var created = bewerbungseintragService.createBewerbungseintrag(
                            buildCreateRequest(firma, stelle, containerName, selectedStatus, null));

                    // 2. Anschreiben hochladen (jetzt mit echter applicationId)
                    Long anschreibenId = null;
                    if (fileToUpload != null) {
                        try {
                            byte[] content = Files.readAllBytes(fileToUpload.toPath());
                            var doc = documentService.upload(content, fileToUpload.getName(),
                                    "application/pdf", DocumentType.ANSCHREIBEN,
                                    DocumentScope.APPLICATION, created.bewerbungseintragId());
                            anschreibenId = doc.documentId();
                        } catch (IOException e) {
                            log.warn("Anschreiben-Upload fehlgeschlagen", e);
                        }
                    }

                    // 3. Falls Anschreiben hochgeladen: per PATCH verknüpfen
                    if (anschreibenId != null) {
                        PatchBewerbungseintragRequest patch = new PatchBewerbungseintragRequest(
                                null, null, null, null, null, null, null, null,
                                null, null, null, null, null, null, null, null,
                                anschreibenId, null, null, null, null, null, null);
                        bewerbungseintragService.patchBewerbungseintrag(created.bewerbungseintragId(), patch);
                    }
                    return created;
                },
                result -> notifyAndClose(result.bewerbungseintragId()),
                error -> {
                    Platform.runLater(() -> saveButton.setDisable(false));
                    log.error("Fehler beim Erstellen", error);
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    showError("Speichern fehlgeschlagen: " + cause.getMessage());
                }
        );
    }

    private CreateBewerbungseintragRequest buildCreateRequest(
            String firma, String stelle, String containerName,
            StatusItem selectedStatus, Long anschreibenId) {
        return new CreateBewerbungseintragRequest(
                null, null, null,
                selectedStatus != null ? selectedStatus.id() : null,
                blankToNull(containerName),
                firma, stelle,
                blankToNull(ansprechpartnerField.getText()),
                blankToNull(ansprechpartnerField.getText()),
                blankToNull(telefonField.getText()),
                blankToNull(telefonField.getText()),
                blankToNull(emailField.getText()),
                blankToNull(ortField.getText()),
                blankToNull(ortField.getText()),
                blankToNull(notizField.getText()),
                blankToNull(urlField.getText()),
                lebenslaufCheck.isSelected()  ? globalLebenslaufId  : null,
                zertifikateCheck.isSelected() ? globalZertifikateId : null,
                zeugnisseCheck.isSelected()   ? globalZeugnisseId   : null,
                anschreibenId,
                null, null,
                pdfBundleCheck.isSelected(),
                null
        , null, null);
    }

    private void patchApplication() {
        StatusItem selectedStatus = statusComboBox.getValue();

        Long anschreibenId = existingAnschreibenId;
        if (!anschreibenCheck.isSelected()) {
            anschreibenId = null;
        }

        if (anschreibenFile != null) {
            File fileToUpload = anschreibenFile;
            final Long finalExistingId = anschreibenId;
            AsyncRunner.run(
                    () -> {
                        try {
                            byte[] content = Files.readAllBytes(fileToUpload.toPath());
                            var doc = documentService.upload(content, fileToUpload.getName(),
                                    "application/pdf", DocumentType.ANSCHREIBEN, DocumentScope.APPLICATION,
                                    currentApplication.bewerbungseintragId());
                            return doc.documentId();
                        } catch (IOException e) {
                            log.warn("Anschreiben-Upload fehlgeschlagen", e);
                            return finalExistingId;
                        }
                    },
                    uploadedId -> doPatch(selectedStatus, uploadedId),
                    error -> {
                        log.error("Anschreiben-Upload fehlgeschlagen", error);
                        doPatch(selectedStatus, finalExistingId);
                    }
            );
        } else {
            doPatch(selectedStatus, anschreibenId);
        }
    }

    private void doPatch(StatusItem selectedStatus, Long anschreibenId) {
        PatchBewerbungseintragRequest request = new PatchBewerbungseintragRequest(
                null, null, null,
                selectedStatus != null ? selectedStatus.id() : null,
                blankToNull(ansprechpartnerField.getText()),
                blankToNull(ansprechpartnerField.getText()),
                blankToNull(telefonField.getText()),
                blankToNull(telefonField.getText()),
                blankToNull(emailField.getText()),
                blankToNull(ortField.getText()),
                blankToNull(ortField.getText()),
                blankToNull(notizField.getText()),
                blankToNull(urlField.getText()),
                lebenslaufCheck.isSelected()  ? globalLebenslaufId  : null,
                zertifikateCheck.isSelected() ? globalZertifikateId : null,
                zeugnisseCheck.isSelected()   ? globalZeugnisseId   : null,
                anschreibenId,
                null, null,
                pdfBundleCheck.isSelected(),
                null
        , null, null);

        Long id = currentApplication.bewerbungseintragId();
        AsyncRunner.run(
                () -> bewerbungseintragService.patchBewerbungseintrag(id, request),
                result -> notifyAndClose(id),
                error -> {
                    Platform.runLater(() -> saveButton.setDisable(false));
                    log.error("Fehler beim Aktualisieren", error);
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    showError("Speichern fehlgeschlagen: " + cause.getMessage());
                }
        );
    }

    @FXML
    public void handleDelete() {
        if (overlayPresenter != null) {
            String company = currentApplication != null && currentApplication.firmaName() != null
                    ? currentApplication.firmaName()
                    : "diese Bewerbung";
            overlayPresenter.accept(OverlayRequest.danger(
                    "Bewerbung löschen",
                    "Bewerbung wirklich löschen?",
                    "Eintrag: " + company + "\n\nDiese Aktion kann nicht rückgängig gemacht werden.",
                    List.of(
                            OverlayAction.ghost("Abbrechen", this::closeForm),
                            OverlayAction.danger("Bewerbung löschen", () -> {
                                if (currentApplication == null) {
                                    closeForm();
                                    return;
                                }
                                Long id = currentApplication.bewerbungseintragId();
                                AsyncRunner.run(
                                        () -> { bewerbungseintragService.deleteBewerbungseintrag(id); return null; },
                                        ignored -> notifyAndClose(null),
                                        error -> {
                                            log.error("Fehler beim Löschen", error);
                                            showError("Löschen fehlgeschlagen: " + error.getMessage());
                                        }
                                );
                            })
                    )
            ));
            return;
        }

        showError("Löschbestätigung konnte nicht geöffnet werden.");
    }

    @FXML
    public void handleCancel() {
        closeForm();
    }

    // ── Hilfsmethoden ──

    /**
     * Benachrichtigt das Dashboard und schließt das Formular.
     */
    private void notifyAndClose(Long bewerbungseintragId) {
        try {
            if (onSaved != null) onSaved.accept(bewerbungseintragId);
        } catch (Exception ex) {
            log.warn("onSaved fehlgeschlagen", ex);
        }
        closeForm();
    }

    private void closeForm() {
        Runnable close = () -> {
            if (onClose != null) {
                onClose.run();
            }
        };
        if (Platform.isFxApplicationThread()) {
            close.run();
        } else {
            Platform.runLater(close);
        }
    }

    private void showAnschreibenPanel(boolean visible) {
        anschreibenPanel.setVisible(visible);
        anschreibenPanel.setManaged(visible);
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            errorLabel.getStyleClass().remove("info-label");
            errorLabel.setText(message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        });
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.getStyleClass().remove("info-label");
    }

    /** Neutrale Rückmeldung im selben Feld wie Fehler, nur nicht rot. */
    private void showInfo(String message) {
        Platform.runLater(() -> {
            errorLabel.setText(message);
            if (!errorLabel.getStyleClass().contains("info-label")) {
                errorLabel.getStyleClass().add("info-label");
            }
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        });
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    record StatusItem(Long id, String titel) {
        @Override public String toString() { return titel; }
    }

    private static class StatusListCell extends ListCell<StatusItem> {
        @Override
        protected void updateItem(StatusItem item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.titel());
        }
    }
}
