import React from 'react';
import { Loader2 } from 'lucide-react';
import { Card } from '../ui/card';

export function LoadingState({ message = 'Loading incidents...' }) {
  return (
    <Card className="flex flex-col items-center justify-center p-12 text-center border-dashed border-gray-800 bg-gray-900/40">
      <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
      <p className="text-sm text-gray-400 font-medium">{message}</p>
    </Card>
  );
}
