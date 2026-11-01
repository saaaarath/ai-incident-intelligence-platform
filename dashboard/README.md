# AI Incident Intelligence Dashboard

A modern React + Vite operations dashboard for the AI Incident Intelligence Platform.

## Features
- **Application Shell**: Modern SRE UI theme with responsive sidebar navigation and live backend status indicator.
- **Incident Stream**: Real-time incident list with severity filtering (Critical, High, Medium, Low) and status filtering (Open, Investigating, Resolved, Closed).
- **Incident Details Drawer**: Correlated anomaly evidence, metadata breakdown, and lifecycle workflow transitions (Acknowledge, Resolve, Close).
- **Resilient States**: Loading spinners, graceful connection error alerts with retry triggers, and clean empty states.
- **API Client & Dev Proxy**: Pre-configured Vite proxy forwarding requests to the Spring Boot incident engine at `http://localhost:18084` (the default Docker Compose host port).

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

The dashboard expects the log processor at `http://localhost:18084` by default. If
you run the log processor directly with Spring Boot (which defaults to port
`8085`), start Vite with:

```bash
VITE_BACKEND_URL=http://localhost:8085 npm run dev
```

### 3. Build for Production
```bash
npm run build
```
