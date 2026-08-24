package com.aiincident.logprocessor.historical;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for querying, searching, and managing historical operational incidents and post-mortems.
 */
@Service
public class HistoricalIncidentService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalIncidentService.class);

    private final HistoricalIncidentRepository repository;
    private final HistoricalIncidentSeeder seeder;

    public HistoricalIncidentService(
            HistoricalIncidentRepository repository,
            HistoricalIncidentSeeder seeder) {
        this.repository = repository;
        this.seeder = seeder;
    }

    @Transactional(readOnly = true)
    public List<HistoricalIncident> getAllIncidents() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<HistoricalIncident> getById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<HistoricalIncident> getByIncidentId(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIncidentId(incidentId.trim());
    }

    @Transactional(readOnly = true)
    public List<HistoricalIncident> getByCategory(HistoricalIncidentCategory category) {
        if (category == null) {
            return repository.findAll();
        }
        return repository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<HistoricalIncident> getByAffectedService(String service) {
        if (service == null || service.isBlank()) {
            return repository.findAll();
        }
        return repository.findByAffectedService(service.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public List<HistoricalIncident> searchIncidents(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAll();
        }
        return repository.searchIncidents(query.trim());
    }

    @Transactional(readOnly = true)
    public List<HistoricalIncident> filter(HistoricalIncidentCategory category, String service, String query) {
        if (query != null && !query.isBlank()) {
            List<HistoricalIncident> searchResults = repository.searchIncidents(query.trim());
            return searchResults.stream()
                    .filter(i -> category == null || i.getCategory() == category)
                    .filter(i -> service == null || service.isBlank() ||
                            i.getAffectedServices().stream().anyMatch(s -> s.equalsIgnoreCase(service.trim())))
                    .toList();
        }

        if (category != null && service != null && !service.isBlank()) {
            return repository.findByAffectedServiceAndCategory(service.trim().toLowerCase(), category);
        } else if (category != null) {
            return repository.findByCategory(category);
        } else if (service != null && !service.isBlank()) {
            return repository.findByAffectedService(service.trim().toLowerCase());
        }

        return repository.findAll();
    }

    @Transactional
    public HistoricalIncident save(HistoricalIncident incident) {
        return repository.save(incident);
    }

    @Transactional
    public int seedDataset() {
        return seeder.seedCanonicalDataset();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }
}
