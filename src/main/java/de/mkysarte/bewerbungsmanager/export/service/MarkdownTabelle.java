package de.mkysarte.bewerbungsmanager.export.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Das Format der Bewerbungstabelle — einmal festgeschrieben, von Export, Vorlage und Import
 * gemeinsam benutzt.
 *
 * <p>Der Grund für die eigene Klasse: Export und Import müssen dieselbe Tabelle meinen. Stünden
 * die Spalten zweimal im Code, ließe eine ergänzte Spalte den Import stillschweigend daneben
 * laufen — und genau darauf baut der Ablauf auf, für den es den Import gibt: Eine KI schreibt
 * die Datei, die Anwendung liest sie wieder ein.
 */
public final class MarkdownTabelle {

    /** Leere Felder bekommen ein Zeichen, sonst franst die Tabelle optisch aus. */
    public static final String LEER = "–";

    public static final List<String> SPALTEN = List.of(
            "Firma", "Stelle", "Ort", "Status",
            "Entwurf seit", "Abgeschickt", "Nachfassen ab", "Abschluss",
            "Ansprechpartner", "E-Mail", "Link");

    /** Ohne diese drei ist eine Karte wertlos — der Import weist solche Zeilen ab. */
    public static final List<String> PFLICHT = List.of("Firma", "Stelle", "Ort");

    /** Angaben zur Stelle; werden übernommen, wenn vorhanden. */
    public static final List<String> OPTIONAL = List.of("Ansprechpartner", "E-Mail", "Link");

    /**
     * Wo die Bewerbung steht. Auch diese Spalten werden übernommen, wenn sie gefüllt sind —
     * dafür gibt es den Import ja: eine anderswo geführte Liste einspielen, ohne dass jede
     * Bewerbung zum Entwurf von heute wird. Fehlen sie, entsteht ein Entwurf.
     */
    public static final List<String> ZUSTAND = List.of(
            "Status", "Entwurf seit", "Abgeschickt", "Abschluss");

    /**
     * Rechnet die Anwendung aus dem Absendedatum und der Nachfassfrist aus. Als Eingabe wäre
     * die Spalte sinnlos, deshalb überliest der Import sie.
     */
    public static final List<String> BERECHNET = List.of("Nachfassen ab");

    /**
     * Alles, was der Import als „kein Wert" liest. Der Export schreibt {@link #LEER}; ein
     * Geviert- oder schlichter Bindestrich kommt von Hand oder aus einer KI oft genauso.
     */
    private static final List<String> PLATZHALTER = List.of("–", "—", "-", "‒", "―");

    /** Ein Balken trennt Zellen nur, wenn er nicht maskiert ist. */
    private static final String ZELLEN_TRENNER = "(?<!\\\\)\\|";

    /**
     * Mehr als drei Leerzeichen Einzug ist in Markdown ein Codeblock, keine Tabelle. Genau das
     * nutzt die Vorlage aus: Ihre Beispielzeile steht eingerückt und wird deshalb beim Import
     * nicht als Datenzeile gelesen.
     */
    private static final int MAX_EINZUG = 3;

    private MarkdownTabelle() {
    }

    public static int spaltenzahl() {
        return SPALTEN.size();
    }

    public static String kopfzeile() {
        return "| " + String.join(" | ", SPALTEN) + " |";
    }

    public static String trennzeile() {
        return "|" + " --- |".repeat(SPALTEN.size());
    }

    /** Gehört die Zeile zur Tabelle? Siehe {@link #MAX_EINZUG}. */
    public static boolean istTabellenzeile(String zeile) {
        if (zeile == null) {
            return false;
        }
        String ohneEinzug = zeile.stripLeading();
        return zeile.length() - ohneEinzug.length() <= MAX_EINZUG && ohneEinzug.startsWith("|");
    }

    /** Die Zeile unter dem Kopf: zwischen den Balken stehen nur Striche, Doppelpunkte, Leerraum. */
    public static boolean istTrennzeile(String zeile) {
        return istTabellenzeile(zeile) && zeile.strip().matches("\\|[-:| \t]+");
    }

    /**
     * Zerlegt eine Tabellenzeile in ihre Zellen.
     *
     * <p>Vor dem ersten Balken steht immer nichts; hinter dem letzten ebenfalls, sofern die
     * Zeile ordentlich abgeschlossen ist. Fehlt der Schlussbalken — was von Hand schnell
     * passiert —, ist das letzte Feld eine echte Zelle und bleibt erhalten.
     */
    public static List<String> zellen(String zeile) {
        String[] teile = zeile.strip().split(ZELLEN_TRENNER, -1);
        List<String> zellen = new ArrayList<>();
        for (int i = 1; i < teile.length; i++) {
            if (i == teile.length - 1 && teile[i].isBlank()) {
                break;
            }
            zellen.add(entmaskieren(teile[i]));
        }
        return Collections.unmodifiableList(zellen);
    }

    /**
     * Bereitet einen Wert für eine Tabellenzelle auf.
     *
     * <p>Zwei Dinge würden die Tabelle sonst zerlegen: ein {@code |} im Text beendet die Zelle
     * vorzeitig, und ein Zeilenumbruch zerreißt die ganze Zeile.
     */
    public static String maskieren(String wert) {
        if (wert == null || wert.isBlank()) {
            return LEER;
        }
        return wert.replace("|", "\\|")
                .replaceAll("\\s*\\R\\s*", " ")
                .trim();
    }

    /** Die Gegenrichtung zu {@link #maskieren}: Maskierung auflösen, Ränder abschneiden. */
    public static String entmaskieren(String zelle) {
        if (zelle == null) {
            return "";
        }
        return zelle.replace("\\|", "|").trim();
    }

    /** Der Inhalt einer Zelle als Wert — Platzhalter und Leerraum werden zu {@code null}. */
    public static String wert(String zelle) {
        if (zelle == null || zelle.isBlank() || PLATZHALTER.contains(zelle.trim())) {
            return null;
        }
        return zelle.trim();
    }
}
