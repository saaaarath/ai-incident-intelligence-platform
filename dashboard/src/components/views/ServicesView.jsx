import React from 'react';
import { Layers, Server, Activity } from 'lucide-react';

export function ServicesView() {
  const services = [
    { name: 'order-service', status: 'healthy', port: 8081, type: 'Microservice' },
    { name: 'payment-service', status: 'healthy', port: 8082, type: 'Microservice' },
    { name: 'inventory-service', status: 'healthy', port: 8083, type: 'Microservice' },
    { name: 'log-processor', status: 'healthy', port: 8080, type: 'Incident Engine' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h3 style={{ fontSize: '1.1rem', fontWeight: 600 }}>Monitored Architecture & Services</h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
            Live microservices connected to the AI Incident Telemetry Pipeline
          </p>
        </div>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
        gap: '1rem'
      }}>
        {services.map((svc) => (
          <div key={svc.name} className="stat-card" style={{ gap: '0.75rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                <Server size={18} color="#818cf8" />
                <span style={{ fontWeight: 600 }}>{svc.name}</span>
              </div>
              <span className="badge badge-resolved">Operational</span>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              <span>Type: {svc.type}</span>
              <span>Port: {svc.port}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
