/**
 * API Adapter Layer
 * 
 * Transforms backend API responses to match frontend model expectations.
 * This allows the frontend to work with the existing backend without
 * major refactoring.
 */

import { api } from './api';
import type { 
  Project, 
  BoardColumn, 
  BoardTask, 
  ProjectMember,
  ProjectDecision 
} from '../store/projectStore';

// Backend types (from API responses)
interface BackendProject {
  id: string;
  name: string;
  description?: string;
  userId: string;
  boards?: BackendBoard[];
  createdAt: string;
  updatedAt: string;
}

interface BackendBoard {
  id: string;
  projectId: string;
  name: string;
  description?: string;
  template?: string;
  stages: BackendStage[];
  settings?: {
    showCardLabels: boolean;
    showCardAssignees: boolean;
    showCardDueDate: boolean;
    showCardPriority: boolean;
    defaultCardType: string;
  };
  createdAt: string;
  updatedAt: string;
}

interface BackendStage {
  id: string;
  boardId: string;
  name: string;
  orderIndex: number;
  color?: string;
  wipLimit?: number;
  cards: BackendCard[];
  createdAt: string;
  updatedAt: string;
}

interface BackendCard {
  id: string;
  stageId: string;
  title: string;
  description?: string;
  type: string;
  priority: 'low' | 'medium' | 'high' | 'critical';
  status: string;
  orderIndex: number;
  dueDate?: string;
  estimatedHours?: number;
  actualHours?: number;
  tags: string[];
  assignedTo?: string;
  parentCardId?: string;
  createdAt: string;
  updatedAt: string;
}

interface AIGenerationResponse {
  project: BackendProject;
  board: BackendBoard;
  generatedStages: number;
  generatedCards: number;
  templateUsed: string;
  suggestions: string[];
}

// ============================================================================
// TRANSFORMATION FUNCTIONS
// ============================================================================

/**
 * Transform backend priority to frontend priority
 * Backend: 'low' | 'medium' | 'high' | 'critical'
 * Frontend: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
 */
function transformPriority(backendPriority: string): 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' {
  const priorityMap: Record<string, 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'> = {
    'low': 'LOW',
    'medium': 'MEDIUM',
    'high': 'HIGH',
    'critical': 'CRITICAL'
  };
  return priorityMap[backendPriority.toLowerCase()] || 'MEDIUM';
}

/**
 * Transform backend priority to frontend format (lowercase)
 * Frontend -> Backend
 */
function transformPriorityToBackend(frontendPriority: string): string {
  return frontendPriority.toLowerCase();
}

/**
 * Transform backend card to frontend task
 */
function transformCard(backendCard: BackendCard): BoardTask {
  return {
    id: backendCard.id,
    title: backendCard.title,
    description: backendCard.description || '',
    priority: transformPriority(backendCard.priority),
    columnId: backendCard.stageId, // Map stageId to columnId
    assignee: backendCard.assignedTo ? { name: 'Team Member' } : undefined, // Backend stores ID, frontend expects name
    createdDate: backendCard.createdAt ? new Date(backendCard.createdAt).toISOString().split('T')[0] : new Date().toISOString().split('T')[0],
  };
}

/**
 * Transform backend stage to frontend column
 */
function transformStage(backendStage: BackendStage): BoardColumn {
  return {
    id: backendStage.id,
    title: backendStage.name, // Backend uses 'name', frontend uses 'title'
    color: backendStage.color || 'bg-gray-100',
  };
}

/**
 * Transform backend board to frontend columns and tasks
 */
function transformBoard(backendBoard: BackendBoard): { columns: BoardColumn[]; tasks: BoardTask[] } {
  const columns = backendBoard.stages.map(transformStage);
  const tasks = backendBoard.stages.flatMap(stage => 
    stage.cards.map(card => transformCard(card))
  );
  return { columns, tasks };
}

/**
 * Transform full backend response to frontend Project
 */
function transformProject(backendResponse: AIGenerationResponse): Project {
  const { project, board } = backendResponse;
  const { columns, tasks } = transformBoard(board);
  
  return {
    id: project.id,
    name: project.name,
    description: project.description || '',
    boardId: board.id,
    members: [], // TODO: Fetch members separately or add to backend response
    columns,
    tasks,
    decisions: [], // TODO: Fetch from meeting store
  };
}

/**
 * Transform backend project list item to frontend format
 */
function transformProjectList(backendProject: BackendProject): Project {
  // For list view, we may not have boards populated
  const board = backendProject.boards?.[0];
  
  if (board) {
    const { columns, tasks } = transformBoard(board);
    return {
      id: backendProject.id,
      name: backendProject.name,
      description: backendProject.description || '',
      boardId: board.id,
      members: [],
      columns,
      tasks,
      decisions: [],
    };
  }
  
  // Fallback if no boards yet
  return {
    id: backendProject.id,
    name: backendProject.name,
    description: backendProject.description || '',
    boardId: '',
    members: [],
    columns: [],
    tasks: [],
    decisions: [],
  };
}

// ============================================================================
// ADAPTED API METHODS
// ============================================================================

export const adaptedApi = {
  // ============================================================================
  // AUTH
  // ============================================================================
  
  async login(email: string, password: string) {
    const result = await api.login(email, password);
    // Token is stored in localStorage by api client
    return {
      user: {
        id: result.user.id,
        name: result.user.name,
        email: result.user.email,
        role: 'Manager', // Default role for frontend
      },
      token: result.token,
    };
  },
  
  async register(email: string, password: string, firstName: string, lastName: string) {
    return api.register(email, password, firstName, lastName);
  },
  
  async getCurrentUser() {
    return api.getCurrentUser();
  },
  
  // ============================================================================
  // PROJECTS (with AI Board Generation)
  // ============================================================================
  
  /**
   * Create project with AI-generated board
   * Transforms the backend response to frontend Project format
   */
  async createProject(projectData: {
    name: string;
    description: string;
    templateHint?: string;
    stageCount?: number;
    cardsPerStage?: number;
  }): Promise<Project> {
    // The backend creates both project and board in one call
    const backendResponse: AIGenerationResponse = await api.createProject({
      name: projectData.name,
      description: projectData.description,
      templateHint: projectData.templateHint,
      stageCount: projectData.stageCount || 5,
      cardsPerStage: projectData.cardsPerStage || 3,
    });
    
    return transformProject(backendResponse);
  },
  
  /**
   * Get all projects for current user
   */
  async getProjects(): Promise<Project[]> {
    const backendProjects: BackendProject[] = await api.getProjects();
    return backendProjects.map(transformProjectList);
  },
  
  /**
   * Get single project with full details
   */
  async getProject(id: string): Promise<Project | null> {
    try {
      const backendProject: BackendProject = await api.getProject(id);
      return transformProjectList(backendProject);
    } catch (error) {
      console.error('Failed to fetch project:', error);
      return null;
    }
  },
  
  /**
   * Update project
   */
  async updateProject(id: string, updates: Partial<Project>): Promise<void> {
    await api.updateProject(id, {
      name: updates.name,
      description: updates.description,
    });
  },
  
  /**
   * Delete project
   */
  async deleteProject(id: string): Promise<void> {
    await api.deleteProject(id);
  },
  
  // ============================================================================
  // BOARDS
  // ============================================================================
  
  /**
   * Get board with columns (stages) and tasks (cards)
   */
  async getBoard(boardId: string): Promise<{ columns: BoardColumn[]; tasks: BoardTask[] } | null> {
    try {
      const backendBoard: BackendBoard = await api.getBoard(boardId);
      return transformBoard(backendBoard);
    } catch (error) {
      console.error('Failed to fetch board:', error);
      return null;
    }
  },
  
  // ============================================================================
  // CARDS / TASKS
  // ============================================================================
  
  /**
   * Create a new card (task)
   */
  async createCard(projectId: string, task: BoardTask): Promise<BoardTask> {
    const backendCard: BackendCard = await api.createCard({
      stageId: task.columnId,
      title: task.title,
      description: task.description,
      priority: transformPriorityToBackend(task.priority),
      type: 'task',
    });
    
    return transformCard(backendCard);
  },
  
  /**
   * Update card
   */
  async updateCard(cardId: string, updates: Partial<BoardTask>): Promise<BoardTask> {
    const backendCard: BackendCard = await api.updateCard(cardId, {
      title: updates.title,
      description: updates.description,
      priority: updates.priority ? transformPriorityToBackend(updates.priority) : undefined,
    });
    
    return transformCard(backendCard);
  },
  
  /**
   * Move card to different stage (column)
   */
  async moveCard(cardId: string, newColumnId: string, orderIndex?: number): Promise<void> {
    await api.moveCard(cardId, newColumnId, orderIndex);
  },
  
  // ============================================================================
  // AI
  // ============================================================================
  
  /**
   * Generate board preview without saving
   */
  async generateBoardPreview(
    projectName: string, 
    description: string, 
    templateHint?: string,
    stageCount?: number,
    cardsPerStage?: number
  ) {
    return api.generateBoardPreview(projectName, description, templateHint, stageCount, cardsPerStage);
  },
};

export default adaptedApi;
