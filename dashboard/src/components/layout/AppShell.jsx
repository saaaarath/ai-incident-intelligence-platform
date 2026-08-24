import React from 'react';
import { Sidebar } from './Sidebar';
import { Navbar } from './Navbar';

export function AppShell({
  currentView,
  onViewChange,
  viewTitle,
  onRefresh,
  isRefreshing,
  openIncidentsCount,
  activeIncidentsCount,
  backendStatus,
  children
}) {
  return (
    <div className="flex h-screen w-screen overflow-hidden bg-gray-950 text-gray-100 antialiased">
      <Sidebar
        currentView={currentView}
        onViewChange={onViewChange}
        openIncidentsCount={openIncidentsCount}
        backendStatus={backendStatus}
      />
      <div className="flex-1 flex flex-col overflow-hidden bg-gray-950">
        <Navbar
          title={viewTitle}
          onRefresh={onRefresh}
          isRefreshing={isRefreshing}
          activeIncidentsCount={activeIncidentsCount}
        />
        <main className="flex-1 overflow-y-auto p-8 flex flex-col gap-6">
          {children}
        </main>
      </div>
    </div>
  );
}
