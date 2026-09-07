package de.mkysarte.bewerbungsmanager;

import de.mkysarte.bewerbungsmanager.benachrichtigung.AutostartService;
import de.mkysarte.bewerbungsmanager.benachrichtigung.ErinnerungsBenachrichtiger;
import de.mkysarte.bewerbungsmanager.benachrichtigung.TrayService;
import de.mkysarte.bewerbungsmanager.common.AppPaths;
import de.mkysarte.bewerbungsmanager.common.FensterZustand;
import de.mkysarte.bewerbungsmanager.common.crypto.DbSession;
import de.mkysarte.bewerbungsmanager.common.crypto.VaultService;
import de.mkysarte.bewerbungsmanager.einstellung.service.EinstellungService;
import de.mkysarte.bewerbungsmanager.ui.AppShell;
import de.mkysarte.bewerbungsmanager.ui.SpringFXMLLoader;
import de.mkysarte.bewerbungsmanager.ui.ThemeService;
import de.mkysarte.bewerbungsmanager.ui.screen.LoginController;
import de.mkysarte.bewerbungsmanager.ui.util.DialogStyler;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;

/**
 * JavaFX Entry Point.
 *
 * <p>Die Reihenfolge ist hier entscheidend und unterscheidet sich von einer gewöhnlichen
 * Spring-Boot-Anwendung: <b>Erst die Anmeldung, dann Spring.</b> Die Datenbank ist
 * verschlüsselt, also braucht die DataSource den Schlüssel schon beim Hochfahren — und den
 * gibt es erst, wenn der Nutzer sein Passwort eingegeben hat.
 *
 * <p>Der Anmeldebildschirm läuft deshalb ohne Spring, mit einem gewöhnlichen
 * {@link FXMLLoader}. Erst danach startet der Context, gebunden an genau eine
 * Datenbankdatei — Demo oder echtes Konto, nie beides.
 *
 * <p>Local-First: kein HTTP-Server, keine ausgehenden Verbindungen.
 */
public class BewerbungsmanagerApp extends Application implements AppShell {

    private static final Logger log = LoggerFactory.getLogger(BewerbungsmanagerApp.class);

    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 860;

    /** Abstand zwischen zwei Erinnerungsdurchläufen im laufenden Betrieb. */
    private static final Duration PRUEF_INTERVALL = Duration.minutes(30);

    private Stage primaryStage;
    private VaultService vaultService;
    private ConfigurableApplicationContext springContext;

    private final TrayService trayService = new TrayService();
    private FensterZustand fensterZustand;
    private Timeline erinnerungsTimer;
    private Timeline sperrTimer;

    /** Mit {@code --minimized} gestartet (Autostart) — dann bleibt das Fenster zunächst zu. */
    private boolean minimiertStarten;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        this.minimiertStarten = getParameters().getRaw().contains(AutostartService.ARG_MINIMIERT);

        Files.createDirectories(AppPaths.dataDirectory());
        this.vaultService = new VaultService(AppPaths.dataDirectory());

        stage.setTitle("Bewerbungsmanager");
        fensterSymboleLaden(stage);
        stage.setMinWidth(1100);
        stage.setMinHeight(760);
        stage.setOnCloseRequest(event -> {
            event.consume();
            fensterSchliessen();
        });

        showLogin();

        // Erst nach der Szene: sonst überschreibt deren bevorzugte Größe die
        // wiederhergestellten Maße.
        fensterZustand = new FensterZustand(AppPaths.dataDirectory());
        fensterZustand.anwendenUndBeobachten(stage, WINDOW_WIDTH, WINDOW_HEIGHT);

        if (minimiertStarten) {
            // Aus dem Autostart heraus: Symbol anlegen und neutral erinnern. Was tatsaechlich
            // ansteht, weiß die App erst nach der Anmeldung - die Datenbank ist verschlüsselt.
            if (trayAnzeigen()) {
                trayService.melden("Bewerbungsmanager",
                        "Bitte anmelden, um Ihre Erinnerungen zu sehen.");
            } else {
                stage.show();
            }
        } else {
            stage.show();
        }
    }

    /**
     * Legt das Anwendungssymbol in mehreren Größen ans Fenster.
     *
     * <p>Mehrere, weil das Betriebssystem sich selbst bedient: Windows nimmt für die Titelleiste
     * eine andere Größe als für die Taskleiste, und Herunterskalieren aus der 256er sieht
     * schlechter aus als eine eigens gerechnete 16er.
     *
     * <p>Im Installer setzt jpackage das Symbol ohnehin; hierauf angewiesen sind der
     * Entwicklungsstart und die Fensterverwaltungen unter Linux. Ein fehlendes Symbol ist
     * deshalb kein Grund, den Start abzubrechen.
     */
    private void fensterSymboleLaden(Stage stage) {
        for (int groesse : new int[]{16, 32, 48, 64, 128, 256}) {
            String pfad = "/icons/icon-" + groesse + ".png";
            try (InputStream in = getClass().getResourceAsStream(pfad)) {
                if (in == null) {
                    log.debug("Anwendungssymbol {} nicht gefunden", pfad);
                    continue;
                }
                stage.getIcons().add(new Image(in));
            } catch (IOException | RuntimeException e) {
                log.debug("Anwendungssymbol {} nicht lesbar", pfad, e);
            }
        }
    }

    // =====================================================================
    //  Anmeldung (ohne Spring)
    // =====================================================================

    private void showLogin() throws IOException {
        LoginController controller = new LoginController(vaultService, this::openSession);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        // Der Controller ist bewusst kein Spring-Bean — es gibt hier noch keinen Context.
        loader.setController(controller);
        Parent view = loader.load();

        // Die Maße der bisherigen Szene übernehmen, damit ein Wechsel zwischen Anmeldung
        // und Dashboard das Fenster nicht springen lässt.
        double width = primaryStage.getScene() != null ? primaryStage.getScene().getWidth() : WINDOW_WIDTH;
        double height = primaryStage.getScene() != null ? primaryStage.getScene().getHeight() : WINDOW_HEIGHT;
        primaryStage.setScene(new Scene(view, width, height));
    }

    // =====================================================================
    //  Sitzung öffnen (ab hier mit Spring)
    // =====================================================================

    /**
     * Startet den Spring-Context für die entsperrte Datenbank und zeigt das Dashboard.
     *
     * <p>{@link DbSession}, die Anwendungshülle und der Infobereich werden als Singletons in
     * den Context gelegt, damit die Oberfläche sie ganz normal injizieren kann. Der
     * {@link TrayService} gehört bewusst der Hülle: Er überlebt das Ab- und Anmelden.
     */
    private void openSession(DbSession session) {
        try {
            springContext = new SpringApplicationBuilder(BewerbungsmanagerDesktopApplication.class)
                    .headless(false)
                    .properties("app.db.url=" + session.jdbcUrl())
                    .initializers(context -> {
                        context.getBeanFactory().registerSingleton("dbSession", session);
                        context.getBeanFactory().registerSingleton("appShell", this);
                        context.getBeanFactory().registerSingleton("trayService", trayService);
                    })
                    .run();

            Parent dashboard = springContext.getBean(SpringFXMLLoader.class).load("/fxml/Dashboard.fxml");
            primaryStage.setScene(new Scene(dashboard, primaryStage.getWidth(), primaryStage.getHeight()));
            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }

            einstellungenUebernommen();
            benachrichtiger().ifPresent(ErinnerungsBenachrichtiger::meldeUeberblick);
        } catch (Exception e) {
            closeSpringContext();
            showFatalError(e);
        }
    }

    @Override
    public void logout() {
        Platform.runLater(() -> {
            stoppeTimer();
            closeSpringContext();
            try {
                showLogin();
            } catch (IOException e) {
                showFatalError(e);
            }
        });
    }

    /**
     * Wird aufgerufen, wenn sich die Einstellungen geändert haben — legt das Symbol im
     * Infobereich an oder entfernt es und startet den Erinnerungs-Zeitgeber neu.
     */
    @Override
    public void einstellungenUebernommen() {
        boolean benachrichtigungen = einstellungen().map(EinstellungService::isBenachrichtigungenAktiv).orElse(false);
        if (benachrichtigungen) {
            trayAnzeigen();
            starteErinnerungsTimer();
        } else {
            stoppeErinnerungsTimer();
            // Das Symbol bleibt, solange die App im Infobereich liegen könnte - sonst
            // verschwindet der einzige Weg zurück ins Fenster.
            if (!primaryStage.isShowing()) {
                trayAnzeigen();
            } else if (!trayGewuenscht()) {
                trayService.entfernen();
            }
        }
    }

    // =====================================================================
    //  Infobereich, Fenster schließen, Auto-Sperre
    // =====================================================================

    private boolean trayAnzeigen() {
        return trayService.anzeigen(this::fensterZeigen, this::beenden);
    }

    /** Soll die App überhaupt im Infobereich liegen dürfen? */
    private boolean trayGewuenscht() {
        String verhalten = einstellungen().map(EinstellungService::getSchliessenVerhalten).orElse("FRAGEN");
        boolean benachrichtigungen = einstellungen().map(EinstellungService::isBenachrichtigungenAktiv).orElse(false);
        return benachrichtigungen || !"BEENDEN".equals(verhalten);
    }

    private void fensterZeigen() {
        stoppeSperrTimer();
        if (!primaryStage.isShowing()) {
            primaryStage.show();
        }
        primaryStage.setIconified(false);
        primaryStage.toFront();
        primaryStage.requestFocus();
    }

    /**
     * Beim Schließen des Fensters: entweder wirklich beenden oder in den Infobereich legen.
     *
     * <p>Die Rückfrage kommt nur, solange der Nutzer sich nicht festgelegt hat — mit
     * „Nicht mehr fragen" wird die Antwort zur Einstellung.
     */
    private void fensterSchliessen() {
        String verhalten = einstellungen().map(EinstellungService::getSchliessenVerhalten).orElse("BEENDEN");

        if ("BEENDEN".equals(verhalten) || !trayService.istVerfuegbar()) {
            beenden();
            return;
        }
        if ("TRAY".equals(verhalten)) {
            inDenInfobereich();
            return;
        }

        Alert frage = new Alert(Alert.AlertType.CONFIRMATION);
        frage.setTitle("Bewerbungsmanager");
        frage.setHeaderText("Im Infobereich weiterlaufen?");

        // setContentText wäre hier wirkungslos: ein gesetzter Inhaltsknoten verdraengt ihn.
        // Deshalb Text und Auswahl gemeinsam als Inhalt.
        Label erklaerung = new Label("So kann die App Sie weiter an Bewerbungen erinnern. "
                + "Andernfalls wird sie vollständig beendet.");
        erklaerung.setWrapText(true);
        erklaerung.setMaxWidth(360);
        CheckBox nichtMehrFragen = new CheckBox("Nicht mehr fragen");
        frage.getDialogPane().setContent(new VBox(12, erklaerung, nichtMehrFragen));

        ButtonType imInfobereich = new ButtonType("Im Infobereich", ButtonBar.ButtonData.YES);
        ButtonType beenden = new ButtonType("Beenden", ButtonBar.ButtonData.NO);
        frage.getButtonTypes().setAll(imInfobereich, beenden);
        DialogStyler.prepare(frage, dunklerModus());

        Optional<ButtonType> antwort = frage.showAndWait();
        if (antwort.isEmpty()) {
            return;
        }
        boolean tray = antwort.get() == imInfobereich;
        if (nichtMehrFragen.isSelected()) {
            einstellungen().ifPresent(e -> e.setSchliessenVerhalten(tray ? "TRAY" : "BEENDEN"));
        }
        if (tray) {
            inDenInfobereich();
        } else {
            beenden();
        }
    }

    private void inDenInfobereich() {
        if (!trayAnzeigen()) {
            // Ohne Infobereich wäre das Fenster unerreichbar - dann lieber beenden.
            beenden();
            return;
        }
        merkeFenstergroesse();
        primaryStage.hide();
        starteSperrTimer();
    }

    /**
     * Sperrt die Datenbank wieder, wenn die App zu lange ungenutzt im Infobereich liegt.
     * Sonst bliebe sie beliebig lange entsperrt — und wer ans Gerät kommt, klappt sie auf.
     */
    private void starteSperrTimer() {
        stoppeSperrTimer();
        int minuten = einstellungen().map(EinstellungService::getAutoSperreMinuten).orElse(0);
        if (minuten <= 0 || springContext == null) {
            return;
        }
        sperrTimer = new Timeline(new KeyFrame(Duration.minutes(minuten), e -> {
            if (!primaryStage.isShowing()) {
                logout();
                trayService.melden("Bewerbungsmanager",
                        "Aus Sicherheitsgründen gesperrt. Bitte erneut anmelden.");
            }
        }));
        sperrTimer.play();
    }

    private void starteErinnerungsTimer() {
        stoppeErinnerungsTimer();
        erinnerungsTimer = new Timeline(new KeyFrame(PRUEF_INTERVALL,
                e -> benachrichtiger().ifPresent(ErinnerungsBenachrichtiger::pruefeUndMelde)));
        erinnerungsTimer.setCycleCount(Animation.INDEFINITE);
        erinnerungsTimer.play();
    }

    private void stoppeErinnerungsTimer() {
        if (erinnerungsTimer != null) {
            erinnerungsTimer.stop();
            erinnerungsTimer = null;
        }
    }

    private void stoppeSperrTimer() {
        if (sperrTimer != null) {
            sperrTimer.stop();
            sperrTimer = null;
        }
    }

    private void stoppeTimer() {
        stoppeErinnerungsTimer();
        stoppeSperrTimer();
    }

    // =====================================================================
    //  Hilfsmittel
    // =====================================================================

    private Optional<EinstellungService> einstellungen() {
        return springContext != null && springContext.isActive()
                ? Optional.of(springContext.getBean(EinstellungService.class))
                : Optional.empty();
    }

    /**
     * Farbmodus für die Dialoge der Hülle. Am Anmeldebildschirm gibt es noch keinen
     * Context und damit keinen {@link ThemeService} — dann gilt hell.
     */
    private boolean dunklerModus() {
        return springContext != null && springContext.isActive()
                && springContext.getBean(ThemeService.class).isDark();
    }

    private Optional<ErinnerungsBenachrichtiger> benachrichtiger() {
        return springContext != null && springContext.isActive()
                ? Optional.of(springContext.getBean(ErinnerungsBenachrichtiger.class))
                : Optional.empty();
    }

    private void beenden() {
        merkeFenstergroesse();
        stoppeTimer();
        closeSpringContext();
        trayService.entfernen();
        Platform.exit();
    }

    /** Haelt die aktuelle Fenstergröße fest, damit der nächste Start sie übernimmt. */
    private void merkeFenstergroesse() {
        if (fensterZustand != null) {
            fensterZustand.speichern();
        }
    }

    private void closeSpringContext() {
        if (springContext != null) {
            springContext.close();
            springContext = null;
        }
    }

    private void showFatalError(Exception e) {
        e.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Bewerbungsmanager");
        alert.setHeaderText("Die Anwendung konnte nicht geöffnet werden.");
        alert.setContentText(String.valueOf(e.getMessage()));
        DialogStyler.prepare(alert, dunklerModus());
        alert.showAndWait();
    }

    @Override
    public void init() {
        // Ohne das beendet sich JavaFX, sobald das letzte Fenster zugeht - die App könnte
        // dann nicht im Infobereich weiterleben.
        Platform.setImplicitExit(false);
    }

    @Override
    public void stop() {
        merkeFenstergroesse();
        stoppeTimer();
        closeSpringContext();
        trayService.entfernen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
