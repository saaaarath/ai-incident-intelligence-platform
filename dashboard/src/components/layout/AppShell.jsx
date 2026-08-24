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
    <div className="app-layout">
      <Sidebar
        currentView={currentView}
        onViewChange={onViewChange}
        openIncidentsCount={openIncidentsCount}
        backendStatus={backendStatus}
      />
      <div className="main-area">
        <Navbar
          title={viewTitle}
          onRefresh={onRefresh}
          isRefreshing={isRefreshing}
          activeIncidentsCount={activeIncidentsCount}
        />
        <main className="content-container">
          {children}
        </main>
      </div>
    </div>
  );
}
