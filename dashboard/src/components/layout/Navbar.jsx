import React from 'react';
import { RefreshCw, Radio, Bell } from 'lucide-react';

export function Navbar({ title, onRefresh, isRefreshing, activeIncidentsCount }) {
  return (
    <header className="top-navbar">
      <div className="navbar-left">
        <h2 className="page-title">{title}</h2>
      </div>

      <div className="navbar-right">
        {activeIncidentsCount > 0 && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0.35rem 0.75rem',
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: '9999px',
            fontSize: '0.75rem',
            color: '#f87171',
            fontWeight: 500
          }}>
            <Radio size={12} className="status-dot checking" />
            <span>{activeIncidentsCount} Active Alert{activeIncidentsCount > 1 ? 's' : ''}</span>
          </div>
        )}

        <button 
          className="btn btn-secondary btn-sm"
          onClick={onRefresh}
          disabled={isRefreshing}
          title="Refresh Data"
        >
          <RefreshCw size={14} className={isRefreshing ? 'spinner' : ''} />
          <span>{isRefreshing ? 'Syncing...' : 'Refresh'}</span>
        </button>
      </div>
    </header>
  );
}
