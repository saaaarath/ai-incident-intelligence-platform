# AI Incident Intelligence Dashboard

A modern React + Vite operations dashboard for the AI Incident Intelligence Platform.

## Features
- **Application Shell**: Modern SRE UI theme with responsive sidebar navigation and live backend status indicator.
- **Incident Stream**: Real-time incident list with severity filtering (Critical, High, Medium, Low) and status filtering (Open, Investigating, Resolved, Closed).
- **Incident Details Drawer**: Correlated anomaly evidence, metadata breakdown, and lifecycle workflow transitions (Acknowledge, Resolve, Close).
- **Resilient States**: Loading spinners, graceful connection error alerts with retry triggers, and clean empty states.
- **API Client & Dev Proxy**: Pre-configured Vite proxy forwarding requests to the Spring Boot incident engine at `http://localhost:8080`.

## Quickstart

### 1. Install Dependencies
```bash
npm install
```

### 2. Run in Development Mode
```bash
npm run dev
```
The dashboard will start on `http://localhost:5173`.

### 3. Build for Production
```bash
npm run build
```
