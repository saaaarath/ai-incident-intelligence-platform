import React from 'react';
import { AlertCircle, AlertTriangle, CheckCircle, Clock } from 'lucide-react';

export function IncidentStats({ incidents = [] }) {
  const total = incidents.length;
  const critical = incidents.filter(i => (i.severity || '').toUpperCase() === 'CRITICAL').length;
  const open = incidents.filter(i => (i.status || '').toUpperCase() === 'OPEN').length;
  const investigating = incidents.filter(i => (i.status || '').toUpperCase() === 'INVESTIGATING').length;
  const resolved = incidents.filter(i => (i.status || '').toUpperCase() === 'RESOLVED' || (i.status || '').toUpperCase() === 'CLOSED').length;

  return (
    <div className="stats-grid">
      <div className="stat-card">
        <div className="stat-label">
          <span>Active Open</span>
          <AlertCircle size={16} color="#f43f5e" />
        </div>
        <div className="stat-value" style={{ color: open > 0 ? '#f43f5e' : 'inherit' }}>
          {open}
        </div>
      </div>

      <div className="stat-card">
        <div className="stat-label">
          <span>Investigating</span>
          <Clock size={16} color="#0ea5e9" />
        </div>
        <div className="stat-value" style={{ color: investigating > 0 ? '#0ea5e9' : 'inherit' }}>
          {investigating}
        </div>
      </div>

      <div className="stat-card">
        <div className="stat-label">
          <span>Critical Severity</span>
          <AlertTriangle size={16} color="#ef4444" />
        </div>
        <div className="stat-value" style={{ color: critical > 0 ? '#ef4444' : 'inherit' }}>
          {critical}
        </div>
      </div>

      <div className="stat-card">
        <div className="stat-label">
          <span>Resolved / Closed</span>
          <CheckCircle size={16} color="#10b981" />
        </div>
        <div className="stat-value" style={{ color: '#10b981' }}>
          {resolved}
        </div>
      </div>
    </div>
  );
}
