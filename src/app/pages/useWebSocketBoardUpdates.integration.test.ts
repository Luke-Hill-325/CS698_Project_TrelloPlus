import { describe, expect, it } from 'vitest';
import {
  createWebSocketBoardUpdateHandler,
  type BoardState,
  type CardMoveEvent,
} from './useWebSocketBoardUpdates';

describe('useWebSocketBoardUpdates integration', () => {
  function buildInitialState(): BoardState {
    return {
      stages: [
        {
          id: 'stage-123',
          cards: [
            {
              id: 'card-1',
              title: 'Implement auth flow',
            },
          ],
        },
        {
          id: 'stage-456',
          cards: [],
        },
      ],
    };
  }

  it('applies CARD_MOVED from websocket message into board state', () => {
    let state = buildInitialState();

    const updateBoardState = (updater: (previous: BoardState) => BoardState) => {
      state = updater(state);
    };

    const onWebSocketMessage = createWebSocketBoardUpdateHandler(updateBoardState);

    const event: CardMoveEvent = {
      type: 'CARD_MOVED',
      cardData: {
        id: 'card-1',
        title: 'Implement auth flow',
      },
      toStageId: 'stage-456',
    };

    expect(() => onWebSocketMessage({ data: JSON.stringify(event) })).not.toThrow();

    const originalStage = state.stages.find((stage) => stage.id === 'stage-123');
    const targetStage = state.stages.find((stage) => stage.id === 'stage-456');

    expect(originalStage?.cards.some((card) => card.id === 'card-1')).toBe(false);
    expect(targetStage?.cards.some((card) => card.id === 'card-1')).toBe(true);
  });

  it('throws and keeps previous state when CARD_MOVED is missing toStageId', () => {
    let state = buildInitialState();

    const updateBoardState = (updater: (previous: BoardState) => BoardState) => {
      state = updater(state);
    };

    const onWebSocketMessage = createWebSocketBoardUpdateHandler(updateBoardState);

    const invalidEvent = {
      type: 'CARD_MOVED',
      cardData: {
        id: 'card-1',
        title: 'Implement auth flow',
      },
    };

    expect(() => onWebSocketMessage({ data: JSON.stringify(invalidEvent) })).toThrow(/toStageId/i);

    const originalStage = state.stages.find((stage) => stage.id === 'stage-123');
    const targetStage = state.stages.find((stage) => stage.id === 'stage-456');

    expect(originalStage?.cards.some((card) => card.id === 'card-1')).toBe(true);
    expect(targetStage?.cards.some((card) => card.id === 'card-1')).toBe(false);
  });

  it('does not mutate state for unknown event types', () => {
    let state = buildInitialState();

    const updateBoardState = (updater: (previous: BoardState) => BoardState) => {
      state = updater(state);
    };

    const onWebSocketMessage = createWebSocketBoardUpdateHandler(updateBoardState);
    const previous = state;

    onWebSocketMessage({ data: JSON.stringify({ type: 'NOOP_EVENT', value: 1 }) });

    expect(state).toBe(previous);
  });

  it('handles malformed websocket payload without crashing the board update loop', () => {
    let state = buildInitialState();

    const updateBoardState = (updater: (previous: BoardState) => BoardState) => {
      state = updater(state);
    };

    const onWebSocketMessage = createWebSocketBoardUpdateHandler(updateBoardState);

    // Root cause: malformed websocket frames bubble JSON.parse errors from parseMessage() and break processing.
    expect(() => onWebSocketMessage({ data: 'not-json' })).not.toThrow();
    expect(state.stages[0].cards).toHaveLength(1);
    expect(state.stages[1].cards).toHaveLength(0);
  });
});
