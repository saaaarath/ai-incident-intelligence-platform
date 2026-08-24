import React, { useState, useEffect } from 'react';
import { SeverityBadge, StatusBadge } from '../common/Badge';
import { incidentApi } from '../../services/api';
import { 
  Sheet, 
  SheetHeader, 
  SheetTitle, 
  SheetDescription, 
  SheetContent, 
  SheetFooter, 
  SheetClose 
} from '../ui/sheet';
import { Button } from '../ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../ui/card';
import { Separator } from '../ui/separator';
import { 
  CheckCircle2, 
  PlayCircle, 
  Archive, 
  Server, 
  Clock, 
  Activity, 
  Layers, 
  Fingerprint,
  Loader2,
  AlertCircle
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
  const formattedStarted = incident.startedAt ? new Date(incident.startedAt).toLocaleString() : 'N/A';
  const formattedDetected = incident.detectedAt ? new Date(incident.detectedAt).toLocaleString() : 'N/A';

  return (
    <Sheet open={!!incident} onOpenChange={(open) => !open && onClose()}>
      {/* Header */}
      <SheetHeader>
        <div className="flex flex-col gap-2 flex-1">
          <div className="flex items-center gap-2">
            <span className="font-mono text-xs font-semibold text-gray-400">INC-{incident.id}</span>
            <SeverityBadge severity={incident.severity} />
            <StatusBadge status={incident.status} />
          </div>
          <SheetTitle>{incident.title || 'Incident Details'}</SheetTitle>
        </div>
        <SheetClose onClose={onClose} />
      </SheetHeader>

      {/* Content */}
      <SheetContent>
        {actionError && (
          <div className="p-3.5 rounded-lg bg-red-950/40 border border-red-500/30 text-red-400 text-xs flex items-center gap-2">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{actionError}</span>
          </div>
        )}

        {/* Overview Grid */}
        <div className="flex flex-col gap-2">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-400">Metadata & Timeline</h4>
          <div className="grid grid-cols-2 gap-3 p-4 rounded-lg bg-gray-950/60 border border-gray-800 text-xs">
            <div className="flex flex-col gap-1">
              <span className="text-gray-500">Primary Service</span>
              <div className="flex items-center gap-1.5 font-mono text-gray-200">
                <Server className="h-3.5 w-3.5 text-indigo-400" />
                <span>{incident.primaryService || 'N/A'}</span>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <span className="text-gray-500">Anomaly Metric</span>
              <div className="flex items-center gap-1.5 font-mono text-gray-200">
                <Activity className="h-3.5 w-3.5 text-indigo-400" />
                <span className="truncate">{incident.metric || 'N/A'}</span>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <span className="text-gray-500">Started Time</span>
              <div className="flex items-center gap-1.5 font-mono text-gray-300">
                <Clock className="h-3.5 w-3.5 text-gray-500" />
                <span>{formattedStarted}</span>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <span className="text-gray-500">Detected Time</span>
              <div className="flex items-center gap-1.5 font-mono text-gray-300">
                <Clock className="h-3.5 w-3.5 text-gray-500" />
                <span>{formattedDetected}</span>
              </div>
            </div>

            {incident.fingerprint && (
              <div className="col-span-2 flex flex-col gap-1 pt-2 border-t border-gray-800/80">
                <span className="text-gray-500">Incident Fingerprint</span>
                <div className="flex items-center gap-1.5 font-mono text-[11px] text-gray-400 truncate">
                  <Fingerprint className="h-3.5 w-3.5 text-gray-500" />
                  <span>{incident.fingerprint}</span>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Description */}
        {incident.description && (
          <div className="flex flex-col gap-2">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-400">Description</h4>
            <div className="p-4 rounded-lg bg-gray-950/60 border border-gray-800 text-sm text-gray-300 leading-relaxed">
              {incident.description}
            </div>
          </div>
        )}

        {/* Correlated Evidence */}
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-400">Correlated Evidence</h4>
            <span className="text-xs text-gray-500 font-mono">
              {evidence.length} record{evidence.length !== 1 ? 's' : ''}
            </span>
          </div>

          {loadingEvidence ? (
            <div className="p-6 text-center text-xs text-gray-500 flex items-center justify-center gap-2">
              <Loader2 className="h-4 w-4 animate-spin text-indigo-500" />
              <span>Fetching telemetry evidence...</span>
            </div>
          ) : evidence.length === 0 ? (
            <div className="p-6 rounded-lg bg-gray-950/40 border border-dashed border-gray-800 text-center text-xs text-gray-500">
              No correlated anomaly evidence records found for this incident.
            </div>
          ) : (
            <div className="flex flex-col gap-2.5">
              {evidence.map((ev, idx) => (
                <Card key={ev.id || idx} className="bg-gray-950/80 border-gray-800 p-3.5 flex flex-col gap-2">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-semibold text-gray-200">
                      {ev.service || 'Service'}: {ev.metric || 'Anomaly'}
                    </span>
                    <SeverityBadge severity={ev.severity} />
                  </div>
                  {ev.description && (
                    <p className="text-xs text-gray-400">{ev.description}</p>
                  )}
                  {ev.anomalyScore !== undefined && ev.anomalyScore !== null && (
                    <span className="text-[11px] font-mono text-gray-500">
                      Score: {ev.anomalyScore}
                    </span>
                  )}
                </Card>
              ))}
            </div>
          )}
        </div>
      </SheetContent>

      {/* Footer Actions */}
      <SheetFooter>
        {status === 'OPEN' && (
          <Button
            variant="default"
            size="sm"
            onClick={() => handleAction('ACKNOWLEDGE')}
            disabled={isUpdating}
            className="gap-1.5"
          >
            <PlayCircle className="h-4 w-4" />
            <span>{isUpdating ? 'Updating...' : 'Acknowledge Incident'}</span>
          </Button>
        )}

        {(status === 'OPEN' || status === 'INVESTIGATING') && (
          <Button
            variant="success"
            size="sm"
            onClick={() => handleAction('RESOLVE')}
            disabled={isUpdating}
            className="gap-1.5"
          >
            <CheckCircle2 className="h-4 w-4" />
            <span>{isUpdating ? 'Updating...' : 'Resolve Incident'}</span>
          </Button>
        )}

        {status === 'RESOLVED' && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => handleAction('CLOSE')}
            disabled={isUpdating}
            className="gap-1.5"
          >
            <Archive className="h-4 w-4" />
            <span>{isUpdating ? 'Updating...' : 'Close Incident'}</span>
          </Button>
        )}

        <Button variant="outline" size="sm" onClick={onClose} className="ml-auto">
          Close Inspector
        </Button>
      </SheetFooter>
    </Sheet>
  );
}
