import React from 'react';
import { Server, Activity, CheckCircle2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../ui/card';
import { Badge } from '../ui/badge';
import { ServiceDependencyGraph } from '../dependencies/ServiceDependencyGraph';

export function ServicesView() {
  const services = [
    { name: 'order-service', status: 'healthy', port: 8081, type: 'Microservice' },
    { name: 'payment-service', status: 'healthy', port: 8082, type: 'Microservice' },
    { name: 'inventory-service', status: 'healthy', port: 8083, type: 'Microservice' },
    { name: 'log-processor', status: 'healthy', port: 8085, type: 'Incident Engine' },
  ];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h3 className="text-base font-semibold text-gray-100">Monitored Architecture & Topology</h3>
        <p className="text-xs text-gray-400 mt-0.5">
          Live microservices, inter-service dependency flow, and telemetry pipeline
        </p>
      </div>

      {/* Global Dependency Visualizer */}
      <ServiceDependencyGraph
        primaryService=""
        rootService=""
        affectedServices={[]}
      />

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {services.map((svc) => (
          <Card key={svc.name} className="hover:border-gray-700 transition-colors">
            <CardContent className="p-5 flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="p-1.5 rounded-md bg-indigo-500/10 text-indigo-400">
                    <Server className="h-4 w-4" />
                  </div>
                  <span className="font-semibold text-sm text-gray-100">{svc.name}</span>
                </div>
                <Badge variant="resolved" className="text-[10px]">Operational</Badge>
              </div>

              <div className="flex items-center justify-between text-xs text-gray-500 font-mono pt-2 border-t border-gray-800/60">
                <span>{svc.type}</span>
                <span>Port {svc.port}</span>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

