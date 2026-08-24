import React from 'react';
import { Card } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { 
  BookOpen, 
  Sparkles, 
  CheckCircle2, 
  Clock, 
  FileText, 
  AlertTriangle,
  Loader2 
} from 'lucide-react';

export function SimilarIncidentsView({
  similarIncidents = [],
  loading,
  error,
  onRetry
}) {
  if (loading) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
        <p className="text-sm text-gray-400">Querying vector embeddings for semantically similar past incidents...</p>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-8 border-red-500/30 bg-red-950/10 flex flex-col items-center justify-center text-center">
        <AlertTriangle className="h-10 w-10 text-red-400 mb-3" />
        <h3 className="text-base font-semibold text-gray-100">Failed to Retrieve Similar Incidents</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">{error}</p>
        {onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry} className="mt-4">
            Retry
          </Button>
        )}
      </Card>
    );
  }

  if (similarIncidents.length === 0) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-indigo-500/10 text-indigo-400 mb-4">
          <BookOpen className="h-6 w-6" />
        </div>
        <h3 className="text-base font-semibold text-gray-100">No Similar Historical Incidents Found</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">
          No historical postmortems in the vector database exceeded the semantic similarity threshold for this incident pattern.
        </p>
      </Card>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-100">Similar Historical Incidents & Runbooks</h3>
          <p className="text-xs text-gray-400 mt-0.5">
            Retrieved via 768-dimensional Gemini semantic embeddings & cosine similarity
          </p>
        </div>
        <Badge variant="outline" className="font-mono text-xs text-gray-300">
          {similarIncidents.length} Match{similarIncidents.length !== 1 ? 'es' : ''}
        </Badge>
      </div>

      <div className="grid grid-cols-1 gap-4">
        {similarIncidents.map((item, idx) => {
          const score = item.similarityScore !== undefined 
            ? Math.round(item.similarityScore * 100) 
            : null;

          return (
            <Card key={item.documentId || idx} className="p-5 bg-gray-900/80 border-gray-800 hover:border-gray-700 transition-colors flex flex-col gap-3">
              <div className="flex items-start justify-between gap-4">
                <div className="flex flex-col gap-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-mono text-xs text-gray-500">{item.documentId}</span>
                    {item.category && (
                      <Badge variant="outline" className="text-[10px] border-indigo-500/30 text-indigo-300">
                        {item.category}
                      </Badge>
                    )}
                    {item.type && (
                      <Badge variant="outline" className="text-[10px] border-gray-700 text-gray-400 font-mono">
                        {item.type}
                      </Badge>
                    )}
                  </div>
                  <h4 className="text-base font-semibold text-gray-100 mt-0.5">
                    {item.title || 'Historical Postmortem'}
                  </h4>
                </div>

                {score !== null && (
                  <Badge 
                    variant={score > 80 ? 'resolved' : 'medium'}
                    className="shrink-0 font-mono"
                  >
                    {score}% Similarity
                  </Badge>
                )}
              </div>

              {item.matchedChunk && (
                <div className="p-3 rounded-lg bg-gray-950/60 border border-gray-800 text-xs text-gray-300 leading-relaxed font-sans">
                  <span className="text-gray-500 font-semibold uppercase text-[10px] block mb-1">Matched Diagnostic Context:</span>
                  {item.matchedChunk}
                </div>
              )}

              {item.content && !item.matchedChunk && (
                <p className="text-xs text-gray-400 line-clamp-3 leading-relaxed">
                  {item.content}
                </p>
              )}
            </Card>
          );
        })}
      </div>
    </div>
  );
}
