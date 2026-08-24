import React from 'react';
import { Search, LayoutGrid, List } from 'lucide-react';
import { Input } from '../ui/input';
import { Select } from '../ui/select';
import { Button } from '../ui/button';
import { Card } from '../ui/card';

export function IncidentFilters({
  searchQuery,
  onSearchChange,
  statusFilter,
  onStatusChange,
  severityFilter,
  onSeverityChange,
  serviceFilter,
  onServiceChange,
  services = [],
  viewMode = 'table',
  onViewModeChange
}) {
  return (
    <Card className="p-4 bg-gray-900/80 border-gray-800 flex flex-col md:flex-row items-center justify-between gap-4">
      {/* Search Input */}
      <div className="relative flex-1 w-full min-w-[240px]">
        <Search className="absolute left-3 top-2.5 h-4 w-4 text-gray-500" />
        <Input
          type="text"
          placeholder="Filter by title, service, metric, or fingerprint..."
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          className="pl-9 bg-gray-950/60 border-gray-800"
        />
      </div>

      {/* Filter Selects */}
      <div className="flex items-center gap-3 w-full md:w-auto flex-wrap sm:flex-nowrap">
        <div className="w-full sm:w-36">
          <Select
            value={statusFilter}
            onChange={(e) => onStatusChange(e.target.value)}
            className="bg-gray-950/60"
          >
            <option value="ALL">All Statuses</option>
            <option value="OPEN">Open</option>
            <option value="INVESTIGATING">Investigating</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
          </Select>
        </div>

        <div className="w-full sm:w-36">
          <Select
            value={severityFilter}
            onChange={(e) => onSeverityChange(e.target.value)}
            className="bg-gray-950/60"
          >
            <option value="ALL">All Severities</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </Select>
        </div>

        <div className="w-full sm:w-40">
          <Select
            value={serviceFilter}
            onChange={(e) => onServiceChange(e.target.value)}
            className="bg-gray-950/60"
          >
            <option value="ALL">All Services</option>
            {services.map((svc) => (
              <option key={svc} value={svc}>{svc}</option>
            ))}
          </Select>
        </div>

        {/* View Mode Toggle */}
        <div className="flex items-center border border-gray-800 rounded-md bg-gray-950/60 p-0.5 shrink-0">
          <Button
            variant="ghost"
            size="sm"
            className={`h-8 px-2.5 rounded-sm ${viewMode === 'table' ? 'bg-gray-800 text-indigo-400' : 'text-gray-400'}`}
            onClick={() => onViewModeChange('table')}
            title="Table View"
          >
            <List className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className={`h-8 px-2.5 rounded-sm ${viewMode === 'cards' ? 'bg-gray-800 text-indigo-400' : 'text-gray-400'}`}
            onClick={() => onViewModeChange('cards')}
            title="Card View"
          >
            <LayoutGrid className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </Card>
  );
}
