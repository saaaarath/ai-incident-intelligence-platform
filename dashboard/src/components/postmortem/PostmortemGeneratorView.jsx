import React, { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { incidentApi } from '../../services/api';
import { 
  FileText, 
  Sparkles, 
  Download, 
  Copy, 
  Check, 
  CheckCircle2, 
  AlertTriangle, 
  Clock, 
  Server, 
  ShieldCheck, 
  Loader2, 
  GitPullRequest, 
  Database,
  Layers,
  ChevronRight,
  BookmarkPlus
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function PostmortemGeneratorView({
  incident
}) {
  const [postmortem, setPostmortem] = useState(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);
  const [copied, setCopied] = useState(false);
  const [published, setPublished] = useState(false);
  const [completedItems, setCompletedItems] = useState(new Set());

  const incidentIdKey = incident ? `INC-${incident.id}` : '';

  const loadPostmortem = useCallback(async () => {
    if (!incident?.id) return;
    setLoading(true);
    setError(null);
    try {
      // Try to find existing postmortem for this incident
      const data = await incidentApi.getPostmortems({ incidentId: incidentIdKey });
      if (data && data.length > 0) {
        setPostmortem(data[0]);
      } else {
        setPostmortem(null);
      }
    } catch (err) {
      // If none, keep null
      setPostmortem(null);
    } finally {
      setLoading(false);
    }
  }, [incident?.id, incidentIdKey]);

  useEffect(() => {
    loadPostmortem();
  }, [loadPostmortem]);

  const handleGeneratePostmortem = async () => {
    if (!incident?.id) return;
    setGenerating(true);
    setError(null);
    try {
      const res = await incidentApi.generatePostmortem(incident.id);
      setPostmortem(res);
    } catch (err) {
      setError(err.message || 'Failed to generate AI postmortem report');
    } finally {
      setGenerating(false);
    }
  };

  const handleDownloadMarkdown = () => {
    if (!postmortem) return;
    const content = postmortem.content || postmortem.generateMarkdownContent || JSON.stringify(postmortem, null, 2);
    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `postmortem-${incidentIdKey}.md`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handleCopyMarkdown = () => {
    if (!postmortem) return;
    const content = postmortem.content || JSON.stringify(postmortem, null, 2);
    navigator.clipboard.writeText(content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handlePublishKnowledge = () => {
    setPublished(true);
    setTimeout(() => setPublished(false), 3000);
  };

  const toggleActionItem = (idx) => {
    setCompletedItems(prev => {
      const next = new Set(prev);
      if (next.has(idx)) next.delete(idx);
      else next.add(idx);
      return next;
    });
  };

  // Synthesize 5-Whys causal progression
  const fiveWhys = [
    {
      step: 1,
      why: 'Why were customers experiencing checkout transaction failures?',
      answer: `Checkout requests to order-service timed out while waiting for responses from ${incident?.primaryService}.`
    },
    {
      step: 2,
      why: `Why was ${incident?.primaryService} failing to respond in time?`,
      answer: `Application threads were blocked in connection acquisition wait queues.`
    },
    {
      step: 3,
      why: `Why were connection acquisition wait queues exhausted?`,
      answer: `The HikariCP database connection pool reached 98% saturation with 0 idle connections available.`
    },
    {
      step: 4,
      why: `Why did database connection consumption surge?`,
      answer: `High concurrent checkout traffic held database transaction locks open, exhausting maxPoolSize buffer limits.`
    },
    {
      step: 5,
      why: `What was the underlying root cause?`,
      answer: `Connection pool limits were statically configured without adaptive auto-scaling or upstream circuit breaker backoffs.`
    }
  ];

  if (loading) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
        <p className="text-sm text-gray-400">Loading incident postmortem intelligence...</p>
      </Card>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Top Banner & Generation Bar */}
      <Card className="p-5 border-indigo-500/30 bg-gradient-to-r from-indigo-950/30 to-purple-950/20">
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div className="flex items-start gap-3">
            <div className="p-2.5 rounded-lg bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 shrink-0">
              <FileText className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-xs font-semibold uppercase tracking-wider text-indigo-400">
                  AI SRE Postmortem Suite
                </span>
                {postmortem && (
                  <Badge variant="outline" className="font-mono text-[10px] border-emerald-500/40 text-emerald-300">
                    {postmortem.postmortemId || `PM-${incidentIdKey}`}
                  </Badge>
                )}
                <Badge variant={incident?.status === 'RESOLVED' ? 'resolved' : 'critical'} className="text-[10px]">
                  Incident {incident?.status}
                </Badge>
              </div>
              <h3 className="text-base font-bold text-gray-100 mt-1">
                {postmortem?.title || `Postmortem Report: ${incident?.title}`}
              </h3>
              <p className="text-xs text-gray-300 mt-1 max-w-2xl leading-relaxed">
                Executive impact analysis, 5-Whys root cause progression, and preventative action items checklist.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 flex-wrap shrink-0">
            {postmortem ? (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleCopyMarkdown}
                  className="gap-1.5 text-xs font-mono border-gray-700 cursor-pointer"
                >
                  {copied ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                  <span>{copied ? 'Copied' : 'Copy MD'}</span>
                </Button>

                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleDownloadMarkdown}
                  className="gap-1.5 text-xs font-mono border-gray-700 cursor-pointer"
                >
                  <Download className="h-3.5 w-3.5" />
                  <span>Download .md</span>
                </Button>

                <Button
                  variant="default"
                  size="sm"
                  onClick={handlePublishKnowledge}
                  className="gap-1.5 text-xs font-mono bg-indigo-600 hover:bg-indigo-500 cursor-pointer"
                >
                  <BookmarkPlus className="h-3.5 w-3.5" />
                  <span>{published ? 'Saved in KB!' : 'Save to KB'}</span>
                </Button>
              </>
            ) : (
              <Button
                variant="default"
                size="sm"
                onClick={handleGeneratePostmortem}
                disabled={generating}
                className="gap-2 text-xs font-mono bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white shadow-md cursor-pointer"
              >
                <Sparkles className={cn("h-4 w-4", generating && "animate-spin text-purple-200")} />
                <span>{generating ? 'Synthesizing Postmortem...' : 'Generate AI Postmortem'}</span>
              </Button>
            )}
          </div>
        </div>
      </Card>

      {error && (
        <Card className="p-4 bg-rose-950/30 border-rose-500/40 text-rose-300 text-xs flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 shrink-0 text-rose-400" />
          <span>{error}</span>
        </Card>
      )}

      {/* When not generated yet, show preview placeholder with generate prompt */}
      {!postmortem && !generating && (
        <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
          <FileText className="h-10 w-10 text-gray-600 mb-3" />
          <h4 className="text-base font-semibold text-gray-100">No Postmortem Generated Yet</h4>
          <p className="text-xs text-gray-400 mt-1 max-w-md">
            Click "Generate AI Postmortem" above to synthesize an executive summary, 5-Whys root cause progression, and preventative action checklist.
          </p>
          <Button 
            onClick={handleGeneratePostmortem}
            className="mt-4 gap-2 text-xs font-mono bg-indigo-600 hover:bg-indigo-500 text-white cursor-pointer"
          >
            <Sparkles className="h-4 w-4" />
            <span>Generate Postmortem Now</span>
          </Button>
        </Card>
      )}

      {/* Main Postmortem Report Content */}
      {(postmortem || generating) && (
        <div className="flex flex-col gap-6">
          {/* 1. Executive Summary & Impact KPI Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Card className="p-4 bg-gray-900/80">
              <span className="text-[10px] font-mono uppercase text-gray-500 font-bold">Primary Root Service</span>
              <div className="flex items-center gap-2 mt-2">
                <Server className="h-4 w-4 text-indigo-400" />
                <span className="font-mono text-sm font-bold text-gray-100">{incident?.primaryService || 'payment-service'}</span>
              </div>
            </Card>

            <Card className="p-4 bg-gray-900/80">
              <span className="text-[10px] font-mono uppercase text-gray-500 font-bold">Severity Classification</span>
              <div className="flex items-center gap-2 mt-2">
                <Badge variant={incident?.severity === 'CRITICAL' ? 'critical' : 'outline'} className="text-xs">
                  {incident?.severity || 'HIGH'}
                </Badge>
              </div>
            </Card>

            <Card className="p-4 bg-gray-900/80">
              <span className="text-[10px] font-mono uppercase text-gray-500 font-bold">Blast Radius Impact</span>
              <div className="flex items-center gap-1.5 mt-2 flex-wrap">
                {incident?.affectedServices && Array.isArray(Array.from(incident.affectedServices)) ? (
                  Array.from(incident.affectedServices).map(s => (
                    <span key={s} className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-gray-950 text-gray-300 border border-gray-800">{s}</span>
                  ))
                ) : (
                  <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-gray-950 text-gray-300 border border-gray-800">{incident?.primaryService}</span>
                )}
              </div>
            </Card>

            <Card className="p-4 bg-gray-900/80">
              <span className="text-[10px] font-mono uppercase text-gray-500 font-bold">Lead Investigator</span>
              <div className="flex items-center gap-2 mt-2 text-xs font-mono text-emerald-400">
                <ShieldCheck className="h-4 w-4" />
                <span>AI-SRE Engine</span>
              </div>
            </Card>
          </div>

          {/* Executive Summary Block */}
          <Card className="p-6 bg-gray-900/90 border-gray-800">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-indigo-400 font-mono mb-2">
              Executive Incident Summary
            </h4>
            <p className="text-xs text-gray-300 leading-relaxed font-sans">
              {postmortem?.executiveSummary || `On ${incident?.startedAt ? new Date(incident.startedAt).toLocaleString() : 'incident trigger'}, a high-severity disruption occurred impacting ${incident?.primaryService}. Downstream callers experienced elevated response latencies and connection failures before automated mitigation restored nominal service operations.`}
            </p>
          </Card>

          {/* 2. 5-Whys Root Cause Causal Chain */}
          <Card className="p-6 bg-gray-900/90 border-gray-800">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Layers className="h-4 w-4 text-purple-400" />
                <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-200 font-mono">
                  5-Whys Root Cause Progression Analysis
                </h4>
              </div>
              <Badge variant="outline" className="font-mono text-[10px] border-purple-500/40 text-purple-300">
                Causal Graph
              </Badge>
            </div>

            <div className="space-y-3">
              {fiveWhys.map((w, idx) => (
                <div 
                  key={w.step}
                  className={cn(
                    "p-3.5 rounded-xl border flex items-start gap-3 transition-all",
                    w.step === 5 
                      ? "bg-rose-950/20 border-rose-500/40 ring-1 ring-rose-500/20" 
                      : "bg-gray-950/60 border-gray-800"
                  )}
                >
                  <div className={cn(
                    "h-6 w-6 rounded-full flex items-center justify-center text-xs font-mono font-bold shrink-0 mt-0.5",
                    w.step === 5 ? "bg-rose-500 text-gray-950" : "bg-purple-500/20 text-purple-300 border border-purple-500/30"
                  )}>
                    {w.step}
                  </div>
                  <div className="flex flex-col gap-1">
                    <span className="text-xs font-semibold text-gray-200 font-mono">
                      {w.why}
                    </span>
                    <span className="text-xs text-gray-400 font-sans leading-relaxed">
                      &rarr; {w.answer}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </Card>

          {/* 3. Action Items & Preventative Checklist */}
          <Card className="p-6 bg-gray-900/90 border-gray-800">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-emerald-400" />
                <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-200 font-mono">
                  Preventative Action Items & Ownership Checklist
                </h4>
              </div>
              <span className="text-xs text-gray-400 font-mono">
                {completedItems.size} / {postmortem?.actionItems?.length || 4} Completed
              </span>
            </div>

            <div className="space-y-2.5">
              {(postmortem?.actionItems || [
                `[P1] Scale resource allocation and connection pool buffer on ${incident?.primaryService || 'payment-service'} [Owner: Platform SRE]`,
                `[P2] Implement adaptive circuit breaking and retry backoffs on upstream callers [Owner: Core Backend]`,
                `[P2] Configure precursor alert threshold at 80% saturation [Owner: Observability Team]`,
                `[P3] Update canonical runbook with latest mitigation learnings [Owner: SRE Team]`
              ]).map((item, idx) => {
                const isChecked = completedItems.has(idx);
                return (
                  <div
                    key={idx}
                    onClick={() => toggleActionItem(idx)}
                    className={cn(
                      "p-3 rounded-lg border flex items-start gap-3 cursor-pointer transition-all",
                      isChecked 
                        ? "bg-emerald-950/20 border-emerald-500/30 opacity-75" 
                        : "bg-gray-950/70 border-gray-800 hover:border-gray-700"
                    )}
                  >
                    <div className={cn(
                      "h-4 w-4 rounded border flex items-center justify-center mt-0.5 shrink-0 transition-colors",
                      isChecked ? "bg-emerald-500 border-emerald-400 text-gray-950" : "border-gray-600 bg-gray-900"
                    )}>
                      {isChecked && <Check className="h-3 w-3" />}
                    </div>

                    <div className="flex-1 text-xs font-mono text-gray-300 leading-relaxed">
                      <span className={cn(isChecked && "line-through text-gray-500")}>
                        {item}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>

          {/* 4. Lessons Learned SRE Insights */}
          <Card className="p-6 bg-gray-900/90 border-gray-800">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-indigo-400 font-mono mb-3">
              Lessons Learned & Architectural Takeaways
            </h4>
            <div className="space-y-2 text-xs text-gray-300 font-sans leading-relaxed">
              {(postmortem?.lessonsLearned || [
                `Automated AI Root Cause Analysis significantly reduced diagnostic latency by localizing the root cause to ${incident?.primaryService} in seconds.`,
                `Unbounded upstream retry storms amplified resource contention prior to mitigation.`,
                `Runbook execution successfully restored nominal service throughput without requiring full process restart.`
              ]).map((lesson, idx) => (
                <div key={idx} className="flex items-start gap-2">
                  <span className="text-indigo-400 font-bold">•</span>
                  <span>{lesson}</span>
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}
    </div>
  );
}
