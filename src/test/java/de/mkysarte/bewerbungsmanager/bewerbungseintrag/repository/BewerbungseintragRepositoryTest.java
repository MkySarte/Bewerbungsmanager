package de.mkysarte.bewerbungsmanager.bewerbungseintrag.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BewerbungseintragRepositoryTest {

    @Mock
    private BewerbungseintragRepository bewerbungseintragRepository;

    @Test
    void existsByIdShouldReturnTrueWhenStubbed() {
        when(bewerbungseintragRepository.existsById(1L)).thenReturn(true);

        boolean exists = bewerbungseintragRepository.existsById(1L);

        assertTrue(exists);
    }

    @Test
    void existsByIdShouldReturnFalseWhenStubbed() {
        when(bewerbungseintragRepository.existsById(2L)).thenReturn(false);

        boolean exists = bewerbungseintragRepository.existsById(2L);

        assertFalse(exists);
    }
}
