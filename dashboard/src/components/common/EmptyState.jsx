import React from 'react';
import { ShieldCheck, Plus } from 'lucide-react';

export function EmptyState({ 
  title = 'All Systems Operational', 
  message = 'No active incidents found matching your current filter criteria.',
  actionLabel,
  onAction 
}) {
  return (
    <div className="state-container">
      <div className="state-icon-wrapper">
        <ShieldCheck size={24} color="#10b981" />
      </div>
      <h3 className="state-title">{title}</h3>
      <p className="state-description">{message}</p>
      {actionLabel && onAction && (
        <button className="btn btn-secondary btn-sm" onClick={onAction}>
          <Plus size={14} /> {actionLabel}
        </button>
      )}
    </div>
  );
}
