import React, { useState, useEffect, useCallback } from 'react';
import { incidentApi } from '../../services/api';
import { SeverityBadge, StatusBadge } from '../common/Badge';
import { Button } from '../ui/button';
import { Card, CardContent } from '../ui/card';
import { Badge } from '../ui/badge';
import { RcaReportView } from '../rca/RcaReportView';
import { IncidentTimelineView } from '../timeline/IncidentTimelineView';
import { IncidentDependencyView } from '../dependencies/IncidentDependencyView';
import { SimilarIncidentsView } from '../historical/SimilarIncidentsView';
import { 
  ArrowLeft, 
  Sparkles, 
  PlayCircle, 
  CheckCircle2, 
  Archive, 
  Server, 
  Clock, 
  Activity, 
  Fingerprint, 
  Layers, 
  BookOpen, 
  Cpu,
  RefreshCw,
  AlertCircle
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function IncidentDetailPage({
  incidentId,
  onBack,
  onIncidentUpdated
}) {
  const [incident, setIncident] = useState(null);
  const [loadingIncident, setLoadingIncident] = useState(true);
  const [incidentError, setIncidentError] = useState(null);

  // Tabs: 'rca', 'timeline', 'dependencies', 'similar'
  const [activeTab, setActiveTab] = useState('rca');

  // Tab Data States
  const [rcaReport, setRcaReport] = useState(null);
  const [loadingRca, setLoadingRca] = useState(false);
  const [analyzingRca, setAnalyzingRca] = useState(false);
  const [rcaError, setRcaError] = useState(null);

  const [timeline, setTimeline] = useState(null);
  const [loadingTimeline, setLoadingTimeline] = useState(false);
  const [timelineError, setTimelineError] = useState(null);

  const [topology, setTopology] = useState(null);
  const [loadingTopology, setLoadingTopology] = useState(false);
  const [topologyError, setTopologyError] = useState(null);

  const [similarIncidents, setSimilarIncidents] = useState([]);
  const [loadingSimilar, setLoadingSimilar] = useState(false);
  const [similarError, setSimilarError] = useState(null);

  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  // 1. Fetch Incident Base
  const loadIncident = useCallback(async () => {
    setLoadingIncident(true);
    setIncidentError(null);
    try {
      const data = await incidentApi.getIncidentById(incidentId);
      setIncident(data);
    } catch (err) {
      setIncidentError(err.message || 'Failed to load incident details.');
    } finally {
      setLoadingIncident(false);
    }
  }, [incidentId]);

  // 2. Fetch RCA Analysis
  const loadRca = useCallback(async () => {
    setLoadingRca(true);
    setRcaError(null);
    try {
      const data = await incidentApi.getIncidentRca(incidentId);
      setRcaReport(data);
    } catch (err) {
      // If 404 / no analysis yet, set null without fatal error
      setRcaReport(null);
    } finally {
      setLoadingRca(false);
    }
  }, [incidentId]);

  // 3. Fetch Timeline
  const loadTimeline = useCallback(async () => {
    setLoadingTimeline(true);
    setTimelineError(null);
    try {
      const data = await incidentApi.getIncidentTimeline(incidentId);
      setTimeline(data);
    } catch (err) {
      setTimelineError(err.message || 'Failed to load timeline events.');
    } finally {
      setLoadingTimeline(false);
    }
  }, [incidentId]);

  // 4. Fetch Topology
  const loadTopology = useCallback(async (serviceName) => {
    if (!serviceName) return;
    setLoadingTopology(true);
    setTopologyError(null);
    try {
      const data = await incidentApi.getServiceTopology(serviceName);
      setTopology(data);
    } catch (err) {
      setTopologyError(err.message || 'Failed to load service dependencies.');
    } finally {
      setLoadingTopology(false);
    }
  }, []);

  // 5. Fetch Similar Incidents
  const loadSimilar = useCallback(async () => {
    setLoadingSimilar(true);
    setSimilarError(null);
    try {
      const data = await incidentApi.getSimilarIncidents(incidentId, 5);
      setSimilarIncidents(data || []);
    } catch (err) {
      setSimilarIncidents([]);
    } finally {
      setLoadingSimilar(false);
    }
  }, [incidentId]);

  useEffect(() => {
    loadIncident();
    loadRca();
    loadTimeline();
    loadSimilar();
  }, [loadIncident, loadRca, loadTimeline, loadSimilar]);

  useEffect(() => {
    if (incident?.primaryService) {
      loadTopology(incident.primaryService);
    }
  }, [incident?.primaryService, loadTopology]);

  // Analyze Action
  const handleAnalyzeIncident = async () => {
    setAnalyzingRca(true);
    setRcaError(null);
    try {
      const report = await incidentApi.analyzeIncident(incidentId, true);
      setRcaReport(report);
      setActiveTab('rca');
    } catch (err) {
      setRcaError(err.message || 'Failed to generate AI Root Cause Analysis.');
    } finally {
      setAnalyzingRca(false);
    }
  };

  // Lifecycle Transitions
  const handleStatusTransition = async (actionType) => {
    setIsUpdatingStatus(true);
    try {
      let updated;
      if (actionType === 'ACKNOWLEDGE') {
        updated = await incidentApi.acknowledgeIncident(incidentId);
      } else if (actionType === 'RESOLVE') {
        updated = await incidentApi.resolveIncident(incidentId);
      } else if (actionType === 'CLOSE') {
        updated = await incidentApi.closeIncident(incidentId);
      }
      if (updated) {
        setIncident(updated);
        if (onIncidentUpdated) onIncidentUpdated(updated);
      }
    } catch (err) {
      alert(`Failed to update incident: ${err.message}`);
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  if (loadingIncident) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <RefreshCw className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
        <p className="text-sm text-gray-400">Loading incident investigation environment...</p>
      </Card>
    );
  }

  if (incidentError || !incident) {
    return (
      <Card className="p-8 border-red-500/30 bg-red-950/10 flex flex-col items-center justify-center text-center">
        <AlertCircle className="h-10 w-10 text-red-400 mb-3" />
        <h3 className="text-base font-semibold text-gray-100">Incident Not Found</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">{incidentError || 'Unable to retrieve incident.'}</p>
        <Button variant="secondary" size="sm" onClick={onBack} className="mt-4 gap-2">
          <ArrowLeft className="h-4 w-4" /> Back to Incident Stream
        </Button>
      </Card>
    );
  }

  const status = (incident.status || '').toUpperCase();
  const formattedStarted = incident.startedAt ? new Date(incident.startedAt).toLocaleString() : 'N/A';
  const formattedDetected = incident.detectedAt ? new Date(incident.detectedAt).toLocaleString() : 'N/A';

  const tabs = [
    { id: 'rca', label: 'AI Root Cause Analysis', icon: Cpu, badge: rcaReport ? 'Ready' : null },
    { id: 'timeline', label: 'Timeline & Events', icon: Clock, badge: timeline?.events?.length || null },
    { id: 'dependencies', label: 'Dependencies & Topology', icon: Layers },
    { id: 'similar', label: 'Similar Historical Incidents', icon: BookOpen, badge: similarIncidents?.length || null },
  ];

  return (
    <div className="flex flex-col gap-6">
      {/* Top Navigation & Actions Bar */}
      <div className="flex items-center justify-between flex-wrap gap-4">
        <Button
          variant="outline"
          size="sm"
          onClick={onBack}
          className="gap-2 text-xs"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>Back to Incident Stream</span>
        </Button>

        <div className="flex items-center gap-2.5 flex-wrap">
          {/* Analyze Button */}
          <Button
            onClick={handleAnalyzeIncident}
            disabled={analyzingRca}
            className="gap-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white shadow-md shadow-indigo-500/20 text-xs font-semibold"
          >
            <Sparkles className={cn("h-4 w-4", analyzingRca && "animate-spin text-purple-200")} />
            <span>{analyzingRca ? 'Analyzing Telemetry...' : (rcaReport ? 'Re-analyze Incident' : 'Analyze Incident')}</span>
          </Button>

          {/* Status Transitions */}
          {status === 'OPEN' && (
            <Button
              variant="default"
              size="sm"
              onClick={() => handleStatusTransition('ACKNOWLEDGE')}
              disabled={isUpdatingStatus}
              className="gap-1.5 text-xs"
            >
              <PlayCircle className="h-4 w-4" />
              <span>Acknowledge</span>
            </Button>
          )}

          {(status === 'OPEN' || status === 'INVESTIGATING') && (
            <Button
              variant="success"
              size="sm"
              onClick={() => handleStatusTransition('RESOLVE')}
              disabled={isUpdatingStatus}
              className="gap-1.5 text-xs"
            >
              <CheckCircle2 className="h-4 w-4" />
              <span>Resolve</span>
            </Button>
          )}

          {status === 'RESOLVED' && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => handleStatusTransition('CLOSE')}
              disabled={isUpdatingStatus}
              className="gap-1.5 text-xs"
            >
              <Archive className="h-4 w-4" />
              <span>Close</span>
            </Button>
          )}
        </div>
      </div>

      {/* Incident Summary Card */}
      <Card className="p-6 bg-gray-900/90 border-gray-800 flex flex-col gap-4">
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="flex flex-col gap-2 flex-1">
            <div className="flex items-center gap-2.5 flex-wrap">
              <span className="font-mono text-sm font-bold text-gray-400">INC-{incident.id}</span>
              <SeverityBadge severity={incident.severity} />
              <StatusBadge status={incident.status} />
              {incident.fingerprint && (
                <span className="text-[11px] font-mono text-gray-500 bg-gray-950 px-2.5 py-0.5 rounded border border-gray-800">
                  fp:{incident.fingerprint.slice(0, 12)}...
                </span>
              )}
            </div>
            <h1 className="text-xl font-bold text-gray-100 tracking-tight leading-snug">
              {incident.title || 'Untitled Incident'}
            </h1>
          </div>
        </div>

        {incident.description && (
          <p className="text-sm text-gray-300 leading-relaxed bg-gray-950/40 p-3.5 rounded-lg border border-gray-800/80">
            {incident.description}
          </p>
        )}

        {/* Quick Metadata Pill Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 border-t border-gray-800/80 text-xs">
          <div className="flex flex-col gap-0.5">
            <span className="text-gray-500 font-medium">Primary Service</span>
            <div className="flex items-center gap-1.5 font-mono text-gray-200">
              <Server className="h-3.5 w-3.5 text-indigo-400" />
              <span>{incident.primaryService || 'N/A'}</span>
            </div>
          </div>

          <div className="flex flex-col gap-0.5">
            <span className="text-gray-500 font-medium">Metric Monitored</span>
            <div className="flex items-center gap-1.5 font-mono text-gray-200">
              <Activity className="h-3.5 w-3.5 text-indigo-400" />
              <span className="truncate">{incident.metric || 'N/A'}</span>
            </div>
          </div>

          <div className="flex flex-col gap-0.5">
            <span className="text-gray-500 font-medium">Started Time</span>
            <div className="flex items-center gap-1.5 font-mono text-gray-300">
              <Clock className="h-3.5 w-3.5 text-gray-500" />
              <span>{formattedStarted}</span>
            </div>
          </div>

          <div className="flex flex-col gap-0.5">
            <span className="text-gray-500 font-medium">Detected Time</span>
            <div className="flex items-center gap-1.5 font-mono text-gray-300">
              <Clock className="h-3.5 w-3.5 text-gray-500" />
              <span>{formattedDetected}</span>
            </div>
          </div>
        </div>
      </Card>

      {/* Investigation Tab Navigation */}
      <div className="flex items-center gap-2 border-b border-gray-800 overflow-x-auto pb-px">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;

          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={cn(
                "flex items-center gap-2.5 px-4 py-3 text-sm font-medium border-b-2 transition-all cursor-pointer whitespace-nowrap",
                isActive
                  ? "border-indigo-500 text-indigo-300 bg-indigo-950/20"
                  : "border-transparent text-gray-400 hover:text-gray-200 hover:bg-gray-900/50"
              )}
            >
              <Icon className={cn("h-4 w-4", isActive ? "text-indigo-400" : "text-gray-500")} />
              <span>{tab.label}</span>
              {tab.badge && (
                <span className={cn(
                  "text-[10px] font-mono px-2 py-0.5 rounded-full font-semibold",
                  isActive ? "bg-indigo-500/30 text-indigo-200" : "bg-gray-800 text-gray-400"
                )}>
                  {tab.badge}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Tab Panels */}
      <div className="mt-2">
        {activeTab === 'rca' && (
          <RcaReportView
            rcaReport={rcaReport}
            loading={loadingRca}
            analyzing={analyzingRca}
            error={rcaError}
            onAnalyze={handleAnalyzeIncident}
            onRetry={loadRca}
          />
        )}

        {activeTab === 'timeline' && (
          <IncidentTimelineView
            timeline={timeline}
            loading={loadingTimeline}
            error={timelineError}
            onRetry={loadTimeline}
          />
        )}

        {activeTab === 'dependencies' && (
          <IncidentDependencyView
            incident={incident}
            topology={topology}
            loading={loadingTopology}
            error={topologyError}
            onRetry={() => loadTopology(incident.primaryService)}
          />
        )}

        {activeTab === 'similar' && (
          <SimilarIncidentsView
            similarIncidents={similarIncidents}
            loading={loadingSimilar}
            error={similarError}
            onRetry={loadSimilar}
          />
        )}
      </div>
    </div>
  );
}
