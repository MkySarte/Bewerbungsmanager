package de.mkysarte.bewerbungsmanager.unterlagen.service;

import de.mkysarte.bewerbungsmanager.common.exception.AppException;
import de.mkysarte.bewerbungsmanager.common.exception.ErrorCode;
import de.mkysarte.bewerbungsmanager.unterlagen.dto.CreateUnterlagenRequest;
import de.mkysarte.bewerbungsmanager.unterlagen.dto.UnterlagenResponse;
import de.mkysarte.bewerbungsmanager.unterlagen.entity.UnterlagenEntity;
import de.mkysarte.bewerbungsmanager.unterlagen.repository.UnterlagenRepository;
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
class UnterlagenServiceTest {

    @Mock
    private UnterlagenRepository unterlagenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UnterlagenService unterlagenService;

    @Test
    void createUnterlagenShouldReturnCreatedUnterlagen() {
        CreateUnterlagenRequest request = new CreateUnterlagenRequest(1L, new byte[]{1}, null, null);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(unterlagenRepository.existsByUserId(1L)).thenReturn(false);
        when(unterlagenRepository.save(any(UnterlagenEntity.class))).thenAnswer(invocation -> {
            UnterlagenEntity entity = invocation.getArgument(0);
            entity.setUnterlagenId(10L);
            entity.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
            return entity;
        });

        UnterlagenResponse response = unterlagenService.createUnterlagen(request);

        assertEquals(10L, response.unterlagenId());
        assertEquals(1L, response.userId());
        verify(unterlagenRepository).save(any(UnterlagenEntity.class));
    }

    @Test
    void createUnterlagenShouldThrowBadRequestWhenUserMissing() {
        CreateUnterlagenRequest request = new CreateUnterlagenRequest(99L, null, null, null);
        when(userRepository.existsById(99L)).thenReturn(false);

        AppException exception = assertThrows(
                AppException.class,
                () -> unterlagenService.createUnterlagen(request)
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(unterlagenRepository, never()).save(any(UnterlagenEntity.class));
    }

    @Test
    void getUnterlagenByIdShouldThrowNotFoundWhenMissing() {
        when(unterlagenRepository.findById(77L)).thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> unterlagenService.getUnterlagenById(77L)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void deleteUnterlagenShouldDeleteWhenExists() {
        when(unterlagenRepository.existsById(5L)).thenReturn(true);

        unterlagenService.deleteUnterlagen(5L);

        verify(unterlagenRepository).deleteById(5L);
    }
}
