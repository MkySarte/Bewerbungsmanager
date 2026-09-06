package de.mkysarte.bewerbungsmanager.user.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void existsByUsernameShouldReturnTrueWhenStubbed() {
        when(userRepository.existsByUsername("anna")).thenReturn(true);

        boolean exists = userRepository.existsByUsername("anna");

        assertTrue(exists);
    }

    @Test
    void existsByUsernameShouldReturnFalseWhenStubbed() {
        when(userRepository.existsByUsername("unknown")).thenReturn(false);

        boolean exists = userRepository.existsByUsername("unknown");

        assertFalse(exists);
    }
}
