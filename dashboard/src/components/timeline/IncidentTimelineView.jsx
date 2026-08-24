import React from 'react';
import { Card } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { 
  Clock, 
  AlertCircle, 
  Activity, 
  Server, 
  GitCommit, 
  Radio, 
  Layers, 
  CheckCircle2, 
  Loader2,
  AlertTriangle
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function IncidentTimelineView({ timeline, loading, error, onRetry }) {
  if (loading) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
        <p className="text-sm text-gray-400">Constructing chronological incident timeline...</p>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-8 border-red-500/30 bg-red-950/10 flex flex-col items-center justify-center text-center">
        <AlertTriangle className="h-10 w-10 text-red-400 mb-3" />
        <h3 className="text-base font-semibold text-gray-100">Failed to Load Timeline</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">{error}</p>
        {onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry} className="mt-4">
            Retry
          </Button>
        )}
      </Card>
    );
  }

  const events = timeline?.events || [];

  if (events.length === 0) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-gray-800 text-gray-400 mb-4">
          <Clock className="h-6 w-6" />
        </div>
        <h3 className="text-base font-semibold text-gray-100">No Timeline Events Recorded</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">
          No correlated telemetry events were captured in the active incident window.
        </p>
      </Card>
    );
  }

  const getEventIcon = (type) => {
    switch (type) {
      case 'ANOMALY_DETECTED':
        return <AlertCircle className="h-4 w-4 text-red-400" />;
      case 'METRIC_SPIKE':
        return <Activity className="h-4 w-4 text-amber-400" />;
      case 'ERROR_BURST':
        return <Radio className="h-4 w-4 text-rose-400" />;
      case 'DEPLOYMENT':
        return <GitCommit className="h-4 w-4 text-sky-400" />;
      case 'LIFECYCLE_STATE_CHANGE':
        return <CheckCircle2 className="h-4 w-4 text-emerald-400" />;
      default:
        return <Layers className="h-4 w-4 text-indigo-400" />;
    }
  };

  return (
    <div className="flex flex-col gap-6">
      {/* Header Summary */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-100">Incident Event Timeline</h3>
          <p className="text-xs text-gray-400 mt-0.5">
            Chronological multi-service telemetry, anomalies, state changes, and deployments
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="outline" className="font-mono text-xs text-gray-300">
            {events.length} Event{events.length !== 1 ? 's' : ''}
          </Badge>
        </div>
      </div>

      {/* Timeline Stream */}
      <div className="relative pl-6 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-0.5 before:bg-gray-800 space-y-6">
        {events.map((event, idx) => {
          const timestamp = event.timestamp ? new Date(event.timestamp).toLocaleString() : 'N/A';
          const sev = (event.severity || 'MEDIUM').toUpperCase();

          return (
            <div key={event.id || idx} className="relative flex items-start gap-4 group">
              {/* Node Marker */}
              <div className="absolute -left-6 mt-1 flex h-5 w-5 items-center justify-center rounded-full bg-gray-900 border border-gray-750 shadow-sm group-hover:border-indigo-500 transition-colors">
                <span className="h-2 w-2 rounded-full bg-indigo-400" />
              </div>

              {/* Event Card */}
              <Card className="flex-1 p-4 bg-gray-900/80 border-gray-800/90 hover:border-gray-700 transition-colors">
                <div className="flex items-center justify-between gap-3 flex-wrap mb-1.5">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="p-1 rounded bg-gray-800/80">
                      {getEventIcon(event.type)}
                    </span>
                    <span className="font-mono text-xs font-semibold text-gray-200">
                      {event.type || 'EVENT'}
                    </span>
                    {event.service && (
                      <Badge variant="outline" className="text-[10px] font-mono border-gray-700 text-indigo-300">
                        {event.service}
                      </Badge>
                    )}
                    {event.severity && (
                      <Badge 
                        variant={sev === 'CRITICAL' ? 'critical' : (sev === 'HIGH' ? 'high' : 'medium')}
                        className="text-[10px]"
                      >
                        {sev}
                      </Badge>
                    )}
                  </div>

                  <span className="font-mono text-xs text-gray-500 flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {timestamp}
                  </span>
                </div>

                <p className="text-sm text-gray-200 font-medium leading-relaxed">
                  {event.summary || event.details || 'Event logged'}
                </p>

                {event.details && event.details !== event.summary && (
                  <p className="text-xs text-gray-400 mt-1.5 leading-relaxed bg-gray-950/50 p-2.5 rounded border border-gray-800/60 font-mono">
                    {event.details}
                  </p>
                )}
              </Card>
            </div>
          );
        })}
      </div>
    </div>
  );
}
