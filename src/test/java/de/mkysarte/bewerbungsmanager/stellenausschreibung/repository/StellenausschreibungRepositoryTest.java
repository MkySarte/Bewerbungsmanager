package de.mkysarte.bewerbungsmanager.stellenausschreibung.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StellenausschreibungRepositoryTest {

    @Mock
    private StellenausschreibungRepository stellenausschreibungRepository;

    @Test
    void existsByIdShouldReturnTrueWhenStubbed() {
        when(stellenausschreibungRepository.existsById(1L)).thenReturn(true);

        boolean exists = stellenausschreibungRepository.existsById(1L);

        assertTrue(exists);
    }

    @Test
    void existsByIdShouldReturnFalseWhenStubbed() {
        when(stellenausschreibungRepository.existsById(2L)).thenReturn(false);

        boolean exists = stellenausschreibungRepository.existsById(2L);

        assertFalse(exists);
    }
}
