package com.aiincident.logprocessor.historical;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for querying, searching, and managing operational runbooks.
 */
@Service
public class RunbookService {

    private final RunbookRepository repository;
    private final HistoricalIncidentSeeder seeder;

    public RunbookService(RunbookRepository repository, HistoricalIncidentSeeder seeder) {
        this.repository = repository;
        this.seeder = seeder;
    }

    @Transactional(readOnly = true)
    public List<Runbook> getAllRunbooks() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Runbook> getById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Runbook> getByRunbookId(String runbookId) {
        if (runbookId == null || runbookId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByRunbookId(runbookId.trim());
    }

    @Transactional(readOnly = true)
    public List<Runbook> getByCategory(HistoricalIncidentCategory category) {
        if (category == null) {
            return repository.findAll();
        }
        return repository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<Runbook> getByApplicableService(String service) {
        if (service == null || service.isBlank()) {
            return repository.findAll();
        }
        return repository.findByApplicableService(service.trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public List<Runbook> searchRunbooks(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAll();
        }
        return repository.searchRunbooks(query.trim());
    }

    @Transactional(readOnly = true)
    public List<Runbook> filter(HistoricalIncidentCategory category, String service, String query) {
        if (query != null && !query.isBlank()) {
            List<Runbook> searchResults = repository.searchRunbooks(query.trim());
            return searchResults.stream()
                    .filter(r -> category == null || r.getCategory() == category)
                    .filter(r -> service == null || service.isBlank() ||
                            r.getApplicableServices().stream().anyMatch(s -> s.equalsIgnoreCase(service.trim())))
                    .toList();
        }

        if (category != null && service != null && !service.isBlank()) {
            return repository.findByApplicableServiceAndCategory(service.trim().toLowerCase(), category);
        } else if (category != null) {
            return repository.findByCategory(category);
        } else if (service != null && !service.isBlank()) {
            return repository.findByApplicableService(service.trim().toLowerCase());
        }

        return repository.findAll();
    }

    @Transactional
    public Runbook save(Runbook runbook) {
        return repository.save(runbook);
    }

    @Transactional
    public int seedRunbooks() {
        return seeder.seedCanonicalRunbooks();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }
}
