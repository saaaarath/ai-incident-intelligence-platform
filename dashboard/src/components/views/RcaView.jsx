import React from 'react';
import { Cpu, CheckCircle } from 'lucide-react';

export function RcaView() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <div>
        <h3 style={{ fontSize: '1.1rem', fontWeight: 600 }}>AI Root Cause Analysis Engine</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
          Automated multi-modal fault correlation, topology tracing, and prompt-engineered diagnostic summaries
        </p>
      </div>

      <div className="state-container">
        <div className="state-icon-wrapper">
          <Cpu size={24} color="#818cf8" />
        </div>
        <h4 className="state-title">Root Cause Analysis Pipeline</h4>
        <p className="state-description">
          RCA reports are automatically synthesized upon incident detection and available through the Incident Stream details.
        </p>
      </div>
    </div>
  );
}
