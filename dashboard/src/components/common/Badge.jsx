import React from 'react';

export function SeverityBadge({ severity }) {
  const sev = (severity || 'UNKNOWN').toUpperCase();
  let badgeClass = 'badge-low';

  if (sev === 'CRITICAL') badgeClass = 'badge-critical';
  else if (sev === 'HIGH') badgeClass = 'badge-high';
  else if (sev === 'MEDIUM') badgeClass = 'badge-medium';
  else if (sev === 'LOW') badgeClass = 'badge-low';

  return <span className={`badge ${badgeClass}`}>{sev}</span>;
}

export function StatusBadge({ status }) {
  const stat = (status || 'UNKNOWN').toUpperCase();
  let badgeClass = 'badge-closed';

  if (stat === 'OPEN') badgeClass = 'badge-open';
  else if (stat === 'INVESTIGATING') badgeClass = 'badge-investigating';
  else if (stat === 'RESOLVED') badgeClass = 'badge-resolved';
  else if (stat === 'CLOSED') badgeClass = 'badge-closed';

  return <span className={`badge ${badgeClass}`}>{stat}</span>;
}
