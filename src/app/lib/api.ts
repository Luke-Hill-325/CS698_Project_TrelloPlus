// API client for TrelloPlus backend
const API_BASE_URL = 'http://localhost:3000/api/v1';

// Helper to get auth token from localStorage
const getToken = () => localStorage.getItem('token');

// Generic fetch wrapper
async function apiClient(endpoint: string, options: RequestInit = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };
  
  // Add auth token if available
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  
  const response = await fetch(url, {
    ...options,
    headers,
  });
  
  const data = await response.json();
  
  if (!response.ok || !data.success) {
    throw new Error(data.error?.message || 'API request failed');
  }
  
  return data.data;
}

// Export API methods
export const api = {
  // Auth
  login: (email: string, password: string) => 
    apiClient('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  
  register: (email: string, password: string, firstName: string, lastName: string) =>
    apiClient('/auth/register', { method: 'POST', body: JSON.stringify({ email, password, firstName, lastName }) }),
  
  getCurrentUser: () => apiClient('/auth/me'),
  
  // AI
  generateBoardPreview: (projectName: string, description: string, templateHint?: string, stageCount?: number, cardsPerStage?: number) =>
    apiClient('/ai/generate-board', { method: 'POST', body: JSON.stringify({ projectName, description, templateHint, stageCount, cardsPerStage }) }),
  
  // Projects
  createProject: (data: any) => apiClient('/projects', { method: 'POST', body: JSON.stringify(data) }),
  getProjects: () => apiClient('/projects'),
  getProject: (id: string) => apiClient(`/projects/${id}`),
  updateProject: (id: string, data: any) => apiClient(`/projects/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteProject: (id: string) => apiClient(`/projects/${id}`, { method: 'DELETE' }),
  
  // Boards
  getBoard: (id: string) => apiClient(`/boards/${id}`),
  
  // Cards
  createCard: (data: any) => apiClient('/cards', { method: 'POST', body: JSON.stringify(data) }),
  getCard: (id: string) => apiClient(`/cards/${id}`),
  updateCard: (id: string, data: any) => apiClient(`/cards/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  moveCard: (id: string, stageId: string, orderIndex?: number) => 
    apiClient(`/cards/${id}/move`, { method: 'PUT', body: JSON.stringify({ stageId, orderIndex }) }),
};
