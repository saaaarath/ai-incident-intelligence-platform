import React from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Card } from '../ui/card';
import { Button } from '../ui/button';

export function ErrorState({ title = 'Error Loading Data', message, onRetry }) {
  return (
    <Card className="flex flex-col items-center justify-center p-12 text-center border-red-500/30 bg-red-950/10">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-500/15 text-red-400 mb-4">
        <AlertTriangle className="h-6 w-6" />
      </div>
      <h3 className="text-base font-semibold text-gray-100">{title}</h3>
      <p className="mt-1 max-w-md text-sm text-gray-400">
        {message || 'An unexpected error occurred while communicating with the backend API.'}
      </p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry} className="mt-4 gap-2">
          <RefreshCw className="h-4 w-4" /> Retry Connection
        </Button>
      )}
    </Card>
  );
}
