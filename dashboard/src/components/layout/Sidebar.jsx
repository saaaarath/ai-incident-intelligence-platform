import React from 'react';
import { 
  Activity, 
  Flame, 
  Layers, 
  BookOpen, 
  Cpu, 
  CheckCircle2, 
  XCircle, 
  Loader2 
} from 'lucide-react';

export function Sidebar({ currentView, onViewChange, openIncidentsCount = 0, backendStatus = 'checking' }) {
  const navItems = [
    { id: 'incidents', label: 'Incident Stream', icon: Flame, badge: openIncidentsCount > 0 ? openIncidentsCount : null },
    { id: 'metrics', label: 'Telemetry & Services', icon: Layers },
    { id: 'knowledge', label: 'Runbooks & Knowledge', icon: BookOpen },
    { id: 'rca', label: 'AI Root Cause Analysis', icon: Cpu },
  ];

  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div className="logo-badge">
          <Activity size={20} />
        </div>
        <div>
          <h1 className="brand-title">AI-SRE Platform</h1>
          <div className="brand-subtitle">Incident Intelligence</div>
        </div>
      </div>

      <nav className="sidebar-nav">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentView === item.id;
          return (
            <button
              key={item.id}
              className={`nav-item ${isActive ? 'active' : ''}`}
              onClick={() => onViewChange(item.id)}
            >
              <Icon size={18} />
              <span>{item.label}</span>
              {item.badge !== null && item.badge !== undefined && (
                <span className="nav-badge">{item.badge}</span>
              )}
            </button>
          );
        })}
      </nav>

      <div className="sidebar-footer">
        <div className="backend-indicator">
          <span className={`status-dot ${backendStatus}`} />
          <span>
            {backendStatus === 'online' && 'Backend Connected'}
            {backendStatus === 'offline' && 'Backend Offline'}
            {backendStatus === 'checking' && 'Connecting...'}
          </span>
        </div>
      </div>
    </aside>
  );
}
