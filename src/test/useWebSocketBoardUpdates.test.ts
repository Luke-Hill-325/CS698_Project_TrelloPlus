/**
 * Tests for useWebSocketBoardUpdates – focusing on the card-moved event bug.
 *
 * Issue: The CardMoveEvent interface only declares `cardData`, but the backend
 * BoardBroadcastService sends { cardData, toStageId }. The handler at lines
 * 110-117 of the hook falls back to cardData.columnId when toStageId is
 * missing from the type, so the card always ends up in its *original* column
 * instead of the destination column.
 *
 * Fix required: Add `toStageId: string` to the CardMoveEvent interface so the
 * handler can correctly resolve the destination column.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useWebSocketBoardUpdates } from '../app/hooks/useWebSocketBoardUpdates';
import { useProjectStore } from '../app/store/projectStore';

// ── Minimal WebSocket mock ───────────────────────────────────────────────────

type WSMessageHandler = (event: MessageEvent) => void;

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  onmessage: WSMessageHandler | null = null;
  onerror: ((event: Event) => void) | null = null;
  url: string;

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  /** Simulate a message arriving from the server */
  simulateMessage(data: unknown) {
    if (this.onmessage) {
      this.onmessage({ data: JSON.stringify(data) } as MessageEvent);
    }
  }

  close() {}
}

// ── Test setup ───────────────────────────────────────────────────────────────

const PROJECT_ID = 'proj-1';
const BOARD_ID = 'board-1';
const CARD_ID = 'card-42';
const SOURCE_STAGE_ID = 'stage-todo';
const TARGET_STAGE_ID = 'stage-done';

function seedStore() {
  useProjectStore.setState({
    projects: [
      {
        id: PROJECT_ID,
        boardId: BOARD_ID,
        name: 'Test Project',
        description: '',
        members: [],
        decisions: [],
        columns: [
          { id: SOURCE_STAGE_ID, title: 'To Do', color: 'bg-blue-100' },
          { id: TARGET_STAGE_ID, title: 'Done',  color: 'bg-green-100' },
        ],
        tasks: [
          {
            id: CARD_ID,
            title: 'Test card',
            description: '',
            priority: 'MEDIUM',
            columnId: SOURCE_STAGE_ID,
            createdDate: new Date().toISOString(),
          },
        ],
      },
    ],
  });
}

// ── Tests ────────────────────────────────────────────────────────────────────

describe('useWebSocketBoardUpdates – card-moved event', () => {
  beforeEach(() => {
    MockWebSocket.instances = [];
    vi.stubGlobal('WebSocket', MockWebSocket);
    seedStore();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    useProjectStore.setState({ projects: [] });
  });

  /**
   * HAPPY PATH (should pass after the fix):
   * When the backend sends { cardData, toStageId }, the hook must move the
   * card to toStageId, NOT leave it in cardData.columnId (the old column).
   */
  it('moves the card to toStageId from the payload', () => {
    renderHook(() =>
      useWebSocketBoardUpdates({
        projectId: PROJECT_ID,
        boardId: BOARD_ID,
        wsUrl: `ws://localhost/ws/board/${BOARD_ID}`,
      })
    );

    const ws = MockWebSocket.instances[0];
    expect(ws).toBeDefined();

    // Backend payload — matches BoardBroadcastService.java output
    ws.simulateMessage({
      type: 'card-moved',
      payload: {
        cardData: {
          id: CARD_ID,
          title: 'Test card',
          description: '',
          priority: 'MEDIUM',
          columnId: SOURCE_STAGE_ID,   // original column (should NOT be used as target)
        },
        toStageId: TARGET_STAGE_ID,    // actual destination (must be used)
      },
    });

    const state = useProjectStore.getState();
    const movedCard = state.projects
      .find((p) => p.id === PROJECT_ID)
      ?.tasks.find((t) => t.id === CARD_ID);

    // This assertion FAILS before the fix because toStageId is absent from
    // the CardMoveEvent interface and the handler falls back to SOURCE_STAGE_ID.
    expect(movedCard?.columnId).toBe(TARGET_STAGE_ID);
  });

  /**
   * REGRESSION GUARD:
   * If toStageId is somehow absent (malformed message), the card should stay
   * where it was rather than moving to an undefined column.
   */
  it('does not corrupt card position when toStageId is absent', () => {
    renderHook(() =>
      useWebSocketBoardUpdates({
        projectId: PROJECT_ID,
        boardId: BOARD_ID,
        wsUrl: `ws://localhost/ws/board/${BOARD_ID}`,
      })
    );

    const ws = MockWebSocket.instances[0];

    // Malformed message – no toStageId
    ws.simulateMessage({
      type: 'card-moved',
      payload: {
        cardData: {
          id: CARD_ID,
          title: 'Test card',
          description: '',
          priority: 'MEDIUM',
          columnId: SOURCE_STAGE_ID,
        },
        // toStageId intentionally omitted
      },
    });

    const state = useProjectStore.getState();
    const card = state.projects
      .find((p) => p.id === PROJECT_ID)
      ?.tasks.find((t) => t.id === CARD_ID);

    // Card should stay in its original column (cardData.columnId fallback)
    expect(card?.columnId).toBe(SOURCE_STAGE_ID);
  });
});
