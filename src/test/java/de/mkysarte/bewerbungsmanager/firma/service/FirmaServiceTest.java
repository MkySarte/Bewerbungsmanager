package de.mkysarte.bewerbungsmanager.firma.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.common.exception.ErrorCode;
import de.mkysarte.bewerbungsmanager.firma.dto.CreateFirmaRequest;
import de.mkysarte.bewerbungsmanager.firma.dto.FirmaResponse;
import de.mkysarte.bewerbungsmanager.firma.entity.FirmaEntity;
import de.mkysarte.bewerbungsmanager.firma.repository.FirmaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmaServiceTest {

    @Mock
    private FirmaRepository firmaRepository;

    @InjectMocks
    private FirmaService firmaService;

    @Test
    void createFirmaShouldReturnCreatedFirma() {
        CreateFirmaRequest request = new CreateFirmaRequest("Acme", "Max", "0123", "max@acme.de");
        when(firmaRepository.save(any(FirmaEntity.class))).thenAnswer(invocation -> {
            FirmaEntity entity = invocation.getArgument(0);
            entity.setFirmaId(10L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            return entity;
        });

        FirmaResponse response = firmaService.createFirma(request);

        assertEquals(10L, response.firmaId());
        assertEquals("Acme", response.name());
        verify(firmaRepository).save(any(FirmaEntity.class));
    }

    @Test
    void getFirmaByIdShouldThrowNotFoundWhenMissing() {
        when(firmaRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> firmaService.getFirmaById(99L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteFirmaShouldDeleteWhenExists() {
        when(firmaRepository.existsById(5L)).thenReturn(true);

        firmaService.deleteFirma(5L);

        verify(firmaRepository).deleteById(5L);
    }
}
