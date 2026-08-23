package com.aiincident.logprocessor.dependency;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceDependencyRepository extends JpaRepository<ServiceDependency, Long> {

    List<ServiceDependency> findBySourceService(String sourceService);

    List<ServiceDependency> findByTargetService(String targetService);

    Optional<ServiceDependency> findBySourceServiceAndTargetService(String sourceService, String targetService);

    boolean existsBySourceServiceAndTargetService(String sourceService, String targetService);

    void deleteBySourceServiceAndTargetService(String sourceService, String targetService);
}
