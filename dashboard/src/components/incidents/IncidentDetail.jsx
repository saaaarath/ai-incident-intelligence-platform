import React, { useState, useEffect } from 'react';
import { SeverityBadge, StatusBadge } from '../common/Badge';
import { incidentApi } from '../../services/api';
import { 
  X, 
  CheckCircle, 
  PlayCircle, 
  Archive, 
  FileText, 
  Layers, 
  Sparkles,
  AlertCircle,
  Loader2
} from 'lucide-react';

export function IncidentDetail({ incident, onClose, onIncidentUpdated }) {
  const [evidence, setEvidence] = useState([]);
  const [loadingEvidence, setLoadingEvidence] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);
  const [actionError, setActionError] = useState(null);

  useEffect(() => {
    if (!incident) return;
    let isMounted = true;

    async function loadEvidence() {
      setLoadingEvidence(true);
      try {
        const data = await incidentApi.getIncidentEvidence(incident.id);
        if (isMounted) setEvidence(data || []);
      } catch (err) {
        console.warn('Could not fetch evidence:', err.message);
      } finally {
        if (isMounted) setLoadingEvidence(false);
      }
    }

    loadEvidence();
    return () => { isMounted = false; };
  }, [incident]);

  if (!incident) return null;

  const handleAction = async (actionType) => {
    setIsUpdating(true);
    setActionError(null);
    try {
      let updated;
      if (actionType === 'ACKNOWLEDGE') {
        updated = await incidentApi.acknowledgeIncident(incident.id);
      } else if (actionType === 'RESOLVE') {
        updated = await incidentApi.resolveIncident(incident.id);
      } else if (actionType === 'CLOSE') {
        updated = await incidentApi.closeIncident(incident.id);
      }
      if (onIncidentUpdated) {
        onIncidentUpdated(updated);
      }
    } catch (err) {
      setActionError(err.message || `Failed to ${actionType.toLowerCase()} incident`);
    } finally {
      setIsUpdating(false);
    }
  };

  const status = (incident.status || '').toUpperCase();

  return (
    <div className="detail-modal-overlay" onClick={onClose}>
      <div className="detail-drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-header">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.4rem' }}>
              <span className="incident-id-tag">INC-{incident.id}</span>
              <SeverityBadge severity={incident.severity} />
              <StatusBadge status={incident.status} />
            </div>
            <h2 style={{ fontSize: '1.2rem', fontWeight: 600 }}>{incident.title || 'Incident Details'}</h2>
          </div>
          <button className="btn-icon" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <div className="drawer-content">
          {actionError && (
            <div style={{
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              color: '#f87171',
              padding: '0.75rem 1rem',
              borderRadius: 'var(--radius-md)',
              fontSize: '0.8125rem'
            }}>
              {actionError}
            </div>
          )}

          {/* Metadata Grid */}
          <div className="drawer-section">
            <h4 className="section-heading">Overview</h4>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: '0.75rem',
              background: 'var(--bg-primary)',
              padding: '1rem',
              borderRadius: 'var(--radius-md)',
              border: '1px solid var(--border-subtle)',
              fontSize: '0.8125rem'
            }}>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Primary Service: </span>
                <strong>{incident.primaryService || 'N/A'}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Metric: </span>
                <strong>{incident.metric || 'N/A'}</strong>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Started At: </span>
                <span>{incident.startedAt ? new Date(incident.startedAt).toLocaleString() : 'N/A'}</span>
              </div>
              <div>
                <span style={{ color: 'var(--text-muted)' }}>Detected At: </span>
                <span>{incident.detectedAt ? new Date(incident.detectedAt).toLocaleString() : 'N/A'}</span>
              </div>
            </div>
          </div>

          {/* Description */}
          {incident.description && (
            <div className="drawer-section">
              <h4 className="section-heading">Description</h4>
              <div style={{
                background: 'var(--bg-primary)',
                padding: '1rem',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--border-subtle)',
                fontSize: '0.85rem',
                color: 'var(--text-secondary)',
                lineHeight: 1.6
              }}>
                {incident.description}
              </div>
            </div>
          )}

          {/* Correlated Evidence */}
          <div className="drawer-section">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <h4 className="section-heading">Correlated Evidence</h4>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                {evidence.length} record{evidence.length !== 1 ? 's' : ''}
              </span>
            </div>

            {loadingEvidence ? (
              <div style={{ textAlign: 'center', padding: '1rem', color: 'var(--text-muted)' }}>
                Loading evidence...
              </div>
            ) : evidence.length === 0 ? (
              <div style={{
                background: 'var(--bg-primary)',
                padding: '1rem',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--border-subtle)',
                fontSize: '0.8125rem',
                color: 'var(--text-muted)',
                textAlign: 'center'
              }}>
                No correlated anomaly evidence records found for this incident.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                {evidence.map((ev, idx) => (
                  <div key={ev.id || idx} className="evidence-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                        {ev.service || 'Service'}: {ev.metric || 'Anomaly'}
                      </span>
                      <SeverityBadge severity={ev.severity} />
                    </div>
                    {ev.description && <span style={{ color: 'var(--text-secondary)' }}>{ev.description}</span>}
                    {ev.anomalyScore && (
                      <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                        Score: {ev.anomalyScore}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Lifecycle Action Buttons */}
          <div className="action-bar">
            {status === 'OPEN' && (
              <button
                className="btn btn-primary"
                onClick={() => handleAction('ACKNOWLEDGE')}
                disabled={isUpdating}
              >
                <PlayCircle size={16} />
                <span>{isUpdating ? 'Updating...' : 'Acknowledge Incident'}</span>
              </button>
            )}

            {(status === 'OPEN' || status === 'INVESTIGATING') && (
              <button
                className="btn btn-secondary"
                style={{ borderColor: 'rgba(16, 185, 129, 0.4)', color: '#10b981' }}
                onClick={() => handleAction('RESOLVE')}
                disabled={isUpdating}
              >
                <CheckCircle size={16} />
                <span>{isUpdating ? 'Updating...' : 'Resolve Incident'}</span>
              </button>
            )}

            {status === 'RESOLVED' && (
              <button
                className="btn btn-secondary"
                onClick={() => handleAction('CLOSE')}
                disabled={isUpdating}
              >
                <Archive size={16} />
                <span>{isUpdating ? 'Updating...' : 'Close Incident'}</span>
              </button>
            )}

            <button className="btn btn-secondary" onClick={onClose} style={{ marginLeft: 'auto' }}>
              Close Panel
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
