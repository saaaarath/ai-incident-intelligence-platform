import React, { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { incidentApi } from '../../services/api';
import { 
  Flame, 
  Database, 
  Clock, 
  AlertOctagon, 
  Radio, 
  GitCommit, 
  ShieldAlert, 
  ShieldCheck, 
  RotateCcw, 
  CheckCircle2, 
  AlertTriangle, 
  Loader2, 
  Zap, 
  Play, 
  Square,
  Lock,
  Unlock,
  Server
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function FailureInjectionSimulator({ onNavigateToIncident }) {
  const [demoMode, setDemoMode] = useState(true);
  const [activeScenarios, setActiveScenarios] = useState([]);
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [actionInProgress, setActionInProgress] = useState(null);
  const [feedback, setFeedback] = useState(null); // { type, message, title, incidentId }

  const scenarios = [
    {
      id: 'DB_CONNECTION_EXHAUSTION',
      title: 'DB Connection Pool Exhaustion',
      service: 'payment-service',
      port: 8082,
      category: 'DATABASE',
      icon: Database,
      color: 'rose',
      description: 'Exhausts HikariCP connection pool to 98% saturation with thread lock contention on PostgreSQL database.',
      metric: 'hikaricp_pending_threads',
      actionLabel: 'Inject DB Exhaustion',
      payload: {
        scenario: 'DB_CONNECTION_EXHAUSTION',
        service: 'payment-service',
        description: 'HikariCP connection pool capacity exhausted (98% saturation)'
      }
    },
    {
      id: 'PAYMENT_LATENCY',
      title: 'Payment Gateway Latency',
      service: 'payment-service',
      port: 8082,
      category: 'LATENCY',
      icon: Clock,
      color: 'amber',
      description: 'Injects 3,000ms response latency on checkout payment authorization endpoints causing upstream caller timeouts.',
      metric: 'http_request_duration_ms',
      actionLabel: 'Inject 3,000ms Latency',
      payload: {
        scenario: 'PAYMENT_LATENCY',
        service: 'payment-service',
        latencyMs: 3000,
        description: 'Injected 3000ms network latency on payment-service'
      }
    },
    {
      id: 'INVENTORY_FAILURE',
      title: 'Inventory Service Failure',
      service: 'inventory-service',
      port: 8083,
      category: 'SERVICE_UNAVAILABLE',
      icon: AlertOctagon,
      color: 'red',
      description: 'Forces inventory stock reservation endpoint to return HTTP 503 Service Unavailable with cache eviction degradation.',
      metric: 'cache_hit_ratio',
      actionLabel: 'Inject 503 Unavailable',
      payload: {
        scenario: 'INVENTORY_FAILURE',
        service: 'inventory-service',
        description: 'Inventory reservation returning 503 Service Unavailable'
      }
    },
    {
      id: 'ERROR_SPIKE',
      title: '500 Error Spike',
      service: 'order-service',
      port: 8081,
      category: 'ERROR_BURST',
      icon: Radio,
      color: 'orange',
      description: 'Floods transaction checkout endpoints with continuous HTTP 500 internal server exceptions.',
      metric: 'http_server_requests_5xx',
      actionLabel: 'Inject Error Spike',
      payload: {
        scenario: 'ERROR_SPIKE',
        service: 'order-service',
        description: 'High frequency 500 internal server error burst'
      }
    },
    {
      id: 'DEPLOYMENT_REGRESSION',
      title: 'Deployment Regression',
      service: 'payment-service',
      port: 8082,
      category: 'DEPLOYMENT',
      icon: GitCommit,
      color: 'sky',
      description: 'Publishes and activates v2.5.0 deployment with incompatible serialization configuration schema.',
      metric: 'deployment_version_mismatch',
      actionLabel: 'Deploy Faulty Version v2.5.0',
      payload: {
        scenario: 'DEPLOYMENT_REGRESSION',
        service: 'payment-service',
        version: 'v2.5.0-regression',
        description: 'Deployed v2.5.0 with config regression on payment-service'
      }
    }
  ];

  const fetchStatus = useCallback(async () => {
    try {
      const data = await incidentApi.getFailureStatus();
      if (data && data.activeScenarios) {
        setActiveScenarios(data.activeScenarios);
      }
    } catch (e) {
      console.warn('Could not fetch failure simulator status', e);
    } finally {
      setLoadingStatus(false);
    }
  }, []);

  useEffect(() => {
    fetchStatus();
    const timer = setInterval(fetchStatus, 5000);
    return () => clearInterval(timer);
  }, [fetchStatus]);

  const handleInjectFailure = async (scenarioObj) => {
    if (!demoMode) {
      setFeedback({
        type: 'error',
        title: 'Safety Protection Guardrail Active',
        message: 'Failure injection is disabled while Development/Demo Mode is OFF. Toggle Demo Mode ON to enable simulator controls.'
      });
      return;
    }

    setActionInProgress(scenarioObj.id);
    setFeedback({
      type: 'started',
      title: `Injecting Failure: ${scenarioObj.title}...`,
      message: `Dispatching failure control payload to ${scenarioObj.service}:${scenarioObj.port}...`
    });

    try {
      const res = await incidentApi.injectFailure(scenarioObj.payload, demoMode);
      setFeedback({
        type: 'success',
        title: 'Failure Scenario Activated & Incident Logged!',
        message: `${scenarioObj.title} is active on ${scenarioObj.service}. Anomaly signature created and logged to Incident Stream.`,
        incidentId: res?.incidentId
      });
      fetchStatus();
    } catch (err) {
      setFeedback({
        type: 'error',
        title: 'Failure Injection Failed',
        message: err.message || 'Error communicating with failure control API.'
      });
    } finally {
      setActionInProgress(null);
    }
  };

  const handleStopScenario = async (scenarioId) => {
    setActionInProgress(scenarioId);
    try {
      await incidentApi.disableFailure(scenarioId);
      setFeedback({
        type: 'success',
        title: 'Failure Scenario Disabled',
        message: `Scenario ${scenarioId} has been disabled and restored to healthy state.`
      });
      fetchStatus();
    } catch (err) {
      setFeedback({
        type: 'error',
        title: 'Failed to Stop Scenario',
        message: err.message || 'Error disabling failure scenario.'
      });
    } finally {
      setActionInProgress(null);
    }
  };

  const handleResetAll = async () => {
    setActionInProgress('RESET_ALL');
    setFeedback({
      type: 'started',
      title: 'Resetting All Failure Injections...',
      message: 'Restoring all microservices to nominal baseline operational telemetry...'
    });

    try {
      await incidentApi.resetAllFailures();
      setActiveScenarios([]);
      setFeedback({
        type: 'success',
        title: 'Platform Baseline Restored',
        message: 'All active failure injections cleared across all microservices.'
      });
      fetchStatus();
    } catch (err) {
      setFeedback({
        type: 'error',
        title: 'Reset Operation Failed',
        message: err.message || 'Error resetting failure states.'
      });
    } finally {
      setActionInProgress(null);
    }
  };

  return (
    <div className="flex flex-col gap-6 max-w-6xl mx-auto pb-12">
      {/* Header & Dev Protection Bar */}
      <div className="flex items-center justify-between flex-wrap gap-4 pb-4 border-b border-gray-800">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/20">
              <Flame className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-xl font-bold text-gray-100 tracking-tight">Failure Injection & Chaos Simulator</h2>
                <Badge variant="destructive" className="font-mono text-[10px] uppercase">
                  Dev / Demo Only
                </Badge>
              </div>
              <p className="text-xs text-gray-400 mt-0.5">
                Simulate realistic microservice failures, telemetry anomalies, and test automated RCA detection
              </p>
            </div>
          </div>
        </div>

        {/* Safety Gate Controls */}
        <div className="flex items-center gap-3">
          <div className={cn(
            "flex items-center gap-2.5 px-3 py-1.5 rounded-lg border text-xs font-mono transition-all",
            demoMode 
              ? "bg-amber-950/30 border-amber-500/40 text-amber-300" 
              : "bg-gray-950 border-gray-800 text-gray-400"
          )}>
            {demoMode ? (
              <>
                <Unlock className="h-4 w-4 text-amber-400" />
                <span>Demo Protection: <strong>UNLOCKED</strong></span>
              </>
            ) : (
              <>
                <Lock className="h-4 w-4 text-gray-500" />
                <span>Demo Protection: <strong>LOCKED</strong></span>
              </>
            )}
            <button
              onClick={() => setDemoMode(!demoMode)}
              className="ml-2 px-2 py-0.5 rounded bg-gray-800 hover:bg-gray-700 text-[11px] text-gray-200 cursor-pointer"
            >
              {demoMode ? 'Lock' : 'Unlock'}
            </button>
          </div>

          {activeScenarios.length > 0 && (
            <Button
              variant="destructive"
              size="sm"
              onClick={handleResetAll}
              disabled={actionInProgress !== null}
              className="gap-1.5 text-xs font-mono"
            >
              <RotateCcw className="h-3.5 w-3.5" />
              <span>Stop All ({activeScenarios.length})</span>
            </Button>
          )}
        </div>
      </div>

      {/* Real-Time Action Feedback Banner */}
      {feedback && (
        <div className={cn(
          "p-4 rounded-xl border flex items-start justify-between gap-3 text-xs animate-in fade-in duration-200",
          feedback.type === 'success' && "bg-emerald-950/30 border-emerald-500/40 text-emerald-300",
          feedback.type === 'error' && "bg-rose-950/30 border-rose-500/40 text-rose-300",
          feedback.type === 'started' && "bg-indigo-950/30 border-indigo-500/40 text-indigo-300"
        )}>
          <div className="flex items-start gap-2.5">
            {feedback.type === 'success' && <CheckCircle2 className="h-5 w-5 text-emerald-400 shrink-0 mt-0.5" />}
            {feedback.type === 'error' && <AlertTriangle className="h-5 w-5 text-rose-400 shrink-0 mt-0.5" />}
            {feedback.type === 'started' && <Loader2 className="h-5 w-5 text-indigo-400 shrink-0 mt-0.5 animate-spin" />}
            <div>
              <h4 className="font-semibold text-sm">{feedback.title}</h4>
              <p className="mt-0.5 text-gray-300 leading-relaxed font-mono text-[11px]">{feedback.message}</p>
              {feedback.incidentId && (
                <div className="mt-2 flex items-center gap-2">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => onNavigateToIncident && onNavigateToIncident(feedback.incidentId)}
                    className="h-7 text-[11px] font-mono gap-1.5 bg-emerald-500/20 text-emerald-300 hover:bg-emerald-500/30 border border-emerald-500/40 cursor-pointer"
                  >
                    <span>Investigate Incident INC-{feedback.incidentId} in Dashboard &rarr;</span>
                  </Button>
                </div>
              )}
            </div>
          </div>
          <button 
            onClick={() => setFeedback(null)}
            className="text-gray-500 hover:text-gray-300 underline font-mono text-[11px] cursor-pointer"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Active Failures Status Bar */}
      <Card className="p-5 bg-gray-900/90 border-gray-800">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <ShieldAlert className="h-4 w-4 text-rose-400" />
            <h3 className="text-sm font-semibold text-gray-100 uppercase tracking-wider font-mono">
              Active Injected Failures
            </h3>
          </div>
          <Badge 
            variant={activeScenarios.length > 0 ? "critical" : "resolved"}
            className="font-mono text-xs"
          >
            {activeScenarios.length} Scenario{activeScenarios.length !== 1 ? 's' : ''} Active
          </Badge>
        </div>

        {activeScenarios.length === 0 ? (
          <div className="p-6 rounded-lg bg-gray-950/50 border border-dashed border-gray-800 flex items-center justify-center gap-2 text-xs text-gray-500 font-mono text-center">
            <CheckCircle2 className="h-4 w-4 text-emerald-400" />
            <span>No active failures currently injected. All microservices operating at nominal baseline.</span>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {activeScenarios.map((act) => (
              <div 
                key={act.id}
                className="p-3.5 rounded-lg bg-rose-950/20 border border-rose-500/30 flex items-center justify-between gap-3"
              >
                <div className="flex flex-col gap-0.5">
                  <div className="flex items-center gap-2">
                    <span className="h-2 w-2 rounded-full bg-rose-400 animate-ping" />
                    <span className="font-mono text-xs font-bold text-gray-100">{act.scenario}</span>
                    <Badge variant="outline" className="text-[10px] font-mono border-rose-500/30 text-rose-300">
                      {act.service}
                    </Badge>
                  </div>
                  <span className="text-[11px] text-gray-400 mt-0.5">{act.description}</span>
                </div>

                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => handleStopScenario(act.id)}
                  disabled={actionInProgress === act.id}
                  className="shrink-0 text-xs border-rose-500/40 text-rose-300 hover:bg-rose-950/50 cursor-pointer"
                >
                  {actionInProgress === act.id ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    'Disable'
                  )}
                </Button>
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* 5 Failure Scenario Action Cards */}
      <div>
        <div className="mb-4">
          <h3 className="text-base font-semibold text-gray-100">Simulate Failure Scenarios</h3>
          <p className="text-xs text-gray-400 mt-0.5">
            Trigger fault injection into live microservices to demonstrate automated anomaly detection, incident creation, and RCA synthesis
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {scenarios.map((scen) => {
            const Icon = scen.icon;
            const isInjecting = actionInProgress === scen.id;
            const isActive = activeScenarios.some(a => a.scenario === scen.id);

            return (
              <Card 
                key={scen.id}
                className={cn(
                  "p-5 flex flex-col justify-between gap-4 transition-all duration-200 hover:border-gray-700 bg-gray-900/80",
                  isActive && "border-rose-500/50 bg-rose-950/10 ring-1 ring-rose-500/30"
                )}
              >
                <div className="flex flex-col gap-3">
                  {/* Card Header */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2.5">
                      <div className="p-2 rounded-lg bg-gray-950 border border-gray-800 text-indigo-400">
                        <Icon className="h-5 w-5" />
                      </div>
                      <div>
                        <h4 className="font-semibold text-sm text-gray-100 leading-tight">
                          {scen.title}
                        </h4>
                        <div className="flex items-center gap-1.5 mt-1">
                          <Badge variant="outline" className="text-[10px] font-mono border-gray-700 text-gray-400">
                            {scen.service}
                          </Badge>
                          <span className="text-[10px] font-mono text-gray-500">:{scen.port}</span>
                        </div>
                      </div>
                    </div>

                    {isActive && (
                      <span className="h-2.5 w-2.5 rounded-full bg-rose-400 animate-ping shrink-0 mt-1" />
                    )}
                  </div>

                  {/* Description */}
                  <p className="text-xs text-gray-300 leading-relaxed min-h-[48px]">
                    {scen.description}
                  </p>

                  {/* Monitored Metric Target */}
                  <div className="flex items-center gap-1.5 text-[11px] font-mono text-gray-400 bg-gray-950/60 p-2 rounded border border-gray-800/80">
                    <span className="text-gray-500 font-sans font-medium">Metric:</span>
                    <span className="text-indigo-300 truncate">{scen.metric}</span>
                  </div>
                </div>

                {/* Action Button */}
                <Button
                  variant={isActive ? "destructive" : "default"}
                  size="sm"
                  onClick={() => handleInjectFailure(scen)}
                  disabled={!demoMode || isInjecting || actionInProgress !== null}
                  className="w-full gap-2 text-xs font-mono cursor-pointer"
                >
                  {isInjecting ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span>Injecting...</span>
                    </>
                  ) : isActive ? (
                    <>
                      <Flame className="h-3.5 w-3.5" />
                      <span>Active (Re-inject)</span>
                    </>
                  ) : (
                    <>
                      <Play className="h-3.5 w-3.5" />
                      <span>{scen.actionLabel}</span>
                    </>
                  )}
                </Button>
              </Card>
            );
          })}
        </div>
      </div>
    </div>
  );
}
