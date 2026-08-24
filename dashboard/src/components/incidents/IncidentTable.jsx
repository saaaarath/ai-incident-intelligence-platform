import React from 'react';
import { SeverityBadge, StatusBadge } from '../common/Badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table';
import { Server, Clock, ChevronRight } from 'lucide-react';

export function IncidentTable({ incidents = [], selectedIncidentId, onSelectIncident }) {
  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-transparent cursor-default">
          <TableHead className="w-[110px]">Incident ID</TableHead>
          <TableHead className="w-[120px]">Severity</TableHead>
          <TableHead className="w-[140px]">Status</TableHead>
          <TableHead>Title & Summary</TableHead>
          <TableHead className="w-[180px]">Primary Service</TableHead>
          <TableHead className="w-[190px]">Detected Time</TableHead>
          <TableHead className="w-[60px] text-right"></TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {incidents.map((incident) => {
          const isSelected = selectedIncidentId === incident.id;
          const formattedDetected = incident.detectedAt 
            ? new Date(incident.detectedAt).toLocaleString() 
            : (incident.startedAt ? new Date(incident.startedAt).toLocaleString() : 'N/A');

          return (
            <TableRow
              key={incident.id}
              onClick={() => onSelectIncident(incident)}
              className={isSelected ? 'bg-indigo-950/20 border-indigo-500/40' : ''}
            >
              <TableCell className="font-mono text-xs font-semibold text-gray-400">
                INC-{incident.id}
              </TableCell>

              <TableCell>
                <SeverityBadge severity={incident.severity} />
              </TableCell>

              <TableCell>
                <StatusBadge status={incident.status} />
              </TableCell>

              <TableCell>
                <div className="flex flex-col gap-1 py-1">
                  <span className="font-medium text-gray-100 leading-tight">
                    {incident.title || 'Untitled Incident'}
                  </span>
                  {incident.metric && (
                    <span className="text-xs font-mono text-gray-500 truncate max-w-md">
                      Metric: {incident.metric}
                    </span>
                  )}
                </div>
              </TableCell>

              <TableCell>
                <div className="flex items-center gap-1.5 text-xs text-gray-300">
                  <Server className="h-3.5 w-3.5 text-gray-500 shrink-0" />
                  <span className="font-mono">{incident.primaryService || 'Unknown'}</span>
                </div>
              </TableCell>

              <TableCell>
                <div className="flex items-center gap-1.5 text-xs text-gray-400 font-mono">
                  <Clock className="h-3.5 w-3.5 text-gray-500 shrink-0" />
                  <span>{formattedDetected}</span>
                </div>
              </TableCell>

              <TableCell className="text-right">
                <ChevronRight className="h-4 w-4 text-gray-600 ml-auto" />
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
