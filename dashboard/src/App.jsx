import React, { useState, useEffect, useCallback } from 'react';
import { AppShell } from './components/layout/AppShell';
import { IncidentList } from './components/incidents/IncidentList';
import { ServicesView } from './components/views/ServicesView';
import { KnowledgeView } from './components/views/KnowledgeView';
import { RcaView } from './components/views/RcaView';
import { incidentApi } from './services/api';

export function App() {
  const [currentView, setCurrentView] = useState('incidents');
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [backendStatus, setBackendStatus] = useState('checking');

  const fetchIncidents = useCallback(async (showRefreshing = false) => {
    if (showRefreshing) setIsRefreshing(true);
    else setLoading(true);
    setError(null);

    try {
      const data = await incidentApi.getIncidents();
      setIncidents(data || []);
      setBackendStatus('online');
    } catch (err) {
      setError(err.message || 'Failed to communicate with backend Incident API');
      setBackendStatus('offline');
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  // Initial load and periodic backend poll
  useEffect(() => {
    fetchIncidents();
    const interval = setInterval(() => {
      fetchIncidents(true);
    }, 15000);
    return () => clearInterval(interval);
  }, [fetchIncidents]);

  const handleIncidentUpdated = (updatedIncident) => {
    setIncidents(prev => 
      prev.map(item => item.id === updatedIncident.id ? updatedIncident : item)
    );
  };

  const openCount = incidents.filter(i => (i.status || '').toUpperCase() === 'OPEN').length;
  const activeCount = incidents.filter(i => ['OPEN', 'INVESTIGATING'].includes((i.status || '').toUpperCase())).length;

  const viewTitles = {
    incidents: 'Incident Stream & Operations',
    metrics: 'Telemetry & Services Overview',
    knowledge: 'Knowledge Base & Runbooks',
    rca: 'AI Root Cause Analysis'
  };

  return (
    <AppShell
      currentView={currentView}
      onViewChange={setCurrentView}
      viewTitle={viewTitles[currentView] || 'Dashboard'}
      onRefresh={() => fetchIncidents(true)}
      isRefreshing={isRefreshing}
      openIncidentsCount={openCount}
      activeIncidentsCount={activeCount}
      backendStatus={backendStatus}
    >
      {currentView === 'incidents' && (
        <IncidentList
          incidents={incidents}
          loading={loading}
          error={error}
          onRetry={() => fetchIncidents(false)}
          onIncidentUpdated={handleIncidentUpdated}
        />
      )}

      {currentView === 'metrics' && <ServicesView />}
      {currentView === 'knowledge' && <KnowledgeView />}
      {currentView === 'rca' && <RcaView />}
    </AppShell>
  );
}

export default App;
