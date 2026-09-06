package de.mkysarte.bewerbungsmanager.document.repository;

import de.mkysarte.bewerbungsmanager.document.entity.DocumentEntity;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentScope;
import de.mkysarte.bewerbungsmanager.document.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findByScope(DocumentScope scope);

    List<DocumentEntity> findByType(DocumentType type);

    List<DocumentEntity> findByApplicationId(Long applicationId);

    List<DocumentEntity> findByScopeAndType(DocumentScope scope, DocumentType type);

    List<DocumentEntity> findByScopeAndApplicationId(DocumentScope scope, Long applicationId);

    List<DocumentEntity> findByTypeAndApplicationId(DocumentType type, Long applicationId);

    List<DocumentEntity> findByScopeAndTypeAndApplicationId(DocumentScope scope, DocumentType type, Long applicationId);
}
