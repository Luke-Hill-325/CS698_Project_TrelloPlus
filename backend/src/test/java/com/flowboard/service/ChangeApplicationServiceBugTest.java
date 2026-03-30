package com.flowboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.entity.*;
import com.flowboard.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug-finding tests for ChangeApplicationService.
 *
 * BUG 1: applyDeleteCard() calls cardRepository.delete() (hard delete) while
 * the rest of the codebase consistently uses soft delete via isDeletionMarked.
 * This breaks audit trails, cascades unexpectedly, and makes deletion
 * irreversible — contrary to the soft-delete pattern used everywhere else.
 *
 * BUG 2: applyUpdateCard() silently appends " (Updated)" to a card's title
 * when neither afterState nor beforeState contains a "title" field. This is
 * invisible data corruption — the caller never requested a title change.
 */
@ExtendWith(MockitoExtension.class)
class ChangeApplicationServiceBugTest {

    @Mock private ChangeRepository changeRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private StageRepository stageRepository;
    @Mock private CardRepository cardRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ChangeSnapshotRepository changeSnapshotRepository;
    @Mock private ChangeAuditEntryRepository changeAuditEntryRepository;

    private ChangeApplicationService changeApplicationService;
    private ObjectMapper objectMapper;

    private User owner;
    private Project project;
    private Meeting meeting;
    private Board board;
    private Stage stage;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        changeApplicationService = new ChangeApplicationService(
            changeRepository,
            boardRepository,
            stageRepository,
            cardRepository,
            userRepository,
            projectMemberRepository,
            changeSnapshotRepository,
            changeAuditEntryRepository,
            objectMapper
        );

        owner = User.builder().id(UUID.randomUUID()).username("owner").email("owner@test.com").build();

        project = Project.builder()
            .id(UUID.randomUUID())
            .name("Test Project")
            .owner(owner)
            .build();

        meeting = Meeting.builder()
            .id(UUID.randomUUID())
            .project(project)
            .title("Sprint")
            .status(Meeting.MeetingStatus.APPROVED)
            .createdBy(owner)
            .build();

        board = Board.builder()
            .id(UUID.randomUUID())
            .name("Main Board")
            .project(project)
            .build();

        stage = Stage.builder()
            .id(UUID.randomUUID())
            .title("To Do")
            .board(board)
            .position(0)
            .cards(new ArrayList<>())
            .build();
    }

    @Test
    @DisplayName("BUG: DELETE_CARD should soft-delete (set isDeletionMarked) instead of hard-deleting")
    void applyChange_deleteCard_shouldSoftDelete_notHardDelete() {
        UUID cardId = UUID.randomUUID();
        Card card = Card.builder()
            .id(cardId)
            .title("Task to delete")
            .stage(stage)
            .priority(Card.Priority.MEDIUM)
            .position(0)
            .isDeletionMarked(false)
            .build();

        String beforeState = "{\"id\":\"" + cardId + "\",\"title\":\"Task to delete\"}";

        Change change = Change.builder()
            .id(UUID.randomUUID())
            .meeting(meeting)
            .changeType(Change.ChangeType.DELETE_CARD)
            .beforeState(beforeState)
            .afterState("{}")
            .status(Change.ChangeStatus.APPROVED)
            .build();

        when(changeRepository.findById(change.getId())).thenReturn(Optional.of(change));
        when(changeRepository.save(any(Change.class))).thenAnswer(i -> i.getArgument(0));
        when(boardRepository.findByProjectId(project.getId())).thenReturn(List.of(board));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(changeSnapshotRepository.save(any(ChangeSnapshot.class))).thenAnswer(i -> i.getArgument(0));
        when(changeAuditEntryRepository.save(any(ChangeAuditEntry.class))).thenAnswer(i -> i.getArgument(0));

        changeApplicationService.applyChange(change.getId(), owner);

        // ASSERTION: Card should be soft-deleted (isDeletionMarked = true), NOT hard-deleted.
        // This test FAILS because applyDeleteCard() calls cardRepository.delete(card)
        // instead of setting isDeletionMarked and saving.
        verify(cardRepository, never()).delete(any(Card.class));
        verify(cardRepository).save(argThat(c ->
            Boolean.TRUE.equals(c.getIsDeletionMarked())
        ));
    }

    @Test
    @DisplayName("BUG: UPDATE_CARD should not mutate title when no title field is in the change payload")
    void applyChange_updateCard_shouldNotMutateTitle_whenNoTitleInPayload() {
        UUID cardId = UUID.randomUUID();
        String originalTitle = "Original Task Title";

        Card card = Card.builder()
            .id(cardId)
            .title(originalTitle)
            .description("Some description")
            .stage(stage)
            .priority(Card.Priority.MEDIUM)
            .position(0)
            .build();

        // Payload only changes description — no "title" field at all.
        String beforeState = "{\"id\":\"" + cardId + "\"}";
        String afterState = "{\"id\":\"" + cardId + "\",\"description\":\"Updated description\"}";

        Change change = Change.builder()
            .id(UUID.randomUUID())
            .meeting(meeting)
            .changeType(Change.ChangeType.UPDATE_CARD)
            .beforeState(beforeState)
            .afterState(afterState)
            .status(Change.ChangeStatus.APPROVED)
            .build();

        when(changeRepository.findById(change.getId())).thenReturn(Optional.of(change));
        when(changeRepository.save(any(Change.class))).thenAnswer(i -> i.getArgument(0));
        when(boardRepository.findByProjectId(project.getId())).thenReturn(List.of(board));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));
        when(changeSnapshotRepository.save(any(ChangeSnapshot.class))).thenAnswer(i -> i.getArgument(0));
        when(changeAuditEntryRepository.save(any(ChangeAuditEntry.class))).thenAnswer(i -> i.getArgument(0));

        changeApplicationService.applyChange(change.getId(), owner);

        // ASSERTION: Title should remain unchanged — the payload didn't request a title change.
        // This test FAILS because applyUpdateCard() appends " (Updated)" to the title
        // when neither afterState nor beforeState has a "title" key (line 167-168).
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());

        assertThat(cardCaptor.getValue().getTitle())
            .as("Card title should remain '%s' when payload has no title field, but got '%s'",
                originalTitle, cardCaptor.getValue().getTitle())
            .isEqualTo(originalTitle);
    }
}
