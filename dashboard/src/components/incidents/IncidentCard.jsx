import React from 'react';
import { SeverityBadge, StatusBadge } from '../common/Badge';
import { Clock, Server, Fingerprint, ChevronRight } from 'lucide-react';
import { Card } from '../ui/card';
import { cn } from '../../lib/utils';

export function IncidentCard({ incident, isSelected, onClick }) {
  const formattedDetected = incident.detectedAt 
    ? new Date(incident.detectedAt).toLocaleString() 
    : (incident.startedAt ? new Date(incident.startedAt).toLocaleString() : 'N/A');

  return (
    <Card 
      className={cn(
        "p-5 flex flex-col gap-3.5 cursor-pointer transition-all duration-150 hover:bg-gray-850 hover:border-gray-700",
        isSelected && "border-indigo-500/60 bg-indigo-950/20 ring-1 ring-indigo-500/30"
      )}
      onClick={() => onClick(incident)}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex flex-col gap-1.5 flex-1">
          <div className="flex items-center gap-2.5 flex-wrap">
            <span className="font-mono text-xs font-semibold text-gray-500">INC-{incident.id}</span>
            <SeverityBadge severity={incident.severity} />
            <StatusBadge status={incident.status} />
          </div>
          <h3 className="text-base font-semibold text-gray-100 leading-snug">
            {incident.title || 'Untitled Incident'}
          </h3>
        </div>
        <ChevronRight className="h-5 w-5 text-gray-600 shrink-0 mt-1" />
      </div>

      {incident.description && (
        <p className="text-sm text-gray-400 line-clamp-2 leading-relaxed">
          {incident.description}
        </p>
      )}

      <div className="flex items-center gap-4 text-xs text-gray-400 flex-wrap pt-1 border-t border-gray-800/60">
        {incident.primaryService && (
          <div className="flex items-center gap-1.5">
            <Server className="h-3.5 w-3.5 text-gray-500" />
            <span className="font-mono">{incident.primaryService}</span>
          </div>
        )}

        <div className="flex items-center gap-1.5">
          <Clock className="h-3.5 w-3.5 text-gray-500" />
          <span className="font-mono">Detected: {formattedDetected}</span>
        </div>

        {incident.fingerprint && (
          <div className="flex items-center gap-1.5 font-mono text-[11px] text-gray-500">
            <Fingerprint className="h-3.5 w-3.5 text-gray-600" />
            <span>{incident.fingerprint.slice(0, 16)}...</span>
          </div>
        )}
      </div>
    </Card>
  );
}
