package com.aiincident.logprocessor.historical;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeder component that populates PostgreSQL with historical incidents, runbooks, and postmortems on startup.
 */
@Component
public class HistoricalIncidentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoricalIncidentSeeder.class);

    private final HistoricalIncidentRepository incidentRepository;
    private final RunbookRepository runbookRepository;
    private final PostmortemRepository postmortemRepository;

    public HistoricalIncidentSeeder(
            HistoricalIncidentRepository incidentRepository,
            RunbookRepository runbookRepository,
            PostmortemRepository postmortemRepository) {
        this.incidentRepository = incidentRepository;
        this.runbookRepository = runbookRepository;
        this.postmortemRepository = postmortemRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAllOperationalKnowledge();
    }

    @Transactional
    public int seedAllOperationalKnowledge() {
        int incCount = seedCanonicalDataset();
        int rbCount = seedCanonicalRunbooks();
        int pmCount = seedCanonicalPostmortems();
        return incCount + rbCount + pmCount;
    }

    @Transactional
    public int seedCanonicalDataset() {
        List<HistoricalIncident> canonical = HistoricalIncidentDataset.getCanonicalIncidents();
        int seededCount = 0;

        for (HistoricalIncident incident : canonical) {
            if (incidentRepository.findByIncidentId(incident.getIncidentId()).isEmpty()) {
                incidentRepository.save(incident);
                seededCount++;
            }
        }

        if (seededCount > 0) {
            log.info("Successfully seeded {} historical incident records into PostgreSQL.", seededCount);
        } else {
            log.info("Historical incidents already seeded (total records: {}).", incidentRepository.count());
        }

        return seededCount;
    }

    @Transactional
    public int seedCanonicalRunbooks() {
        List<Runbook> canonical = RunbookDataset.getCanonicalRunbooks();
        int seededCount = 0;

        for (Runbook runbook : canonical) {
            if (runbookRepository.findByRunbookId(runbook.getRunbookId()).isEmpty()) {
                runbookRepository.save(runbook);
                seededCount++;
            }
        }

        if (seededCount > 0) {
            log.info("Successfully seeded {} operational runbooks into PostgreSQL.", seededCount);
        } else {
            log.info("Operational runbooks already seeded (total records: {}).", runbookRepository.count());
        }

        return seededCount;
    }

    @Transactional
    public int seedCanonicalPostmortems() {
        List<Postmortem> canonical = PostmortemDataset.getCanonicalPostmortems();
        int seededCount = 0;

        for (Postmortem postmortem : canonical) {
            if (postmortemRepository.findByPostmortemId(postmortem.getPostmortemId()).isEmpty()) {
                postmortemRepository.save(postmortem);
                seededCount++;
            }
        }

        if (seededCount > 0) {
            log.info("Successfully seeded {} post-mortem records into PostgreSQL.", seededCount);
        } else {
            log.info("Post-mortem records already seeded (total records: {}).", postmortemRepository.count());
        }

        return seededCount;
    }
}
