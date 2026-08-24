import React, { useState } from 'react';
import { Card } from '../ui/card';
import { Badge } from '../ui/badge';
import { 
  Server, 
  Database, 
  ArrowDown, 
  ArrowRight, 
  AlertTriangle, 
  CheckCircle2, 
  Activity, 
  Layers,
  Zap,
  Info,
  ShieldAlert
} from 'lucide-react';
import { cn } from '../../lib/utils';

/**
 * ServiceDependencyGraph Component
 * Visualizes the microservice call chain:
 * Order -> Payment -> Inventory
 * and Payment -> PostgreSQL
 * 
 * Accurately highlights the root cause failure origin vs symptom propagation.
 */
export function ServiceDependencyGraph({
  primaryService = '',
  rootService = '',
  affectedServices = [],
  metric = '',
  severity = 'HIGH'
}) {
  const [selectedNode, setSelectedNode] = useState(null);

  const root = (rootService || primaryService || '').toLowerCase().trim();
  const affectedList = (affectedServices || []).map(s => (s || '').toLowerCase().trim());
  if (primaryService && !affectedList.includes(primaryService.toLowerCase().trim())) {
    affectedList.push(primaryService.toLowerCase().trim());
  }

  // Node role evaluator
  const getNodeState = (serviceName) => {
    const name = serviceName.toLowerCase().trim();
    const isRoot = name === root;
    const isAffected = isRoot || affectedList.includes(name);

    if (isRoot) {
      return {
        role: 'ROOT_CAUSE',
        label: 'Primary Failure Origin',
        badgeVariant: 'destructive',
        borderColor: 'border-rose-500 shadow-lg shadow-rose-950/50 ring-2 ring-rose-500/40 bg-rose-950/30',
        textColor: 'text-rose-300',
        iconColor: 'text-rose-400',
        statusText: 'Fault Localized Here',
        statusBg: 'bg-rose-500/20 text-rose-300 border-rose-500/30',
      };
    }

    if (isAffected) {
      return {
        role: 'SYMPTOM',
        label: 'Symptom Cascade',
        badgeVariant: 'high',
        borderColor: 'border-amber-500/80 shadow-md shadow-amber-950/30 ring-1 ring-amber-500/30 bg-amber-950/20',
        textColor: 'text-amber-300',
        iconColor: 'text-amber-400',
        statusText: 'Degraded / Impacted',
        statusBg: 'bg-amber-500/20 text-amber-300 border-amber-500/30',
      };
    }

    return {
      role: 'HEALTHY',
      label: 'Operational',
      badgeVariant: 'resolved',
      borderColor: 'border-gray-800 bg-gray-900/90 hover:border-gray-700',
      textColor: 'text-gray-200',
      iconColor: 'text-emerald-400',
      statusText: 'Normal Operation',
      statusBg: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    };
  };

  const orderState = getNodeState('order-service');
  const paymentState = getNodeState('payment-service');
  const inventoryState = getNodeState('inventory-service');
  const postgresState = getNodeState('postgres');

  return (
    <Card className="p-6 bg-gray-900/90 border-gray-800 flex flex-col gap-6">
      {/* Graph Header & Legend */}
      <div className="flex items-center justify-between flex-wrap gap-4 pb-4 border-b border-gray-800">
        <div>
          <div className="flex items-center gap-2">
            <Layers className="h-4 w-4 text-indigo-400" />
            <h3 className="text-base font-semibold text-gray-100">Service Dependency Call Chain</h3>
          </div>
          <p className="text-xs text-gray-400 mt-0.5">
            Topology trace visualizing fault localization and downstream symptom cascade
          </p>
        </div>

        {/* Legend */}
        <div className="flex items-center gap-3 text-xs flex-wrap font-mono">
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-rose-950/40 border border-rose-500/30 text-rose-300">
            <span className="h-2 w-2 rounded-full bg-rose-400 animate-pulse" />
            <span>Primary Root Cause</span>
          </div>
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-amber-950/40 border border-amber-500/30 text-amber-300">
            <span className="h-2 w-2 rounded-full bg-amber-400" />
            <span>Symptom Cascade</span>
          </div>
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-emerald-950/30 border border-emerald-500/20 text-emerald-300">
            <span className="h-2 w-2 rounded-full bg-emerald-400" />
            <span>Healthy / Operational</span>
          </div>
        </div>
      </div>

      {/* Visual Graph Layout */}
      <div className="py-4 px-2">
        <div className="max-w-3xl mx-auto flex flex-col items-center">
          
          {/* LEVEL 1: ORDER SERVICE */}
          <div className="w-full max-w-md">
            <div 
              onClick={() => setSelectedNode('order-service')}
              className={cn(
                "p-4 rounded-xl border transition-all cursor-pointer flex items-center justify-between gap-4",
                orderState.borderColor,
                selectedNode === 'order-service' && "ring-2 ring-indigo-400"
              )}
            >
              <div className="flex items-center gap-3">
                <div className={cn("p-2.5 rounded-lg bg-gray-950/80 border border-gray-800", orderState.iconColor)}>
                  <Server className="h-5 w-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold text-sm text-gray-100">order-service</span>
                    <span className="text-[10px] font-mono text-gray-500">:8081</span>
                  </div>
                  <span className="text-xs text-gray-400">Order placement & checkout gateway</span>
                </div>
              </div>

              <div className="flex flex-col items-end gap-1">
                <span className={cn("text-[10px] font-mono px-2 py-0.5 rounded-full border", orderState.statusBg)}>
                  {orderState.label}
                </span>
                {orderState.role === 'ROOT_CAUSE' && metric && (
                  <span className="text-[10px] font-mono text-rose-400 font-semibold truncate max-w-[140px]">
                    {metric}
                  </span>
                )}
              </div>
            </div>
          </div>

          {/* CONNECTOR 1: Order -> Payment */}
          <div className="flex flex-col items-center my-1 relative">
            <div className="h-8 w-0.5 bg-gradient-to-b from-indigo-500/60 to-indigo-500/80" />
            <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-gray-950 border border-gray-800 text-[10px] font-mono text-indigo-300 shadow-sm">
              <span>HTTP REST (POST /pay)</span>
            </div>
            <ArrowDown className="h-4 w-4 text-indigo-400 -mt-0.5" />
          </div>

          {/* LEVEL 2: PAYMENT SERVICE & POSTGRES BRANCH */}
          <div className="w-full flex flex-col md:flex-row items-center justify-center gap-4 relative">
            
            {/* Payment Service Node */}
            <div className="w-full max-w-md">
              <div 
                onClick={() => setSelectedNode('payment-service')}
                className={cn(
                  "p-4 rounded-xl border transition-all cursor-pointer flex items-center justify-between gap-4",
                  paymentState.borderColor,
                  selectedNode === 'payment-service' && "ring-2 ring-indigo-400"
                )}
              >
                <div className="flex items-center gap-3">
                  <div className={cn("p-2.5 rounded-lg bg-gray-950/80 border border-gray-800", paymentState.iconColor)}>
                    <Server className="h-5 w-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-mono font-bold text-sm text-gray-100">payment-service</span>
                      <span className="text-[10px] font-mono text-gray-500">:8082</span>
                    </div>
                    <span className="text-xs text-gray-400">Payment authorization & ledger</span>
                  </div>
                </div>

                <div className="flex flex-col items-end gap-1">
                  <span className={cn("text-[10px] font-mono px-2 py-0.5 rounded-full border", paymentState.statusBg)}>
                    {paymentState.label}
                  </span>
                  {paymentState.role === 'ROOT_CAUSE' && metric && (
                    <span className="text-[10px] font-mono text-rose-400 font-semibold truncate max-w-[140px]">
                      {metric}
                    </span>
                  )}
                </div>
              </div>
            </div>

            {/* Horizontal Branch: Payment -> PostgreSQL */}
            <div className="flex items-center gap-2 md:pl-2">
              <div className="hidden md:flex items-center">
                <div className="w-6 h-0.5 bg-purple-500/60" />
                <div className="px-1.5 py-0.5 rounded bg-gray-950 border border-purple-500/30 text-[9px] font-mono text-purple-300">
                  JDBC
                </div>
                <ArrowRight className="h-3.5 w-3.5 text-purple-400 -ml-0.5" />
              </div>

              {/* PostgreSQL Node */}
              <div 
                onClick={() => setSelectedNode('postgres')}
                className={cn(
                  "p-3.5 rounded-xl border transition-all cursor-pointer flex items-center gap-3 shrink-0 min-w-[200px]",
                  postgresState.borderColor,
                  selectedNode === 'postgres' && "ring-2 ring-indigo-400"
                )}
              >
                <div className={cn("p-2 rounded-lg bg-gray-950/80 border border-gray-800", postgresState.iconColor)}>
                  <Database className="h-4 w-4" />
                </div>
                <div>
                  <div className="flex items-center gap-1.5">
                    <span className="font-mono font-bold text-xs text-gray-100">PostgreSQL</span>
                    <span className="text-[9px] font-mono text-gray-500">:5432</span>
                  </div>
                  <span className="text-[11px] text-gray-400">Database Cluster</span>
                </div>
              </div>
            </div>
          </div>

          {/* CONNECTOR 2: Payment -> Inventory */}
          <div className="flex flex-col items-center my-1 relative">
            <div className="h-8 w-0.5 bg-gradient-to-b from-indigo-500/60 to-indigo-500/80" />
            <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-gray-950 border border-gray-800 text-[10px] font-mono text-indigo-300 shadow-sm">
              <span>HTTP REST (POST /reserve)</span>
            </div>
            <ArrowDown className="h-4 w-4 text-indigo-400 -mt-0.5" />
          </div>

          {/* LEVEL 3: INVENTORY SERVICE */}
          <div className="w-full max-w-md">
            <div 
              onClick={() => setSelectedNode('inventory-service')}
              className={cn(
                "p-4 rounded-xl border transition-all cursor-pointer flex items-center justify-between gap-4",
                inventoryState.borderColor,
                selectedNode === 'inventory-service' && "ring-2 ring-indigo-400"
              )}
            >
              <div className="flex items-center gap-3">
                <div className={cn("p-2.5 rounded-lg bg-gray-950/80 border border-gray-800", inventoryState.iconColor)}>
                  <Server className="h-5 w-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold text-sm text-gray-100">inventory-service</span>
                    <span className="text-[10px] font-mono text-gray-500">:8083</span>
                  </div>
                  <span className="text-xs text-gray-400">Stock reservation & catalog cache</span>
                </div>
              </div>

              <div className="flex flex-col items-end gap-1">
                <span className={cn("text-[10px] font-mono px-2 py-0.5 rounded-full border", inventoryState.statusBg)}>
                  {inventoryState.label}
                </span>
                {inventoryState.role === 'ROOT_CAUSE' && metric && (
                  <span className="text-[10px] font-mono text-rose-400 font-semibold truncate max-w-[140px]">
                    {metric}
                  </span>
                )}
              </div>
            </div>
          </div>

        </div>
      </div>

      {/* Selected Node Details Card */}
      {selectedNode && (
        <div className="p-4 rounded-xl bg-gray-950/80 border border-gray-800 flex items-start justify-between gap-4 animate-in fade-in duration-200">
          <div className="flex flex-col gap-1 text-xs">
            <div className="flex items-center gap-2">
              <Info className="h-4 w-4 text-indigo-400" />
              <span className="font-semibold text-gray-200 uppercase font-mono tracking-wider">
                Node Inspection: {selectedNode}
              </span>
            </div>
            <p className="text-gray-400 mt-1">
              {selectedNode === root ? (
                <span className="text-rose-300 font-medium">
                  Identified as the origin of the primary failure signature ({metric || 'Operational degradation'}). Downstream and upstream callers are propagating latency or errors from this node.
                </span>
              ) : affectedList.includes(selectedNode) ? (
                <span className="text-amber-300 font-medium">
                  Experiencing cascade symptoms (timeouts or elevated 5xx error rate) due to upstream or downstream dependencies.
                </span>
              ) : (
                <span className="text-emerald-300 font-medium">
                  Service telemetry is within nominal operating bounds.
                </span>
              )}
            </p>
          </div>
          <button 
            onClick={() => setSelectedNode(null)}
            className="text-xs text-gray-500 hover:text-gray-300 font-mono underline cursor-pointer"
          >
            Dismiss
          </button>
        </div>
      )}
    </Card>
  );
}
