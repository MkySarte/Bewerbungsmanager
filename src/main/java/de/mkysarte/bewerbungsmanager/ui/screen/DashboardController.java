package de.mkysarte.bewerbungsmanager.ui.screen;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.PatchBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.service.BewerbungseintragService;
import de.mkysarte.bewerbungsmanager.benachrichtigung.AutostartService;
import de.mkysarte.bewerbungsmanager.benachrichtigung.TrayService;
import de.mkysarte.bewerbungsmanager.common.InitialAccess;
import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentScope;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentType;
import de.mkysarte.bewerbungsmanager.document.service.PdfPreviewService;
import de.mkysarte.bewerbungsmanager.document.service.DocumentService;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.Erinnerung;
import de.mkysarte.bewerbungsmanager.erinnerung.dto.ErinnerungsIntervall;
import de.mkysarte.bewerbungsmanager.erinnerung.service.ErinnerungService;
import de.mkysarte.bewerbungsmanager.export.dto.ImportErgebnis;
import de.mkysarte.bewerbungsmanager.export.service.MarkdownExportService;
import de.mkysarte.bewerbungsmanager.export.service.MarkdownImportService;
import de.mkysarte.bewerbungsmanager.session.CurrentUserHolder;
import de.mkysarte.bewerbungsmanager.session.DesktopSessionService;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
import de.mkysarte.bewerbungsmanager.ui.AppShell;
import de.mkysarte.bewerbungsmanager.ui.SpringFXMLLoader;
import de.mkysarte.bewerbungsmanager.ui.ThemeService;
import de.mkysarte.bewerbungsmanager.ui.util.AsyncRunner;
import de.mkysarte.bewerbungsmanager.ui.util.DialogStyler;
import de.mkysarte.bewerbungsmanager.ui.util.OverlayAction;
import de.mkysarte.bewerbungsmanager.ui.util.OverlayPresenter;
import de.mkysarte.bewerbungsmanager.ui.util.OverlayRequest;
import de.mkysarte.bewerbungsmanager.ui.util.OverlayVariant;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.ArrayList;

/**
 * Controller für das Haupt-Dashboard.
 */
@Component
@RequiredArgsConstructor
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final BewerbungseintragService bewerbungseintragService;
    private final StatusRepository statusRepository;
    private final DesktopSessionService sessionService;
    private final SpringFXMLLoader fxmlLoader;
    private final DocumentService documentService;
    private final PdfPreviewService pdfPreviewService;
    private final CurrentUserHolder currentUserHolder;
    private final ThemeService themeService;
    private final AppShell appShell;
    private final ErinnerungService erinnerungService;
    private final EinstellungService einstellungService;
    private final TrayService trayService;
    private final MarkdownExportService markdownExportService;
    private final MarkdownImportService markdownImportService;

    // Alle verfügbaren Status (für Inline-Dropdown auf den Karten)
    private List<StatusEntity> allStatuses = List.of();

    @FXML private Label welcomeLabel;
    @FXML private Label usernameLabel;
    @FXML private HBox documentsGrid;
    @FXML private HBox statsGrid;
    @FXML private TextField searchField;
    @FXML private ListView<BewerbungseintragResponse> applicationList;
    @FXML private VBox emptyState;
    @FXML private Label statusBarLabel;
    @FXML private Button themeToggleBtn;
    @FXML private FontIcon themeIcon;
    @FXML private Button createUserButton;
    @FXML private Button resetDataButton;
    @FXML private Button openDocumentsButton;
    @FXML private Button newApplicationButton;
    @FXML private StackPane formOverlay;
    @FXML private StackPane formOverlayContent;
    @FXML private VBox overlayShell;
    @FXML private Button overlayCloseButton;
    @FXML private StackPane confirmOverlay;
    @FXML private StackPane confirmOverlayContent;
    @FXML private VBox confirmOverlayShell;
    @FXML private StackPane previewOverlay;
    @FXML private VBox previewOverlayShell;
    @FXML private Label previewTitleLabel;
    @FXML private VBox previewPagesBox;
    @FXML private ScrollPane previewScrollPane;
    @FXML private Label previewPageLabel;
    @FXML private Label previewZoomLabel;
    @FXML private Button previewPreviousButton;
    @FXML private Button previewNextButton;

    private final ObservableList<BewerbungseintragResponse> allApplications = FXCollections.observableArrayList();
    private final OverlayPresenter overlayPresenter = new OverlayPresenter();
    private List<Image> previewImages = new ArrayList<>();
    private int previewCurrentPageIndex = 0;
    private double previewZoomFactor = 1.0;
    private static final double PREVIEW_BASE_WIDTH = 860.0;
    private BewerbungseintragResponse pendingDeleteApplication;

    /** Demo-Sitzung mit Musterdaten statt des echten Kontos. */
    private boolean demoSession;

    /** Muss beim Löschen des echten Kontos abgetippt werden. */
    private static final String CONFIRM_DELETE_PHRASE = "LOESCHEN";

    // null = Gesamt, "ABGESCHICKT", "ABSAGE", "ERFOLG" oder FILTER_FAELLIG
    private String activeStatusFilter = null;

    /** Pseudo-Filter: keine Statusbezeichnung, sondern "hat offene Erinnerungen". */
    private static final String FILTER_FAELLIG = "__FAELLIG__";

    // Dokument-State für die 3 Unterlagen-Felder
    private Long lebenslaufId = null;
    private String lebenslaufName = null;
    private Long zertifikateId = null;
    private String zertifikateName = null;
    private Long zeugnisseId = null;
    private String zeugnisseName = null;

    @FXML
    public void initialize() {
        String username = currentUserHolder.getUsername();
        demoSession = sessionService.isCurrentSessionDemo();

        if (username != null) {
            welcomeLabel.setText("Willkommen zurück, " + username + (demoSession ? " (Demo)!" : "!"));
            if (usernameLabel != null) {
                usernameLabel.setText(demoSession ? username + " (Demo)" : username);
            }
        }

        applySessionUiState();

        allStatuses = statusRepository.findAll();
        applicationList.setCellFactory(lv -> new ApplicationCell());

        buildDocumentsGrid();
        loadApplications();
        loadDocuments();
        updateThemeIcon();

        if (overlayShell != null && formOverlay != null) {
            restoreResponsiveOverlaySize();
        }
        overlayPresenter.attachHost(confirmOverlay, confirmOverlayShell, confirmOverlayContent);

        if (demoSession) {
            Platform.runLater(this::showDemoModeNotice);
        }
    }

    // =====================================================================
    //  Theme
    // =====================================================================

    /**
     * Blendet die Bedienelemente ein, die zur Art der Sitzung passen.
     *
     * <p>In der Demo ist alles bedienbar - die Musterdaten liegen in einer eigenen Datenbank
     * und können nichts kaputt machen. Nur die Konto-Aktionen unterscheiden sich: einrichten
     * geht ausschließlich aus der Demo heraus und nur, solange es noch kein Konto gibt.
     */
    private void applySessionUiState() {
        boolean accountConfigured = sessionService.isAccountConfigured();

        if (createUserButton != null) {
            boolean canSetUp = demoSession && !accountConfigured;
            createUserButton.setVisible(canSetUp);
            createUserButton.setManaged(canSetUp);
        }
        if (resetDataButton != null) {
            // Das echte Konto lässt sich nur aus der Demo heraus löschen - der Notausgang,
            // wenn Passwort und Masterpasswort verloren sind.
            boolean canDelete = demoSession && accountConfigured;
            resetDataButton.setVisible(canDelete);
            resetDataButton.setManaged(canDelete);
        }
        if (statusBarLabel != null && demoSession) {
            statusBarLabel.setText(accountConfigured
                    ? "Demo-Zugang: Musterdaten. Ihr eigenes Konto ist verschlüsselt und hier nicht sichtbar."
                    : "Demo-Zugang: Musterdaten zum Ausprobieren. Über „Eigenes Konto einrichten“ geht es richtig los.");
        }
    }

    private void showDemoModeNotice() {
        boolean accountConfigured = sessionService.isAccountConfigured();
        overlayPresenter.show(OverlayRequest.builder()
                .title("Demo-Zugang")
                .subtitle("Sie sehen Musterdaten - zum gefahrlosen Ausprobieren.")
                .message(accountConfigured
                        ? "Ihre echten Bewerbungsdaten liegen in einer eigenen, verschlüsselten Datei. Der Demo-Zugang besitzt deren Schlüssel nicht und kommt an sie nicht heran. Melden Sie sich mit Ihrem eigenen Konto an, um damit zu arbeiten."
                        : "Probieren Sie ruhig alles aus - hier kann nichts kaputtgehen. Wenn es Ihnen gefällt, richten Sie über „Eigenes Konto einrichten“ Ihr verschlüsseltes Konto ein. Es startet leer, die Musterdaten bleiben hier zurück.")
                .variant(OverlayVariant.WARNING)
                .actions(List.of(OverlayAction.primary("Verstanden", null)))
                .build());
    }


    @FXML
    public void handleToggleTheme() {
        themeService.toggle();
        Parent root = themeToggleBtn.getScene().getRoot();
        themeService.apply(root);
        updateThemeIcon();
    }

    private void updateThemeIcon() {
        if (themeIcon != null) {
            themeIcon.setIconLiteral(themeService.isDark() ? "fas-sun" : "far-moon");
        }
    }

    // =====================================================================
    //  Unterlagen (Lebenslauf / Zertifikate / Zeugnisse)
    // =====================================================================

    private void loadDocuments() {
        AsyncRunner.run(
                () -> documentService.list(DocumentScope.GLOBAL, null, null),
                docs -> {
                    lebenslaufId = null; lebenslaufName = null;
                    zertifikateId = null; zertifikateName = null;
                    zeugnisseId = null; zeugnisseName = null;

                    for (var doc : docs) {
                        if (doc.type() == DocumentType.LEBENSLAUF) {
                            lebenslaufId = doc.documentId(); lebenslaufName = doc.fileName();
                        } else if (doc.type() == DocumentType.ZERTIFIKATE) {
                            zertifikateId = doc.documentId(); zertifikateName = doc.fileName();
                        } else if (doc.type() == DocumentType.ZEUGNISSE) {
                            zeugnisseId = doc.documentId(); zeugnisseName = doc.fileName();
                        }
                    }
                    buildDocumentsGrid();
                },
                error -> log.warn("Dokumente konnten nicht geladen werden: {}", error.getMessage())
        );
    }

    private void buildDocumentsGrid() {
        documentsGrid.getChildren().clear();
        documentsGrid.getChildren().addAll(
                buildUploadCard("Lebenslauf",    lebenslaufId,    lebenslaufName,    DocumentType.LEBENSLAUF),
                buildUploadCard("Zertifikate",   zertifikateId,   zertifikateName,   DocumentType.ZERTIFIKATE),
                buildUploadCard("Zeugnisse",     zeugnisseId,     zeugnisseName,     DocumentType.ZEUGNISSE)
        );
    }

    private VBox buildUploadCard(String title, Long docId, String docName, DocumentType type) {
        VBox card = new VBox(10);
        card.getStyleClass().add("upload-field");
        card.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setPadding(new Insets(16, 16, 16, 16));
        // Feste Höhe + prefWidth=0 damit alle drei Karten exakt gleich groß sind
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefHeight(196);
        card.setMinHeight(196);
        card.setMaxHeight(196);

        String iconLit = docId != null ? "far-check-circle" : "far-file-alt";
        FontIcon icon = new FontIcon(iconLit);
        icon.setIconSize(36);
        icon.getStyleClass().add(docId != null ? "upload-icon-done" : "upload-icon");

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_RIGHT);
        topRow.setMinHeight(24);

        if (docId != null) {
            Button removeTop = new Button();
            removeTop.getStyleClass().addAll("upload-trash-btn", "icon-btn");
            FontIcon trash = new FontIcon("fas-trash-alt");
            trash.setIconSize(12);
            removeTop.setGraphic(trash);
            final Long id = docId;
            removeTop.setOnAction(e -> deleteDocument(id, type));
            topRow.getChildren().add(removeTop);
        }

        Label caption = new Label(docId != null ? docName : title);
        caption.getStyleClass().add(docId != null ? "upload-caption-done" : "upload-caption");
        caption.setWrapText(false);
        caption.setEllipsisString("…");
        caption.setMaxWidth(Double.MAX_VALUE);
        caption.setStyle("-fx-text-overrun: ellipsis;");
        caption.setAlignment(Pos.CENTER);

        Region contentSpacer = new Region();
        VBox.setVgrow(contentSpacer, Priority.ALWAYS);

        card.getChildren().addAll(topRow, icon, caption, contentSpacer);

        if (docId == null) {
            Button pick = new Button("Datei auswählen");
            pick.getStyleClass().add("ghost-btn");
            pick.setMaxWidth(Double.MAX_VALUE);
            pick.setOnAction(e -> uploadDocument(type));
            card.getChildren().add(pick);
        } else {
            final Long id = docId;
            final String name = docName;

            Button preview = new Button("Anzeigen");
            preview.getStyleClass().add("ghost-btn");
            preview.setOnAction(e -> previewDocument(id));

            Button download = new Button("Herunterladen");
            download.getStyleClass().add("btn-primary");
            download.setOnAction(e -> saveDocument(id, name));

            HBox actions = new HBox(8, preview, download);
            actions.setAlignment(Pos.CENTER);
            card.getChildren().add(actions);
        }

        return card;
    }

    private void uploadDocument(DocumentType type) {
        FileChooser fc = new FileChooser();
        fc.setTitle(type.name() + " hochladen");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));
        File file = fc.showOpenDialog(statsGrid.getScene().getWindow());
        if (file == null) return;

        AsyncRunner.run(
                () -> {
                    try {
                        byte[] content = Files.readAllBytes(file.toPath());
                        return documentService.upload(content, file.getName(), "application/pdf",
                                type, DocumentScope.GLOBAL, null);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                result -> loadDocuments(),
                error -> { log.error("Upload fehlgeschlagen", error); showError("Upload fehlgeschlagen."); }
        );
    }

    private void previewDocument(Long id) {
        AsyncRunner.run(
                () -> documentService.download(id),
                dl -> openPdfPreview(dl.fileName(), dl.content()),
                error -> showError("Vorschau fehlgeschlagen.")
        );
    }

    private void openPdfPreview(String fileName, byte[] content) {
        AsyncRunner.run(
                () -> {
                    try {
                        return pdfPreviewService.renderPdf(content);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                images -> {
                    previewTitleLabel.setText(fileName != null ? fileName : "PDF-Vorschau");
                    previewImages = images;
                    previewCurrentPageIndex = 0;
                    previewZoomFactor = 1.0;
                    renderCurrentPreviewPage();
                    previewOverlay.setVisible(true);
                    previewOverlay.setManaged(true);
                    previewOverlay.setFocusTraversable(true);
                    previewOverlay.setOnKeyPressed(event -> {
                        if (event.getCode() == KeyCode.ESCAPE) {
                            handleClosePreviewOverlay();
                            event.consume();
                        }
                    });
                    Platform.runLater(previewOverlay::requestFocus);
                },
                error -> {
                    log.error("PDF-Vorschau fehlgeschlagen", error);
                    showError("PDF-Vorschau konnte nicht geladen werden.");
                }
        );
    }

    private void saveDocument(Long id, String name) {
        AsyncRunner.run(
                () -> documentService.download(id),
                dl -> Platform.runLater(() -> {
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Dokument speichern");
                    chooser.setInitialFileName(dl.fileName() != null ? dl.fileName() : name);
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));
                    File target = chooser.showSaveDialog(statsGrid.getScene().getWindow());
                    if (target == null) {
                        return;
                    }
                    try {
                        Files.write(target.toPath(), dl.content());
                        showInfo("Dokument gespeichert: " + target.getName());
                    } catch (IOException e) {
                        log.error("Speichern fehlgeschlagen", e);
                        showError("Speichern fehlgeschlagen.");
                    }
                }),
                error -> showError("Download fehlgeschlagen.")
        );
    }

    private void deleteDocument(Long id, DocumentType type) {
        overlayPresenter.show(OverlayRequest.danger(
                "Datei entfernen",
                type.name() + " wirklich entfernen?",
                "Diese Aktion kann nicht rückgängig gemacht werden.",
                List.of(
                        OverlayAction.ghost("Abbrechen", null),
                        OverlayAction.danger("Datei entfernen", () -> AsyncRunner.run(
                                () -> { documentService.delete(id); return null; },
                                ignored -> {
                                    loadDocuments();
                                    loadApplications();
                                },
                                error -> {
                                    log.error("Dokument-Löschen fehlgeschlagen", error);
                                    showError("Entfernen fehlgeschlagen.");
                                }
                        ))
                )
        ));
    }

    // =====================================================================
    //  Anwendungen laden
    // =====================================================================

    private void loadApplications() {
        loadApplications(null);
    }

    /**
     * Laedt die Liste neu.
     *
     * @param sichtbarHalten Id einer Bewerbung, die danach sichtbar sein muss - oder
     *                       {@code null}. Ohne das verschwaende eine gerade angelegte Bewerbung
     *                       lautlos, wenn gerade ein Filter wie "Erfolge" aktiv ist.
     */
    private void loadApplications(Long sichtbarHalten) {
        statusBarLabel.setText("Lade Bewerbungen…");
        AsyncRunner.run(
                bewerbungseintragService::getAllBewerbungseintraege,
                applications -> {
                    allApplications.setAll(applications);
                    applyFilter();
                    if (sichtbarHalten != null && !istInDerListe(sichtbarHalten)) {
                        activeStatusFilter = null;
                        applyFilter();
                    }
                    buildStatsGrid(applications);
                    statusBarLabel.setText("");
                },
                error -> {
                    log.error("Fehler beim Laden der Bewerbungen", error);
                    statusBarLabel.setText("Fehler beim Laden");
                }
        );
    }

    private boolean istInDerListe(Long bewerbungseintragId) {
        return applicationList.getItems().stream()
                .anyMatch(a -> bewerbungseintragId.equals(a.bewerbungseintragId()));
    }

    // =====================================================================
    //  Statistik-Kacheln (6 feste Karten)
    // =====================================================================

    private void buildStatsGrid(List<BewerbungseintragResponse> applications) {
        long total       = applications.size();
        long abgeschickt = applications.stream()
                .filter(a -> "ABGESCHICKT".equalsIgnoreCase(a.statusTitel()))
                .count();
        long absagen     = applications.stream()
                .filter(a -> { String t = a.statusTitel();
                    return t != null && (t.equalsIgnoreCase("ABSAGE") || t.equalsIgnoreCase("ABSAGE_ERHALTEN")); })
                .count();
        long erfolge     = applications.stream()
                .filter(a -> "ERFOLG".equalsIgnoreCase(a.statusTitel()))
                .count();
        long faellig     = applications.stream()
                .filter(erinnerungService::istFaellig)
                .count();
        long entwuerfe   = applications.stream()
                .filter(a -> ErinnerungService.STATUS_ENTWURF.equalsIgnoreCase(a.statusTitel()))
                .count();

        statsGrid.getChildren().clear();

        // "Entwurf" steht vorn, weil dort jede neue Bewerbung landet - und "Gesamt" hinten:
        // Wer gerade etwas angelegt hat, sucht den Entwurf, nicht die Summe.
        for (VBox card : List.of(
                buildStatCard("ENTWURF",      "Entwürfe",     entwuerfe,   "draft"),
                buildStatCard("ABGESCHICKT",  "Abgeschickt",  abgeschickt, "orange"),
                buildStatCard("ABSAGE",       "Absagen",      absagen,     "red"),
                buildStatCard("ERFOLG",       "Erfolge",      erfolge,     "green"),
                buildStatCard(FILTER_FAELLIG, "Fällig",       faellig,     "orange"),
                buildStatCard(null,           "Gesamt",       total,       "blue")
        )) {
            HBox.setHgrow(card, Priority.ALWAYS);
            statsGrid.getChildren().add(card);
        }
    }

    private VBox buildStatCard(String filterKey, String label, long count, String tone) {
        Label lblText = new Label(label);
        lblText.getStyleClass().add("stat-title");

        Label countLabel = new Label(String.valueOf(count));
        countLabel.getStyleClass().add("stat-count");

        VBox left = new VBox(2, lblText, countLabel);
        left.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(left, Priority.ALWAYS);

        FontIcon icon = new FontIcon(iconLiteralForTone(tone));
        icon.setIconSize(22);
        icon.getStyleClass().add("stat-icon-" + tone);

        HBox card = new HBox(12, left, icon);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("stat-card");

        boolean isActive = (filterKey == null && activeStatusFilter == null)
                || (filterKey != null && filterKey.equals(activeStatusFilter));
        if (isActive) {
            card.getStyleClass().add("stat-card-active");
        }

        VBox wrapper = new VBox(card);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPadding(new Insets(14, 16, 14, 16));

        card.setOnMouseClicked(e -> {
            activeStatusFilter = filterKey;
            searchField.clear();
            applyFilter();
            buildStatsGrid(allApplications);
        });

        return wrapper;
    }

    private String iconLiteralForTone(String tone) {
        return switch (tone) {
            case "orange" -> "far-clock";
            case "red"    -> "fas-times-circle";
            case "green"  -> "far-check-circle";
            case "draft"  -> "far-edit";
            default       -> "far-file-alt";
        };
    }

    // =====================================================================
    //  Filter & Suche
    // =====================================================================

    @FXML
    public void handleSearch() {
        activeStatusFilter = null;
        applyFilter();
        buildStatsGrid(allApplications);
    }

    private void applyFilter() {
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";

        List<BewerbungseintragResponse> filtered = allApplications.stream()
                .filter(a -> {
                    if (FILTER_FAELLIG.equals(activeStatusFilter)) {
                        if (!erinnerungService.istFaellig(a)) return false;
                    } else if (activeStatusFilter != null) {
                        String t = a.statusTitel();
                        if ("ABSAGE".equals(activeStatusFilter)) {
                            if (t == null || (!t.equalsIgnoreCase("ABSAGE") && !t.equalsIgnoreCase("ABSAGE_ERHALTEN"))) return false;
                        } else {
                            if (!activeStatusFilter.equalsIgnoreCase(t)) return false;
                        }
                    }
                    if (!query.isBlank()) {
                        return containsIgnoreCase(a.firmaName(), query)
                                || containsIgnoreCase(a.stellenbezeichnung(), query)
                                || containsIgnoreCase(a.ansprechpartner(), query)
                                || containsIgnoreCase(a.location(), query);
                    }
                    return true;
                })
                .toList();

        applicationList.setItems(FXCollections.observableArrayList(filtered));

        boolean empty = filtered.isEmpty();
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);
        applicationList.setVisible(!empty);
        applicationList.setManaged(!empty);

        long total = allApplications.size();
        long shown = filtered.size();
        if (!query.isBlank() || activeStatusFilter != null) {
            statusBarLabel.setText(shown + " von " + total + " Bewerbung(en)");
        } else {
            statusBarLabel.setText(total + " Bewerbung(en)");
        }
    }

    // =====================================================================
    //  Navigation & Aktionen
    // =====================================================================

    @FXML
    public void handleApplicationClick(MouseEvent event) {
        if (isInsideInteractiveCardArea(event)) {
            return;
        }
        if (event.getClickCount() >= 1) {
            BewerbungseintragResponse selected = applicationList.getSelectionModel().getSelectedItem();
            if (selected != null) openApplicationForm(selected);
        }
    }

    private boolean isInsideInteractiveCardArea(MouseEvent event) {
        Object target = event.getTarget();
        if (!(target instanceof Node node)) {
            return false;
        }
        while (node != null) {
            if (node.getStyleClass().contains("application-note")
                    || node.getStyleClass().contains("application-note-row")
                    || node.getStyleClass().contains("inline-note-editor")
                    || node.getStyleClass().contains("inline-note-actions")
                    || node.getStyleClass().contains("card-status-btn")
                    || node.getStyleClass().contains("card-delete-btn")) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    @FXML
    public void handleNewApplication() {
        openApplicationForm(null);
    }

    void openApplicationForm(BewerbungseintragResponse application) {
        try {
            SpringFXMLLoader.LoadResult<ApplicationFormController> result =
                    fxmlLoader.loadWithController("/fxml/ApplicationForm.fxml");
            result.controller().setApplication(application);
            result.controller().setOnSaved(id -> {
                loadApplications(id);
                hideFormOverlay();
            });
            result.controller().setOnClose(this::hideFormOverlay);
            result.controller().setOverlayPresenter(overlayPresenter::show);

            showOverlayContent(result.view(), false);
        } catch (IOException e) {
            log.error("ApplicationForm konnte nicht geöffnet werden", e);
            showError("Formular konnte nicht geöffnet werden.");
        }
    }

    @FXML
    public void handleCloseOverlay() {
        hideFormOverlay();
    }

    private void hideFormOverlay() {
        hideConfirmOverlay();
        restoreOverlayDefaults();
        formOverlayContent.getChildren().clear();
        formOverlay.setVisible(false);
        formOverlay.setManaged(false);
    }

    void deleteApplication(BewerbungseintragResponse application) {
        pendingDeleteApplication = application;
        overlayPresenter.show(OverlayRequest.danger(
                "Bewerbung löschen",
                "Bewerbung bei " + nvl(application.firmaName(), "?") + " wirklich löschen?",
                "Diese Aktion kann nicht rückgängig gemacht werden.",
                List.of(
                        OverlayAction.ghost("Abbrechen", () -> pendingDeleteApplication = null),
                        OverlayAction.danger("Bewerbung löschen", () -> {
                            if (pendingDeleteApplication == null) {
                                return;
                            }
                            AsyncRunner.run(
                                    () -> { bewerbungseintragService.deleteBewerbungseintrag(pendingDeleteApplication.bewerbungseintragId()); return null; },
                                    ignored -> {
                                        pendingDeleteApplication = null;
                                        loadApplications();
                                    },
                                    error -> {
                                        log.error("Löschen fehlgeschlagen", error);
                                        showError("Löschen fehlgeschlagen.");
                                    }
                            );
                        })
                )
        ));
    }

    @FXML
    public void handleOpenDocuments() {
        try {
            SpringFXMLLoader.LoadResult<DocumentsController> result =
                    fxmlLoader.loadWithController("/fxml/Documents.fxml");
            result.controller().setOnClose(this::hideFormOverlay);
            result.controller().setReopenDocuments(this::handleOpenDocuments);
            result.controller().setOverlayPresenter(overlayPresenter::show);
            result.controller().setPreviewHandler((documentId, fileName) -> {
                AsyncRunner.run(
                        () -> documentService.download(documentId),
                        download -> openPdfPreview(fileName, download.content()),
                        error -> showError("PDF-Vorschau konnte nicht geladen werden.")
                );
            });
            showOverlayContent(result.view(), false);
        } catch (IOException e) {
            log.error("Dokumentenansicht konnte nicht geöffnet werden", e);
            showError("Dokumentenansicht konnte nicht geöffnet werden.");
        }
    }

    @FXML
    public void handleClosePreviewOverlay() {
        previewImages = new ArrayList<>();
        previewCurrentPageIndex = 0;
        previewZoomFactor = 1.0;
        previewPagesBox.getChildren().clear();
        previewOverlay.setVisible(false);
        previewOverlay.setManaged(false);
    }

    @FXML
    public void handlePreviewZoomIn() {
        previewZoomFactor = Math.min(2.4, previewZoomFactor + 0.2);
        renderCurrentPreviewPage();
    }

    @FXML
    public void handlePreviewZoomOut() {
        previewZoomFactor = Math.max(0.6, previewZoomFactor - 0.2);
        renderCurrentPreviewPage();
    }

    @FXML
    public void handlePreviewPreviousPage() {
        if (previewCurrentPageIndex > 0) {
            previewCurrentPageIndex--;
            renderCurrentPreviewPage();
        }
    }

    @FXML
    public void handlePreviewNextPage() {
        if (previewCurrentPageIndex < previewImages.size() - 1) {
            previewCurrentPageIndex++;
            renderCurrentPreviewPage();
        }
    }

    @FXML
    public void handlePreviewFitWidth() {
        if (previewImages == null || previewImages.isEmpty() || previewScrollPane == null) {
            return;
        }
        double contentWidth = Math.max(420, previewScrollPane.getViewportBounds().getWidth() - 48);
        previewZoomFactor = contentWidth / PREVIEW_BASE_WIDTH;
        renderCurrentPreviewPage();
    }

    @FXML
    public void handlePreviewFitPage() {
        if (previewImages == null || previewImages.isEmpty() || previewScrollPane == null) {
            return;
        }
        Image image = previewImages.get(previewCurrentPageIndex);
        double availableWidth = Math.max(420, previewScrollPane.getViewportBounds().getWidth() - 48);
        double availableHeight = Math.max(300, previewScrollPane.getViewportBounds().getHeight() - 48);
        double widthScale = availableWidth / image.getWidth();
        double heightScale = availableHeight / image.getHeight();
        previewZoomFactor = Math.min(widthScale, heightScale);
        renderCurrentPreviewPage();
    }

    private void renderCurrentPreviewPage() {
        previewPagesBox.getChildren().clear();
        previewZoomLabel.setText((int) Math.round(previewZoomFactor * 100) + "%");
        if (previewImages == null || previewImages.isEmpty()) {
            previewPageLabel.setText("Seite 0 / 0");
            updatePreviewPagingVisibility(0);
            Label placeholder = new Label("Keine Vorschau verfügbar.");
            placeholder.getStyleClass().add("preview-placeholder");
            previewPagesBox.getChildren().add(placeholder);
            return;
        }

        Image image = previewImages.get(previewCurrentPageIndex);
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(PREVIEW_BASE_WIDTH * previewZoomFactor);
        StackPane wrapper = new StackPane(view);
        wrapper.getStyleClass().add("preview-page");
        previewPagesBox.getChildren().add(wrapper);
        previewPageLabel.setText("Seite " + (previewCurrentPageIndex + 1) + " / " + previewImages.size());
        updatePreviewPagingVisibility(previewImages.size());
    }

    private void updatePreviewPagingVisibility(int pageCount) {
        boolean multiplePages = pageCount > 1;
        previewPageLabel.setVisible(multiplePages);
        previewPageLabel.setManaged(multiplePages);
        previewPreviousButton.setVisible(multiplePages);
        previewPreviousButton.setManaged(multiplePages);
        previewNextButton.setVisible(multiplePages);
        previewNextButton.setManaged(multiplePages);
    }

    /**
     * Abmelden ist mehr als ein Bildschirmwechsel: Die Datenbank haengt an der Sitzung, also
     * wird der ganze Spring-Context geschlossen und der Anmeldebildschirm neu aufgebaut.
     * Danach ist die entschlüsselte Datei wieder zu.
     */
    @FXML
    public void handleLogout() {
        sessionService.logout();
        appShell.logout();
    }

    private void hideConfirmOverlay() {
        overlayPresenter.hide();
    }

    /**
     * Die zum Status passende Zeile auf der Karte.
     *
     * <p>Entwurf und Abgeschickt sind Zustaende, in denen man etwas tun muss - die bekommen ein
     * Menue direkt neben dem Datum. Absage und Erfolg sind abgeschlossen; dort ist das Datum
     * die ganze Information - anklickbar ist es trotzdem, denn wer eine bestehende Liste
     * einspielt, braucht genau dort die Korrektur.
     */
    private HBox buildStatusZeile(BewerbungseintragResponse item) {
        String status = item.statusTitel() == null ? "" : item.statusTitel();

        if (ErinnerungService.STATUS_ABGESCHICKT.equalsIgnoreCase(status)) {
            return abgeschicktZeile(item);
        }
        if (ErinnerungService.STATUS_ENTWURF.equalsIgnoreCase(status)) {
            return entwurfZeile(item);
        }
        if (status.toUpperCase(java.util.Locale.ROOT).startsWith("ABSAGE")) {
            return abschlussZeile("fas-times-circle", "Absage am", item.absageAm(), item, "ABSAGE");
        }
        if ("ERFOLG".equalsIgnoreCase(status)) {
            return abschlussZeile("fas-check-circle", "Erfolg am", item.erfolgAm(), item, "ERFOLG");
        }
        return abschlussZeile("far-calendar-alt", "Angelegt am", item.createdAt(), item, null);
    }

    /** Entwurf: seit wann er liegt, dazu der Rhythmus der Erinnerung. */
    private HBox entwurfZeile(BewerbungseintragResponse item) {
        LocalDate seit = erinnerungService.entwurfSeit(item);
        HBox text = datumszeile("Entwurf seit", seit,
                datum -> speichereErstelltAm(item, datum));

        ErinnerungsIntervall intervall = erinnerungService.effektivesIntervall(item);
        Button menue = new Button("Erinnern: " + intervall.getBezeichnung());
        menue.getStyleClass().add("card-inline-btn");
        menue.setGraphic(new FontIcon("fas-chevron-down"));
        menue.setContentDisplay(ContentDisplay.RIGHT);
        menue.setGraphicTextGap(6);

        ContextMenu auswahl = new ContextMenu();
        auswahl.getStyleClass().add("status-context-menu");
        for (ErinnerungsIntervall wahl : ErinnerungsIntervall.values()) {
            MenuItem eintrag = new MenuItem(wahl.getBezeichnung());
            eintrag.getStyleClass().add("status-context-item");
            eintrag.setOnAction(e -> AsyncRunner.run(
                    () -> bewerbungseintragService.setzeEntwurfIntervall(
                            item.bewerbungseintragId(), wahl.name()),
                    r -> loadApplications(),
                    err -> {
                        log.error("Erinnerungsrhythmus konnte nicht gesetzt werden", err);
                        showError("Der Rhythmus konnte nicht gespeichert werden.");
                    }));
            auswahl.getItems().add(eintrag);
        }
        menue.setOnAction(e -> {
            e.consume();
            auswahl.show(menue, javafx.geometry.Side.BOTTOM, 0, 4);
        });

        return statusZeile("far-edit", text, menue, false);
    }

    /** Abgeschickt: beide Daten nebeneinander, dazu die Frist zum Ändern. */
    private HBox abgeschicktZeile(BewerbungseintragResponse item) {
        LocalDate ab = erinnerungService.nachfassenAb(item);
        boolean faellig = erinnerungService.nachfassenFaellig(item);

        LocalDate abgeschickt = item.abgeschicktAm() != null
                ? item.abgeschicktAm().toLocalDate()
                : null;
        HBox text = datumszeile("Abgeschickt am", abgeschickt,
                datum -> speichereStatusDatum(item, ErinnerungService.STATUS_ABGESCHICKT, datum));
        if (ab != null) {
            // Das Nachfassdatum rechnet die Anwendung aus - es bleibt reiner Text.
            Label nachfassen = new Label("  ·  Nachfassen ab " + formatiere(ab));
            nachfassen.getStyleClass().add("status-line-text");
            text.getChildren().add(nachfassen);
        }

        Button menue = new Button("Nachfassen nach " + erinnerungService.effektiveFristTage(item) + " Tagen");
        menue.getStyleClass().add("card-inline-btn");
        menue.setGraphic(new FontIcon("fas-chevron-down"));
        menue.setContentDisplay(ContentDisplay.RIGHT);
        menue.setGraphicTextGap(6);

        ContextMenu auswahl = new ContextMenu();
        auswahl.getStyleClass().add("status-context-menu");
        for (int tage : new int[]{7, 10, 14, 21, 30}) {
            MenuItem eintrag = new MenuItem(tage + " Tage");
            eintrag.getStyleClass().add("status-context-item");
            final int gewaehlt = tage;
            eintrag.setOnAction(e -> speichereFrist(item, gewaehlt));
            auswahl.getItems().add(eintrag);
        }
        MenuItem eigener = new MenuItem("Eigener Wert ...");
        eigener.getStyleClass().add("status-context-item");
        eigener.setOnAction(e -> frageEigeneFrist(item));
        auswahl.getItems().add(eigener);

        MenuItem standard = new MenuItem("Standard verwenden");
        standard.getStyleClass().add("status-context-item");
        standard.setOnAction(e -> speichereFrist(item, null));
        auswahl.getItems().add(standard);

        menue.setOnAction(e -> {
            e.consume();
            auswahl.show(menue, javafx.geometry.Side.BOTTOM, 0, 4);
        });

        return statusZeile(faellig ? "far-clock" : "fas-paper-plane", text, menue, faellig);
    }

    /** Absage und Erfolg: das Datum, sonst nichts — auch dort ist es korrigierbar. */
    private HBox abschlussZeile(String icon, String praefix, java.time.LocalDateTime zeitpunkt,
                                BewerbungseintragResponse item, String statusTitel) {
        LocalDate datum = zeitpunkt != null ? zeitpunkt.toLocalDate() : null;
        HBox text = statusTitel == null
                ? nurText(praefix, datum)
                : datumszeile(praefix, datum, neu -> speichereStatusDatum(item, statusTitel, neu));
        return statusZeile(icon, text, null, false);
    }

    /** „Angelegt am …" hat keinen Statusverlauf hinter sich und bleibt deshalb unantastbar. */
    private HBox nurText(String praefix, LocalDate datum) {
        Label label = new Label(praefix + " " + formatiere(datum));
        label.getStyleClass().add("status-line-text");
        HBox zeile = new HBox(label);
        zeile.setAlignment(Pos.CENTER_LEFT);
        return zeile;
    }

    /**
     * Beschriftung plus anklickbares Datum.
     *
     * <p>Das Datum ist ein flacher Knopf statt eines Labels: Wer eine bestehende Liste
     * einspielt, hat vor Wochen abgeschickt — dann steht hier der Importtag, und Nachfassfrist
     * wie Fälligkeit rechnen auf dem falschen Tag. Ein Klick genügt zum Richtigstellen.
     */
    private HBox datumszeile(String praefix, LocalDate datum, Consumer<LocalDate> speichern) {
        Label label = new Label(praefix);
        label.getStyleClass().add("status-line-text");

        Button knopf = new Button(formatiere(datum));
        knopf.getStyleClass().add("card-date-btn");
        knopf.setTooltip(new Tooltip("Datum ändern"));
        knopf.setOnAction(e -> {
            e.consume();
            frageDatum(praefix, datum, speichern);
        });

        HBox zeile = new HBox(6, label, knopf);
        zeile.setAlignment(Pos.CENTER_LEFT);
        return zeile;
    }

    /** Kleines Overlay mit Kalender. Es gibt sonst keinen DatePicker in der Anwendung. */
    private void frageDatum(String titel, LocalDate vorgabe, Consumer<LocalDate> speichern) {
        Label ueberschrift = new Label(titel);
        ueberschrift.getStyleClass().add("overlay-title");

        DatePicker kalender = new DatePicker(vorgabe != null ? vorgabe : LocalDate.now());
        kalender.getStyleClass().add("form-input");
        kalender.setMaxWidth(Double.MAX_VALUE);
        kalender.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(LocalDate wert) {
                return wert == null ? "" : wert.format(ErinnerungService.DATUM);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return LocalDate.parse(text.trim(), ErinnerungService.DATUM);
                } catch (java.time.format.DateTimeParseException ex) {
                    return null;
                }
            }
        });

        Label inlineError = new Label();
        inlineError.getStyleClass().add("error-label");
        inlineError.setVisible(false);
        inlineError.setManaged(false);

        Button abbrechen = new Button("Abbrechen");
        abbrechen.getStyleClass().add("ghost-btn");
        abbrechen.setOnAction(e -> hideFormOverlay());

        Button uebernehmen = new Button("Übernehmen");
        uebernehmen.getStyleClass().add("btn-primary");
        uebernehmen.setOnAction(e -> {
            LocalDate gewaehlt = kalender.getValue();
            if (gewaehlt == null) {
                inlineError.setText("Bitte ein Datum im Format TT.MM.JJJJ wählen.");
                inlineError.setVisible(true);
                inlineError.setManaged(true);
                return;
            }
            hideFormOverlay();
            speichern.accept(gewaehlt);
        });

        HBox actions = new HBox(10, abbrechen, uebernehmen);
        actions.getStyleClass().add("admin-overlay-actions");

        VBox content = new VBox(14, ueberschrift, kalender, inlineError, actions);
        content.getStyleClass().add("admin-overlay-panel");
        showOverlayContent(content, true);
        Platform.runLater(kalender::requestFocus);
    }

    private void speichereStatusDatum(BewerbungseintragResponse item, String statusTitel,
                                      LocalDate datum) {
        AsyncRunner.run(
                () -> bewerbungseintragService.setzeStatusDatum(
                        item.bewerbungseintragId(), statusTitel, datum),
                r -> loadApplications(),
                err -> {
                    log.error("Statusdatum konnte nicht gesetzt werden", err);
                    showError("Das Datum konnte nicht gespeichert werden.");
                });
    }

    /**
     * „Entwurf seit" ist keine Verlaufszeile, sondern das Feld {@code erstelltAm} am Eintrag
     * selbst — deshalb geht es hier über einen Patch statt über den Statusverlauf.
     */
    private void speichereErstelltAm(BewerbungseintragResponse item, LocalDate datum) {
        AsyncRunner.run(
                () -> bewerbungseintragService.setzeErstelltAm(item.bewerbungseintragId(), datum),
                r -> loadApplications(),
                err -> {
                    log.error("Entwurfsdatum konnte nicht gesetzt werden", err);
                    showError("Das Datum konnte nicht gespeichert werden.");
                });
    }

    private HBox statusZeile(String iconLiteral, Node text, Button menue, boolean hervorheben) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(12);
        icon.getStyleClass().add(hervorheben ? "reminder-icon" : "status-line-icon");

        HBox zeile = new HBox(8, icon, text);
        zeile.setAlignment(Pos.CENTER_LEFT);
        zeile.getStyleClass().add(hervorheben ? "reminder-row" : "status-line-row");
        if (hervorheben) {
            text.getStyleClass().add("reminder-text");
        }
        if (menue != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            zeile.getChildren().addAll(spacer, menue);
        }
        return zeile;
    }

    private void speichereFrist(BewerbungseintragResponse item, Integer tage) {
        AsyncRunner.run(
                () -> bewerbungseintragService.setzeNachfassFrist(item.bewerbungseintragId(), tage),
                r -> loadApplications(),
                err -> {
                    log.error("Nachfassfrist konnte nicht gesetzt werden", err);
                    showError("Die Frist konnte nicht gespeichert werden.");
                });
    }

    private void frageEigeneFrist(BewerbungseintragResponse item) {
        TextInputDialog eingabe = new TextInputDialog(
                String.valueOf(erinnerungService.effektiveFristTage(item)));
        eingabe.setTitle("Nachfassfrist");
        eingabe.setHeaderText("Nach wie vielen Tagen erinnern?");
        eingabe.setContentText("Tage:");
        DialogStyler.apply(eingabe.getDialogPane(), themeService.isDark());
        eingabe.showAndWait().ifPresent(wert -> {
            try {
                speichereFrist(item, Integer.parseInt(wert.trim()));
            } catch (NumberFormatException ex) {
                showError("Bitte eine Zahl eingeben.");
            }
        });
    }

    private String formatiere(LocalDate datum) {
        return datum == null ? "unbekannt" : datum.format(ErinnerungService.DATUM);
    }

    /** Auffaellige, aber nicht aufdringliche Hinweiszeile auf der Karte. */
    private HBox buildErinnerungsZeile(Erinnerung erinnerung) {
        FontIcon icon = new FontIcon(erinnerung.art() == Erinnerung.Art.ANSCHREIBEN_FEHLT
                ? "far-file-alt"
                : "far-clock");
        icon.setIconSize(12);
        icon.getStyleClass().add("reminder-icon");

        Label text = new Label(erinnerung.text());
        text.getStyleClass().add("reminder-text");
        text.setWrapText(true);

        HBox zeile = new HBox(8, icon, text);
        zeile.setAlignment(Pos.CENTER_LEFT);
        zeile.getStyleClass().add("reminder-row");
        return zeile;
    }

    /**
     * Einstellungen: globaler Standard für die Nachfassfrist und der Schalter für die
     * Anschreiben-Erinnerung. Gelten für die gerade geöffnete Datenbank.
     */
    @FXML
    public void handleOpenSettings() {
        Label title = new Label("Einstellungen");
        title.getStyleClass().add("overlay-title");

        Label subtitle = new Label("Gelten für " + (demoSession ? "den Demo-Zugang" : "Ihr Konto") + ".");
        subtitle.getStyleClass().add("overlay-subtitle");
        subtitle.setWrapText(true);

        // ── Erinnerungen ──
        TextField fristInput = new TextField(String.valueOf(einstellungService.getNachfassFristTage()));
        fristInput.getStyleClass().add("form-input");

        ComboBox<ErinnerungsIntervall> intervallBox = new ComboBox<>();
        intervallBox.getItems().setAll(ErinnerungsIntervall.values());
        intervallBox.setValue(einstellungService.getEntwurfIntervall());
        intervallBox.setMaxWidth(Double.MAX_VALUE);
        intervallBox.getStyleClass().add("form-input");

        CheckBox anschreibenBox = new CheckBox("An fehlendes Anschreiben erinnern");
        anschreibenBox.getStyleClass().add("attachment-check");
        anschreibenBox.setSelected(einstellungService.isErinnerungAnschreibenAktiv());


        // ── Benachrichtigungen ──
        CheckBox benachrichtigungBox = new CheckBox("Windows-Benachrichtigungen anzeigen");
        benachrichtigungBox.getStyleClass().add("attachment-check");
        benachrichtigungBox.setSelected(einstellungService.isBenachrichtigungenAktiv());
        benachrichtigungBox.setDisable(!trayService.istVerfuegbar());

        String benachrichtigungHinweis = trayService.istVerfuegbar()
                ? "Erinnerungen erscheinen im Infobereich, solange die App läuft."
                : "Dieses System bietet keinen Infobereich - Benachrichtigungen sind nicht möglich.";

        ComboBox<String> schliessenBox = new ComboBox<>();
        schliessenBox.getItems().setAll("FRAGEN", "TRAY", "BEENDEN");
        schliessenBox.setValue(einstellungService.getSchliessenVerhalten());
        schliessenBox.setMaxWidth(Double.MAX_VALUE);
        schliessenBox.getStyleClass().add("form-input");
        schliessenBox.setButtonCell(schliessenZelle());
        schliessenBox.setCellFactory(lv -> schliessenZelle());

        // ── Sicherheit ──
        TextField sperreInput = new TextField(String.valueOf(einstellungService.getAutoSperreMinuten()));
        sperreInput.getStyleClass().add("form-input");


        // ── Autostart ──
        CheckBox autostartBox = new CheckBox("Mit Windows starten");
        autostartBox.getStyleClass().add("attachment-check");
        autostartBox.setSelected(einstellungService.isAutostartAktiv());
        autostartBox.setDisable(!AutostartService.istVerfuegbar());

        String autostartHinweis = AutostartService.istVerfuegbar()
                ? "Die App startet mit Windows in den Infobereich. Was ansteht, sieht sie erst nach "
                        + "Ihrer Anmeldung - vorher ist die Datenbank verschlüsselt."
                : AutostartService.nichtVerfuegbarGrund();

        Label inlineError = new Label();
        inlineError.getStyleClass().add("error-label");
        inlineError.setVisible(false);
        inlineError.setManaged(false);

        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("ghost-btn");
        cancel.setOnAction(e -> hideFormOverlay());

        Button submit = new Button("Speichern");
        submit.getStyleClass().add("btn-primary");
        submit.setOnAction(e -> {
            int tage;
            int sperre;
            try {
                tage = Integer.parseInt(fristInput.getText().trim());
                sperre = Integer.parseInt(sperreInput.getText().trim());
            } catch (NumberFormatException ex) {
                inlineError.setText("Bitte Zahlen für Frist und Sperrzeit eingeben.");
                inlineError.setVisible(true);
                inlineError.setManaged(true);
                return;
            }

            einstellungService.setNachfassFristTage(tage);
            einstellungService.setEntwurfIntervall(intervallBox.getValue());
            einstellungService.setErinnerungAnschreibenAktiv(anschreibenBox.isSelected());
            einstellungService.setBenachrichtigungenAktiv(benachrichtigungBox.isSelected());
            einstellungService.setSchliessenVerhalten(schliessenBox.getValue());
            einstellungService.setAutoSperreMinuten(sperre);

            // Der Registrierungseintrag ist die Wahrheit - die Einstellung folgt ihm, damit
            // der Haken nicht etwas behauptet, was gar nicht eingetragen wurde.
            boolean autostart = autostartBox.isSelected();
            if (autostart != einstellungService.isAutostartAktiv()) {
                boolean erfolg = AutostartService.setzen(autostart);
                einstellungService.setAutostartAktiv(erfolg && autostart);
                if (!erfolg && autostart) {
                    inlineError.setText("Autostart konnte nicht eingerichtet werden.");
                    inlineError.setVisible(true);
                    inlineError.setManaged(true);
                    return;
                }
            }

            hideFormOverlay();
            appShell.einstellungenUebernommen();
            // Erinnerungen haengen an diesen Werten - Liste und Kacheln neu aufbauen.
            loadApplications();
        });

        HBox actions = new HBox(10, cancel, submit);
        actions.getStyleClass().add("admin-overlay-actions");

        VBox content = new VBox(18,
                title, subtitle,
                abschnitt("Erinnerungen"),
                einstellung("Nachfassen nach", mitEinheit(fristInput, "Tagen"),
                        "Gilt für Bewerbungen ohne eigene Frist - auf jeder Karte einzeln überschreibbar."),
                einstellung("Entwürfe erinnern", intervallBox,
                        "Wie oft sich ein liegengebliebener Entwurf meldet."),
                schalter(anschreibenBox, null),
                abschnitt("Benachrichtigungen"),
                schalter(benachrichtigungBox, benachrichtigungHinweis),
                einstellung("Beim Schließen des Fensters", schliessenBox, null),
                abschnitt("Sicherheit"),
                einstellung("Automatisch sperren nach", mitEinheit(sperreInput, "Minuten"),
                        "Liegt die App so lange ungenutzt im Infobereich, wird die verschlüsselte "
                                + "Datenbank geschlossen und das Passwort erneut verlangt. 0 = nie sperren."),
                schalter(autostartBox, autostartHinweis),
                inlineError, actions
        );
        content.getStyleClass().add("admin-overlay-panel");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        // An die Fensterhoehe binden: so nutzt der Dialog den vorhandenen Platz und
        // scrollt nur, wenn der Inhalt wirklich nicht hineinpasst.
        scroll.maxHeightProperty().bind(formOverlay.heightProperty().multiply(0.85));
        showOverlayContent(scroll, true);
        Platform.runLater(fristInput::requestFocus);
    }

    /**
     * Speichert die Bewerbungen als Markdown-Tabelle.
     *
     * <p>Exportiert wird bewusst {@code applicationList.getItems()} und nicht der gesamte
     * Bestand: Das ist genau die gefilterte Ansicht, also wirken Suche und Statusfilter sich
     * aus. Einmal filtern, einmal exportieren.
     */
    @FXML
    public void handleExportMarkdown() {
        List<BewerbungseintragResponse> angezeigt = List.copyOf(applicationList.getItems());
        if (angezeigt.isEmpty()) {
            showInfo("Es gibt nichts zu exportieren.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Bewerbungen als Markdown speichern");
        chooser.setInitialFileName(markdownExportService.buildFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown-Dateien", "*.md"));
        File target = chooser.showSaveDialog(statsGrid.getScene().getWindow());
        if (target == null) {
            return;
        }

        AsyncRunner.run(
                () -> {
                    try {
                        Files.writeString(target.toPath(), markdownExportService.export(angezeigt),
                                java.nio.charset.StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        // AsyncRunner nimmt nur ungeprueft geworfene Ausnahmen entgegen;
                        // der Fehlerzweig unten meldet sie dem Nutzer.
                        throw new java.io.UncheckedIOException(e);
                    }
                    return null;
                },
                ignored -> showInfo(angezeigt.size() + " Bewerbung(en) gespeichert: " + target.getName()),
                error -> {
                    log.error("Markdown-Export fehlgeschlagen", error);
                    showError("Die Datei konnte nicht gespeichert werden.");
                }
        );
    }

    /**
     * Speichert die Vorlage, mit der sich die Bewerbungstabelle von außen befüllen lässt.
     *
     * <p>Gedacht zum Weiterreichen: Wer eine Stellenrecherche erledigt — ein Mensch oder eine
     * KI —, bekommt damit die Tabelle samt Anweisung, welche Spalten Pflicht sind. Das
     * ausgefüllte Ergebnis liest {@link #handleImportMarkdown()} wieder ein.
     */
    @FXML
    public void handleVorlageSpeichern() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Vorlage speichern");
        chooser.setInitialFileName(markdownExportService.buildVorlageFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown-Dateien", "*.md"));
        File target = chooser.showSaveDialog(statsGrid.getScene().getWindow());
        if (target == null) {
            return;
        }

        AsyncRunner.run(
                () -> {
                    try {
                        Files.writeString(target.toPath(), markdownExportService.vorlage(),
                                java.nio.charset.StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                    return null;
                },
                ignored -> showInfo("Vorlage gespeichert: " + target.getName()
                        + "\n\nSie enthält die Anweisung und eine leere Tabelle. Ausgefüllt lässt "
                        + "sie sich über \"Aus Markdown importieren\" wieder einlesen."),
                error -> {
                    log.error("Vorlage konnte nicht gespeichert werden", error);
                    showError("Die Vorlage konnte nicht gespeichert werden.");
                }
        );
    }

    /**
     * Liest eine Markdown-Tabelle ein und legt daraus Bewerbungen an.
     *
     * <p>Das Gegenstück zum Export: dieselbe Tabelle, nur in die andere Richtung. Angelegt wird
     * alles Verwertbare; was schon da ist oder nicht taugt, steht hinterher im Bericht.
     */
    @FXML
    public void handleImportMarkdown() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Bewerbungen aus Markdown einlesen");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown-Dateien", "*.md"));
        File quelle = chooser.showOpenDialog(statsGrid.getScene().getWindow());
        if (quelle == null) {
            return;
        }

        AsyncRunner.run(
                () -> {
                    try {
                        return markdownImportService.importiere(Files.readString(
                                quelle.toPath(), java.nio.charset.StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                },
                ergebnis -> {
                    loadApplications();
                    zeigeImportBericht(ergebnis);
                },
                error -> {
                    // AsyncRunner reicht die Ursache in einer CompletionException herein - ohne
                    // Auspacken bliebe die eigentliche Meldung unsichtbar.
                    Throwable ursache = error.getCause() != null ? error.getCause() : error;
                    log.error("Markdown-Import fehlgeschlagen", ursache);
                    if (ursache instanceof AppException fachlich) {
                        showError(fachlich.getMessage());
                    } else {
                        showError("Die Datei konnte nicht gelesen werden.");
                    }
                }
        );
    }

    /**
     * Meldet, was der Import bewirkt hat.
     *
     * <p>Ging alles glatt, genügt ein Satz. Gab es Übersprungene oder unbrauchbare Zeilen,
     * werden sie einzeln aufgeführt — eine bloße Zahl ließe den Nutzer raten, welche Zeile
     * gemeint ist.
     */
    private void zeigeImportBericht(ImportErgebnis ergebnis) {
        String kopf = ergebnis.angelegt() == 1
                ? "1 Bewerbung angelegt."
                : ergebnis.angelegt() + " Bewerbungen angelegt.";

        if (ergebnis.ohneBeanstandung()) {
            showInfo(kopf);
            return;
        }

        Label title = new Label("Import abgeschlossen");
        title.getStyleClass().add("overlay-title");

        StringBuilder zusammenfassung = new StringBuilder(kopf);
        if (!ergebnis.uebersprungen().isEmpty()) {
            zusammenfassung.append("  ").append(ergebnis.uebersprungen().size())
                    .append(" übersprungen.");
        }
        if (!ergebnis.hinweise().isEmpty()) {
            zusammenfassung.append("  ").append(ergebnis.hinweise().size())
                    .append(ergebnis.hinweise().size() == 1 ? " Zeile" : " Zeilen")
                    .append(" nicht verwertbar.");
        }
        Label subtitle = new Label(zusammenfassung.toString());
        subtitle.getStyleClass().add("overlay-subtitle");
        subtitle.setWrapText(true);

        VBox content = new VBox(18, title, subtitle);
        content.getStyleClass().add("admin-overlay-panel");
        zeilenblock(content, "Übersprungen — Firma und Stelle gibt es bereits",
                ergebnis.uebersprungen());
        zeilenblock(content, "Nicht verwertbar", ergebnis.hinweise());

        Button schliessen = new Button("Verstanden");
        schliessen.getStyleClass().add("btn-primary");
        schliessen.setOnAction(e -> hideFormOverlay());
        HBox actions = new HBox(10, schliessen);
        actions.getStyleClass().add("admin-overlay-actions");
        content.getChildren().add(actions);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        scroll.maxHeightProperty().bind(formOverlay.heightProperty().multiply(0.85));
        showOverlayContent(scroll, true);
    }

    /** Eine überschriebene Liste von Meldungen; bei leerer Liste passiert nichts. */
    private void zeilenblock(VBox ziel, String ueberschrift, List<String> meldungen) {
        if (meldungen.isEmpty()) {
            return;
        }
        VBox block = new VBox(4);
        Label kopf = new Label(ueberschrift);
        kopf.getStyleClass().add("form-label");
        block.getChildren().add(kopf);
        for (String meldung : meldungen) {
            Label zeile = new Label(meldung);
            zeile.getStyleClass().add("form-hint");
            zeile.setWrapText(true);
            block.getChildren().add(zeile);
        }
        ziel.getChildren().add(block);
    }

    /**
     * Ein Einstellungsblock: Bezeichnung, Bedienelement und optionaler Hinweis dicht
     * beieinander.
     *
     * <p>Ohne diese Gruppierung haben alle Elemente denselben Abstand, und eine Bezeichnung
     * gehört optisch nicht mehr zu ihrem Feld - man sieht nur noch eine Zahl.
     */
    private VBox einstellung(String bezeichnung, Node bedienelement, String hinweis) {
        Label label = new Label(bezeichnung);
        label.getStyleClass().add("form-label");

        VBox block = new VBox(6, label, bedienelement);
        if (hinweis != null) {
            block.getChildren().add(hinweisLabel(hinweis));
        }
        return block;
    }

    /** Schalter mit optionalem Hinweis darunter. */
    private VBox schalter(CheckBox box, String hinweis) {
        VBox block = new VBox(6, box);
        if (hinweis != null) {
            block.getChildren().add(hinweisLabel(hinweis));
        }
        return block;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private Label hinweisLabel(String text) {
        Label hinweis = new Label(text);
        hinweis.getStyleClass().add("form-hint");
        hinweis.setWrapText(true);
        return hinweis;
    }

    /**
     * Zahlenfeld mit der Einheit daneben.
     *
     * <p>Eine nackte "30" sagt niemandem, ob Minuten, Tage oder Stunden gemeint sind. Die
     * Einheit gehört an die Zahl, nicht in eine Klammer in der Überschrift.
     */
    private HBox mitEinheit(TextField feld, String einheit) {
        feld.setPrefWidth(90);
        feld.setMaxWidth(90);

        Label label = new Label(einheit);
        label.getStyleClass().add("form-unit");

        HBox zeile = new HBox(10, feld, label);
        zeile.setAlignment(Pos.CENTER_LEFT);
        return zeile;
    }

    private Label abschnitt(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-section-title");
        return label;
    }

    /** Zeigt die gespeicherten Schlüsselwoerter als verstaendlichen Text an. */
    private ListCell<String> schliessenZelle() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String wert, boolean empty) {
                super.updateItem(wert, empty);
                if (empty || wert == null) {
                    setText(null);
                    return;
                }
                setText(switch (wert) {
                    case "TRAY" -> "In den Infobereich legen";
                    case "BEENDEN" -> "Anwendung beenden";
                    default -> "Jedes Mal fragen";
                });
            }
        };
    }

    @FXML
    public void handleCreateUser() {
        if (!demoSession) {
            showInfo("Sie arbeiten bereits mit Ihrem eigenen Konto.");
            return;
        }
        if (sessionService.isAccountConfigured()) {
            showInfo("Es ist bereits ein Konto eingerichtet. Diese App ist für genau einen Zugang gedacht.");
            return;
        }

        Label title = new Label("Eigenes Konto einrichten");
        title.getStyleClass().add("overlay-title");

        Label subtitle = new Label(
                "Ihr Konto bekommt eine eigene, verschlüsselte Datenbank. Sie startet leer - die Musterdaten "
                        + "bleiben im Demo-Zugang zurück.");
        subtitle.getStyleClass().add("overlay-subtitle");
        subtitle.setWrapText(true);

        Label warning = new Label(
                "Wichtig: Anschliessend wird einmalig ein Masterpasswort erzeugt - bitte die PDF sicher aufbewahren. "
                        + "Ihre Daten sind verschlüsselt; gehen Passwort UND Masterpasswort verloren, sind sie "
                        + "endgültig nicht mehr lesbar. In die App selbst kommen Sie dann weiterhin über "
                        + InitialAccess.USERNAME + " / " + InitialAccess.PASSWORD + ".");
        warning.getStyleClass().add("overlay-warning");
        warning.setWrapText(true);

        TextField usernameInput = new TextField();
        usernameInput.setPromptText("Benutzername");
        usernameInput.getStyleClass().add("form-input");

        PasswordField passwordInput = new PasswordField();
        passwordInput.setPromptText("Passwort (mind. 6 Zeichen)");
        passwordInput.getStyleClass().add("form-input");

        Label inlineError = new Label();
        inlineError.getStyleClass().add("error-label");
        inlineError.setVisible(false);
        inlineError.setManaged(false);

        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("ghost-btn");
        cancel.setOnAction(e -> hideFormOverlay());

        Button submit = new Button("Konto einrichten");
        submit.getStyleClass().add("btn-primary");
        submit.setOnAction(e -> {
            String username = usernameInput.getText().trim();
            String password = passwordInput.getText();
            if (username.isBlank()) {
                inlineError.setText("Benutzername ist erforderlich.");
                inlineError.setManaged(true);
                inlineError.setVisible(true);
                return;
            }
            if (password.length() < 6) {
                inlineError.setText("Das Passwort muss mindestens 6 Zeichen haben.");
                inlineError.setManaged(true);
                inlineError.setVisible(true);
                return;
            }
            submit.setDisable(true);
            inlineError.setManaged(false);
            inlineError.setVisible(false);
            try {
                DesktopSessionService.CreatedUserCredentials created = sessionService.completeSetup(username, password);
                showCreatedUserResult(created);
            } catch (IllegalArgumentException ex) {
                submit.setDisable(false);
                inlineError.setText(ex.getMessage());
                inlineError.setManaged(true);
                inlineError.setVisible(true);
            } catch (Exception ex) {
                submit.setDisable(false);
                log.error("Konto einrichten fehlgeschlagen", ex);
                inlineError.setText("Fehler beim Einrichten des Kontos.");
                inlineError.setManaged(true);
                inlineError.setVisible(true);
            }
        });

        HBox actions = new HBox(10, cancel, submit);
        actions.getStyleClass().add("admin-overlay-actions");

        VBox content = new VBox(12,
                title,
                subtitle,
                warning,
                new Label("Benutzername"), usernameInput,
                new Label("Passwort"), passwordInput,
                inlineError,
                actions
        );
        content.getStyleClass().add("admin-overlay-panel");
        showOverlayContent(content, true);
        Platform.runLater(usernameInput::requestFocus);
    }

    @FXML
    public void handleChangePassword() {
        PasswordField currentPw = new PasswordField();
        currentPw.setPromptText("Aktuelles Passwort");
        currentPw.getStyleClass().add("form-input");
        PasswordField newPw = new PasswordField();
        newPw.setPromptText("Neues Passwort (mind. 8 Zeichen)");
        newPw.getStyleClass().add("form-input");
        PasswordField confirmPw = new PasswordField();
        confirmPw.setPromptText("Neues Passwort bestätigen");
        confirmPw.getStyleClass().add("form-input");

        Label inlineError = new Label();
        inlineError.getStyleClass().add("error-label");
        inlineError.setVisible(false);
        inlineError.setManaged(false);

        VBox content = new VBox(10,
                formLabel("Aktuelles Passwort"), currentPw,
                formLabel("Neues Passwort"), newPw,
                formLabel("Bestätigung"), confirmPw,
                inlineError);
        content.getStyleClass().addAll("admin-overlay-panel", "overlay-form-stack");

        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("ghost-btn");
        cancel.setOnAction(e -> hideFormOverlay());

        Button submit = new Button("Passwort wechseln");
        submit.getStyleClass().add("btn-primary");
        submit.setOnAction(e -> {
            inlineError.setManaged(false);
            inlineError.setVisible(false);
            if (!newPw.getText().equals(confirmPw.getText())) {
                inlineError.setText("Die neuen Passwörter stimmen nicht überein.");
                inlineError.setManaged(true);
                inlineError.setVisible(true);
                return;
            }
            if (newPw.getText().length() < 8) {
                inlineError.setText("Das neue Passwort muss mindestens 8 Zeichen haben.");
                inlineError.setManaged(true);
                inlineError.setVisible(true);
                return;
            }
            submit.setDisable(true);
            try {
                sessionService.changePassword(currentPw.getText(), newPw.getText());
                hideFormOverlay();
                showInfo("Passwort erfolgreich geändert.");
            } catch (IllegalArgumentException ex) {
                submit.setDisable(false);
                inlineError.setText("Das aktuelle Passwort ist falsch.");
                inlineError.setManaged(true);
                inlineError.setVisible(true);
            } catch (Exception ex) {
                submit.setDisable(false);
                log.error("Passwort ändern fehlgeschlagen", ex);
                inlineError.setText("Fehler beim Ändern des Passworts.");
                inlineError.setManaged(true);
                inlineError.setVisible(true);
            }
        });

        HBox actions = new HBox(10, cancel, submit);
        actions.getStyleClass().add("admin-overlay-actions");
        content.getChildren().add(actions);
        showOverlayContent(content, true);
        Platform.runLater(currentPw::requestFocus);
    }

    /**
     * Loescht das echte Konto - der Notausgang, wenn Passwort und Masterpasswort verloren sind.
     *
     * <p>Die Daten sind danach endgültig weg: Ohne Schlüssel-Tresor lässt sich die
     * verschlüsselte Datei nicht mehr öffnen. Deshalb muss die Bestätigung abgetippt werden -
     * ein Klick aus Versehen darf das nicht auslösen.
     */
    @FXML
    public void handleResetData() {
        if (!demoSession || !sessionService.isAccountConfigured()) {
            showInfo("Es ist kein eigenes Konto vorhanden, das gelöscht werden könnte.");
            return;
        }

        Label title = new Label("Eigenes Konto löschen");
        title.getStyleClass().add("overlay-title");

        Label subtitle = new Label("Nur noetig, wenn Passwort und Masterpasswort verloren sind.");
        subtitle.getStyleClass().add("overlay-subtitle");
        subtitle.setWrapText(true);

        Label warning = new Label(
                "Alle Bewerbungen und Dokumente des eigenen Kontos werden gelöscht und sind danach "
                        + "endgültig nicht mehr herstellbar - auch nicht mit dem Passwort oder Masterpasswort. "
                        + "Der Demo-Zugang und seine Musterdaten bleiben erhalten.");
        warning.getStyleClass().add("overlay-warning");
        warning.setWrapText(true);

        Label prompt = new Label("Zum Bestätigen bitte eintippen: " + CONFIRM_DELETE_PHRASE);
        prompt.getStyleClass().add("form-label");
        prompt.setWrapText(true);

        TextField confirmInput = new TextField();
        confirmInput.setPromptText(CONFIRM_DELETE_PHRASE);
        confirmInput.getStyleClass().add("form-input");

        Label inlineError = new Label();
        inlineError.getStyleClass().add("error-label");
        inlineError.setVisible(false);
        inlineError.setManaged(false);

        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("ghost-btn");
        cancel.setOnAction(e -> hideFormOverlay());

        Button submit = new Button("Konto endgültig löschen");
        submit.getStyleClass().add("btn-danger");
        submit.setOnAction(e -> {
            if (!CONFIRM_DELETE_PHRASE.equals(confirmInput.getText().trim())) {
                inlineError.setText("Bitte genau " + CONFIRM_DELETE_PHRASE + " eintippen.");
                inlineError.setVisible(true);
                inlineError.setManaged(true);
                return;
            }
            submit.setDisable(true);
            AsyncRunner.run(
                    () -> {
                        sessionService.deleteRealAccount();
                        return null;
                    },
                    ignored -> {
                        hideFormOverlay();
                        applySessionUiState();
                        showInfo("Das eigene Konto wurde gelöscht. Sie können jetzt ein neues einrichten.");
                    },
                    error -> {
                        submit.setDisable(false);
                        log.error("Konto löschen fehlgeschlagen", error);
                        showError("Fehler beim Löschen des Kontos.");
                    }
            );
        });

        HBox actions = new HBox(10, cancel, submit);
        actions.getStyleClass().add("admin-overlay-actions");

        VBox content = new VBox(12, title, subtitle, warning, prompt, confirmInput, inlineError, actions);
        content.getStyleClass().add("admin-overlay-panel");
        showOverlayContent(content, true);
        Platform.runLater(confirmInput::requestFocus);
    }

    private void showCreatedUserResult(DesktopSessionService.CreatedUserCredentials created) {
        Label title = new Label("Masterpasswort erzeugt");
        title.getStyleClass().add("overlay-title");

        Label subtitle = new Label(
                "Bitte das Masterpasswort jetzt speichern - es ist der einzige Ersatz für ein vergessenes "
                        + "Passwort. Danach melden Sie sich mit Ihrem neuen Konto an.");
        subtitle.getStyleClass().add("overlay-subtitle");
        subtitle.setWrapText(true);

        Label credentials = new Label("Benutzer: " + created.username() + "\nMasterpasswort: " + created.masterPassword());
        credentials.getStyleClass().add("overlay-warning");
        credentials.setWrapText(true);

        Button download = new Button("PDF speichern");
        download.getStyleClass().add("btn-primary");
        download.setOnAction(e -> saveMasterPasswordPdf(created));

        Button relogin = new Button("Zur Anmeldung");
        relogin.getStyleClass().add("ghost-btn");
        relogin.setOnAction(e -> {
            hideFormOverlay();
            sessionService.logout();
            appShell.logout();
        });

        HBox actions = new HBox(10, relogin, download);
        actions.getStyleClass().add("admin-overlay-actions");

        VBox content = new VBox(12, title, subtitle, credentials, actions);
        content.getStyleClass().add("admin-overlay-panel");
        showOverlayContent(content, true);
    }

    private void saveMasterPasswordPdf(DesktopSessionService.CreatedUserCredentials created) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Masterpasswort speichern");
        chooser.setInitialFileName(created.pdfFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF-Dateien", "*.pdf"));
        File target = chooser.showSaveDialog(statsGrid.getScene().getWindow());
        if (target == null) {
            return;
        }
        try {
            Files.write(target.toPath(), created.pdfBytes());
            statusBarLabel.setText("Masterpasswort-PDF gespeichert: " + target.getName());
        } catch (IOException e) {
            log.error("Masterpasswort-PDF konnte nicht gespeichert werden", e);
            showError("Masterpasswort-PDF konnte nicht gespeichert werden.");
        }
    }

    private void showOverlayContent(Parent content, boolean compact) {
        hideConfirmOverlay();
        if (overlayShell != null) {
            overlayShell.getStyleClass().remove("overlay-shell-compact");
            if (compact) {
                overlayShell.prefWidthProperty().unbind();
                overlayShell.maxWidthProperty().unbind();
                overlayShell.setPrefWidth(520);
                overlayShell.setMaxWidth(520);
                overlayShell.setMaxHeight(Region.USE_PREF_SIZE);
                overlayShell.getStyleClass().add("overlay-shell-compact");
            } else {
                restoreResponsiveOverlaySize();
            }
        }
        if (overlayCloseButton != null) {
            overlayCloseButton.setVisible(!compact);
            overlayCloseButton.setManaged(!compact);
        }
        if (formOverlayContent != null) {
            VBox.setVgrow(formOverlayContent, compact ? Priority.NEVER : Priority.ALWAYS);
            formOverlayContent.setMaxHeight(compact ? Region.USE_PREF_SIZE : Double.MAX_VALUE);
        }
        formOverlayContent.getChildren().setAll(content);
        formOverlay.setVisible(true);
        formOverlay.setManaged(true);
    }

    private void restoreOverlayDefaults() {
        if (overlayShell != null) {
            overlayShell.getStyleClass().remove("overlay-shell-compact");
            overlayShell.setMaxHeight(900);
            restoreResponsiveOverlaySize();
        }
        if (overlayCloseButton != null) {
            overlayCloseButton.setVisible(true);
            overlayCloseButton.setManaged(true);
        }
        if (formOverlayContent != null) {
            VBox.setVgrow(formOverlayContent, Priority.ALWAYS);
            formOverlayContent.setMaxHeight(Double.MAX_VALUE);
        }
    }

    private void restoreResponsiveOverlaySize() {
        if (overlayShell != null && formOverlay != null) {
            overlayShell.prefWidthProperty().unbind();
            overlayShell.maxWidthProperty().unbind();
            overlayShell.maxWidthProperty().bind(formOverlay.widthProperty().multiply(0.96));
            overlayShell.prefWidthProperty().bind(formOverlay.widthProperty().multiply(0.92));
        }
    }

    // =====================================================================
    //  Hilfsmethoden
    // =====================================================================

    private boolean containsIgnoreCase(String text, String query) {
        return text != null && text.toLowerCase().contains(query);
    }

    private static String nvl(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v : fallback;
    }

    // =====================================================================
    //  Zwischenablage und Systemprogramme
    //
    //  Die Anwendung selbst verbindet sich weiterhin nirgendwohin. Sie reicht auf Klick eine
    //  Adresse an Browser oder Mailprogramm weiter - das ist eine Handlung des Nutzers, kein
    //  Netzverkehr der Anwendung. Siehe SICHERHEIT.md.
    // =====================================================================

    private void inDieZwischenablage(String wert, String was) {
        ClipboardContent inhalt = new ClipboardContent();
        inhalt.putString(wert);
        Clipboard.getSystemClipboard().setContent(inhalt);
        statusBarLabel.setText(was + " kopiert.");
    }

    private void oeffneImBrowser(String url) {
        // Wer den Link von Hand eingetragen hat, laesst das Schema gern weg.
        String vollstaendig = url.matches("(?i)^[a-z][a-z0-9+.-]*://.*") ? url : "https://" + url;
        oeffne(Desktop.Action.BROWSE,
                () -> Desktop.getDesktop().browse(URI.create(vollstaendig)),
                "Der Link konnte nicht geöffnet werden.", url, "Link");
    }

    private void oeffneMailprogramm(String adresse) {
        oeffne(Desktop.Action.MAIL,
                () -> Desktop.getDesktop().mail(URI.create("mailto:" + adresse)),
                "Das Mailprogramm konnte nicht geöffnet werden.", adresse, "E-Mail-Adresse");
    }

    private void oeffne(Desktop.Action aktion, DesktopAufruf aufruf, String fehlertext,
                        String wert, String was) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(aktion)) {
            // Kein Browser oder Mailprogramm ansprechbar: dann wenigstens kopieren, statt den
            // Klick ins Leere laufen zu lassen.
            inDieZwischenablage(wert, was);
            return;
        }
        // Der Aufruf kann blockieren, bis das andere Programm oben ist - deshalb nicht auf dem
        // FX-Thread, sonst friert das Fenster so lange ein.
        AsyncRunner.run(
                () -> {
                    try {
                        aufruf.ausfuehren();
                    } catch (IOException | RuntimeException e) {
                        throw new IllegalStateException(fehlertext, e);
                    }
                },
                () -> { },
                err -> {
                    log.error(fehlertext, err);
                    showError(fehlertext);
                });
    }

    @FunctionalInterface
    private interface DesktopAufruf {
        void ausfuehren() throws IOException;
    }

    void showError(String msg) {
        Platform.runLater(() -> {
            overlayPresenter.show(OverlayRequest.danger(
                    "Fehler",
                    "Aktion konnte nicht abgeschlossen werden.",
                    msg,
                    List.of(OverlayAction.primary("Verstanden", null))
            ));
        });
    }

    private void showInfo(String msg) {
        Platform.runLater(() -> {
            overlayPresenter.show(OverlayRequest.info(
                    "Hinweis",
                    "",
                    msg,
                    OverlayAction.primary("Verstanden", null)
            ));
        });
    }

    // =====================================================================
    //  Custom ListCell — zeigt Karte mit Fortschrittsbalken + Löschen
    // =====================================================================

    private class ApplicationCell extends ListCell<BewerbungseintragResponse> {

        @Override
        protected void updateItem(BewerbungseintragResponse item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null); setGraphic(null);
                setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
                return;
            }
            setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

            VBox card = new VBox(8);
            card.getStyleClass().add("application-card");
            // Karte muss ListView-Breite kennen, damit HGrow im Header funktioniert
            if (getListView() != null) {
                card.prefWidthProperty().bind(getListView().widthProperty().subtract(32));
            }

            // ── Kopfzeile: Icon + Firma ──
            StackPane companyIconWrap = new StackPane();
            companyIconWrap.getStyleClass().add("application-company-icon-wrap");
            FontIcon companyIcon = new FontIcon("fas-briefcase");
            companyIcon.setIconSize(14);
            companyIcon.getStyleClass().add("application-company-icon");
            companyIconWrap.getChildren().add(companyIcon);

            Label company = new Label(nvl(item.firmaName(), "Unbekanntes Unternehmen"));
            company.getStyleClass().add("application-company");
            HBox.setHgrow(company, Priority.ALWAYS);

            VBox titleBlock = new VBox(4);
            titleBlock.getStyleClass().add("application-title-block");
            titleBlock.getChildren().add(company);

            // Status-Badge als Button (öffnet ContextMenu)
            String statusText = item.statusTitel() != null ? item.statusTitel() : "—";
            Button statusBtn = new Button(statusText + " ");
            statusBtn.getStyleClass().addAll("card-status-btn", statusBadgeStyle(item.statusTitel()));
            statusBtn.setGraphic(new FontIcon("fas-chevron-down"));
            statusBtn.setContentDisplay(ContentDisplay.RIGHT);
            statusBtn.setGraphicTextGap(6);

            ContextMenu statusMenu = new ContextMenu();
            statusMenu.getStyleClass().add("status-context-menu");
            for (StatusEntity s : allStatuses) {
                MenuItem mi = new MenuItem(s.getTitel());
                mi.getStyleClass().add("status-context-item");
                mi.setOnAction(e -> {
                    if (s.getTitel().equalsIgnoreCase(item.statusTitel())) return;
                    PatchBewerbungseintragRequest req = new PatchBewerbungseintragRequest(
                            null, null, null, s.getStatusId(),
                            null, null, null, null, null, null, null,
                            null, null, null, null, null, null, null, null,
                            null, null, null, null);
                    AsyncRunner.run(
                            () -> bewerbungseintragService.patchBewerbungseintrag(item.bewerbungseintragId(), req),
                            r -> loadApplications(),
                            err -> log.error("Status-Update fehlgeschlagen", err));
                });
                statusMenu.getItems().add(mi);
            }
            statusBtn.setOnAction(e -> {
                e.consume();
                statusMenu.show(statusBtn, javafx.geometry.Side.BOTTOM, 0, 4);
            });

            Button deleteBtn = new Button();
            deleteBtn.getStyleClass().add("card-delete-btn");
            FontIcon trash = new FontIcon("fas-trash-alt");
            trash.setIconSize(13);
            deleteBtn.setGraphic(trash);
            deleteBtn.setOnAction(e -> { e.consume(); deleteApplication(item); });

            Region headerSpacer = new Region();
            HBox.setHgrow(headerSpacer, Priority.ALWAYS);

            HBox header = new HBox(12, companyIconWrap, titleBlock, headerSpacer, deleteBtn);
            header.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(titleBlock, Priority.ALWAYS);
            card.getChildren().add(header);

            // ── Position ──
            if (item.stellenbezeichnung() != null && !item.stellenbezeichnung().isBlank()) {
                Label pos = new Label(item.stellenbezeichnung());
                pos.getStyleClass().add("application-position");
                titleBlock.getChildren().add(pos);
            }

            // ── Meta-Zeile ──
            HBox meta = new HBox(42); meta.setAlignment(Pos.CENTER_LEFT);
            meta.getStyleClass().add("application-meta-row");
            String contact = nvl(item.ansprechpartner(), item.contactPerson());
            if (contact != null)                          meta.getChildren().add(metaItem("fas-user", contact));
            if (item.email() != null && !item.email().isBlank()) {
                String adresse = item.email();
                meta.getChildren().add(metaAktion("fas-envelope", adresse,
                        () -> oeffneMailprogramm(adresse), "E-Mail-Adresse"));
            }
            String ort = nvl(item.location(), item.standort());
            if (ort != null)                              meta.getChildren().add(metaItem("fas-map-marker-alt", ort));
            if (!meta.getChildren().isEmpty())            card.getChildren().add(meta);

            HBox metaSecondary = new HBox(42); metaSecondary.setAlignment(Pos.CENTER_LEFT);
            metaSecondary.getStyleClass().add("application-meta-row");
            if (item.url() != null && !item.url().isBlank()) {
                String adresse = item.url();
                metaSecondary.getChildren().add(metaAktion("fas-external-link-alt", adresse,
                        () -> oeffneImBrowser(adresse), "Link"));
            }
            String phone = nvl(item.telefon(), item.phone());
            // Eine Telefonnummer laesst sich am Rechner nicht sinnvoll "oeffnen" - nur kopieren.
            if (phone != null) metaSecondary.getChildren().add(metaAktion("fas-phone-alt", phone,
                    null, "Telefonnummer"));
            if (!metaSecondary.getChildren().isEmpty()) card.getChildren().add(metaSecondary);

            // Zum Status passende Zeile: Datum plus - wo es etwas zu tun gibt - ein Menue.
            card.getChildren().add(buildStatusZeile(item));

            if (erinnerungService.anschreibenFehlt(item)) {
                card.getChildren().add(buildErinnerungsZeile(new Erinnerung(
                        Erinnerung.Art.ANSCHREIBEN_FEHLT, "Noch kein Anschreiben hochgeladen")));
            }

            // ── Unterlagen-Fortschrittsbalken ──
            int cnt = 0;
            if (item.lebenslaufId()  != null && item.lebenslaufId().equals(lebenslaufId))   cnt++;
            if (item.zertifikateId() != null && item.zertifikateId().equals(zertifikateId)) cnt++;
            if (item.zeugnisseId()   != null && item.zeugnisseId().equals(zeugnisseId))     cnt++;
            if (item.anschreibenId() != null) cnt++;

            Label progressLabel = new Label("Unterlagen  (" + cnt + "/4)");
            progressLabel.getStyleClass().add("application-progress-label");

            Label progressPercent = new Label((cnt * 25) + "%");
            progressPercent.getStyleClass().add("application-progress-percent");

            HBox progressHeader = new HBox(progressLabel, new Region(), progressPercent);
            progressHeader.getStyleClass().add("application-progress-header");
            HBox.setHgrow(progressHeader.getChildren().get(1), Priority.ALWAYS);

            Pane track = new Pane();
            track.getStyleClass().add("application-progress-track");
            track.setMinHeight(12); track.setMaxHeight(12); track.setPrefHeight(12);
            Pane fill = new Pane();
            fill.setMinHeight(12); fill.setMaxHeight(12); fill.setPrefHeight(12);
            fill.setStyle("-fx-background-color:" + (cnt==4?"#16a34a":cnt>=2?"#2563eb":"#2563eb") + ";-fx-background-radius:999;");
            final int finalCnt = cnt;
            track.widthProperty().addListener((ob, ov, nv) -> fill.setPrefWidth(nv.doubleValue() * finalCnt / 4.0));
            StackPane trackStack = new StackPane(track, fill);
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            VBox.setVgrow(trackStack, Priority.NEVER);

            VBox progressBlock = new VBox(8, progressHeader, trackStack);
            progressBlock.getStyleClass().add("application-progress-block");
            card.getChildren().add(progressBlock);

            VBox noteEditor = buildInlineNoteEditor(item);

            HBox noteActions = new HBox(10, statusBtn);
            noteActions.getStyleClass().add("application-note-actions");
            noteActions.setAlignment(Pos.CENTER_RIGHT);

            HBox noteRow = new HBox(18, noteEditor, noteActions);
            noteRow.setAlignment(Pos.CENTER_LEFT);
            noteRow.getStyleClass().add("application-note-row");
            HBox.setHgrow(noteEditor, Priority.ALWAYS);
            card.getChildren().add(noteRow);

            setText(null); setGraphic(card);
        }

        private String statusBadgeStyle(String titel) {
            if (titel == null) return "card-status-gray";
            return switch (titel.toUpperCase()) {
                case "ABGESCHICKT"               -> "card-status-orange";
                case "ABSAGE", "ABSAGE_ERHALTEN" -> "card-status-red";
                case "ERFOLG"                    -> "card-status-green";
                default                          -> "card-status-gray";
            };
        }

        private HBox metaItem(String icon, String text) {
            FontIcon ic = new FontIcon(icon); ic.setIconSize(12); ic.getStyleClass().add("meta-icon");
            Label l = new Label(text); l.getStyleClass().add("application-meta-item");
            HBox box = new HBox(8, ic, l); box.setAlignment(Pos.CENTER_LEFT);
            box.getStyleClass().add("application-meta-box");
            return box;
        }

        /**
         * Wie {@link #metaItem}, aber der Wert lässt sich benutzen statt nur lesen.
         *
         * <p>Bisher stand hier reiner Text: Wer einen Link öffnen wollte, musste die Karte
         * aufklappen, den Text markieren, kopieren und im Browser einfügen — bei der E-Mail
         * dasselbe. Jetzt öffnet ein Klick direkt, und der Knopf daneben legt den Wert in die
         * Zwischenablage.
         */
        private HBox metaAktion(String icon, String text, Runnable oeffnen, String was) {
            FontIcon ic = new FontIcon(icon); ic.setIconSize(12); ic.getStyleClass().add("meta-icon");

            HBox box = new HBox(8, ic);
            box.setAlignment(Pos.CENTER_LEFT);
            box.getStyleClass().add("application-meta-box");

            if (oeffnen != null) {
                Hyperlink wert = new Hyperlink(text);
                wert.getStyleClass().add("application-meta-link");
                wert.setOnAction(e -> { e.consume(); oeffnen.run(); });
                box.getChildren().add(wert);
            } else {
                Label wert = new Label(text);
                wert.getStyleClass().add("application-meta-item");
                box.getChildren().add(wert);
            }

            Button kopieren = new Button();
            kopieren.getStyleClass().add("meta-copy-btn");
            kopieren.setGraphic(new FontIcon("far-copy"));
            kopieren.setTooltip(new Tooltip(was + " kopieren"));
            kopieren.setOnAction(e -> { e.consume(); inDieZwischenablage(text, was); });
            box.getChildren().add(kopieren);

            return box;
        }

        private VBox buildInlineNoteEditor(BewerbungseintragResponse item) {
            VBox noteEditor = new VBox(8);
            HBox.setHgrow(noteEditor, Priority.ALWAYS);
            showInlineNoteDisplay(noteEditor, item);
            return noteEditor;
        }

        private void showInlineNoteDisplay(VBox noteEditor, BewerbungseintragResponse item) {
            String preview = (item.notiz() != null && !item.notiz().isBlank())
                    ? item.notiz()
                    : "Keine Rückmeldung";
            Label notiz = new Label(preview);
            notiz.getStyleClass().add("application-note");
            notiz.setWrapText(true);
            notiz.setMaxWidth(Double.MAX_VALUE);
            notiz.setOnMouseClicked(e -> {
                e.consume();
                showInlineNoteEdit(noteEditor, item);
            });
            noteEditor.getChildren().setAll(notiz);
        }

        private void showInlineNoteEdit(VBox noteEditor, BewerbungseintragResponse item) {
            TextArea editor = new TextArea(item.notiz() != null ? item.notiz() : "");
            editor.getStyleClass().addAll("form-textarea", "inline-note-editor");
            editor.setPromptText("Rückmeldung eingeben...");
            editor.setWrapText(true);

            Button cancelEdit = new Button("Abbrechen");
            cancelEdit.getStyleClass().add("ghost-btn");
            cancelEdit.setOnAction(cancelEvent -> {
                cancelEvent.consume();
                showInlineNoteDisplay(noteEditor, item);
            });

            Button saveEdit = new Button("Notiz speichern");
            saveEdit.getStyleClass().add("btn-primary");
            saveEdit.setOnAction(saveEvent -> {
                saveEvent.consume();
                saveEdit.setDisable(true);
                String normalizedNote = editor.getText() != null && !editor.getText().trim().isBlank()
                        ? editor.getText().trim()
                        : null;
                PatchBewerbungseintragRequest req = new PatchBewerbungseintragRequest(
                        null, null, null, null,
                        null, null, null, null, null, null, null,
                        normalizedNote, null, null, null, null, null, null, null,
                        null, null, null, null);
                AsyncRunner.run(
                        () -> bewerbungseintragService.patchBewerbungseintrag(item.bewerbungseintragId(), req),
                        updated -> loadApplications(),
                        err -> {
                            log.error("Inline-Notiz konnte nicht gespeichert werden", err);
                            Platform.runLater(() -> saveEdit.setDisable(false));
                        }
                );
            });

            HBox editActions = new HBox(8, cancelEdit, saveEdit);
            editActions.getStyleClass().add("inline-note-actions");
            noteEditor.getChildren().setAll(editor, editActions);
            Platform.runLater(editor::requestFocus);
        }
    }
}
