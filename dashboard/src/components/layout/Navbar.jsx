import React from 'react';
import { RefreshCw, Radio } from 'lucide-react';
import { Button } from '../ui/button';
import { Badge } from '../ui/badge';
import { cn } from '../../lib/utils';

export function Navbar({ title, onRefresh, isRefreshing, activeIncidentsCount }) {
  return (
    <header className="h-16 px-8 bg-gray-900/80 border-b border-gray-800 flex items-center justify-between shrink-0 backdrop-blur-md">
      <div className="flex items-center gap-4">
        <h2 className="text-lg font-semibold tracking-tight text-gray-100">{title}</h2>
      </div>

      <div className="flex items-center gap-3">
        {activeIncidentsCount > 0 && (
          <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-rose-500/15 border border-rose-500/30 text-rose-400 text-xs font-semibold">
            <span className="h-2 w-2 rounded-full bg-rose-500 animate-pulse-dot" />
            <span>{activeIncidentsCount} Active Alert{activeIncidentsCount > 1 ? 's' : ''}</span>
          </div>
        )}

        <Button
          variant="outline"
          size="sm"
          onClick={onRefresh}
          disabled={isRefreshing}
          className="gap-2 text-xs"
        >
          <RefreshCw className={cn("h-3.5 w-3.5", isRefreshing && "animate-spin text-indigo-400")} />
          <span>{isRefreshing ? 'Syncing...' : 'Refresh'}</span>
        </Button>
      </div>
    </header>
  );
}
