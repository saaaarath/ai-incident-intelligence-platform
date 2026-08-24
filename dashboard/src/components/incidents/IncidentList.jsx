import React, { useState, useMemo } from 'react';
import { IncidentStats } from './IncidentStats';
import { IncidentFilters } from './IncidentFilters';
import { IncidentCard } from './IncidentCard';
import { IncidentDetail } from './IncidentDetail';
import { LoadingState } from '../common/LoadingState';
import { ErrorState } from '../common/ErrorState';
import { EmptyState } from '../common/EmptyState';

export function IncidentList({
  incidents = [],
  loading,
  error,
  onRetry,
  onIncidentUpdated
}) {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [severityFilter, setSeverityFilter] = useState('ALL');
  const [serviceFilter, setServiceFilter] = useState('ALL');
  const [selectedIncident, setSelectedIncident] = useState(null);

  // Extract unique services from incident list for filtering
  const availableServices = useMemo(() => {
    const set = new Set();
    incidents.forEach(inc => {
      if (inc.primaryService) set.add(inc.primaryService);
    });
    return Array.from(set);
  }, [incidents]);

  // Filtered incidents
  const filteredIncidents = useMemo(() => {
    return incidents.filter((inc) => {
      // Status filter
      if (statusFilter !== 'ALL' && (inc.status || '').toUpperCase() !== statusFilter) {
        return false;
      }
      // Severity filter
      if (severityFilter !== 'ALL' && (inc.severity || '').toUpperCase() !== severityFilter) {
        return false;
      }
      // Service filter
      if (serviceFilter !== 'ALL' && inc.primaryService !== serviceFilter) {
        return false;
      }
      // Text search
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const titleMatch = (inc.title || '').toLowerCase().includes(query);
        const descMatch = (inc.description || '').toLowerCase().includes(query);
        const serviceMatch = (inc.primaryService || '').toLowerCase().includes(query);
        const fingerprintMatch = (inc.fingerprint || '').toLowerCase().includes(query);
        const idMatch = String(inc.id || '').includes(query);

        if (!titleMatch && !descMatch && !serviceMatch && !fingerprintMatch && !idMatch) {
          return false;
        }
      }
      return true;
    });
  }, [incidents, statusFilter, severityFilter, serviceFilter, searchQuery]);

  const handleIncidentUpdated = (updated) => {
    setSelectedIncident(updated);
    if (onIncidentUpdated) {
      onIncidentUpdated(updated);
    }
  };

  return (
    <>
      <IncidentStats incidents={incidents} />

      <IncidentFilters
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        statusFilter={statusFilter}
        onStatusChange={setStatusFilter}
        severityFilter={severityFilter}
        onSeverityChange={setSeverityFilter}
        serviceFilter={serviceFilter}
        onServiceChange={setServiceFilter}
        services={availableServices}
      />

      {loading && <LoadingState message="Fetching live incidents from AI-SRE backend..." />}

      {!loading && error && (
        <ErrorState
          title="Backend Connection Error"
          message={error}
          onRetry={onRetry}
        />
      )}

      {!loading && !error && filteredIncidents.length === 0 && (
        <EmptyState
          title={incidents.length === 0 ? "No Active Incidents" : "No Matching Incidents"}
          message={incidents.length === 0 
            ? "Your telemetry pipeline is clear. No incidents currently detected." 
            : "No incidents match your current filter and search criteria."
          }
        />
      )}

      {!loading && !error && filteredIncidents.length > 0 && (
        <div className="incident-list">
          {filteredIncidents.map((incident) => (
            <IncidentCard
              key={incident.id}
              incident={incident}
              isSelected={selectedIncident && selectedIncident.id === incident.id}
              onClick={setSelectedIncident}
            />
          ))}
        </div>
      )}

      {selectedIncident && (
        <IncidentDetail
          incident={selectedIncident}
          onClose={() => setSelectedIncident(null)}
          onIncidentUpdated={handleIncidentUpdated}
        />
      )}
    </>
  );
}
