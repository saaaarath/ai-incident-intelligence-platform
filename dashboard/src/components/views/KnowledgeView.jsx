import React, { useState } from 'react';
import { BookOpen, FileCode, FileText, History } from 'lucide-react';
import { Card, CardContent } from '../ui/card';
import { Badge } from '../ui/badge';
import { RunbookCatalogView } from '../runbooks/RunbookCatalogView';
import { PostmortemCatalogView } from '../postmortem/PostmortemCatalogView';
import { cn } from '../../lib/utils';

export function KnowledgeView() {
  const [activeSubTab, setActiveSubTab] = useState('runbooks');

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h3 className="text-base font-semibold text-gray-100">Operational Knowledge & SRE Reports</h3>
          <p className="text-xs text-gray-400 mt-0.5">
            Canonical runbooks, AI postmortem reports, and pgvector semantic incident intelligence
          </p>
        </div>

        {/* Sub-tab Switcher */}
        <div className="flex items-center gap-1.5 p-1 bg-gray-900 border border-gray-800 rounded-lg">
          <button
            onClick={() => setActiveSubTab('runbooks')}
            className={cn(
              "flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition-all cursor-pointer",
              activeSubTab === 'runbooks'
                ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/40"
                : "text-gray-400 hover:text-gray-200"
            )}
          >
            <FileCode className="h-3.5 w-3.5" />
            <span>Runbook Catalog</span>
          </button>

          <button
            onClick={() => setActiveSubTab('postmortems')}
            className={cn(
              "flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition-all cursor-pointer",
              activeSubTab === 'postmortems'
                ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/40"
                : "text-gray-400 hover:text-gray-200"
            )}
          >
            <FileText className="h-3.5 w-3.5" />
            <span>Postmortem Library</span>
          </button>

          <button
            onClick={() => setActiveSubTab('historical')}
            className={cn(
              "flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition-all cursor-pointer",
              activeSubTab === 'historical'
                ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/40"
                : "text-gray-400 hover:text-gray-200"
            )}
          >
            <History className="h-3.5 w-3.5" />
            <span>Semantic Intelligence</span>
          </button>
        </div>
      </div>

      {activeSubTab === 'runbooks' && <RunbookCatalogView />}
      {activeSubTab === 'postmortems' && <PostmortemCatalogView />}

      {activeSubTab === 'historical' && (
        <Card className="flex flex-col items-center justify-center p-12 text-center border-dashed border-gray-800 bg-gray-900/40">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-indigo-500/10 text-indigo-400 mb-4">
            <BookOpen className="h-6 w-6" />
          </div>
          <h4 className="text-base font-semibold text-gray-100">Vector Knowledge Base & Semantic Search</h4>
          <p className="mt-1 max-w-md text-xs text-gray-400">
            24 canonical postmortems across 8 failure categories are embedded in PostgreSQL pgvector. When exploring an incident in the Incident Stream, similar historical incidents are automatically retrieved and ranked by cosine similarity.
          </p>
        </Card>
      )}
    </div>
  );
}


