import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRoot, type Root } from 'react-dom/client';
import { act } from 'react';
import { ChangeDetailModal } from './ChangeDetailModal';
import { useProjectStore } from '../store/projectStore';
import type { ChangeRequest } from '../store/changeStore';

vi.mock('./ui/dialog', () => ({
  Dialog: ({ children }: { children: any }) => <div>{children}</div>,
  DialogContent: ({ children }: { children: any }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: any }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: any }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: any }) => <p>{children}</p>,
}));

function createContainer() {
  const container = document.createElement('div');
  document.body.appendChild(container);
  return container;
}

function renderModal(root: Root, change: ChangeRequest) {
  act(() => {
    root.render(<ChangeDetailModal change={change} open onClose={() => undefined} />);
  });
}

function buildChange(): ChangeRequest {
  return {
    id: 'change-1',
    meetingId: 'meeting-1',
    meetingTitle: 'U3 Review',
    type: 'UPDATE_CARD',
    status: 'PENDING',
    requestedBy: 'system',
    requestedAt: '2026-04-05T10:00:00.000Z',
    projectId: 'project-1',
    before: {
      id: 'card-1',
      title: 'Old title',
      description: 'Old description',
      columnId: 'stage-1',
      columnTitle: 'To Do',
    },
    after: {
      id: 'card-1',
      title: 'Updated title',
      description: 'Updated description',
      columnId: 'stage-1',
      columnTitle: 'To Do',
    },
    affectedCards: [],
    affectedStages: [],
    affectedMembers: [],
    riskLevel: 'LOW',
    approvals: [],
    requiredApprovals: 0,
    rollbackAvailable: false,
  };
}

beforeEach(() => {
  useProjectStore.setState({
    projects: [
      {
        id: 'project-1',
        name: 'U3 Project',
        description: 'Project with live board state',
        boardId: 'board-1',
        members: [],
        columns: [
          { id: 'stage-1', title: 'Review', color: '#f8fafc' },
        ],
        tasks: [
          {
            id: 'card-1',
            title: 'Live title',
            description: 'Live description',
            priority: 'HIGH',
            createdDate: '2026-04-04T09:00:00.000Z',
            columnId: 'stage-1',
          },
        ],
        decisions: [],
      },
    ],
  });
});

afterEach(() => {
  document.body.innerHTML = '';
});

describe('ChangeDetailModal', () => {
  it('renders the live before state for update changes and shows resolved column labels', () => {
    const container = createContainer();
    const root = createRoot(container);

    renderModal(root, buildChange());

    expect(container.textContent).toContain('Change Details');
    expect(container.textContent).toContain('Before');
    expect(container.textContent).toContain('After');
    expect(container.textContent).toContain('Live title');
    expect(container.textContent).toContain('Live description');
    expect(container.textContent).toContain('Review');
    expect(container.textContent).toContain('Updated title');
    expect(container.textContent).toContain('Updated description');

    act(() => {
      root.unmount();
    });
    container.remove();
  });
});