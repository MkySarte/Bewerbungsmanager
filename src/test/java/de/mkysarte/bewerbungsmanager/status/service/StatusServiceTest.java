package de.mkysarte.bewerbungsmanager.status.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.common.exception.ErrorCode;
import de.mkysarte.bewerbungsmanager.status.dto.CreateStatusRequest;
import de.mkysarte.bewerbungsmanager.status.dto.PatchStatusRequest;
import de.mkysarte.bewerbungsmanager.status.dto.StatusResponse;
import de.mkysarte.bewerbungsmanager.status.dto.UpdateStatusRequest;
import de.mkysarte.bewerbungsmanager.status.entity.StatusEntity;
import de.mkysarte.bewerbungsmanager.status.repository.StatusRepository;
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
class StatusServiceTest {

    @Mock
    private StatusRepository statusRepository;

    @InjectMocks
    private StatusService statusService;

    @Test
    void createStatusShouldReturnCreatedStatus() {
        CreateStatusRequest request = new CreateStatusRequest("Offen", "Neu erstellt");
        when(statusRepository.existsByTitel("Offen")).thenReturn(false);
        when(statusRepository.save(any(StatusEntity.class))).thenAnswer(invocation -> {
            StatusEntity entity = invocation.getArgument(0);
            entity.setStatusId(1L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            return entity;
        });

        StatusResponse response = statusService.createStatus(request);

        assertEquals(1L, response.statusId());
        assertEquals("Offen", response.titel());
        verify(statusRepository).save(any(StatusEntity.class));
    }

    @Test
    void createStatusShouldThrowConflictWhenTitleExists() {
        CreateStatusRequest request = new CreateStatusRequest("Offen", "Duplikat");
        when(statusRepository.existsByTitel("Offen")).thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> statusService.createStatus(request)
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        verify(statusRepository, never()).save(any(StatusEntity.class));
    }

    @Test
    void getStatusByIdShouldThrowNotFoundWhenMissing() {
        when(statusRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> statusService.getStatusById(99L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void updateStatusShouldReturnUpdatedStatus() {
        UpdateStatusRequest request = new UpdateStatusRequest("Interview", "Termin gesetzt");
        StatusEntity existing = new StatusEntity();
        existing.setStatusId(5L);
        existing.setTitel("Offen");
        existing.setBeschreibung("Neu");
        when(statusRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(statusRepository.existsByTitel("Interview")).thenReturn(false);
        when(statusRepository.save(existing)).thenReturn(existing);

        StatusResponse response = statusService.updateStatus(5L, request);

        assertEquals("Interview", response.titel());
        assertEquals("Termin gesetzt", response.beschreibung());
        verify(statusRepository).save(existing);
    }

    @Test
    void updateStatusShouldThrowNotFoundWhenStatusMissing() {
        UpdateStatusRequest request = new UpdateStatusRequest("Interview", "Termin gesetzt");
        when(statusRepository.findById(99L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> statusService.updateStatus(99L, request)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void patchStatusShouldUpdateOnlyProvidedFields() {
        PatchStatusRequest request = new PatchStatusRequest(null, "Geändert");
        StatusEntity existing = new StatusEntity();
        existing.setStatusId(5L);
        existing.setTitel("Interview");
        existing.setBeschreibung("Alt");
        when(statusRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(statusRepository.save(existing)).thenReturn(existing);

        StatusResponse response = statusService.patchStatus(5L, request);

        assertEquals("Interview", response.titel());
        assertEquals("Geändert", response.beschreibung());
        verify(statusRepository).save(existing);
    }

    @Test
    void patchStatusShouldThrowBadRequestWhenNoFieldsProvided() {
        PatchStatusRequest request = new PatchStatusRequest(null, null);
        StatusEntity existing = new StatusEntity();
        existing.setStatusId(5L);
        existing.setTitel("Interview");
        when(statusRepository.findById(5L)).thenReturn(Optional.of(existing));

        AppException exception = assertThrows(
                AppException.class,
                () -> statusService.patchStatus(5L, request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void deleteStatusShouldDeleteWhenExists() {
        when(statusRepository.existsById(3L)).thenReturn(true);

        statusService.deleteStatus(3L);

        verify(statusRepository).deleteById(3L);
    }
}
