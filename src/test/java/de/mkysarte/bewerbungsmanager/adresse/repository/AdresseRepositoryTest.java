package de.mkysarte.bewerbungsmanager.adresse.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdresseRepositoryTest {

    @Mock
    private AdresseRepository adresseRepository;

    @Test
    void existsByFirmaIdShouldReturnTrueWhenStubbed() {
        when(adresseRepository.existsByFirmaId(1L)).thenReturn(true);

        boolean exists = adresseRepository.existsByFirmaId(1L);

        assertTrue(exists);
    }

    @Test
    void existsByFirmaIdShouldReturnFalseWhenStubbed() {
        when(adresseRepository.existsByFirmaId(2L)).thenReturn(false);

        boolean exists = adresseRepository.existsByFirmaId(2L);

        assertFalse(exists);
    }
}
