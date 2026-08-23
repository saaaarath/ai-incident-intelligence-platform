package com.aiincident.logprocessor.dependency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "service_dependencies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_service_dep_source_target", columnNames = {"source_service", "target_service"})
        },
        indexes = {
                @Index(name = "idx_dep_source", columnList = "source_service"),
                @Index(name = "idx_dep_target", columnList = "target_service")
        }
)
public class ServiceDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_service", nullable = false)
    private String sourceService;

    @Column(name = "target_service", nullable = false)
    private String targetService;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false)
    private ServiceDependencyType dependencyType;

    @Column(name = "criticality")
    private String criticality;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ServiceDependency() {
    }

    public ServiceDependency(
            String sourceService,
            String targetService,
            ServiceDependencyType dependencyType,
            String criticality,
            String description) {
        this.sourceService = sourceService != null ? sourceService.toLowerCase().trim() : "unknown";
        this.targetService = targetService != null ? targetService.toLowerCase().trim() : "unknown";
        this.dependencyType = dependencyType != null ? dependencyType : ServiceDependencyType.HTTP_REST;
        this.criticality = criticality != null ? criticality : "HIGH";
        this.description = description;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService != null ? sourceService.toLowerCase().trim() : null;
    }

    public String getTargetService() {
        return targetService;
    }

    public void setTargetService(String targetService) {
        this.targetService = targetService != null ? targetService.toLowerCase().trim() : null;
    }

    public ServiceDependencyType getDependencyType() {
        return dependencyType;
    }

    public void setDependencyType(ServiceDependencyType dependencyType) {
        this.dependencyType = dependencyType;
    }

    public String getCriticality() {
        return criticality;
    }

    public void setCriticality(String criticality) {
        this.criticality = criticality;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
