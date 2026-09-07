package de.mkysarte.bewerbungsmanager.export.service;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.erinnerung.service.ErinnerungService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Schreibt die Bewerbungen als Markdown-Tabelle — und die Vorlage, mit der sich dieselbe
 * Tabelle von außen befüllen lässt.
 *
 * <p>Gedacht für alles außerhalb der App: ausdrucken, weitergeben, ablegen. Markdown, weil es
 * sowohl als reiner Text lesbar ist als auch in jedem Betrachter als richtige Tabelle erscheint.
 *
 * <p>Exportiert wird immer genau die übergebene Liste — die Oberfläche reicht die gefilterte
 * Ansicht herein, sodass Suche und Statusfilter sich auswirken.
 *
 * <p>Das Format selbst steht in {@link MarkdownTabelle}, damit Export, Vorlage und
 * {@link MarkdownImportService} nicht auseinanderlaufen können.
 */
@Service
public class MarkdownExportService {

    private final ErinnerungService erinnerungService;

    public MarkdownExportService(ErinnerungService erinnerungService) {
        this.erinnerungService = erinnerungService;
    }

    /** Dateiname mit Datum, z. B. {@code bewerbungen-2026-09-06.md}. */
    public String buildFileName() {
        return "bewerbungen-" + erinnerungService.heute() + ".md";
    }

    /** Dateiname der Vorlage — ohne Datum, sie veraltet nicht. */
    public String buildVorlageFileName() {
        return "bewerbungen-vorlage.md";
    }

    public String export(List<BewerbungseintragResponse> applications) {
        List<BewerbungseintragResponse> liste = applications == null ? List.of() : applications;

        StringBuilder md = new StringBuilder();
        md.append("# Bewerbungen\n\n");
        md.append("Stand: ").append(erinnerungService.heute().format(ErinnerungService.DATUM))
                .append(" — ").append(liste.size())
                .append(liste.size() == 1 ? " Bewerbung" : " Bewerbungen")
                .append("\n");
        // Die Legende darf keinen Balken enthalten: Alles mit Balken zaehlt beim Einlesen als
        // Tabellenzeile.
        md.append(legende()).append("\n\n");

        md.append(MarkdownTabelle.kopfzeile()).append("\n");
        md.append(MarkdownTabelle.trennzeile()).append("\n");

        for (BewerbungseintragResponse a : liste) {
            md.append("| ").append(String.join(" | ", zeile(a))).append(" |\n");
        }
        return md.toString();
    }

    /**
     * Die leere Schablone samt Anweisung.
     *
     * <p>Sie ist dafür da, jemandem — oder einer KI — vorgelegt zu werden, der eine
     * Stellenrecherche in diese Tabelle schreiben soll. Deshalb steht in der Anweisung
     * ausdrücklich, welche Spalten Pflicht sind und welche die Anwendung ohnehin selbst führt:
     * Sonst kommen Daten zurück, die beim Import stillschweigend verfallen.
     */
    public String vorlage() {
        StringBuilder md = new StringBuilder();
        md.append("# Bewerbungen\n\n");
        md.append("## Anweisung\n\n");
        md.append("Fülle die Tabelle am Ende dieser Datei aus — eine Zeile je Stelle.\n");
        md.append("Spaltenzahl und Reihenfolge müssen exakt eingehalten werden.\n");
        md.append("Leere Felder bekommen einen Gedankenstrich: ").append(MarkdownTabelle.LEER)
                .append("\n");
        md.append("Ein Balken im Text muss als \\| geschrieben werden.\n\n");

        md.append("Pflicht:    ").append(String.join(" · ", MarkdownTabelle.PFLICHT)).append("\n");
        md.append("Optional:   ").append(String.join(" · ", MarkdownTabelle.OPTIONAL)).append("\n");
        md.append("Zustand:    ").append(String.join(" · ", MarkdownTabelle.ZUSTAND)).append("\n");
        md.append("            Dürfen gefüllt werden, wenn die Bewerbung schon läuft.\n");
        md.append("            Status: ENTWURF, ABGESCHICKT, ABSAGE oder ERFOLG.\n");
        md.append("            Datum als TT.MM.JJJJ; in der Spalte Abschluss mit dem\n");
        md.append("            Wort davor, also \"Absage 18.09.2026\".\n");
        md.append("            Bleiben sie leer, entsteht ein Entwurf von heute.\n");
        md.append("Ignoriert:  ").append(String.join(" · ", MarkdownTabelle.BERECHNET)).append("\n");
        md.append("            Rechnet die Anwendung selbst aus. Bitte trotzdem\n");
        md.append("            mitschreiben, damit die Spaltenzahl stimmt — Inhalt: ")
                .append(MarkdownTabelle.LEER).append("\n\n");

        md.append("Zeilen, deren Firma und Stelle es schon gibt, werden übersprungen.\n\n");

        md.append("Beispiel einer Zeile:\n\n");
        // Vier Leerzeichen Einzug: In Markdown ist das ein Codeblock, keine Tabellenzeile -
        // deshalb legt ein unbearbeitet zurueckgespieltes Muster keine Karte an.
        md.append("    ").append(beispielzeile()).append("\n\n");

        md.append(MarkdownTabelle.kopfzeile()).append("\n");
        md.append(MarkdownTabelle.trennzeile()).append("\n");
        return md.toString();
    }

    /**
     * Beschreibt in einem Satz ohne Balken, was beim Wiedereinlesen zählt.
     *
     * <p>Aus den Listen erzeugt statt getippt: Kommt eine Spalte hinzu, wandert sie von selbst
     * in den Text.
     */
    private String legende() {
        return "Pflichtfelder beim Import: " + String.join(", ", MarkdownTabelle.PFLICHT)
                + ". Die Spalte " + aufzaehlung(MarkdownTabelle.BERECHNET)
                + " rechnet die Anwendung selbst aus, alle übrigen werden übernommen.";
    }

    private String aufzaehlung(List<String> woerter) {
        if (woerter.size() < 2) {
            return String.join("", woerter);
        }
        return String.join(", ", woerter.subList(0, woerter.size() - 1))
                + " und " + woerter.get(woerter.size() - 1);
    }

    private String beispielzeile() {
        StringBuilder zeile = new StringBuilder("|");
        for (String spalte : MarkdownTabelle.SPALTEN) {
            zeile.append(' ').append(switch (spalte) {
                case "Firma" -> "Muster GmbH";
                case "Stelle" -> "Fachinformatiker Anwendungsentwicklung";
                case "Ort" -> "Bremen";
                case "Ansprechpartner" -> "Frau Meier";
                case "E-Mail" -> "bewerbung@muster.example";
                case "Link" -> "https://muster.example/stellen/42";
                default -> MarkdownTabelle.LEER;
            }).append(" |");
        }
        return zeile.toString();
    }

    private List<String> zeile(BewerbungseintragResponse a) {
        return List.of(
                MarkdownTabelle.maskieren(a.firmaName()),
                MarkdownTabelle.maskieren(a.stellenbezeichnung()),
                MarkdownTabelle.maskieren(erstesNichtLeeres(a.location(), a.standort())),
                MarkdownTabelle.maskieren(a.statusTitel()),
                datum(erinnerungService.entwurfSeit(a)),
                datum(a.abgeschicktAm()),
                datum(erinnerungService.nachfassenAb(a)),
                abschluss(a),
                MarkdownTabelle.maskieren(erstesNichtLeeres(a.ansprechpartner(), a.contactPerson())),
                MarkdownTabelle.maskieren(a.email()),
                MarkdownTabelle.maskieren(a.url())
        );
    }

    /**
     * Absage und Erfolg schließen einander aus — es kann immer nur eines von beidem
     * der letzte Stand sein.
     */
    private String abschluss(BewerbungseintragResponse a) {
        if (a.absageAm() != null) {
            return "Absage " + a.absageAm().toLocalDate().format(ErinnerungService.DATUM);
        }
        if (a.erfolgAm() != null) {
            return "Erfolg " + a.erfolgAm().toLocalDate().format(ErinnerungService.DATUM);
        }
        return MarkdownTabelle.LEER;
    }

    private String datum(LocalDateTime zeitpunkt) {
        return zeitpunkt == null
                ? MarkdownTabelle.LEER
                : zeitpunkt.toLocalDate().format(ErinnerungService.DATUM);
    }

    private String datum(LocalDate datum) {
        return datum == null ? MarkdownTabelle.LEER : datum.format(ErinnerungService.DATUM);
    }

    private String erstesNichtLeeres(String einer, String anderer) {
        if (einer != null && !einer.isBlank()) {
            return einer;
        }
        return anderer;
    }
}
