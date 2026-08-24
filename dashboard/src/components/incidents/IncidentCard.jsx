import React from 'react';
import { SeverityBadge, StatusBadge } from '../common/Badge';
import { Clock, Server, Fingerprint, ChevronRight } from 'lucide-react';

export function IncidentCard({ incident, isSelected, onClick }) {
  const formattedTime = incident.startedAt 
    ? new Date(incident.startedAt).toLocaleString() 
    : (incident.detectedAt ? new Date(incident.detectedAt).toLocaleString() : 'N/A');

  return (
    <div 
      className={`incident-card ${isSelected ? 'selected' : ''}`}
      onClick={() => onClick(incident)}
    >
      <div className="incident-header">
        <div className="incident-title-area">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <span className="incident-id-tag">INC-{incident.id}</span>
            <SeverityBadge severity={incident.severity} />
            <StatusBadge status={incident.status} />
          </div>
          <h3 className="incident-title">{incident.title || 'Untitled Incident'}</h3>
        </div>
        <ChevronRight size={18} color="var(--text-muted)" />
      </div>

      {incident.description && (
        <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineClamp: 2, display: '-webkit-box', WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
          {incident.description}
        </p>
      )}

      <div className="incident-meta">
        {incident.primaryService && (
          <div className="meta-item">
            <Server size={14} color="var(--text-muted)" />
            <span>{incident.primaryService}</span>
          </div>
        )}

        <div className="meta-item">
          <Clock size={14} color="var(--text-muted)" />
          <span>{formattedTime}</span>
        </div>

        {incident.fingerprint && (
          <div className="meta-item" style={{ fontFamily: 'var(--font-mono)', fontSize: '0.72rem' }}>
            <Fingerprint size={14} color="var(--text-muted)" />
            <span>{incident.fingerprint}</span>
          </div>
        )}
      </div>
    </div>
  );
}
