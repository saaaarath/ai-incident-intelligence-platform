import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

export function ErrorState({ title = 'Error Loading Data', message, onRetry }) {
  return (
    <div className="state-container">
      <div className="state-icon-wrapper error">
        <AlertTriangle size={24} />
      </div>
      <h3 className="state-title">{title}</h3>
      <p className="state-description">
        {message || 'An unexpected error occurred while communicating with the backend API.'}
      </p>
      {onRetry && (
        <button className="btn btn-secondary btn-sm" onClick={onRetry}>
          <RefreshCw size={14} /> Retry Connection
        </button>
      )}
    </div>
  );
}
