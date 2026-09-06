package de.mkysarte.bewerbungsmanager.adresse.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.common.exception.ErrorCode;

import de.mkysarte.bewerbungsmanager.adresse.dto.AdresseResponse;
import de.mkysarte.bewerbungsmanager.adresse.dto.CreateAdresseRequest;
import de.mkysarte.bewerbungsmanager.adresse.entity.AdresseEntity;
import de.mkysarte.bewerbungsmanager.adresse.repository.AdresseRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdresseServiceTest {

    @Mock
    private AdresseRepository adresseRepository;

    @Mock
    private FirmaRepository firmaRepository;

    @InjectMocks
    private AdresseService adresseService;

    @Test
    void createAdresseShouldReturnCreatedAdresse() {
        CreateAdresseRequest request = new CreateAdresseRequest(1L, "Musterstraße", "1", "12345", "Berlin", "DE");
        when(firmaRepository.existsById(1L)).thenReturn(true);
        when(adresseRepository.existsByFirmaId(1L)).thenReturn(false);
        when(adresseRepository.save(any(AdresseEntity.class))).thenAnswer(invocation -> {
            AdresseEntity entity = invocation.getArgument(0);
            entity.setAdresseId(10L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            return entity;
        });

        AdresseResponse response = adresseService.createAdresse(request);

        assertEquals(10L, response.adresseId());
        assertEquals("Berlin", response.ort());
        verify(adresseRepository).save(any(AdresseEntity.class));
    }

    @Test
    void createAdresseShouldThrowBadRequestWhenFirmaMissing() {
        CreateAdresseRequest request = new CreateAdresseRequest(99L, "Musterstraße", null, null, "Berlin", null);
        when(firmaRepository.existsById(99L)).thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> adresseService.createAdresse(request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(adresseRepository, never()).save(any(AdresseEntity.class));
    }

    @Test
    void getAdresseByIdShouldThrowNotFoundWhenMissing() {
        when(adresseRepository.findById(77L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> adresseService.getAdresseById(77L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteAdresseShouldDeleteWhenExists() {
        when(adresseRepository.existsById(5L)).thenReturn(true);

        adresseService.deleteAdresse(5L);

        verify(adresseRepository).deleteById(5L);
    }
}
