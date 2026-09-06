package de.mkysarte.bewerbungsmanager.firma.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmaRepositoryTest {

    @Mock
    private FirmaRepository firmaRepository;

    @Test
    void existsByIdShouldReturnTrueWhenStubbed() {
        when(firmaRepository.existsById(1L)).thenReturn(true);

        boolean exists = firmaRepository.existsById(1L);

        assertTrue(exists);
    }

    @Test
    void existsByIdShouldReturnFalseWhenStubbed() {
        when(firmaRepository.existsById(2L)).thenReturn(false);

        boolean exists = firmaRepository.existsById(2L);

        assertFalse(exists);
    }
}
