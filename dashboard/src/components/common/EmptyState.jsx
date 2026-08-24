import React from 'react';
import { ShieldCheck, Plus } from 'lucide-react';
import { Card } from '../ui/card';
import { Button } from '../ui/button';

export function EmptyState({ 
  title = 'All Systems Operational', 
  message = 'No active incidents found matching your current filter criteria.',
  actionLabel,
  onAction 
}) {
  return (
    <Card className="flex flex-col items-center justify-center p-12 text-center border-dashed border-gray-800 bg-gray-900/40">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/15 text-emerald-400 mb-4">
        <ShieldCheck className="h-6 w-6" />
      </div>
      <h3 className="text-base font-semibold text-gray-100">{title}</h3>
      <p className="mt-1 max-w-md text-sm text-gray-400">{message}</p>
      {actionLabel && onAction && (
        <Button variant="secondary" size="sm" onClick={onAction} className="mt-4 gap-2">
          <Plus className="h-4 w-4" /> {actionLabel}
        </Button>
      )}
    </Card>
  );
}
