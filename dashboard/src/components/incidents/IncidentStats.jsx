import React from 'react';
import { AlertCircle, AlertTriangle, CheckCircle, Clock } from 'lucide-react';
import { Card, CardContent } from '../ui/card';

export function IncidentStats({ incidents = [] }) {
  const total = incidents.length;
  const critical = incidents.filter(i => (i.severity || '').toUpperCase() === 'CRITICAL').length;
  const open = incidents.filter(i => (i.status || '').toUpperCase() === 'OPEN').length;
  const investigating = incidents.filter(i => (i.status || '').toUpperCase() === 'INVESTIGATING').length;
  const resolved = incidents.filter(i => (i.status || '').toUpperCase() === 'RESOLVED' || (i.status || '').toUpperCase() === 'CLOSED').length;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <Card className="hover:border-gray-700 transition-colors">
        <CardContent className="p-5 flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-gray-400">
            <span>Active Open</span>
            <AlertCircle className="h-4 w-4 text-rose-500" />
          </div>
          <div className="text-2xl font-bold font-mono text-rose-400">
            {open}
          </div>
        </CardContent>
      </Card>

      <Card className="hover:border-gray-700 transition-colors">
        <CardContent className="p-5 flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-gray-400">
            <span>Investigating</span>
            <Clock className="h-4 w-4 text-sky-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-sky-400">
            {investigating}
          </div>
        </CardContent>
      </Card>

      <Card className="hover:border-gray-700 transition-colors">
        <CardContent className="p-5 flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-gray-400">
            <span>Critical Severity</span>
            <AlertTriangle className="h-4 w-4 text-red-500" />
          </div>
          <div className="text-2xl font-bold font-mono text-red-400">
            {critical}
          </div>
        </CardContent>
      </Card>

      <Card className="hover:border-gray-700 transition-colors">
        <CardContent className="p-5 flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-gray-400">
            <span>Resolved / Closed</span>
            <CheckCircle className="h-4 w-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-bold font-mono text-emerald-400">
            {resolved}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
