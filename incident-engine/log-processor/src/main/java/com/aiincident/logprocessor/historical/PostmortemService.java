package com.aiincident.logprocessor.historical;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for querying, searching, and managing incident postmortems.
 */
@Service
public class PostmortemService {

    private final PostmortemRepository repository;
    private final HistoricalIncidentSeeder seeder;

    public PostmortemService(PostmortemRepository repository, HistoricalIncidentSeeder seeder) {
        this.repository = repository;
        this.seeder = seeder;
    }

    @Transactional(readOnly = true)
    public List<Postmortem> getAllPostmortems() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Postmortem> getById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Postmortem> getByPostmortemId(String postmortemId) {
        if (postmortemId == null || postmortemId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByPostmortemId(postmortemId.trim());
    }

    @Transactional(readOnly = true)
    public Optional<Postmortem> getByIncidentId(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIncidentId(incidentId.trim());
    }

    @Transactional(readOnly = true)
    public List<Postmortem> getByCategory(HistoricalIncidentCategory category) {
        if (category == null) {
            return repository.findAll();
        }
        return repository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<Postmortem> searchPostmortems(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAll();
        }
        return repository.searchPostmortems(query.trim());
    }

    @Transactional(readOnly = true)
    public List<Postmortem> filter(HistoricalIncidentCategory category, String incidentId, String query) {
        if (query != null && !query.isBlank()) {
            List<Postmortem> searchResults = repository.searchPostmortems(query.trim());
            return searchResults.stream()
                    .filter(p -> category == null || p.getCategory() == category)
                    .filter(p -> incidentId == null || incidentId.isBlank() ||
                            p.getIncidentId().equalsIgnoreCase(incidentId.trim()))
                    .toList();
        }

        if (incidentId != null && !incidentId.isBlank()) {
            return repository.findByIncidentId(incidentId.trim())
                    .map(List::of)
                    .orElseGet(List::of);
        } else if (category != null) {
            return repository.findByCategory(category);
        }

        return repository.findAll();
    }

    @Transactional
    public Postmortem save(Postmortem postmortem) {
        return repository.save(postmortem);
    }

    @Transactional
    public int seedPostmortems() {
        return seeder.seedCanonicalPostmortems();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }
}
