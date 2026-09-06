package de.mkysarte.bewerbungsmanager.bewerbungseintrag.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public record UpdateBewerbungseintragRequest(
        @NotNull @Positive Long bewerbungscontainerId,
        @NotNull @Positive Long firmaId,
        @NotNull @Positive Long stellenausschreibungId,
        @NotNull @Positive Long statusId,
        @Size(max = 255) String contactPerson,
        @Size(max = 255) String ansprechpartner,
        @Size(max = 50) String phone,
        @Size(max = 50) String telefon,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String location,
        @Size(max = 255) String standort,
        @Size(max = 5000) String notiz,
        @URL String url,
        @Positive Long lebenslaufId,
        @Positive Long zertifikateId,
        @Positive Long zeugnisseId,
        @Positive Long anschreibenId,
        LocalDate erstelltAm,
        LocalDate aktualisiertAm,
        Boolean pdfBundleGewuenscht,
        @PositiveOrZero Integer nachfassFristTage,
        @Size(max = 20) String entwurfErinnerungIntervall,
        LocalDate letzteErinnerungAm
) {
}
