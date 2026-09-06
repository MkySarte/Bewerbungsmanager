package de.mkysarte.bewerbungsmanager.status.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusRepositoryTest {

    @Mock
    private StatusRepository statusRepository;

    @Test
    void existsByTitelShouldReturnTrueWhenStubbed() {
        when(statusRepository.existsByTitel("Offen")).thenReturn(true);

        boolean exists = statusRepository.existsByTitel("Offen");

        assertTrue(exists);
    }

    @Test
    void existsByTitelShouldReturnFalseWhenStubbed() {
        when(statusRepository.existsByTitel("NichtVorhanden")).thenReturn(false);

        boolean exists = statusRepository.existsByTitel("NichtVorhanden");

        assertFalse(exists);
    }
}
