package de.mkysarte.bewerbungsmanager.export.dto;

import java.util.List;

/**
 * Was ein Markdown-Import bewirkt hat.
 *
 * <p>Eine unbrauchbare Zeile kippt bewusst nicht den ganzen Import — bei einer Datei aus
 * fremder Feder wäre sonst eine einzige Unachtsamkeit genug, um zwanzig gute Zeilen
 * mitzureißen. Stattdessen wird sie vermerkt und der Rest angelegt.
 *
 * @param angelegt      Zahl der neu angelegten Bewerbungen
 * @param uebersprungen Zeilen, deren Firma und Stelle es bereits gab
 * @param hinweise      Zeilen, die nicht verwertbar waren — je mit ihrer Zeilennummer
 */
public record ImportErgebnis(int angelegt, List<String> uebersprungen, List<String> hinweise) {

    public ImportErgebnis {
        uebersprungen = uebersprungen == null ? List.of() : List.copyOf(uebersprungen);
        hinweise = hinweise == null ? List.of() : List.copyOf(hinweise);
    }

    /** Alles glattgegangen — nichts übersprungen, nichts zu bemängeln. */
    public boolean ohneBeanstandung() {
        return uebersprungen.isEmpty() && hinweise.isEmpty();
    }
}
