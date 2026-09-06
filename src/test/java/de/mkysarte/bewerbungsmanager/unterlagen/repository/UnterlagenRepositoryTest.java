package de.mkysarte.bewerbungsmanager.unterlagen.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnterlagenRepositoryTest {

    @Mock
    private UnterlagenRepository unterlagenRepository;

    @Test
    void existsByUserIdShouldReturnTrueWhenStubbed() {
        when(unterlagenRepository.existsByUserId(10L)).thenReturn(true);

        boolean exists = unterlagenRepository.existsByUserId(10L);

        assertTrue(exists);
    }

    @Test
    void existsByUserIdShouldReturnFalseWhenStubbed() {
        when(unterlagenRepository.existsByUserId(999L)).thenReturn(false);

        boolean exists = unterlagenRepository.existsByUserId(999L);

        assertFalse(exists);
    }
}
