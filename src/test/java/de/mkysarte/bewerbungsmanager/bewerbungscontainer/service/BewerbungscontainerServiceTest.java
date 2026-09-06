package de.mkysarte.bewerbungsmanager.bewerbungscontainer.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.common.exception.ErrorCode;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.dto.BewerbungscontainerResponse;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.dto.CreateBewerbungscontainerRequest;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.entity.BewerbungscontainerEntity;
import de.mkysarte.bewerbungsmanager.bewerbungscontainer.repository.BewerbungscontainerRepository;
import de.mkysarte.bewerbungsmanager.user.repository.UserRepository;
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
class BewerbungscontainerServiceTest {

    @Mock
    private BewerbungscontainerRepository bewerbungscontainerRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BewerbungscontainerService bewerbungscontainerService;

    @Test
    void createContainerShouldReturnCreatedContainer() {
        CreateBewerbungscontainerRequest request = new CreateBewerbungscontainerRequest(1L);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(bewerbungscontainerRepository.existsByUserId(1L)).thenReturn(false);
        when(bewerbungscontainerRepository.save(any(BewerbungscontainerEntity.class))).thenAnswer(invocation -> {
            BewerbungscontainerEntity entity = invocation.getArgument(0);
            entity.setBewerbungscontainerId(11L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            return entity;
        });

        BewerbungscontainerResponse response = bewerbungscontainerService.createContainer(request);

        assertEquals(11L, response.bewerbungscontainerId());
        assertEquals(1L, response.userId());
        assertEquals("Standardcontainer", response.name());
        verify(bewerbungscontainerRepository).save(any(BewerbungscontainerEntity.class));
    }

    @Test
    void createContainerShouldThrowBadRequestWhenUserMissing() {
        CreateBewerbungscontainerRequest request = new CreateBewerbungscontainerRequest(99L);
        when(userRepository.existsById(99L)).thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> bewerbungscontainerService.createContainer(request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(bewerbungscontainerRepository, never()).save(any(BewerbungscontainerEntity.class));
    }

    @Test
    void getContainerByIdShouldThrowNotFoundWhenMissing() {
        when(bewerbungscontainerRepository.findById(77L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> bewerbungscontainerService.getContainerById(77L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteContainerShouldDeleteWhenExists() {
        when(bewerbungscontainerRepository.existsById(5L)).thenReturn(true);

        bewerbungscontainerService.deleteContainer(5L);

        verify(bewerbungscontainerRepository).deleteById(5L);
    }
}
