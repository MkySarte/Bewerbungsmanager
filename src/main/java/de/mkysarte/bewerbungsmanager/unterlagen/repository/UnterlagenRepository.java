package de.mkysarte.bewerbungsmanager.unterlagen.repository;

import de.mkysarte.bewerbungsmanager.unterlagen.entity.UnterlagenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnterlagenRepository extends JpaRepository<UnterlagenEntity, Long> {

    boolean existsByUserId(Long userId);
}
