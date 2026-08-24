import React from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { 
  Cpu, 
  Sparkles, 
  CheckCircle2, 
  AlertTriangle, 
  FileText, 
  ShieldAlert, 
  Layers, 
  Clock, 
  ArrowRight,
  HelpCircle,
  Loader2,
  Server,
  Activity
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function RcaReportView({
  rcaReport,
  loading,
  analyzing,
  error,
  onAnalyze,
  onRetry
}) {
  if (analyzing) {
    return (
      <Card className="p-12 border-indigo-500/30 bg-indigo-950/10 flex flex-col items-center justify-center text-center">
        <div className="relative mb-6">
          <div className="h-16 w-16 rounded-full border-2 border-indigo-500/20 flex items-center justify-center">
            <Sparkles className="h-8 w-8 text-indigo-400 animate-pulse" />
          </div>
          <Loader2 className="h-16 w-16 text-indigo-500 animate-spin absolute inset-0" />
        </div>
        <h3 className="text-lg font-semibold text-gray-100">AI Root Cause Analysis in Progress...</h3>
        <p className="text-sm text-gray-400 mt-2 max-w-md">
          Correlating multi-modal anomaly telemetry, dependency topologies, vector-matched historical incidents, and LLM diagnostic synthesis.
        </p>
        <div className="flex items-center gap-2 mt-6 text-xs text-indigo-300 font-mono bg-indigo-950/40 px-4 py-2 rounded-full border border-indigo-500/20">
          <span className="h-2 w-2 rounded-full bg-indigo-400 animate-ping" />
          <span>Synthesizing root cause hypothesis & evidence grounding...</span>
        </div>
      </Card>
    );
  }

  if (loading) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
        <p className="text-sm text-gray-400">Loading AI Root Cause Analysis report...</p>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-8 border-red-500/30 bg-red-950/10 flex flex-col items-center justify-center text-center">
        <AlertTriangle className="h-10 w-10 text-red-400 mb-3" />
        <h3 className="text-base font-semibold text-gray-100">Failed to Retrieve RCA Report</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">{error}</p>
        {onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry} className="mt-4">
            Retry
          </Button>
        )}
      </Card>
    );
  }

  if (!rcaReport || !rcaReport.rootCause) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-indigo-500/10 text-indigo-400 mb-4">
          <Cpu className="h-7 w-7" />
        </div>
        <h3 className="text-base font-semibold text-gray-100">No RCA Report Available Yet</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">
          This incident has not been processed by the AI Root Cause Analysis engine yet. Run an analysis to generate diagnosis, evidence grounding, and remediation actions.
        </p>
        {onAnalyze && (
          <Button onClick={onAnalyze} className="mt-6 gap-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 shadow-md shadow-indigo-500/20">
            <Sparkles className="h-4 w-4" />
            <span>Analyze Incident with AI</span>
          </Button>
        )}
      </Card>
    );
  }

  const { rootCause, confidence, evidence = [], alternativeHypotheses = [], recommendedInvestigation = [], uncertaintyNotes = [], metadata } = rcaReport;

  const confidenceLevel = (confidence?.level || 'MEDIUM').toUpperCase();
  const confidenceScore = confidence?.score !== undefined ? Math.round(confidence.score * 100) : 75;

  return (
    <div className="flex flex-col gap-6">
      {/* Top Banner: Root Cause & Confidence */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Root Cause Card */}
        <Card className="lg:col-span-2 p-6 border-indigo-500/30 bg-gradient-to-br from-indigo-950/30 via-gray-900 to-gray-900 flex flex-col justify-between gap-4">
          <div>
            <div className="flex items-center justify-between gap-2 flex-wrap mb-2">
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded-md bg-indigo-500/20 text-indigo-400">
                  <Cpu className="h-4 w-4" />
                </span>
                <span className="text-xs font-semibold uppercase tracking-wider text-indigo-400">
                  Identified Root Cause
                </span>
              </div>
              {rootCause?.category && (
                <Badge variant="outline" className="font-mono text-xs border-indigo-500/30 text-indigo-300">
                  {rootCause.category}
                </Badge>
              )}
            </div>

            <h3 className="text-lg font-bold text-gray-100 leading-snug">
              {rootCause?.statement || 'Unknown Root Cause'}
            </h3>

            {rootCause?.inferenceDetails && (
              <p className="text-sm text-gray-300 mt-2.5 leading-relaxed bg-gray-950/50 p-3 rounded-lg border border-gray-800/80">
                {rootCause.inferenceDetails}
              </p>
            )}
          </div>

          <div className="flex items-center justify-between pt-3 border-t border-gray-800/60 text-xs text-gray-400">
            <div className="flex items-center gap-1.5 font-mono">
              <Server className="h-3.5 w-3.5 text-indigo-400" />
              <span>Fault Origin: <strong className="text-gray-200">{rootCause?.rootService || 'Unknown'}</strong></span>
            </div>
            <Badge variant={rootCause?.isDirectlyObserved ? 'resolved' : 'investigating'} className="text-[10px]">
              {rootCause?.isDirectlyObserved ? 'Directly Observed' : 'Topologically Inferred'}
            </Badge>
          </div>
        </Card>

        {/* AI Confidence Gauge */}
        <Card className="p-6 flex flex-col justify-between gap-4 bg-gray-900/80">
          <div>
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-semibold uppercase tracking-wider text-gray-400">
                AI Confidence
              </span>
              <Badge 
                variant={confidenceLevel === 'HIGH' ? 'resolved' : (confidenceLevel === 'MEDIUM' ? 'medium' : 'destructive')}
              >
                {confidenceLevel} ({confidenceScore}%)
              </Badge>
            </div>

            {/* Progress Bar */}
            <div className="w-full bg-gray-800 rounded-full h-2.5 overflow-hidden my-2">
              <div 
                className={cn(
                  "h-2.5 rounded-full transition-all duration-500",
                  confidenceLevel === 'HIGH' && "bg-emerald-500",
                  confidenceLevel === 'MEDIUM' && "bg-amber-500",
                  confidenceLevel === 'LOW' && "bg-red-500"
                )}
                style={{ width: `${confidenceScore}%` }}
              />
            </div>

            <p className="text-xs text-gray-400 mt-3 leading-relaxed">
              {confidence?.rationale || 'Confidence estimated based on anomaly telemetry density and cross-service evidence grounding.'}
            </p>
          </div>

          {metadata && (
            <div className="text-[11px] font-mono text-gray-500 pt-3 border-t border-gray-800 flex items-center justify-between">
              <span>Model: {metadata.model || 'Gemini'}</span>
              <span>{metadata.executionLatencyMs ? `${metadata.executionLatencyMs}ms` : ''}</span>
            </div>
          )}
        </Card>
      </div>

      {/* Recommended Investigation & Remediation Checklist */}
      <Card className="p-6 bg-gray-900/80">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-emerald-400" />
            <h4 className="text-base font-semibold text-gray-100">Recommended Investigation & Action Plan</h4>
          </div>
          <span className="text-xs font-mono text-gray-400">
            {recommendedInvestigation.length} action{recommendedInvestigation.length !== 1 ? 's' : ''}
          </span>
        </div>

        {recommendedInvestigation.length === 0 ? (
          <p className="text-xs text-gray-500">No specific investigation steps generated.</p>
        ) : (
          <div className="flex flex-col gap-3">
            {recommendedInvestigation.map((item, idx) => {
              const priority = (item.priority || 'MEDIUM').toUpperCase();
              return (
                <div key={idx} className="p-3.5 rounded-lg bg-gray-950/60 border border-gray-800 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                  <div className="flex items-start gap-3">
                    <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-indigo-500/20 text-indigo-400 text-xs font-mono font-bold mt-0.5">
                      {idx + 1}
                    </span>
                    <div className="flex flex-col gap-0.5">
                      <span className="text-sm font-medium text-gray-200">{item.action}</span>
                      {item.justification && (
                        <span className="text-xs text-gray-400 leading-relaxed">{item.justification}</span>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0 ml-8 sm:ml-0">
                    <Badge 
                      variant={priority === 'IMMEDIATE' || priority === 'CRITICAL' ? 'destructive' : (priority === 'HIGH' ? 'high' : 'medium')}
                      className="text-[10px]"
                    >
                      {priority}
                    </Badge>
                    {item.runbookRef && (
                      <span className="text-[11px] font-mono text-indigo-400 bg-indigo-950/40 px-2 py-0.5 rounded border border-indigo-500/20">
                        {item.runbookRef}
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </Card>

      {/* Grounded Evidence Items */}
      <Card className="p-6 bg-gray-900/80">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Activity className="h-5 w-5 text-indigo-400" />
            <h4 className="text-base font-semibold text-gray-100">Evidence Grounding</h4>
          </div>
          <span className="text-xs font-mono text-gray-400">
            {evidence.length} supporting record{evidence.length !== 1 ? 's' : ''}
          </span>
        </div>

        {evidence.length === 0 ? (
          <p className="text-xs text-gray-500">No specific evidence records attached.</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {evidence.map((ev, idx) => (
              <div key={idx} className="p-3.5 rounded-lg bg-gray-950/60 border border-gray-800 flex flex-col gap-2">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className="text-[10px] font-mono border-gray-700">
                      {ev.type || 'EVIDENCE'}
                    </Badge>
                    <span className="text-xs font-mono text-indigo-300 font-semibold">{ev.service}</span>
                  </div>
                  {ev.timestamp && (
                    <span className="text-[11px] font-mono text-gray-500">
                      {new Date(ev.timestamp).toLocaleTimeString()}
                    </span>
                  )}
                </div>

                <p className="text-xs text-gray-300 leading-relaxed">{ev.observation}</p>

                {ev.sourceId && (
                  <span className="text-[10px] font-mono text-gray-500 truncate">
                    Ref ID: {ev.sourceId}
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Alternative Hypotheses & Uncertainties (if present) */}
      {(alternativeHypotheses.length > 0 || uncertaintyNotes.length > 0) && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {alternativeHypotheses.length > 0 && (
            <Card className="p-5 bg-gray-900/80">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-3 flex items-center gap-2">
                <HelpCircle className="h-4 w-4 text-amber-400" />
                Alternative Hypotheses Considered
              </h4>
              <div className="flex flex-col gap-2.5">
                {alternativeHypotheses.map((alt, idx) => (
                  <div key={idx} className="p-3 rounded-lg bg-gray-950/60 border border-gray-800 text-xs flex flex-col gap-1">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-gray-200">{alt.hypothesis}</span>
                      <Badge variant="outline" className="text-[10px]">
                        Likelihood: {alt.likelihood}
                      </Badge>
                    </div>
                    {alt.reasonsForRejection && (
                      <span className="text-gray-400 mt-0.5">Rejected: {alt.reasonsForRejection}</span>
                    )}
                  </div>
                ))}
              </div>
            </Card>
          )}

          {uncertaintyNotes.length > 0 && (
            <Card className="p-5 bg-gray-900/80">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-400 mb-3 flex items-center gap-2">
                <ShieldAlert className="h-4 w-4 text-sky-400" />
                Uncertainty & Diagnostic Limitations
              </h4>
              <ul className="flex flex-col gap-2 list-disc list-inside text-xs text-gray-300">
                {uncertaintyNotes.map((note, idx) => (
                  <li key={idx} className="leading-relaxed text-gray-400">{note}</li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}
