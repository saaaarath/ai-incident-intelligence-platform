import React from 'react';
import { Badge } from '../ui/badge';

export function SeverityBadge({ severity, className }) {
  const sev = (severity || 'UNKNOWN').toUpperCase();
  let variant = 'low';

  if (sev === 'CRITICAL') variant = 'critical';
  else if (sev === 'HIGH') variant = 'high';
  else if (sev === 'MEDIUM') variant = 'medium';
  else if (sev === 'LOW') variant = 'low';

  return <Badge variant={variant} className={className}>{sev}</Badge>;
}

export function StatusBadge({ status, className }) {
  const stat = (status || 'UNKNOWN').toUpperCase();
  let variant = 'closed';

  if (stat === 'OPEN') variant = 'open';
  else if (stat === 'INVESTIGATING') variant = 'investigating';
  else if (stat === 'RESOLVED') variant = 'resolved';
  else if (stat === 'CLOSED') variant = 'closed';

  return <Badge variant={variant} className={className}>{stat}</Badge>;
}
