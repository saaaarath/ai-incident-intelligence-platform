import React from 'react';
import { Card, CardContent } from '../ui/card';
import { Badge } from '../ui/badge';
import { Button } from '../ui/button';
import { 
  Server, 
  ArrowRight, 
  ArrowLeft, 
  Database, 
  Layers, 
  Network, 
  CheckCircle2, 
  AlertCircle,
  Loader2,
  AlertTriangle
} from 'lucide-react';

export function IncidentDependencyView({
  incident,
  topology,
  loading,
  error,
  onRetry
}) {
  if (loading) {
    return (
      <Card className="p-12 border-dashed border-gray-800 bg-gray-900/40 flex flex-col items-center justify-center text-center">
        <Loader2 className="h-8 w-8 text-indigo-500 animate-spin mb-3" />
        <p className="text-sm text-gray-400">Loading service topology and dependency graph...</p>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className="p-8 border-red-500/30 bg-red-950/10 flex flex-col items-center justify-center text-center">
        <AlertTriangle className="h-10 w-10 text-red-400 mb-3" />
        <h3 className="text-base font-semibold text-gray-100">Failed to Load Dependency Topology</h3>
        <p className="text-sm text-gray-400 mt-1 max-w-md">{error}</p>
        {onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry} className="mt-4">
            Retry
          </Button>
        )}
      </Card>
    );
  }

  const affectedServices = incident?.affectedServices ? Array.from(incident.affectedServices) : (incident?.primaryService ? [incident.primaryService] : []);
  const rootService = incident?.rootService || incident?.primaryService;
  const currentService = (incident?.primaryService || '').toLowerCase().trim();

  // Extract structured upstream callers (sourceService -> currentService)
  const upstream = React.useMemo(() => {
    if (!topology) return [];
    if (topology.directDependencies && Array.isArray(topology.directDependencies)) {
      return topology.directDependencies.filter(
        d => d.targetService?.toLowerCase() === currentService
      );
    }
    if (topology.upstream && Array.isArray(topology.upstream)) {
      return topology.upstream.map(name => ({
        sourceService: name,
        targetService: currentService,
        dependencyType: 'HTTP_REST'
      }));
    }
    return topology.upstreamCallers || [];
  }, [topology, currentService]);

  // Extract structured downstream dependencies (currentService -> targetService)
  const downstream = React.useMemo(() => {
    if (!topology) return [];
    if (topology.directDependencies && Array.isArray(topology.directDependencies)) {
      return topology.directDependencies.filter(
        d => d.sourceService?.toLowerCase() === currentService
      );
    }
    if (topology.downstream && Array.isArray(topology.downstream)) {
      return topology.downstream.map(name => ({
        sourceService: currentService,
        targetService: name,
        dependencyType: name.includes('postgres') || name.includes('database') ? 'DATABASE' : 'HTTP_REST'
      }));
    }
    return topology.downstreamDependencies || [];
  }, [topology, currentService]);

  return (
    <div className="flex flex-col gap-6">
      {/* Affected Services Breakdown */}
      <div>
        <h3 className="text-base font-semibold text-gray-100">Affected Services & Impact Scope</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Fault origin service versus downstream symptom propagation
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
          {/* Primary / Root Service */}
          <Card className="p-5 border-indigo-500/30 bg-indigo-950/20">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-indigo-400">
                Primary Root Service
              </span>
              <Badge variant="destructive" className="text-[10px]">Root Cause</Badge>
            </div>
            <div className="flex items-center gap-2 mt-2">
              <Server className="h-5 w-5 text-indigo-400" />
              <span className="font-mono text-base font-bold text-gray-100">{rootService || 'Unknown'}</span>
            </div>
            <p className="text-xs text-gray-400 mt-2">
              Source service where the primary anomaly or failure signature originated.
            </p>
          </Card>

          {/* All Affected Services */}
          <Card className="p-5 bg-gray-900/80 sm:col-span-2">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-gray-400">
                Correlated Impacted Services ({affectedServices.length})
              </span>
            </div>
            <div className="flex items-center gap-2 flex-wrap mt-2">
              {affectedServices.map((svc) => (
                <div key={svc} className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-gray-950 border border-gray-800 font-mono text-xs text-gray-200">
                  <Server className="h-3.5 w-3.5 text-gray-500" />
                  <span>{svc}</span>
                  {svc === rootService ? (
                    <span className="text-[10px] text-rose-400 ml-1 font-sans font-bold">(Root)</span>
                  ) : (
                    <span className="text-[10px] text-amber-400 ml-1 font-sans">(Symptom)</span>
                  )}
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>

      {/* Dependency Topology Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Upstream Callers */}
        <Card className="p-6 bg-gray-900/80">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <ArrowLeft className="h-4 w-4 text-sky-400" />
              <h4 className="text-sm font-semibold text-gray-100">Upstream Callers (Depends on this service)</h4>
            </div>
            <Badge variant="outline" className="font-mono text-xs">{upstream.length}</Badge>
          </div>

          {upstream.length === 0 ? (
            <div className="p-6 rounded-lg bg-gray-950/40 border border-dashed border-gray-800 text-center text-xs text-gray-500">
              No registered upstream callers for {incident?.primaryService}.
            </div>
          ) : (
            <div className="flex flex-col gap-2.5">
              {upstream.map((dep, idx) => (
                <div key={idx} className="p-3 rounded-lg bg-gray-950/60 border border-gray-800 flex items-center justify-between">
                  <div className="flex items-center gap-2 font-mono text-xs text-gray-200">
                    <Server className="h-3.5 w-3.5 text-sky-400" />
                    <span>{dep.sourceService}</span>
                  </div>
                  <Badge variant="outline" className="text-[10px] font-mono border-gray-700">
                    {dep.dependencyType || 'HTTP_REST'}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Downstream Dependencies */}
        <Card className="p-6 bg-gray-900/80">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <ArrowRight className="h-4 w-4 text-purple-400" />
              <h4 className="text-sm font-semibold text-gray-100">Downstream Dependencies (Called by this service)</h4>
            </div>
            <Badge variant="outline" className="font-mono text-xs">{downstream.length}</Badge>
          </div>

          {downstream.length === 0 ? (
            <div className="p-6 rounded-lg bg-gray-950/40 border border-dashed border-gray-800 text-center text-xs text-gray-500">
              No downstream dependencies registered for {incident?.primaryService}.
            </div>
          ) : (
            <div className="flex flex-col gap-2.5">
              {downstream.map((dep, idx) => (
                <div key={idx} className="p-3 rounded-lg bg-gray-950/60 border border-gray-800 flex items-center justify-between">
                  <div className="flex items-center gap-2 font-mono text-xs text-gray-200">
                    {dep.targetService.includes('postgres') || dep.targetService.includes('database') ? (
                      <Database className="h-3.5 w-3.5 text-purple-400" />
                    ) : (
                      <Server className="h-3.5 w-3.5 text-purple-400" />
                    )}
                    <span>{dep.targetService}</span>
                  </div>
                  <Badge variant="outline" className="text-[10px] font-mono border-gray-700">
                    {dep.dependencyType || 'HTTP_REST'}
                  </Badge>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
