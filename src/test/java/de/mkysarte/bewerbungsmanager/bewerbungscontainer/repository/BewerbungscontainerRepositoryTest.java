package de.mkysarte.bewerbungsmanager.bewerbungscontainer.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BewerbungscontainerRepositoryTest {

    @Mock
    private BewerbungscontainerRepository bewerbungscontainerRepository;

    @Test
    void existsByUserIdShouldReturnTrueWhenStubbed() {
        when(bewerbungscontainerRepository.existsByUserId(10L)).thenReturn(true);

        boolean exists = bewerbungscontainerRepository.existsByUserId(10L);

        assertTrue(exists);
    }

    @Test
    void existsByUserIdShouldReturnFalseWhenStubbed() {
        when(bewerbungscontainerRepository.existsByUserId(999L)).thenReturn(false);

        boolean exists = bewerbungscontainerRepository.existsByUserId(999L);

        assertFalse(exists);
    }
}
