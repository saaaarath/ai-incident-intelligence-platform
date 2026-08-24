import React, { useState, useEffect, useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { incidentApi } from '../../services/api';
import { 
  BookOpen, 
  Search, 
  Filter, 
  Server, 
  Layers, 
  Terminal, 
  CheckCircle2, 
  ChevronRight, 
  Loader2, 
  AlertTriangle,
  Play,
  FileText,
  Copy,
  Check
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function RunbookCatalogView() {
  const [runbooks, setRunbooks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [selectedRunbook, setSelectedRunbook] = useState(null);
  const [copiedId, setCopiedId] = useState(null);

  const categories = [
    { id: 'ALL', label: 'All Runbooks' },
    { id: 'DATABASE_CONNECTION_EXHAUSTION', label: 'Database' },
    { id: 'NETWORK_LATENCY', label: 'Latency & Network' },
    { id: 'SERVICE_UNAVAILABLE', label: '503 & Outages' },
    { id: 'MEMORY_PRESSURE', label: 'Memory & Capacity' },
    { id: 'DEPLOYMENT_REGRESSION', label: 'Deployment' },
    { id: 'CACHE_FAILURE', label: 'Cache & Redis' },
  ];

  const fetchRunbooks = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await incidentApi.getRunbooks();
      setRunbooks(data || []);
      if (data && data.length > 0 && !selectedRunbook) {
        setSelectedRunbook(data[0]);
      }
    } catch (err) {
      setError(err.message || 'Failed to load runbooks catalog');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRunbooks();
  }, []);

  const filteredRunbooks = useMemo(() => {
    return runbooks.filter((rb) => {
      const matchesCat = selectedCategory === 'ALL' || rb.category === selectedCategory;
      const q = searchQuery.toLowerCase().trim();
      if (!q) return matchesCat;

      const matchesQuery = 
        rb.runbookId?.toLowerCase().includes(q) ||
        rb.title?.toLowerCase().includes(q) ||
        rb.applicableServices?.some(s => s.toLowerCase().includes(q)) ||
        rb.triggerSymptoms?.some(s => s.toLowerCase().includes(q));

      return matchesCat && matchesQuery;
    });
  }, [runbooks, selectedCategory, searchQuery]);

  const handleCopy = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  return (
    <div className="flex flex-col gap-6">
      {/* Top Filter & Search Bar */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
        {/* Search */}
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-500" />
          <input
            type="text"
            placeholder="Search runbooks by ID, title, service, or symptom..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2 rounded-lg bg-gray-900 border border-gray-800 text-xs text-gray-200 placeholder-gray-500 focus:outline-hidden focus:border-indigo-500 transition-colors"
          />
        </div>

        {/* Category Pills */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 max-w-full">
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => setSelectedCategory(cat.id)}
              className={cn(
                "px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all cursor-pointer",
                selectedCategory === cat.id
                  ? "bg-indigo-600/20 text-indigo-300 border border-indigo-500/40"
                  : "bg-gray-900 border border-gray-800 text-gray-400 hover:text-gray-200 hover:bg-gray-800/60"
              )}
            >
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
          <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
          <p className="text-sm text-gray-400">Loading operational runbook library...</p>
        </Card>
      ) : error ? (
        <Card className="p-8 border-red-500/30 bg-red-950/10 flex flex-col items-center justify-center text-center">
          <AlertTriangle className="h-10 w-10 text-red-400 mb-3" />
          <h3 className="text-base font-semibold text-gray-100">Failed to Load Runbook Catalog</h3>
          <p className="text-sm text-gray-400 mt-1 max-w-md">{error}</p>
          <Button variant="secondary" size="sm" onClick={fetchRunbooks} className="mt-4">
            Retry
          </Button>
        </Card>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Runbook List (5 cols) */}
          <div className="lg:col-span-5 space-y-3 max-h-[700px] overflow-y-auto pr-1">
            <div className="flex items-center justify-between text-xs text-gray-400 font-mono mb-1">
              <span>{filteredRunbooks.length} Runbooks Available</span>
            </div>

            {filteredRunbooks.length === 0 ? (
              <Card className="p-8 border-dashed border-gray-800 text-center text-xs text-gray-500 font-mono">
                No runbooks match your search criteria.
              </Card>
            ) : (
              filteredRunbooks.map((rb) => {
                const isSelected = selectedRunbook?.runbookId === rb.runbookId;
                return (
                  <Card
                    key={rb.runbookId}
                    onClick={() => setSelectedRunbook(rb)}
                    className={cn(
                      "p-4 cursor-pointer transition-all duration-150 border hover:border-gray-700",
                      isSelected
                        ? "bg-indigo-950/20 border-indigo-500/40 ring-1 ring-indigo-500/30"
                        : "bg-gray-900/80 border-gray-800"
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="font-mono text-[10px] border-gray-700">
                          {rb.runbookId}
                        </Badge>
                        <Badge 
                          variant={rb.severity === 'CRITICAL' ? 'critical' : 'outline'}
                          className="text-[10px]"
                        >
                          {rb.severity}
                        </Badge>
                      </div>
                      <ChevronRight className={cn(
                        "h-4 w-4 transition-transform",
                        isSelected ? "text-indigo-400 translate-x-1" : "text-gray-600"
                      )} />
                    </div>

                    <h4 className="font-semibold text-sm text-gray-100 mt-2 line-clamp-1">
                      {rb.title}
                    </h4>

                    <div className="flex items-center gap-2 mt-3 flex-wrap">
                      {rb.applicableServices?.map((svc) => (
                        <span key={svc} className="text-[10px] font-mono px-2 py-0.5 rounded bg-gray-950 text-gray-400 border border-gray-800">
                          {svc}
                        </span>
                      ))}
                      <span className="text-[10px] text-gray-500 font-mono ml-auto">
                        {rb.mitigationSteps?.length || 0} Steps
                      </span>
                    </div>
                  </Card>
                );
              })
            )}
          </div>

          {/* Selected Runbook Inspector (7 cols) */}
          <div className="lg:col-span-7">
            {selectedRunbook ? (
              <Card className="p-6 bg-gray-900/90 border-gray-800 flex flex-col gap-6">
                {/* Header */}
                <div className="flex items-start justify-between flex-wrap gap-3 pb-4 border-b border-gray-800">
                  <div>
                    <div className="flex items-center gap-2 mb-1.5">
                      <Badge variant="outline" className="font-mono text-xs text-indigo-300 border-indigo-500/40">
                        {selectedRunbook.runbookId}
                      </Badge>
                      <Badge variant="secondary" className="text-xs">
                        {selectedRunbook.category}
                      </Badge>
                    </div>
                    <h3 className="text-lg font-bold text-gray-100">
                      {selectedRunbook.title}
                    </h3>
                  </div>

                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleCopy(selectedRunbook.content || selectedRunbook.title, selectedRunbook.runbookId)}
                    className="gap-1.5 text-xs font-mono border-gray-700 cursor-pointer"
                  >
                    {copiedId === selectedRunbook.runbookId ? (
                      <>
                        <Check className="h-3.5 w-3.5 text-emerald-400" />
                        <span>Copied Markdown</span>
                      </>
                    ) : (
                      <>
                        <Copy className="h-3.5 w-3.5" />
                        <span>Copy Markdown</span>
                      </>
                    )}
                  </Button>
                </div>

                {/* Applicable Services & Severity */}
                <div className="grid grid-cols-2 gap-4">
                  <div className="p-3.5 rounded-lg bg-gray-950 border border-gray-800">
                    <span className="text-[10px] font-mono uppercase text-gray-500 font-bold">Applicable Services</span>
                    <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
                      {selectedRunbook.applicableServices?.map((s) => (
                        <Badge key={s} variant="outline" className="font-mono text-xs">{s}</Badge>
                      ))}
                    </div>
                  </div>

                  <div className="p-3.5 rounded-lg bg-gray-950 border border-gray-800">
                    <span className="text-[10px] font-mono uppercase text-gray-500 font-bold">Severity Rating</span>
                    <div className="mt-1.5">
                      <Badge variant={selectedRunbook.severity === 'CRITICAL' ? 'critical' : 'outline'}>
                        {selectedRunbook.severity}
                      </Badge>
                    </div>
                  </div>
                </div>

                {/* Trigger Symptoms */}
                {selectedRunbook.triggerSymptoms && selectedRunbook.triggerSymptoms.length > 0 && (
                  <div>
                    <h4 className="text-xs font-semibold uppercase font-mono text-gray-400 mb-2">
                      Trigger Symptoms
                    </h4>
                    <div className="space-y-1.5">
                      {selectedRunbook.triggerSymptoms.map((sym, idx) => (
                        <div key={idx} className="p-2.5 rounded bg-gray-950/80 border border-gray-800 text-xs text-gray-300 font-mono">
                          • {sym}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Prerequisites */}
                {selectedRunbook.prerequisites && selectedRunbook.prerequisites.length > 0 && (
                  <div>
                    <h4 className="text-xs font-semibold uppercase font-mono text-gray-400 mb-2">
                      Prerequisites & Pre-Flight Checks
                    </h4>
                    <div className="space-y-1.5">
                      {selectedRunbook.prerequisites.map((prereq, idx) => (
                        <div key={idx} className="flex items-start gap-2 p-2.5 rounded bg-gray-950/80 border border-gray-800 text-xs text-gray-300 font-mono">
                          <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400 shrink-0 mt-0.5" />
                          <span>{prereq}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Mitigation Steps */}
                {selectedRunbook.mitigationSteps && selectedRunbook.mitigationSteps.length > 0 && (
                  <div>
                    <h4 className="text-xs font-semibold uppercase font-mono text-gray-400 mb-2">
                      Mitigation Procedures
                    </h4>
                    <div className="space-y-2">
                      {selectedRunbook.mitigationSteps.map((step, idx) => (
                        <div key={idx} className="p-3 rounded-lg bg-gray-950 border border-gray-800 flex items-start gap-3">
                          <div className="h-5 w-5 rounded-full bg-indigo-500/20 text-indigo-300 flex items-center justify-center text-[10px] font-mono font-bold shrink-0 mt-0.5">
                            {idx + 1}
                          </div>
                          <span className="text-xs text-gray-200 font-mono leading-relaxed">{step}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Verification Steps */}
                {selectedRunbook.verificationSteps && selectedRunbook.verificationSteps.length > 0 && (
                  <div>
                    <h4 className="text-xs font-semibold uppercase font-mono text-gray-400 mb-2">
                      Post-Mitigation Verification
                    </h4>
                    <div className="space-y-1.5">
                      {selectedRunbook.verificationSteps.map((ver, idx) => (
                        <div key={idx} className="p-2.5 rounded bg-gray-950/80 border border-gray-800 text-xs text-gray-300 font-mono">
                          ✔ {ver}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Escalation Path */}
                {selectedRunbook.escalationPath && (
                  <div className="p-3.5 rounded-lg bg-gray-950 border border-gray-800/80">
                    <span className="text-[10px] font-mono uppercase text-gray-500 font-bold">Escalation Path</span>
                    <p className="text-xs text-gray-300 mt-1">{selectedRunbook.escalationPath}</p>
                  </div>
                )}
              </Card>
            ) : (
              <Card className="p-12 border-dashed border-gray-800 text-center text-xs text-gray-500 font-mono">
                Select a runbook from the left list to view its complete mitigation procedures and automation commands.
              </Card>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
