import React from 'react';
import { Search } from 'lucide-react';

export function IncidentFilters({
  searchQuery,
  onSearchChange,
  statusFilter,
  onStatusChange,
  severityFilter,
  onSeverityChange,
  serviceFilter,
  onServiceChange,
  services = []
}) {
  return (
    <div className="filter-bar">
      <div className="search-input-wrapper">
        <Search size={16} />
        <input
          type="text"
          className="search-input"
          placeholder="Filter by title, description, or fingerprint..."
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </div>

      <div className="filter-group">
        <select
          className="select-input"
          value={statusFilter}
          onChange={(e) => onStatusChange(e.target.value)}
        >
          <option value="ALL">All Statuses</option>
          <option value="OPEN">Open</option>
          <option value="INVESTIGATING">Investigating</option>
          <option value="RESOLVED">Resolved</option>
          <option value="CLOSED">Closed</option>
        </select>

        <select
          className="select-input"
          value={severityFilter}
          onChange={(e) => onSeverityChange(e.target.value)}
        >
          <option value="ALL">All Severities</option>
          <option value="CRITICAL">Critical</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>

        {services.length > 0 && (
          <select
            className="select-input"
            value={serviceFilter}
            onChange={(e) => onServiceChange(e.target.value)}
          >
            <option value="ALL">All Services</option>
            {services.map((svc) => (
              <option key={svc} value={svc}>{svc}</option>
            ))}
          </select>
        )}
      </div>
    </div>
  );
}
