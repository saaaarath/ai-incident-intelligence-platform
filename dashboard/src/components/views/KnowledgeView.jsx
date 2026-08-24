import React from 'react';
import { BookOpen, Search, FileText } from 'lucide-react';

export function KnowledgeView() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <div>
        <h3 style={{ fontSize: '1.1rem', fontWeight: 600 }}>Operational Knowledge & Runbooks</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
          Historical incident postmortems and semantic vector embeddings for rapid mitigation
        </p>
      </div>

      <div className="state-container">
        <div className="state-icon-wrapper">
          <BookOpen size={24} color="#818cf8" />
        </div>
        <h4 className="state-title">Semantic Incident Intelligence</h4>
        <p className="state-description">
          Automated similarity search and runbook matching are activated when selecting active incidents in the Incident Stream.
        </p>
      </div>
    </div>
  );
}
