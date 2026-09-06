package de.mkysarte.bewerbungsmanager.stellenausschreibung.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.common.exception.ErrorCode;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.dto.CreateStellenausschreibungRequest;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.dto.StellenausschreibungResponse;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.entity.StellenausschreibungEntity;
import de.mkysarte.bewerbungsmanager.stellenausschreibung.repository.StellenausschreibungRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StellenausschreibungServiceTest {

    @Mock
    private StellenausschreibungRepository stellenausschreibungRepository;

    @Mock
    private FirmaRepository firmaRepository;

    @InjectMocks
    private StellenausschreibungService stellenausschreibungService;

    @Test
    void createStellenausschreibungShouldReturnCreatedEntity() {
        CreateStellenausschreibungRequest request = new CreateStellenausschreibungRequest(
                1L,
                "Java Developer",
                "https://example.com/job",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                "Berlin",
                "50%",
                "Beschreibung"
        );
        when(firmaRepository.existsById(1L)).thenReturn(true);
        when(stellenausschreibungRepository.save(any(StellenausschreibungEntity.class))).thenAnswer(invocation -> {
            StellenausschreibungEntity entity = invocation.getArgument(0);
            entity.setStellenausschreibungId(21L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            return entity;
        });

        StellenausschreibungResponse response = stellenausschreibungService.createStellenausschreibung(request);

        assertEquals(21L, response.stellenausschreibungId());
        assertEquals("Java Developer", response.titel());
        verify(stellenausschreibungRepository).save(any(StellenausschreibungEntity.class));
    }

    @Test
    void createStellenausschreibungShouldThrowBadRequestWhenFirmaMissing() {
        CreateStellenausschreibungRequest request = new CreateStellenausschreibungRequest(
                99L,
                "Java Developer",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(firmaRepository.existsById(99L)).thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> stellenausschreibungService.createStellenausschreibung(request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(stellenausschreibungRepository, never()).save(any(StellenausschreibungEntity.class));
    }

    @Test
    void getStellenausschreibungByIdShouldThrowNotFoundWhenMissing() {
        when(stellenausschreibungRepository.findById(77L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> stellenausschreibungService.getStellenausschreibungById(77L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteStellenausschreibungShouldDeleteWhenExists() {
        when(stellenausschreibungRepository.existsById(5L)).thenReturn(true);

        stellenausschreibungService.deleteStellenausschreibung(5L);

        verify(stellenausschreibungRepository).deleteById(5L);
    }
}
