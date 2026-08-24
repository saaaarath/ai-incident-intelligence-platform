import React from 'react';
import { BookOpen } from 'lucide-react';
import { Card, CardContent } from '../ui/card';

export function KnowledgeView() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h3 className="text-base font-semibold text-gray-100">Operational Knowledge & Runbooks</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Historical incident postmortems and semantic vector embeddings for rapid mitigation
        </p>
      </div>

      <Card className="flex flex-col items-center justify-center p-12 text-center border-dashed border-gray-800 bg-gray-900/40">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-indigo-500/10 text-indigo-400 mb-4">
          <BookOpen className="h-6 w-6" />
        </div>
        <h4 className="text-base font-semibold text-gray-100">Semantic Incident Intelligence</h4>
        <p className="mt-1 max-w-md text-xs text-gray-400">
          Automated similarity search and runbook matching are activated when inspecting active incidents in the Incident Stream.
        </p>
      </Card>
    </div>
  );
}
