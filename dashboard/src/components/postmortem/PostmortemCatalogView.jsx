import React, { useState, useEffect, useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { incidentApi } from '../../services/api';
import { 
  FileText, 
  Search, 
  Download, 
  Copy, 
  Check, 
  Server, 
  Layers, 
  ChevronRight, 
  Loader2, 
  AlertTriangle,
  Clock,
  ShieldCheck
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function PostmortemCatalogView() {
  const [postmortems, setPostmortems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedPostmortem, setSelectedPostmortem] = useState(null);
  const [copiedId, setCopiedId] = useState(null);

  const fetchPostmortems = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await incidentApi.getPostmortems();
      setPostmortems(data || []);
      if (data && data.length > 0 && !selectedPostmortem) {
        setSelectedPostmortem(data[0]);
      }
    } catch (err) {
      setError(err.message || 'Failed to load postmortems');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPostmortems();
  }, []);

  const filtered = useMemo(() => {
    return postmortems.filter((pm) => {
      const q = searchQuery.toLowerCase().trim();
      if (!q) return true;
      return (
        pm.postmortemId?.toLowerCase().includes(q) ||
        pm.incidentId?.toLowerCase().includes(q) ||
        pm.title?.toLowerCase().includes(q) ||
        pm.category?.toLowerCase().includes(q)
      );
    });
  }, [postmortems, searchQuery]);

  const handleCopy = (content, id) => {
    navigator.clipboard.writeText(content);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleDownload = (pm) => {
    const content = pm.content || JSON.stringify(pm, null, 2);
    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `${pm.postmortemId || 'postmortem'}.md`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="flex flex-col gap-6">
      {/* Search */}
      <div className="relative max-w-md">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-500" />
        <input
          type="text"
          placeholder="Search postmortems by ID, incident ID, title, or category..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-9 pr-4 py-2 rounded-lg bg-gray-900 border border-gray-800 text-xs text-gray-200 placeholder-gray-500 focus:outline-hidden focus:border-indigo-500 transition-colors"
        />
      </div>

      {loading ? (
        <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
          <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
          <p className="text-sm text-gray-400">Loading SRE postmortem library...</p>
        </Card>
      ) : error ? (
        <Card className="p-8 border-red-500/30 bg-red-950/10 flex flex-col items-center justify-center text-center">
          <AlertTriangle className="h-10 w-10 text-red-400 mb-3" />
          <h3 className="text-base font-semibold text-gray-100">Failed to Load Postmortems</h3>
          <p className="text-sm text-gray-400 mt-1 max-w-md">{error}</p>
          <Button variant="secondary" size="sm" onClick={fetchPostmortems} className="mt-4">
            Retry
          </Button>
        </Card>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Postmortem List (5 cols) */}
          <div className="lg:col-span-5 space-y-3 max-h-[700px] overflow-y-auto pr-1">
            <div className="flex items-center justify-between text-xs text-gray-400 font-mono mb-1">
              <span>{filtered.length} Published Postmortems</span>
            </div>

            {filtered.length === 0 ? (
              <Card className="p-8 border-dashed border-gray-800 text-center text-xs text-gray-500 font-mono">
                No postmortems found. Generate a postmortem from any incident in the Incident Stream.
              </Card>
            ) : (
              filtered.map((pm) => {
                const isSelected = selectedPostmortem?.postmortemId === pm.postmortemId;
                return (
                  <Card
                    key={pm.postmortemId}
                    onClick={() => setSelectedPostmortem(pm)}
                    className={cn(
                      "p-4 cursor-pointer transition-all duration-150 border hover:border-gray-700",
                      isSelected
                        ? "bg-indigo-950/20 border-indigo-500/40 ring-1 ring-indigo-500/30"
                        : "bg-gray-900/80 border-gray-800"
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="font-mono text-[10px] border-indigo-500/40 text-indigo-300">
                          {pm.postmortemId}
                        </Badge>
                        <Badge variant="secondary" className="text-[10px]">
                          {pm.incidentId}
                        </Badge>
                      </div>
                      <ChevronRight className={cn(
                        "h-4 w-4 transition-transform",
                        isSelected ? "text-indigo-400 translate-x-1" : "text-gray-600"
                      )} />
                    </div>

                    <h4 className="font-semibold text-sm text-gray-100 mt-2 line-clamp-1">
                      {pm.title}
                    </h4>

                    <div className="flex items-center gap-2 mt-3 text-[10px] font-mono text-gray-500">
                      <span>{pm.category}</span>
                      <span className="ml-auto">{pm.actionItems?.length || 0} Action Items</span>
                    </div>
                  </Card>
                );
              })
            )}
          </div>

          {/* Selected Postmortem Inspector (7 cols) */}
          <div className="lg:col-span-7">
            {selectedPostmortem ? (
              <Card className="p-6 bg-gray-900/90 border-gray-800 flex flex-col gap-6">
                {/* Header */}
                <div className="flex items-start justify-between flex-wrap gap-3 pb-4 border-b border-gray-800">
                  <div>
                    <div className="flex items-center gap-2 mb-1.5">
                      <Badge variant="outline" className="font-mono text-xs text-indigo-300 border-indigo-500/40">
                        {selectedPostmortem.postmortemId}
                      </Badge>
                      <Badge variant="secondary" className="text-xs">
                        {selectedPostmortem.incidentId}
                      </Badge>
                    </div>
                    <h3 className="text-lg font-bold text-gray-100">
                      {selectedPostmortem.title}
                    </h3>
                  </div>

                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleCopy(selectedPostmortem.content || selectedPostmortem.title, selectedPostmortem.postmortemId)}
                      className="gap-1.5 text-xs font-mono border-gray-700 cursor-pointer"
                    >
                      {copiedId === selectedPostmortem.postmortemId ? (
                        <>
                          <Check className="h-3.5 w-3.5 text-emerald-400" />
                          <span>Copied</span>
                        </>
                      ) : (
                        <>
                          <Copy className="h-3.5 w-3.5" />
                          <span>Copy MD</span>
                        </>
                      )}
                    </Button>

                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleDownload(selectedPostmortem)}
                      className="gap-1.5 text-xs font-mono border-gray-700 cursor-pointer"
                    >
                      <Download className="h-3.5 w-3.5" />
                      <span>Download</span>
                    </Button>
                  </div>
                </div>

                {/* Executive Summary */}
                <div className="p-4 rounded-lg bg-gray-950 border border-gray-800">
                  <span className="text-[10px] font-mono uppercase text-indigo-400 font-bold">Executive Summary</span>
                  <p className="text-xs text-gray-300 mt-1.5 leading-relaxed">{selectedPostmortem.executiveSummary}</p>
                </div>

                {/* Root Cause Analysis */}
                <div className="p-4 rounded-lg bg-gray-950 border border-gray-800">
                  <span className="text-[10px] font-mono uppercase text-purple-400 font-bold">Root Cause Analysis</span>
                  <p className="text-xs text-gray-300 mt-1.5 leading-relaxed font-mono">{selectedPostmortem.rootCauseAnalysis}</p>
                </div>

                {/* Action Items */}
                {selectedPostmortem.actionItems && selectedPostmortem.actionItems.length > 0 && (
                  <div>
                    <h4 className="text-xs font-semibold uppercase font-mono text-gray-400 mb-2">
                      Preventative Action Items
                    </h4>
                    <div className="space-y-1.5">
                      {selectedPostmortem.actionItems.map((item, idx) => (
                        <div key={idx} className="p-2.5 rounded bg-gray-950 border border-gray-800 text-xs text-gray-300 font-mono">
                          • {item}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Lessons Learned */}
                {selectedPostmortem.lessonsLearned && selectedPostmortem.lessonsLearned.length > 0 && (
                  <div>
                    <h4 className="text-xs font-semibold uppercase font-mono text-gray-400 mb-2">
                      Lessons Learned
                    </h4>
                    <div className="space-y-1.5">
                      {selectedPostmortem.lessonsLearned.map((lesson, idx) => (
                        <div key={idx} className="p-2.5 rounded bg-gray-950 border border-gray-800 text-xs text-gray-300">
                          ✔ {lesson}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </Card>
            ) : (
              <Card className="p-12 border-dashed border-gray-800 text-center text-xs text-gray-500 font-mono">
                Select a postmortem from the left list to view its complete analysis.
              </Card>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
