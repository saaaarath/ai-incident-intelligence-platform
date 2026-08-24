/**
 * API client for interacting with the AI-SRE backend service.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

async function request(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };

  try {
    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      let errorData;
      try {
        errorData = await response.json();
      } catch (e) {
        errorData = { error: response.statusText };
      }
      throw new ApiError(
        errorData.error || errorData.message || `Request failed with status ${response.status}`,
        response.status,
        errorData
      );
    }

    if (response.status === 204) {
      return null;
    }

    return await response.json();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(
      error.message || 'Network connection error. Is the backend server running?',
      0,
      null
    );
  }
}

export const incidentApi = {
  /**
   * Check backend availability / health.
   */
  async checkHealth() {
    try {
      const response = await fetch(`${API_BASE_URL}/actuator/health`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
      });
      return response.ok;
    } catch (e) {
      // Fallback: try fetching incidents list with limit 1
      try {
        const response = await fetch(`${API_BASE_URL}/incidents`, {
          method: 'GET',
          headers: { 'Accept': 'application/json' },
        });
        return response.ok;
      } catch {
        return false;
      }
    }
  },

  /**
   * Fetch list of incidents with optional filters.
   */
  async getIncidents(params = {}) {
    const query = new URLSearchParams();
    if (params.status) query.append('status', params.status);
    if (params.severity) query.append('severity', params.severity);
    if (params.service) query.append('service', params.service);
    if (params.fingerprint) query.append('fingerprint', params.fingerprint);
    if (params.from) query.append('from', params.from);
    if (params.to) query.append('to', params.to);

    const queryString = query.toString();
    const endpoint = `/incidents${queryString ? `?${queryString}` : ''}`;
    return await request(endpoint);
  },

  /**
   * Fetch an incident by its ID.
   */
  async getIncidentById(id) {
    return await request(`/incidents/${id}`);
  },

  /**
   * Fetch evidence records correlated with an incident.
   */
  async getIncidentEvidence(id) {
    return await request(`/incidents/${id}/evidence`);
  },

  /**
   * Fetch historically similar incidents for an incident.
   */
  async getSimilarIncidents(id, topK = 5) {
    return await request(`/incidents/${id}/similar?topK=${topK}`);
  },

  /**
   * Fetch relevant runbooks for an incident.
   */
  async getRelevantRunbooks(id, topK = 3) {
    return await request(`/incidents/${id}/runbooks?topK=${topK}`);
  },

  /**
   * Fetch full AI context for an incident.
   */
  async getIncidentContext(id, topK = 3) {
    return await request(`/incidents/${id}/context?topK=${topK}`);
  },

  /**
   * Acknowledge an incident (Transitions OPEN -> INVESTIGATING).
   */
  async acknowledgeIncident(id) {
    return await request(`/incidents/${id}/acknowledge`, { method: 'POST' });
  },

  /**
   * Resolve an incident (Transitions -> RESOLVED).
   */
  async resolveIncident(id) {
    return await request(`/incidents/${id}/resolve`, { method: 'POST' });
  },

  /**
   * Close an incident (Transitions -> CLOSED).
   */
  async closeIncident(id) {
    return await request(`/incidents/${id}/close`, { method: 'POST' });
  },

  /**
   * Create a new manual incident.
   */
  async createIncident(incidentData) {
    return await request('/incidents', {
      method: 'POST',
      body: JSON.stringify(incidentData),
    });
  },
};
