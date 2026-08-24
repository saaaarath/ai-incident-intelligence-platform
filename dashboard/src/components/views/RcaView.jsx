import React from 'react';
import { Cpu } from 'lucide-react';
import { Card } from '../ui/card';

export function RcaView() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h3 className="text-base font-semibold text-gray-100">AI Root Cause Analysis Engine</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Automated multi-modal fault correlation, topology tracing, and prompt-engineered diagnostic summaries
        </p>
      </div>

      <Card className="flex flex-col items-center justify-center p-12 text-center border-dashed border-gray-800 bg-gray-900/40">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-indigo-500/10 text-indigo-400 mb-4">
          <Cpu className="h-6 w-6" />
        </div>
        <h4 className="text-base font-semibold text-gray-100">Root Cause Analysis Pipeline</h4>
        <p className="mt-1 max-w-md text-xs text-gray-400">
          RCA reports are automatically synthesized upon incident detection and available through the Incident Stream details.
        </p>
      </Card>
    </div>
  );
}
