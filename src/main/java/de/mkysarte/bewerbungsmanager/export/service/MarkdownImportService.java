package de.mkysarte.bewerbungsmanager.export.service;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.CreateBewerbungseintragRequest;
import de.mkysarte.bewerbungsmanager.bewerbungseintrag.service.BewerbungseintragService;
import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.common.exception.ErrorCode;
import de.mkysarte.bewerbungsmanager.erinnerung.service.ErinnerungService;
import de.mkysarte.bewerbungsmanager.export.dto.ImportErgebnis;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Liest eine Markdown-Tabelle im Format des {@link MarkdownExportService} und legt daraus
 * Bewerbungen an.
 *
 * <p>Der Zweck: eine Stellenrecherche außerhalb der Anwendung erledigen lassen — von Hand oder
 * von einer KI, der man die Vorlage vorlegt — und das Ergebnis in einem Zug einspielen, statt
 * jede Karte einzeln zu tippen.
 *
 * <p>Eingabe sind {@link MarkdownTabelle#PFLICHT}, {@link MarkdownTabelle#OPTIONAL} und
 * {@link MarkdownTabelle#ZUSTAND} — Status und Datumsangaben werden also übernommen, wenn die
 * Datei sie mitbringt. Fehlen sie, entsteht ein Entwurf von heute; eine frisch gefundene Stelle
 * ist ja noch keine abgeschickte Bewerbung. Überlesen wird allein
 * {@link MarkdownTabelle#BERECHNET}.
 */
@Service
public class MarkdownImportService {

    /** {@code @Size(max = 255)} auf dem Request wird nirgends ausgewertet; hier also selbst. */
    private static final int MAX_LAENGE = 255;

    private static final String STATUS_ENTWURF = "ENTWURF";
    private static final String STATUS_ABGESCHICKT = "ABGESCHICKT";
    private static final String STATUS_ABSAGE = "ABSAGE";
    private static final String STATUS_ERFOLG = "ERFOLG";

    // Aus der Spaltenliste abgeleitet, nicht abgeschrieben: Eine Umsortierung der Tabelle
    // verschiebt damit von selbst auch die Zugriffe.
    private static final int SP_FIRMA = MarkdownTabelle.SPALTEN.indexOf("Firma");
    private static final int SP_STELLE = MarkdownTabelle.SPALTEN.indexOf("Stelle");
    private static final int SP_ORT = MarkdownTabelle.SPALTEN.indexOf("Ort");
    private static final int SP_STATUS = MarkdownTabelle.SPALTEN.indexOf("Status");
    private static final int SP_ENTWURF_SEIT = MarkdownTabelle.SPALTEN.indexOf("Entwurf seit");
    private static final int SP_ABGESCHICKT = MarkdownTabelle.SPALTEN.indexOf("Abgeschickt");
    private static final int SP_ABSCHLUSS = MarkdownTabelle.SPALTEN.indexOf("Abschluss");
    private static final int SP_ANSPRECHPARTNER = MarkdownTabelle.SPALTEN.indexOf("Ansprechpartner");
    private static final int SP_EMAIL = MarkdownTabelle.SPALTEN.indexOf("E-Mail");
    private static final int SP_LINK = MarkdownTabelle.SPALTEN.indexOf("Link");

    private final BewerbungseintragService bewerbungseintragService;
    private final StatusRepository statusRepository;

    public MarkdownImportService(BewerbungseintragService bewerbungseintragService,
                                 StatusRepository statusRepository) {
        this.bewerbungseintragService = bewerbungseintragService;
        this.statusRepository = statusRepository;
    }

    /**
     * Liest den Text und legt an, was verwertbar ist.
     *
     * @throws AppException wenn die Datei überhaupt keine oder eine fremde Tabelle enthält —
     *                      dann wird nichts angelegt, statt aus Bruchstücken zu raten.
     */
    public ImportErgebnis importiere(String markdown) {
        List<Rohzeile> tabelle = tabellenzeilen(markdown);
        if (tabelle.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "In der Datei steht keine Tabelle. Erwartet wird diese Kopfzeile:\n\n"
                            + MarkdownTabelle.kopfzeile());
        }
        pruefeKopfzeile(tabelle.get(0));

        StatusEntity entwurf = entwurfStatus();
        // Bestand und Datei teilen sich eine Menge: Damit faellt eine Doublette gegen den
        // Bestand und eine Doublette innerhalb derselben Datei durch dieselbe Pruefung.
        Set<String> bekannt = bestandsschluessel();

        int angelegt = 0;
        List<String> uebersprungen = new ArrayList<>();
        List<String> hinweise = new ArrayList<>();

        for (Rohzeile zeile : tabelle.subList(1, tabelle.size())) {
            if (MarkdownTabelle.istTrennzeile(zeile.text())) {
                continue;
            }

            List<String> zellen = MarkdownTabelle.zellen(zeile.text());
            if (zellen.size() != MarkdownTabelle.spaltenzahl()) {
                hinweise.add(zeile.markieren(zellen.size() + " statt "
                        + MarkdownTabelle.spaltenzahl() + " Spalten"));
                continue;
            }

            String firma = MarkdownTabelle.wert(zellen.get(SP_FIRMA));
            String stelle = MarkdownTabelle.wert(zellen.get(SP_STELLE));
            String ort = MarkdownTabelle.wert(zellen.get(SP_ORT));

            String fehlend = fehlendePflichtfelder(firma, stelle, ort);
            if (fehlend != null) {
                hinweise.add(zeile.markieren(fehlend));
                continue;
            }

            String ansprechpartner = MarkdownTabelle.wert(zellen.get(SP_ANSPRECHPARTNER));
            String email = MarkdownTabelle.wert(zellen.get(SP_EMAIL));
            String link = MarkdownTabelle.wert(zellen.get(SP_LINK));

            String zuLang = zuLangesFeld(firma, stelle, ort, ansprechpartner, email);
            if (zuLang != null) {
                hinweise.add(zeile.markieren(zuLang + " ist länger als " + MAX_LAENGE
                        + " Zeichen"));
                continue;
            }

            // Eine unbrauchbare Adresse landete sonst ungeprueft an der Firma. Die Zeile ist
            // deswegen aber nicht wertlos - nur das eine Feld.
            if (email != null && !email.contains("@")) {
                hinweise.add(zeile.markieren("„" + email + "\" ist keine E-Mail-Adresse und "
                        + "wurde weggelassen"));
                email = null;
            }

            String schluessel = schluessel(firma, stelle);
            if (!bekannt.add(schluessel)) {
                uebersprungen.add(zeile.markieren(firma + " — " + stelle
                        + " gibt es bereits"));
                continue;
            }

            StatusEntity status = statusAus(zellen.get(SP_STATUS), entwurf);
            LocalDate entwurfSeit = datum(zellen.get(SP_ENTWURF_SEIT), "Entwurf seit",
                    zeile, hinweise);
            LocalDate abgeschicktAm = datum(zellen.get(SP_ABGESCHICKT), "Abgeschickt",
                    zeile, hinweise);
            LocalDate abschlussAm = abschlussdatum(zellen.get(SP_ABSCHLUSS), zeile, hinweise);

            try {
                BewerbungseintragResponse angelegteBewerbung =
                        bewerbungseintragService.createBewerbungseintrag(anlegeAuftrag(
                                status.getStatusId(), firma, stelle, ort,
                                ansprechpartner, email, link, entwurfSeit));
                datenNachtragen(angelegteBewerbung.bewerbungseintragId(), status,
                        abgeschicktAm, abschlussAm);
                angelegt++;
            } catch (AppException e) {
                // Der Schluessel wieder heraus: Sonst blockiert die gescheiterte Zeile eine
                // spaetere, gleichlautende, die vielleicht durchkaeme.
                bekannt.remove(schluessel);
                hinweise.add(zeile.markieren(e.getMessage()));
            }
        }

        return new ImportErgebnis(angelegt, uebersprungen, hinweise);
    }

    /**
     * Baut den Anlege-Auftrag.
     *
     * <p>Zwei Felder bleiben mit Bedacht leer:
     * <ul>
     *   <li>{@code containerName} — ein abweichender Name würde den vorhandenen
     *       Bewerbungscontainer <em>umbenennen</em>.
     *   <li>Leere Spalten werden als {@code null} durchgereicht, nicht als leerer Text. Der
     *       Anlege-Pfad schreibt Ansprechpartner und E-Mail auf die <em>Firma</em> und den Ort
     *       auf die <em>Stellenausschreibung</em>; er fasst ein Feld nur an, wenn es nicht
     *       {@code null} ist. Ein leerer Text würde gepflegte Kontaktdaten löschen.
     * </ul>
     */
    private CreateBewerbungseintragRequest anlegeAuftrag(Long statusId, String firma,
                                                         String stelle, String ort,
                                                         String ansprechpartner, String email,
                                                         String link, LocalDate erstelltAm) {
        return new CreateBewerbungseintragRequest(
                null, null, null, statusId,
                null,
                firma, stelle,
                null, ansprechpartner,
                null, null,
                email,
                null, ort,
                null, link,
                null, null, null, null,
                erstelltAm, null, null, null, null, null);
    }

    /**
     * Trägt die Datumsangaben nach, die erst nach dem Anlegen gesetzt werden können.
     *
     * <p>Sie leben im Statusverlauf, nicht am Eintrag — und der Verlauf entsteht erst beim
     * Speichern. Ohne diesen Schritt stünde bei jeder eingespielten Bewerbung der
     * Importzeitpunkt, und Nachfassfrist wie Fälligkeit rechneten auf dem falschen Tag.
     *
     * <p>Das Absendedatum wird auch dann eingetragen, wenn die Bewerbung bereits als Absage
     * oder Erfolg hereinkommt: Abgeschickt wurde sie ja trotzdem, und ohne diesen Eintrag
     * fehlte der Zeitpunkt für immer.
     */
    private void datenNachtragen(Long id, StatusEntity status,
                                 LocalDate abgeschicktAm, LocalDate abschlussAm) {
        if (abgeschicktAm != null) {
            bewerbungseintragService.setzeStatusDatum(id, STATUS_ABGESCHICKT, abgeschicktAm);
        }
        if (abschlussAm != null && istAbschluss(status.getTitel())) {
            bewerbungseintragService.setzeStatusDatum(id, status.getTitel(), abschlussAm);
        }
    }

    private boolean istAbschluss(String statusTitel) {
        return STATUS_ABSAGE.equalsIgnoreCase(statusTitel)
                || STATUS_ERFOLG.equalsIgnoreCase(statusTitel);
    }

    /**
     * Der Status aus der Datei — unbekannt oder leer bedeutet Entwurf.
     *
     * <p>Ein Tippfehler soll die Zeile nicht kosten; ein Entwurf ist der harmloseste
     * Ausgangspunkt, und auf der Karte lässt sich der Status mit einem Klick richtigstellen.
     */
    private StatusEntity statusAus(String zelle, StatusEntity entwurf) {
        String titel = MarkdownTabelle.wert(zelle);
        if (titel == null) {
            return entwurf;
        }
        return statusRepository.findByTitel(titel.trim().toUpperCase(Locale.ROOT))
                .orElse(entwurf);
    }

    /** Ein Datum im Format der Anwendung; unlesbares kostet nur das Feld, nicht die Zeile. */
    private LocalDate datum(String zelle, String spalte, Rohzeile zeile, List<String> hinweise) {
        String wert = MarkdownTabelle.wert(zelle);
        if (wert == null) {
            return null;
        }
        try {
            return LocalDate.parse(wert, ErinnerungService.DATUM);
        } catch (DateTimeParseException e) {
            hinweise.add(zeile.markieren("„" + wert + "\" in der Spalte " + spalte
                    + " ist kein Datum im Format TT.MM.JJJJ und wurde weggelassen"));
            return null;
        }
    }

    /**
     * Die Abschluss-Spalte trägt ein Wort vor dem Datum, so wie der Export sie schreibt:
     * {@code Absage 18.09.2026}. Welches Wort dort steht, ist hier gleichgültig — der Status
     * kommt aus seiner eigenen Spalte.
     */
    private LocalDate abschlussdatum(String zelle, Rohzeile zeile, List<String> hinweise) {
        String wert = MarkdownTabelle.wert(zelle);
        if (wert == null) {
            return null;
        }
        String[] teile = wert.trim().split("\\s+");
        return datum(teile[teile.length - 1], "Abschluss", zeile, hinweise);
    }

    private String fehlendePflichtfelder(String firma, String stelle, String ort) {
        List<String> fehlend = new ArrayList<>();
        if (firma == null) {
            fehlend.add("Firma");
        }
        if (stelle == null) {
            fehlend.add("Stelle");
        }
        if (ort == null) {
            fehlend.add("Ort");
        }
        if (fehlend.isEmpty()) {
            return null;
        }
        return String.join(" und ", fehlend) + (fehlend.size() == 1 ? " fehlt" : " fehlen");
    }

    private String zuLangesFeld(String firma, String stelle, String ort,
                                String ansprechpartner, String email) {
        if (laenger(firma)) {
            return "Firma";
        }
        if (laenger(stelle)) {
            return "Stelle";
        }
        if (laenger(ort)) {
            return "Ort";
        }
        if (laenger(ansprechpartner)) {
            return "Ansprechpartner";
        }
        if (laenger(email)) {
            return "E-Mail";
        }
        return null;
    }

    private boolean laenger(String wert) {
        return wert != null && wert.length() > MAX_LAENGE;
    }

    /**
     * Sammelt die Tabellenzeilen und merkt sich, in welcher Zeile der Datei sie standen —
     * ohne die Nummer wäre ein Hinweis in einer Datei mit fünfzig Zeilen wertlos.
     */
    private List<Rohzeile> tabellenzeilen(String markdown) {
        List<Rohzeile> zeilen = new ArrayList<>();
        if (markdown == null) {
            return zeilen;
        }
        String[] alle = markdown.split("\\R", -1);
        for (int i = 0; i < alle.length; i++) {
            if (MarkdownTabelle.istTabellenzeile(alle[i])) {
                zeilen.add(new Rohzeile(i + 1, alle[i]));
            }
        }
        return zeilen;
    }

    /**
     * Die erste Tabellenzeile muss der Kopf sein.
     *
     * <p>Ohne diese Prüfung würde eine beliebige fremde Markdown-Tabelle spaltenweise
     * fehlgedeutet und lautlos Unsinn angelegt.
     */
    private void pruefeKopfzeile(Rohzeile kopf) {
        List<String> gefunden = MarkdownTabelle.zellen(kopf.text());
        if (!gefunden.equals(MarkdownTabelle.SPALTEN)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Die Kopfzeile passt nicht. Gefunden in Zeile " + kopf.nummer() + ":\n\n"
                            + String.join(" | ", gefunden)
                            + "\n\nErwartet:\n\n"
                            + String.join(" | ", MarkdownTabelle.SPALTEN));
        }
    }

    private StatusEntity entwurfStatus() {
        return statusRepository.findByTitel(STATUS_ENTWURF)
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR,
                        "Der Status " + STATUS_ENTWURF + " fehlt in der Datenbank."));
    }

    private Set<String> bestandsschluessel() {
        Set<String> schluessel = new HashSet<>();
        for (BewerbungseintragResponse a : bewerbungseintragService.getAllBewerbungseintraege()) {
            if (a.firmaName() != null && a.stellenbezeichnung() != null) {
                schluessel.add(schluessel(a.firmaName(), a.stellenbezeichnung()));
            }
        }
        return schluessel;
    }

    /** Firma und Stelle zusammen — Groß- und Kleinschreibung spielt dabei keine Rolle. */
    private String schluessel(String firma, String stelle) {
        // Getrennt wird mit einem Zeichen, das in keinem Firmennamen vorkommt: Mit einem
        // Leerzeichen waeren "Muster GmbH" + "Entwickler" und "Muster" + "GmbH Entwickler"
        // derselbe Schluessel.
        return firma.trim().toLowerCase(Locale.ROOT)
                + '\u0000'
                + stelle.trim().toLowerCase(Locale.ROOT);
    }

    /** Eine Tabellenzeile mit ihrer Position in der Datei. */
    private record Rohzeile(int nummer, String text) {

        String markieren(String meldung) {
            return "Zeile " + nummer + ": " + meldung;
        }
    }
}
