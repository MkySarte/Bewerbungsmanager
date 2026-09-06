package de.mkysarte.bewerbungsmanager.export.service;

import de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto.BewerbungseintragResponse;
import de.mkysarte.bewerbungsmanager.erinnerung.service.ErinnerungService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Schreibt die Bewerbungen als Markdown-Tabelle.
 *
 * <p>Gedacht für alles außerhalb der App: ausdrucken, weitergeben, ablegen. Markdown, weil es
 * sowohl als reiner Text lesbar ist als auch in jedem Betrachter als richtige Tabelle erscheint.
 *
 * <p>Exportiert wird immer genau die übergebene Liste — die Oberfläche reicht die gefilterte
 * Ansicht herein, sodass Suche und Statusfilter sich auswirken.
 */
@Service
public class MarkdownExportService {

    /** Leere Felder bekommen ein Zeichen, sonst franst die Tabelle optisch aus. */
    private static final String LEER = "–";

    private static final List<String> SPALTEN = List.of(
            "Firma", "Stelle", "Ort", "Status",
            "Entwurf seit", "Abgeschickt", "Nachfassen ab", "Abschluss",
            "Ansprechpartner", "E-Mail", "Link");

    private final ErinnerungService erinnerungService;

    public MarkdownExportService(ErinnerungService erinnerungService) {
        this.erinnerungService = erinnerungService;
    }

    /** Dateiname mit Datum, z. B. {@code bewerbungen-2026-09-06.md}. */
    public String buildFileName() {
        return "bewerbungen-" + erinnerungService.heute() + ".md";
    }

    public String export(List<BewerbungseintragResponse> applications) {
        List<BewerbungseintragResponse> liste = applications == null ? List.of() : applications;

        StringBuilder md = new StringBuilder();
        md.append("# Bewerbungen\n\n");
        md.append("Stand: ").append(erinnerungService.heute().format(ErinnerungService.DATUM))
                .append(" — ").append(liste.size())
                .append(liste.size() == 1 ? " Bewerbung" : " Bewerbungen")
                .append("\n\n");

        md.append("| ").append(String.join(" | ", SPALTEN)).append(" |\n");
        md.append("|").append(" --- |".repeat(SPALTEN.size())).append("\n");

        for (BewerbungseintragResponse a : liste) {
            md.append("| ").append(String.join(" | ", zeile(a))).append(" |\n");
        }
        return md.toString();
    }

    private List<String> zeile(BewerbungseintragResponse a) {
        return List.of(
                zelle(a.firmaName()),
                zelle(a.stellenbezeichnung()),
                zelle(erstesNichtLeeres(a.location(), a.standort())),
                zelle(a.statusTitel()),
                datum(erinnerungService.entwurfSeit(a)),
                datum(a.abgeschicktAm()),
                datum(erinnerungService.nachfassenAb(a)),
                abschluss(a),
                zelle(erstesNichtLeeres(a.ansprechpartner(), a.contactPerson())),
                zelle(a.email()),
                zelle(a.url())
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
        return LEER;
    }

    private String datum(LocalDateTime zeitpunkt) {
        return zeitpunkt == null ? LEER : zeitpunkt.toLocalDate().format(ErinnerungService.DATUM);
    }

    private String datum(LocalDate datum) {
        return datum == null ? LEER : datum.format(ErinnerungService.DATUM);
    }

    /**
     * Bereitet einen Wert für eine Tabellenzelle auf.
     *
     * <p>Zwei Dinge würden die Tabelle sonst zerlegen: ein {@code |} im Text beendet die Zelle
     * vorzeitig, und ein Zeilenumbruch zerreißt die ganze Zeile.
     */
    private String zelle(String wert) {
        if (wert == null || wert.isBlank()) {
            return LEER;
        }
        return wert.replace("|", "\\|")
                .replaceAll("\\s*\\R\\s*", " ")
                .trim();
    }

    private String erstesNichtLeeres(String einer, String anderer) {
        if (einer != null && !einer.isBlank()) {
            return einer;
        }
        return anderer;
    }
}
