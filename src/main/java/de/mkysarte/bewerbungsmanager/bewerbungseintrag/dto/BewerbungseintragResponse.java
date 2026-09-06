package de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BewerbungseintragResponse(
        Long bewerbungseintragId,
        Long bewerbungscontainerId,
        String bewerbungscontainerName,
        Long firmaId,
        String firmaName,
        Long stellenausschreibungId,
        String stellenbezeichnung,
        String contactPerson,
        String ansprechpartner,
        String phone,
        String telefon,
        String email,
        String location,
        String standort,
        Long statusId,
        String statusTitel,
        String notiz,
        String url,
        Long lebenslaufId,
        Long zertifikateId,
        Long zeugnisseId,
        Long anschreibenId,
        LocalDate erstelltAm,
        LocalDate aktualisiertAm,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean pdfBundleGewuenscht,
        Integer nachfassFristTage,
        /** Rhythmus der Entwurfs-Erinnerung; null = globaler Standard. */
        String entwurfErinnerungIntervall,
        /** Wann zuletzt benachrichtigt wurde - verhindert Wiederholungen am selben Tag. */
        LocalDate letzteErinnerungAm,
        /** Wann zuletzt auf ABGESCHICKT gesetzt - aus dem Statusverlauf, sonst null. */
        LocalDateTime abgeschicktAm,
        /** Wann zuletzt auf ABSAGE gesetzt - aus dem Statusverlauf, sonst null. */
        LocalDateTime absageAm,
        /** Wann zuletzt auf ERFOLG gesetzt - aus dem Statusverlauf, sonst null. */
        LocalDateTime erfolgAm
) {
}
