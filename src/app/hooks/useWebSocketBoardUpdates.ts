import { useEffect, useRef } from 'react';
import { useProjectStore } from '../store/projectStore';

// ── WebSocket event types ──────────────────────────────────────────────────

interface CardData {
  id: string;
  title: string;
  description: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  columnId: string;
}

// FIX: CardMoveEvent now declares `toStageId` to match the backend payload
// emitted by BoardBroadcastService (lines 100-107). Previously this field was
// absent, causing the handler to fall back to cardData.columnId (the source
// column) and the card never moved to the correct destination stage.
interface CardMoveEvent {
  cardData: CardData;
  toStageId: string;
}

interface CardCreatedEvent {
  cardData: CardData;
}

interface CardUpdatedEvent {
  cardData: CardData;
}

interface CardDeletedEvent {
  cardId: string;
}

interface StageCreatedEvent {
  stageId: string;
  title: string;
  color: string;
}

interface StageDeletedEvent {
  stageId: string;
}

type BoardWebSocketMessage =
  | { type: 'card-moved';   payload: CardMoveEvent }
  | { type: 'card-created'; payload: CardCreatedEvent }
  | { type: 'card-updated'; payload: CardUpdatedEvent }
  | { type: 'card-deleted'; payload: CardDeletedEvent }
  | { type: 'stage-created'; payload: StageCreatedEvent }
  | { type: 'stage-deleted'; payload: StageDeletedEvent };

// ── Hook ──────────────────────────────────────────────────────────────────

interface UseWebSocketBoardUpdatesOptions {
  projectId: string;
  boardId: string;
  wsUrl?: string;
}

export function useWebSocketBoardUpdates({
  projectId,
  boardId,
  wsUrl,
}: UseWebSocketBoardUpdatesOptions): void {
  const wsRef = useRef<WebSocket | null>(null);
  const { moveTask, addTask, updateTask, deleteTask } = useProjectStore.getState();

  useEffect(() => {
    if (!boardId) return;

    const url = wsUrl ?? `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws/board/${boardId}`;
    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onmessage = (event: MessageEvent) => {
      let message: BoardWebSocketMessage;
      try {
        message = JSON.parse(event.data as string) as BoardWebSocketMessage;
      } catch {
        return;
      }

      // ── lines 110-117 ──────────────────────────────────────────────────
      if (message.type === 'card-moved') {
        const { cardData, toStageId } = message.payload;
        if (toStageId) {
          moveTask(projectId, cardData.id, toStageId);
        }
      }
      // ───────────────────────────────────────────────────────────────────

      if (message.type === 'card-created') {
        addTask(projectId, {
          id: message.payload.cardData.id,
          title: message.payload.cardData.title,
          description: message.payload.cardData.description,
          priority: message.payload.cardData.priority,
          columnId: message.payload.cardData.columnId,
          createdDate: new Date().toISOString(),
        });
      }

      if (message.type === 'card-updated') {
        updateTask(projectId, {
          id: message.payload.cardData.id,
          title: message.payload.cardData.title,
          description: message.payload.cardData.description,
          priority: message.payload.cardData.priority,
          columnId: message.payload.cardData.columnId,
          createdDate: new Date().toISOString(),
        });
      }

      if (message.type === 'card-deleted') {
        deleteTask(projectId, message.payload.cardId);
      }
    };

    ws.onerror = (err) => {
      console.error('[WebSocket] board update error', err);
    };

    return () => {
      ws.close();
      wsRef.current = null;
    };
  }, [boardId, projectId, wsUrl, moveTask, addTask, updateTask, deleteTask]);
}
