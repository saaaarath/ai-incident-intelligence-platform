import React, { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { incidentApi } from '../../services/api';
import { 
  BookOpen, 
  Play, 
  CheckCircle2, 
  AlertTriangle, 
  Loader2, 
  Terminal, 
  ShieldCheck, 
  Sparkles, 
  RotateCcw,
  Zap,
  Check,
  ChevronRight,
  Server,
  ArrowRight
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function IncidentRunbookRunner({
  incident,
  onIncidentResolved
}) {
  const [runbooks, setRunbooks] = useState([]);
  const [selectedRunbook, setSelectedRunbook] = useState(null);
  const [loading, setLoading] = useState(true);
  const [executingStep, setExecutingStep] = useState(null); // stepIndex | 'ALL'
  const [completedSteps, setCompletedSteps] = useState(new Set());
  const [executionLogs, setExecutionLogs] = useState([]);
  const [executionResult, setExecutionResult] = useState(null);
  const [error, setError] = useState(null);

  // Load runbooks and automatically pick best recommendation
  const loadRunbooks = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await incidentApi.getRunbooks();
      const allRbs = data || [];
      setRunbooks(allRbs);

      if (allRbs.length > 0) {
        // Find best match for incident
        const metric = (incident?.metric || '').toLowerCase();
        const service = (incident?.primaryService || '').toLowerCase();
        const title = (incident?.title || '').toLowerCase();

        let best = allRbs.find(r => {
          const rId = r.runbookId?.toLowerCase() || '';
          const rTitle = r.title?.toLowerCase() || '';
          if (metric.includes('hikaricp') || metric.includes('pool') || title.includes('connection')) {
            return rId.includes('db') || rTitle.includes('connection') || rTitle.includes('database');
          }
          if (metric.includes('latency') || metric.includes('duration') || title.includes('latency') || title.includes('timeout')) {
            return rId.includes('lat') || rTitle.includes('latency');
          }
          if (metric.includes('5xx') || metric.includes('500') || title.includes('500') || title.includes('error')) {
            return rId.includes('spike') || rTitle.includes('error') || rId.includes('503');
          }
          if (metric.includes('deployment') || title.includes('deployment') || title.includes('regression')) {
            return rId.includes('reg') || rTitle.includes('deployment');
          }
          return r.applicableServices?.some(s => s.toLowerCase() === service);
        });

        setSelectedRunbook(best || allRbs[0]);
      }
    } catch (err) {
      setError(err.message || 'Failed to load operational runbooks');
    } finally {
      setLoading(false);
    }
  }, [incident]);

  useEffect(() => {
    loadRunbooks();
  }, [loadRunbooks]);

  const handleExecuteStep = async (stepIndex) => {
    if (!selectedRunbook) return;
    setExecutingStep(stepIndex);
    setError(null);

    const logEntry = `[${new Date().toLocaleTimeString()}] Executing step ${stepIndex + 1}: "${selectedRunbook.mitigationSteps[stepIndex]}"...`;
    setExecutionLogs(prev => [...prev, logEntry]);

    try {
      const payload = {
        incidentId: incident?.id,
        stepIndex: stepIndex,
        executeAll: false,
        autoResolve: stepIndex === (selectedRunbook.mitigationSteps.length - 1)
      };

      const res = await incidentApi.executeRunbook(selectedRunbook.runbookId || selectedRunbook.id, payload);
      setCompletedSteps(prev => new Set([...prev, stepIndex]));
      
      if (res.executionLogs && res.executionLogs.length > 0) {
        setExecutionLogs(prev => [...prev, ...res.executionLogs]);
      }

      if (res.incidentResolved) {
        setExecutionResult({
          status: 'SUCCESS',
          message: `Incident INC-${incident?.id} resolved via runbook mitigation!`
        });
        if (onIncidentResolved) onIncidentResolved();
      }
    } catch (err) {
      setError(err.message || 'Error executing runbook step');
      setExecutionLogs(prev => [...prev, `[ERROR] Failed to execute step: ${err.message}`]);
    } finally {
      setExecutingStep(null);
    }
  };

  const handleExecuteAll = async () => {
    if (!selectedRunbook) return;
    setExecutingStep('ALL');
    setError(null);

    setExecutionLogs([
      `[${new Date().toLocaleTimeString()}] Initiating full automated mitigation for ${selectedRunbook.runbookId} (${selectedRunbook.title})...`
    ]);

    try {
      const payload = {
        incidentId: incident?.id,
        executeAll: true,
        autoResolve: true
      };

      const res = await incidentApi.executeRunbook(selectedRunbook.runbookId || selectedRunbook.id, payload);
      
      const allStepIndices = selectedRunbook.mitigationSteps.map((_, idx) => idx);
      setCompletedSteps(new Set(allStepIndices));

      if (res.executionLogs && res.executionLogs.length > 0) {
        setExecutionLogs(res.executionLogs);
      }

      setExecutionResult({
        status: 'SUCCESS',
        message: `Automated mitigation completed. Incident INC-${incident?.id} has been transitioned to RESOLVED.`
      });

      if (onIncidentResolved) onIncidentResolved();
    } catch (err) {
      setError(err.message || 'Error running automated mitigation');
      setExecutionLogs(prev => [...prev, `[ERROR] Automated mitigation failed: ${err.message}`]);
    } finally {
      setExecutingStep(null);
    }
  };

  if (loading) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
        <p className="text-sm text-gray-400">Loading recommended SRE runbooks...</p>
      </Card>
    );
  }

  if (error && !selectedRunbook) {
    return (
      <Card className="p-8 border-red-500/30 bg-red-950/10 flex flex-col items-center justify-center text-center">
        <AlertTriangle className="h-10 w-10 text-red-400 mb-3" />
        <h3 className="text-base font-semibold text-gray-100">Failed to Load Runbooks</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">{error}</p>
        <Button variant="secondary" size="sm" onClick={loadRunbooks} className="mt-4">
          Retry
        </Button>
      </Card>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Recommended Runbook Banner */}
      <Card className="p-5 border-indigo-500/30 bg-gradient-to-r from-indigo-950/30 to-purple-950/20">
        <div className="flex items-start justify-between flex-wrap gap-4">
          <div className="flex items-start gap-3">
            <div className="p-2.5 rounded-lg bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 shrink-0">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-xs font-semibold uppercase tracking-wider text-indigo-400">
                  AI Recommended Runbook
                </span>
                {selectedRunbook && (
                  <Badge variant="outline" className="font-mono text-[10px] border-indigo-500/40 text-indigo-300">
                    {selectedRunbook.runbookId}
                  </Badge>
                )}
                {incident?.status === 'RESOLVED' && (
                  <Badge variant="resolved" className="text-[10px]">
                    Incident Resolved
                  </Badge>
                )}
              </div>
              <h3 className="text-base font-bold text-gray-100 mt-1">
                {selectedRunbook?.title || 'Operational Mitigation Procedure'}
              </h3>
              <p className="text-xs text-gray-300 mt-1 max-w-2xl leading-relaxed">
                Matched to fault signature on <span className="font-mono text-indigo-300">{incident?.primaryService}</span> ({incident?.metric || 'fault telemetry'}).
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <Button
              variant="default"
              size="sm"
              onClick={handleExecuteAll}
              disabled={executingStep !== null || incident?.status === 'RESOLVED'}
              className="gap-2 text-xs font-mono bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm cursor-pointer"
            >
              {executingStep === 'ALL' ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span>Executing Mitigation...</span>
                </>
              ) : (
                <>
                  <Zap className="h-3.5 w-3.5 text-amber-300" />
                  <span>Auto-Mitigate & Resolve</span>
                </>
              )}
            </Button>
          </div>
        </div>
      </Card>

      {/* Main Runner Grid: Steps & Real-Time Terminal Console */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Prerequisites & Mitigation Steps (7 cols) */}
        <div className="lg:col-span-7 flex flex-col gap-6">
          {/* Prerequisites */}
          {selectedRunbook?.prerequisites && selectedRunbook.prerequisites.length > 0 && (
            <Card className="p-5 bg-gray-900/80">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="h-4 w-4 text-emerald-400" />
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-300 font-mono">
                    Pre-Flight Safety Checks
                  </h4>
                </div>
                <Badge variant="outline" className="text-[10px] text-emerald-400 border-emerald-500/30">
                  Ready ({selectedRunbook.prerequisites.length})
                </Badge>
              </div>

              <div className="space-y-2">
                {selectedRunbook.prerequisites.map((prereq, idx) => (
                  <div key={idx} className="flex items-start gap-2.5 p-2.5 rounded bg-gray-950/60 border border-gray-800 text-xs">
                    <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0 mt-0.5" />
                    <span className="text-gray-300 font-mono text-[11px] leading-relaxed">{prereq}</span>
                  </div>
                ))}
              </div>
            </Card>
          )}

          {/* Step-by-Step Mitigation Workflow */}
          <Card className="p-5 bg-gray-900/80">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <Terminal className="h-4 w-4 text-indigo-400" />
                <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-300 font-mono">
                  Mitigation Action Steps
                </h4>
              </div>
              <span className="text-xs text-gray-400 font-mono">
                {completedSteps.size} / {selectedRunbook?.mitigationSteps?.length || 0} Executed
              </span>
            </div>

            <div className="space-y-3">
              {selectedRunbook?.mitigationSteps?.map((step, idx) => {
                const isCompleted = completedSteps.has(idx);
                const isRunning = executingStep === idx;

                return (
                  <div 
                    key={idx}
                    className={cn(
                      "p-4 rounded-xl border transition-all duration-150 flex items-start justify-between gap-3",
                      isCompleted 
                        ? "bg-emerald-950/20 border-emerald-500/30" 
                        : isRunning 
                          ? "bg-indigo-950/30 border-indigo-500/40 ring-1 ring-indigo-500/20"
                          : "bg-gray-950/60 border-gray-800 hover:border-gray-700"
                    )}
                  >
                    <div className="flex items-start gap-3">
                      <div className={cn(
                        "h-6 w-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 font-mono mt-0.5",
                        isCompleted 
                          ? "bg-emerald-500 text-gray-950" 
                          : isRunning 
                            ? "bg-indigo-600 text-white" 
                            : "bg-gray-800 text-gray-300"
                      )}>
                        {isCompleted ? <Check className="h-3.5 w-3.5" /> : idx + 1}
                      </div>

                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-semibold text-gray-200">
                            Step {idx + 1}
                          </span>
                          {isCompleted && (
                            <Badge variant="resolved" className="text-[9px] py-0">Completed</Badge>
                          )}
                          {isRunning && (
                            <Badge variant="outline" className="text-[9px] py-0 border-indigo-500/40 text-indigo-300 animate-pulse">Running</Badge>
                          )}
                        </div>
                        <p className="text-xs text-gray-300 mt-1 font-mono leading-relaxed">
                          {step}
                        </p>
                      </div>
                    </div>

                    <Button
                      variant={isCompleted ? "secondary" : "outline"}
                      size="sm"
                      onClick={() => handleExecuteStep(idx)}
                      disabled={isRunning || executingStep !== null || isCompleted}
                      className={cn(
                        "shrink-0 text-xs font-mono h-8 cursor-pointer",
                        isCompleted && "text-emerald-400 border-emerald-500/30"
                      )}
                    >
                      {isRunning ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : isCompleted ? (
                        'Done'
                      ) : (
                        <>
                          <Play className="h-3 w-3" />
                          <span>Run Step</span>
                        </>
                      )}
                    </Button>
                  </div>
                );
              })}
            </div>
          </Card>

          {/* Post-Mitigation Verification Checks */}
          {selectedRunbook?.verificationSteps && selectedRunbook.verificationSteps.length > 0 && (
            <Card className="p-5 bg-gray-900/80">
              <div className="flex items-center gap-2 mb-3">
                <CheckCircle2 className="h-4 w-4 text-purple-400" />
                <h4 className="text-xs font-semibold uppercase tracking-wider text-gray-300 font-mono">
                  Verification & Health Check Criteria
                </h4>
              </div>

              <div className="space-y-2">
                {selectedRunbook.verificationSteps.map((ver, idx) => (
                  <div key={idx} className="flex items-start gap-2.5 p-2.5 rounded bg-gray-950/60 border border-gray-800 text-xs">
                    <ChevronRight className="h-4 w-4 text-purple-400 shrink-0 mt-0.5" />
                    <span className="text-gray-300 font-mono text-[11px] leading-relaxed">{ver}</span>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>

        {/* Right Column: Real-Time Execution Console & Escalation (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6">
          {/* Terminal Execution Console */}
          <Card className="p-5 bg-gray-950 border-gray-800 flex flex-col flex-1 min-h-[380px]">
            <div className="flex items-center justify-between pb-3 mb-3 border-b border-gray-800">
              <div className="flex items-center gap-2">
                <Terminal className="h-4 w-4 text-emerald-400" />
                <span className="text-xs font-mono font-bold text-gray-200 uppercase tracking-wider">
                  Remediation Terminal Logs
                </span>
              </div>
              <span className="flex items-center gap-1.5 text-[10px] font-mono text-gray-500">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
                TTY Output
              </span>
            </div>

            {executionLogs.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-center p-6 text-xs text-gray-600 font-mono">
                <Terminal className="h-8 w-8 text-gray-700 mb-2" />
                <span>No mitigation actions run yet.</span>
                <span className="text-[11px] text-gray-700 mt-1">Execute a step or click Auto-Mitigate to see live output.</span>
              </div>
            ) : (
              <div className="flex-1 space-y-1.5 font-mono text-[11px] text-gray-300 overflow-y-auto max-h-[380px] p-2 rounded bg-black/40 border border-gray-900">
                {executionLogs.map((line, idx) => (
                  <div 
                    key={idx} 
                    className={cn(
                      "leading-relaxed",
                      line.includes('ERROR') && "text-rose-400 font-bold",
                      line.includes('COMPLETED') && "text-emerald-300 font-semibold",
                      line.includes('RESOLVED') && "text-emerald-400 font-bold bg-emerald-950/30 p-1 rounded",
                      line.includes('PREREQUISITE') && "text-sky-300"
                    )}
                  >
                    {line}
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Escalation Path Card */}
          {selectedRunbook?.escalationPath && (
            <Card className="p-5 bg-gray-900/80">
              <span className="text-[10px] font-mono uppercase tracking-wider text-gray-400 font-semibold">
                Escalation Hierarchy
              </span>
              <p className="text-xs text-gray-300 mt-1.5 leading-relaxed">
                {selectedRunbook.escalationPath}
              </p>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
