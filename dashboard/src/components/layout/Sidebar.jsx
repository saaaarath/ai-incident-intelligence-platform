import React from 'react';
import { 
  Activity, 
  Flame, 
  Layers, 
  BookOpen, 
  Cpu
} from 'lucide-react';
import { cn } from '../../lib/utils';

export function Sidebar({ currentView, onViewChange, openIncidentsCount = 0, backendStatus = 'checking' }) {
  const navItems = [
    { id: 'incidents', label: 'Incident Stream', icon: Flame, badge: openIncidentsCount > 0 ? openIncidentsCount : null },
    { id: 'metrics', label: 'Telemetry & Services', icon: Layers },
    { id: 'knowledge', label: 'Runbooks & Knowledge', icon: BookOpen },
    { id: 'rca', label: 'AI Root Cause Analysis', icon: Cpu },
  ];

  return (
    <aside className="w-64 shrink-0 bg-gray-900/90 border-r border-gray-800 flex flex-col h-screen backdrop-blur-md select-none">
      {/* Brand Header */}
      <div className="h-16 px-6 border-b border-gray-800 flex items-center gap-3">
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 text-white shadow-md shadow-indigo-500/20">
          <Activity className="h-5 w-5" />
        </div>
        <div>
          <h1 className="text-sm font-bold tracking-tight text-gray-100">AI-SRE Platform</h1>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-gray-400">Incident Intelligence</p>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentView === item.id;
          return (
            <button
              key={item.id}
              className={cn(
                "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 cursor-pointer text-left",
                isActive
                  ? "bg-indigo-600/15 text-indigo-300 border border-indigo-500/30 shadow-xs"
                  : "text-gray-400 hover:text-gray-200 hover:bg-gray-800/60 border border-transparent"
              )}
              onClick={() => onViewChange(item.id)}
            >
              <Icon className={cn("h-4 w-4 shrink-0", isActive ? "text-indigo-400" : "text-gray-400")} />
              <span className="flex-1 truncate">{item.label}</span>
              {item.badge !== null && item.badge !== undefined && (
                <span className={cn(
                  "ml-auto text-xs px-2 py-0.5 rounded-full font-mono font-semibold",
                  isActive ? "bg-indigo-500/30 text-indigo-200" : "bg-gray-800 text-gray-400"
                )}>
                  {item.badge}
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* Footer Backend Status */}
      <div className="p-4 border-t border-gray-800 bg-gray-950/40">
        <div className="flex items-center gap-2 text-xs text-gray-400">
          <span className={cn(
            "h-2.5 w-2.5 rounded-full",
            backendStatus === 'online' && "bg-emerald-500 shadow-xs shadow-emerald-500",
            backendStatus === 'offline' && "bg-rose-500 shadow-xs shadow-rose-500",
            backendStatus === 'checking' && "bg-amber-500 animate-pulse-dot"
          )} />
          <span className="font-medium">
            {backendStatus === 'online' && 'Backend Connected'}
            {backendStatus === 'offline' && 'Backend Offline'}
            {backendStatus === 'checking' && 'Connecting to API...'}
          </span>
        </div>
      </div>
    </aside>
  );
}
