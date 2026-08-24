package com.aiincident.logprocessor.historical;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeder component that populates PostgreSQL with historical operational incident dataset on startup.
 */
@Component
public class HistoricalIncidentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoricalIncidentSeeder.class);

    private final HistoricalIncidentRepository repository;

    public HistoricalIncidentSeeder(HistoricalIncidentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedCanonicalDataset();
    }

    @Transactional
    public int seedCanonicalDataset() {
        List<HistoricalIncident> canonical = HistoricalIncidentDataset.getCanonicalIncidents();
        int seededCount = 0;

        for (HistoricalIncident incident : canonical) {
            if (repository.findByIncidentId(incident.getIncidentId()).isEmpty()) {
                repository.save(incident);
                seededCount++;
            }
        }

        if (seededCount > 0) {
            log.info("Successfully seeded {} historical incident post-mortem records into PostgreSQL.", seededCount);
        } else {
            log.info("Historical incident dataset is already seeded (total records: {}).", repository.count());
        }

        return seededCount;
    }
}
