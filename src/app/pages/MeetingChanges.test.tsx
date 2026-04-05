import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react';
import { MeetingChanges } from './MeetingChanges';

const pageMocks = vi.hoisted(() => ({
  navigate: vi.fn(),
  setProjects: vi.fn(),
  getMeeting: vi.fn(),
  listChanges: vi.fn(),
  getUserProjects: vi.fn(),
  getProject: vi.fn(),
  applyChange: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock('react-router', () => ({
  useNavigate: () => pageMocks.navigate,
  useParams: () => ({ meetingId: 'meeting-1' }),
}));

vi.mock('../components/ChangeDetailModal', () => ({
  ChangeDetailModal: () => <div data-testid="change-detail-modal" />,
}));

vi.mock('../store/projectStore', () => ({
  useProjectStore: (selector: (state: { setProjects: typeof pageMocks.setProjects }) => unknown) =>
    selector({ setProjects: pageMocks.setProjects }),
}));

vi.mock('../services/api', () => ({
  apiService: {
    getMeeting: pageMocks.getMeeting,
    listChanges: pageMocks.listChanges,
    getUserProjects: pageMocks.getUserProjects,
    getProject: pageMocks.getProject,
    applyChange: pageMocks.applyChange,
  },
  mapProjectResponseToProject: (project: any) => ({
    id: project.id,
    name: project.name,
    description: project.description,
    boardId: project.board_id,
    members: [],
    columns: [],
    tasks: [],
    decisions: [],
  }),
}));

vi.mock('sonner', () => ({
  toast: {
    success: pageMocks.toastSuccess,
    error: pageMocks.toastError,
  },
}));

function createMeetingResponse() {
  return {
    id: 'meeting-1',
    projectId: 'project-1',
    title: 'U3 Review',
    description: 'Review proposed board changes',
    meetingDate: '2026-04-05',
    meetingTime: '10:00:00',
    platform: 'Zoom',
    meetingLink: 'https://example.com/meeting',
    status: 'PENDING_APPROVAL',
    createdAt: '2026-04-05T10:00:00.000Z',
    members: [],
  };
}

function createProjectResponse(taskIds: string[] = ['card-1']) {
  return {
    id: 'project-1',
    name: 'U3 Project',
    description: 'Project used for meeting changes',
    board_id: 'board-1',
    members: [
      {
        id: 'owner-1',
        email: 'owner@example.com',
        username: 'owner',
        role: 'owner',
        created_at: '2026-04-01T00:00:00.000Z',
      },
    ],
    columns: [
      {
        id: 'stage-1',
        title: 'To Do',
        color: '#ffffff',
        cards: [],
      },
    ],
    tasks: taskIds.map((taskId) => ({
      id: taskId,
      title: 'Implement auth flow',
      description: 'Current live board state',
      priority: 'HIGH',
      column_id: 'stage-1',
      created_at: '2026-04-04T09:00:00.000Z',
    })),
    created_at: '2026-04-01T00:00:00.000Z',
  };
}

function createChange(changeOverrides: Record<string, unknown> = {}) {
  return {
    id: 'change-1',
    meetingId: 'meeting-1',
    changeType: 'UPDATE_CARD',
    beforeState: JSON.stringify({
      id: 'card-1',
      title: 'Old title',
      description: 'Old description',
      columnId: 'stage-1',
      columnTitle: 'To Do',
    }),
    afterState: JSON.stringify({
      id: 'card-1',
      title: 'Updated title',
      description: 'Updated description',
      columnId: 'stage-1',
      columnTitle: 'To Do',
    }),
    status: 'PENDING',
    createdAt: '2026-04-05T10:05:00.000Z',
    ...changeOverrides,
  };
}

function createStaleChange() {
  return createChange({
    id: 'change-stale',
    beforeState: JSON.stringify({
      id: 'missing-card',
      title: 'Deleted task',
      columnId: 'stage-1',
      columnTitle: 'To Do',
    }),
    afterState: JSON.stringify({
      id: 'missing-card',
      title: 'Deleted task',
      columnId: 'stage-1',
      columnTitle: 'To Do',
    }),
  });
}

function createContainer() {
  const container = document.createElement('div');
  document.body.appendChild(container);
  return container;
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}

async function renderWithRoot(root: Root) {
  await act(async () => {
    root.render(<MeetingChanges />);
  });
}

function findButton(container: HTMLElement, label: string) {
  return Array.from(container.querySelectorAll('button')).find((button) => {
    const text = button.textContent?.replace(/\s+/g, ' ').trim();
    return text === label;
  });
}

beforeEach(() => {
  pageMocks.navigate.mockReset();
  pageMocks.setProjects.mockReset();
  pageMocks.getMeeting.mockReset();
  pageMocks.listChanges.mockReset();
  pageMocks.getUserProjects.mockReset();
  pageMocks.getProject.mockReset();
  pageMocks.applyChange.mockReset();
  pageMocks.toastSuccess.mockReset();
  pageMocks.toastError.mockReset();
  localStorage.clear();
  localStorage.setItem('user', JSON.stringify({ id: 'owner-1', username: 'owner', role: 'owner' }));
});

afterEach(() => {
  document.body.innerHTML = '';
});

describe('MeetingChanges', () => {
  it('loads meeting changes and applies a change to the board', async () => {
    const container = createContainer();
    const root = createRoot(container);

    pageMocks.getMeeting.mockResolvedValue(createMeetingResponse());
    pageMocks.listChanges.mockResolvedValue([createChange()]);
    pageMocks.getUserProjects.mockResolvedValue([createProjectResponse()]);
    pageMocks.getProject.mockResolvedValue(createProjectResponse());
    pageMocks.applyChange.mockResolvedValue({ message: 'Change applied to board' });

    await renderWithRoot(root);
    await flush();
    await flush();

    expect(container.textContent).toContain('Board Changes');
    expect(container.textContent).toContain('U3 Review');
    expect(container.textContent).toContain('Apply to Board');

    const applyButton = findButton(container, 'Apply to Board');
    expect(applyButton).toBeDefined();

    await act(async () => {
      applyButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    await flush();
    await flush();

    expect(pageMocks.applyChange).toHaveBeenCalledWith('change-1');
    expect(pageMocks.getUserProjects).toHaveBeenCalled();
    expect(pageMocks.getProject).toHaveBeenCalledWith('project-1');
    expect(pageMocks.toastSuccess).toHaveBeenCalledWith('Change applied to board');
    expect(container.textContent).toContain('Applied');

    await act(async () => {
      root.unmount();
    });
    container.remove();
  });

  it('marks stale changes as unavailable to apply', async () => {
    const container = createContainer();
    const root = createRoot(container);

    pageMocks.getMeeting.mockResolvedValue(createMeetingResponse());
    pageMocks.listChanges.mockResolvedValue([createStaleChange()]);
    pageMocks.getUserProjects.mockResolvedValue([createProjectResponse([])]);
    pageMocks.getProject.mockResolvedValue(createProjectResponse([]));
    pageMocks.applyChange.mockResolvedValue({ message: 'Should not be used' });

    await renderWithRoot(root);
    await flush();
    await flush();

    expect(container.textContent).toContain('Target missing');
    expect(container.textContent).toContain('This change references a card that no longer exists.');

    const staleButton = findButton(container, 'Stale Target');
    expect(staleButton).toBeDefined();
    expect(staleButton?.disabled).toBe(true);

    await act(async () => {
      staleButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(pageMocks.applyChange).not.toHaveBeenCalled();

    await act(async () => {
      root.unmount();
    });
    container.remove();
  });
});