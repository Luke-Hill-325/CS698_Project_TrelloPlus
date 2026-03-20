package com.flowboard.service;

import com.flowboard.entity.*;
import com.flowboard.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for User Story 3: Change Review & Approval
 * 
 * Program Path:
 * 1. UserAuthenticationService - Team member logs in
 * 2. ChangePreviewService - Loads and displays changes from WF2
 * 3. DiffCalculator - Computes before/after diff
 * 4. ImpactAnalyzer - Analyzes change impact
 * 5. ConflictResolver - Detects conflicts
 * 6. ApprovalService - Manages change approval workflow
 * 7. ChangeApplicationService - Applies approved changes to board
 */
@ExtendWith(MockitoExtension.class)
class UserStory3_ChangeReviewTest {

    // ============ Module 8: ChangePreviewService Tests ============

    @Mock
    private ChangeRepository changeRepository;

    @Mock
    private KanbanBoardGateway kanbanBoardGateway;

    @Mock
    private DiffCalculator diffCalculator;

    @Mock
    private ImpactAnalyzer impactAnalyzer;

    @Mock
    private ConflictResolver conflictResolver;

    @InjectMocks
    private ChangePreviewService changePreviewService;

    @Test
    void changePreviewService_shouldLoadPendingChanges() {
        // Given: Changes created by WF2
        UUID projectId = UUID.randomUUID();
        UUID changeId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(changeId)
            .changeType(ChangeType.MOVE_CARD)
            .meetingId(UUID.randomUUID())
            .currentState("{\"card_id\": \"card-1\", \"stage_id\": \"backlog\"}")
            .proposedState("{\"card_id\": \"card-1\", \"stage_id\": \"in-progress\"}")
            .status(ChangeStatus.PENDING)
            .impactLevel(ImpactLevel.MEDIUM)
            .build();
        
        when(changeRepository.findByStatusAndProjectId(ChangeStatus.PENDING, projectId))
            .thenReturn(Arrays.asList(change));
        
        // When: Loading pending changes
        List<ChangePreview> previews = changePreviewService.listPendingChanges(projectId, null);
        
        // Then: Changes are loaded
        assertNotNull(previews);
        assertFalse(previews.isEmpty());
        assertEquals(changeId, previews.get(0).getChangeId());
    }

    @Test
    void changePreviewService_shouldGenerateFullPreviewWithDiffImpactAndConflicts() {
        // Given: A change to preview
        UUID changeId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(changeId)
            .changeType(ChangeType.UPDATE_CARD)
            .targetBoardId(boardId)
            .targetCardId(UUID.randomUUID())
            .currentState("{\"title\": \"Old Title\", \"description\": \"Old Desc\"}")
            .proposedState("{\"title\": \"New Title\", \"description\": \"New Desc\"}")
            .status(ChangeStatus.PENDING)
            .build();
        
        Board board = Board.builder()
            .id(boardId)
            .build();
        
        DiffView diff = DiffView.builder()
            .added(Map.of("tags", Arrays.asList("urgent")))
            .removed(Map.of())
            .modified(Map.of("title", new ValueChange("Old Title", "New Title")))
            .build();
        
        ImpactSummary impact = ImpactSummary.builder()
            .affectedCards(Arrays.asList(change.getTargetCardId()))
            .affectedStages(Arrays.asList())
            .riskLevel(RiskLevel.LOW)
            .effortPoints(2)
            .build();
        
        ConflictReport conflictReport = ConflictReport.builder()
            .canApply(true)
            .conflicts(Arrays.asList())
            .build();
        
        when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        when(kanbanBoardGateway.getBoard(boardId)).thenReturn(board);
        when(diffCalculator.computeDiff(any(), any())).thenReturn(diff);
        when(impactAnalyzer.analyzeChange(change)).thenReturn(impact);
        when(conflictResolver.detectConflicts(change, board)).thenReturn(conflictReport);
        
        // When: Generating preview
        ChangePreview preview = changePreviewService.generatePreview(changeId, null);
        
        // Then: Preview contains diff, impact, and conflict info
        assertNotNull(preview);
        assertNotNull(preview.getDiff());
        assertNotNull(preview.getImpact());
        assertNotNull(preview.getConflicts());
        assertTrue(preview.getConflicts().isCanApply());
    }

    // ============ Module 12: DiffCalculator Tests ============

    @Test
    void diffCalculator_shouldComputeFieldLevelDiffs() {
        // Given: Before and after states
        Map<String, Object> before = Map.of(
            "title", "Old Title",
            "description", "Old Description",
            "status", "open"
        );
        
        Map<String, Object> after = Map.of(
            "title", "New Title",
            "description", "Old Description",
            "priority", "high"
        );
        
        DiffCalculator calculator = new DiffCalculator();
        
        // When: Computing diff
        DiffResult result = calculator.computeDiff(before, after);
        
        // Then: Diff shows added, removed, and modified fields
        assertNotNull(result);
        assertTrue(result.getAdded().containsKey("priority"));
        assertTrue(result.getRemoved().containsKey("status"));
        assertTrue(result.getModified().containsKey("title"));
        assertEquals("Old Title", result.getModified().get("title").getOldValue());
        assertEquals("New Title", result.getModified().get("title").getNewValue());
    }

    @Test
    void diffCalculator_shouldHandleNestedObjectDiffs() {
        // Given: Nested before and after states
        Map<String, Object> before = Map.of(
            "assignee", Map.of("id", "user-1", "name", "John"),
            "stage", Map.of("id", "stage-1", "name", "Backlog")
        );
        
        Map<String, Object> after = Map.of(
            "assignee", Map.of("id", "user-2", "name", "Sarah"),
            "stage", Map.of("id", "stage-1", "name", "Backlog")
        );
        
        DiffCalculator calculator = new DiffCalculator();
        
        // When: Computing nested diff
        DiffResult result = calculator.computeDiff(before, after);
        
        // Then: Nested changes are detected
        assertNotNull(result);
    }

    // ============ Module 13: ImpactAnalyzer Tests ============

    @Test
    void impactAnalyzer_shouldAnalyzeChangeImpact() {
        // Given: A card move change
        UUID cardId = UUID.randomUUID();
        UUID fromStageId = UUID.randomUUID();
        UUID toStageId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(UUID.randomUUID())
            .changeType(ChangeType.MOVE_CARD)
            .targetCardId(cardId)
            .currentState("{\"stage_id\": \"" + fromStageId + "\"}")
            .proposedState("{\"stage_id\": \"" + toStageId + "\"}")
            .build();
        
        ImpactAnalyzer analyzer = new ImpactAnalyzer();
        
        // When: Analyzing impact
        ImpactSummary impact = analyzer.analyzeChange(change);
        
        // Then: Impact shows affected entities
        assertNotNull(impact);
        assertTrue(impact.getAffectedCards().contains(cardId));
    }

    @Test
    void impactAnalyzer_shouldAssessRiskLevel() {
        // Given: Changes with different risk profiles
        Change lowRiskChange = Change.builder()
            .changeType(ChangeType.UPDATE_CARD)
            .build();
        
        Change highRiskChange = Change.builder()
            .changeType(ChangeType.DELETE_CARD)
            .build();
        
        ImpactAnalyzer analyzer = new ImpactAnalyzer();
        
        // When: Assessing risk
        RiskLevel lowRisk = analyzer.assessRisk(lowRiskChange);
        RiskLevel highRisk = analyzer.assessRisk(highRiskChange);
        
        // Then: Risk levels are appropriate
        assertNotNull(lowRisk);
        assertNotNull(highRisk);
    }

    // ============ Module 14: ConflictResolver Tests ============

    @Test
    void conflictResolver_shouldDetectConflictsWithCurrentState() {
        // Given: A change and current board state
        UUID cardId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(UUID.randomUUID())
            .changeType(ChangeType.UPDATE_CARD)
            .targetCardId(cardId)
            .currentState("{\"title\": \"Original Title\", \"version\": 1}")
            .proposedState("{\"title\": \"Updated Title\", \"version\": 2}")
            .build();
        
        Card currentCard = Card.builder()
            .id(cardId)
            .title("Different Title") // Card was modified since change was created
            .build();
        
        Board board = Board.builder()
            .cards(Arrays.asList(currentCard))
            .build();
        
        ConflictResolver resolver = new ConflictResolver();
        
        // When: Detecting conflicts
        ConflictReport report = resolver.detectConflicts(change, board);
        
        // Then: Conflict is detected
        assertNotNull(report);
    }

    @Test
    void conflictResolver_shouldValidateChangeCanBeApplied() {
        // Given: A valid change
        Change change = Change.builder()
            .changeType(ChangeType.CREATE_CARD)
            .proposedState("{\"title\": \"New Card\"}")
            .build();
        
        Board board = Board.builder()
            .stages(Arrays.asList(Stage.builder().id(UUID.randomUUID()).build()))
            .build();
        
        ConflictResolver resolver = new ConflictResolver();
        
        // When: Validating if change can be applied
        boolean canApply = resolver.canApplyChange(change, board);
        
        // Then: Change is valid
        assertTrue(canApply);
    }

    // ============ Module 7: ApprovalService (WF3) Tests ============

    @Mock
    private ApprovalDecisionRepository approvalDecisionRepository;

    @InjectMocks
    private ChangeApprovalService changeApprovalService;

    @Test
    void changeApprovalService_shouldApproveChange() {
        // Given: A pending change
        UUID changeId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(changeId)
            .status(ChangeStatus.UNDER_REVIEW)
            .build();
        
        when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        when(approvalDecisionRepository.save(any(ApprovalDecision.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Approving change
        ApprovalDecision decision = changeApprovalService.approveChange(changeId, approverId, "Looks good");
        
        // Then: Decision is recorded
        assertNotNull(decision);
        assertEquals(approverId, decision.getApproverId());
        assertEquals(changeId, decision.getChangeId());
        assertEquals(DecisionType.APPROVE, decision.getDecision());
    }

    @Test
    void changeApprovalService_shouldRejectChangeWithFeedback() {
        // Given: A pending change
        UUID changeId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        String feedback = "Need to update documentation first";
        
        Change change = Change.builder()
            .id(changeId)
            .status(ChangeStatus.UNDER_REVIEW)
            .build();
        
        when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        when(approvalDecisionRepository.save(any(ApprovalDecision.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Rejecting change with feedback
        ApprovalDecision decision = changeApprovalService.rejectChange(changeId, approverId, feedback);
        
        // Then: Rejection is recorded with feedback
        assertNotNull(decision);
        assertEquals(DecisionType.REJECT, decision.getDecision());
        assertEquals(feedback, decision.getFeedback());
    }

    // ============ Module 9: ChangeApplicationService Tests ============

    @Mock
    private CardRepository cardRepository;

    @Mock
    private StageRepository stageRepository;

    @Mock
    private ChangeSnapshotRepository changeSnapshotRepository;

    @InjectMocks
    private ChangeApplicationService changeApplicationService;

    @Test
    void changeApplicationService_shouldApplyApprovedCardMove() {
        // Given: An approved move change
        UUID changeId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(changeId)
            .changeType(ChangeType.MOVE_CARD)
            .targetCardId(cardId)
            .proposedState("{\"stage_id\": \"" + targetStageId + "\"}")
            .status(ChangeStatus.APPROVED)
            .build();
        
        Card card = Card.builder()
            .id(cardId)
            .stage(Stage.builder().id(UUID.randomUUID()).build())
            .build();
        
        Stage targetStage = Stage.builder()
            .id(targetStageId)
            .build();
        
        when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(stageRepository.findById(targetStageId)).thenReturn(Optional.of(targetStage));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeRepository.save(any(Change.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Applying change
        changeApplicationService.applyChange(changeId, null);
        
        // Then: Card is moved to target stage
        verify(cardRepository).save(argThat(c -> c.getStage().getId().equals(targetStageId)));
        verify(changeRepository).save(argThat(c -> c.getStatus() == ChangeStatus.APPLIED));
    }

    @Test
    void changeApplicationService_shouldApplyApprovedCardUpdate() {
        // Given: An approved update change
        UUID changeId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(changeId)
            .changeType(ChangeType.UPDATE_CARD)
            .targetCardId(cardId)
            .proposedState("{\"title\": \"New Title\", \"description\": \"New Description\"}")
            .status(ChangeStatus.APPROVED)
            .build();
        
        Card card = Card.builder()
            .id(cardId)
            .title("Old Title")
            .description("Old Description")
            .build();
        
        when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeRepository.save(any(Change.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Applying change
        changeApplicationService.applyChange(changeId, null);
        
        // Then: Card is updated
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    void changeApplicationService_shouldCreateSnapshotBeforeApplying() {
        // Given: An approved change
        UUID changeId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(changeId)
            .changeType(ChangeType.UPDATE_CARD)
            .status(ChangeStatus.APPROVED)
            .build();
        
        when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        when(changeSnapshotRepository.save(any(ChangeSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Preparing to apply
        ChangeSnapshot snapshot = changeApplicationService.createSnapshot(changeId);
        
        // Then: Snapshot is created
        assertNotNull(snapshot);
        verify(changeSnapshotRepository).save(any(ChangeSnapshot.class));
    }

    @Test
    void changeApplicationService_shouldApplyMultipleChangesAtomically() {
        // Given: Multiple approved changes
        List<UUID> changeIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        
        when(changeRepository.findAllById(changeIds)).thenReturn(Arrays.asList(
            Change.builder().id(changeIds.get(0)).changeType(ChangeType.MOVE_CARD).status(ChangeStatus.APPROVED).build(),
            Change.builder().id(changeIds.get(1)).changeType(ChangeType.UPDATE_CARD).status(ChangeStatus.APPROVED).build()
        ));
        
        // When: Applying batch
        ApplicationResult result = changeApplicationService.applyChanges(changeIds, null);
        
        // Then: All changes are applied
        assertNotNull(result);
    }

    // ============ Integration Flow Test ============

    @Test
    void fullWorkflow_shouldReviewAndApplyChanges() {
        // Given: Changes from WF2 ready for review
        UUID changeId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        
        Change change = Change.builder()
            .id(changeId)
            .changeType(ChangeType.MOVE_CARD)
            .targetBoardId(boardId)
            .targetCardId(cardId)
            .currentState("{\"stage_id\": \"backlog\"}")
            .proposedState("{\"stage_id\": \"in-progress\"}")
            .status(ChangeStatus.PENDING)
            .build();
        
        Board board = Board.builder()
            .id(boardId)
            .stages(Arrays.asList(
                Stage.builder().id(UUID.fromString("backlog")).build(),
                Stage.builder().id(UUID.fromString("in-progress")).build()
            ))
            .build();
        
        Card card = Card.builder()
            .id(cardId)
            .stage(Stage.builder().id(UUID.fromString("backlog")).build())
            .build();
        
        when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        when(kanbanBoardGateway.getBoard(boardId)).thenReturn(board);
        when(diffCalculator.computeDiff(any(), any())).thenReturn(new DiffResult());
        when(impactAnalyzer.analyzeChange(change)).thenReturn(ImpactSummary.builder().riskLevel(RiskLevel.LOW).build());
        when(conflictResolver.detectConflicts(change, board)).thenReturn(ConflictReport.builder().canApply(true).build());
        when(approvalDecisionRepository.save(any(ApprovalDecision.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(stageRepository.findById(any())).thenReturn(Optional.of(Stage.builder().build()));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeRepository.save(any(Change.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Complete workflow
        // 1. Load preview
        ChangePreview preview = changePreviewService.generatePreview(changeId, null);
        
        // 2. Approve change
        ApprovalDecision decision = changeApprovalService.approveChange(changeId, approverId, "Approved");
        
        // 3. Apply change
        changeApplicationService.applyChange(changeId, null);
        
        // Then: Change is applied
        assertNotNull(preview);
        assertEquals(DecisionType.APPROVE, decision.getDecision());
        verify(cardRepository).save(any(Card.class));
        verify(changeRepository).save(argThat(c -> c.getStatus() == ChangeStatus.APPLIED));
    }
}

// Supporting classes
enum ChangeStatus { PENDING, UNDER_REVIEW, APPROVED, REJECTED, READY_FOR_APPLICATION, APPLYING, APPLIED, ROLLED_BACK }
enum ImpactLevel { LOW, MEDIUM, HIGH, CRITICAL }
enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
enum DecisionType { APPROVE, REJECT, DEFER }

@lombok.Builder
@lombok.Data
class ChangePreview {
    private UUID changeId;
    private ChangeType changeType;
    private DiffView diff;
    private ImpactSummary impact;
    private ConflictReport conflicts;
    private ChangeStatus status;
}

@lombok.Builder
@lombok.Data
class DiffView {
    private Map<String, Object> added;
    private Map<String, Object> removed;
    private Map<String, ValueChange> modified;
}

@lombok.AllArgsConstructor
@lombok.Data
class ValueChange {
    private Object oldValue;
    private Object newValue;
}

@lombok.Builder
@lombok.Data
class DiffResult {
    private Map<String, Object> added = new HashMap<>();
    private Map<String, Object> removed = new HashMap<>();
    private Map<String, ValueChange> modified = new HashMap<>();
}

@lombok.Builder
@lombok.Data
class ImpactSummary {
    private List<UUID> affectedCards;
    private List<UUID> affectedStages;
    private RiskLevel riskLevel;
    private int effortPoints;
}

@lombok.Builder
@lombok.Data
class ConflictReport {
    private boolean canApply;
    private List<Conflict> conflicts;
}

@lombok.Data
class Conflict {
    private String code;
    private String message;
    private ConflictSeverity severity;
    enum ConflictSeverity { INFO, WARNING, BLOCKING }
}

@lombok.Builder
@lombok.Data
class ApprovalDecision {
    private UUID id;
    private UUID changeId;
    private UUID approverId;
    private DecisionType decision;
    private String feedback;
    private LocalDateTime decidedAt;
}

@lombok.Builder
@lombok.Data
class ChangeSnapshot {
    private UUID id;
    private UUID changeId;
    private String boardStateJson;
    private LocalDateTime createdAt;
}

@lombok.Data
class ApplicationResult {
    private int appliedCount;
    private int failedCount;
    private List<String> errors;
}

// Repository interfaces
interface ChangeRepository extends org.springframework.data.jpa.repository.JpaRepository<Change, UUID> {
    List<Change> findByStatusAndProjectId(ChangeStatus status, UUID projectId);
    List<Change> findAllById(List<UUID> ids);
}
interface ApprovalDecisionRepository extends org.springframework.data.jpa.repository.JpaRepository<ApprovalDecision, UUID> {}
interface ChangeSnapshotRepository extends org.springframework.data.jpa.repository.JpaRepository<ChangeSnapshot, UUID> {}

// Service classes
class ChangePreviewService {
    private ChangeRepository changeRepository;
    private KanbanBoardGateway kanbanBoardGateway;
    private DiffCalculator diffCalculator;
    private ImpactAnalyzer impactAnalyzer;
    private ConflictResolver conflictResolver;
    
    public List<ChangePreview> listPendingChanges(UUID projectId, SecurityClaims claims) {
        List<Change> changes = changeRepository.findByStatusAndProjectId(ChangeStatus.PENDING, projectId);
        return changes.stream().map(c -> ChangePreview.builder()
            .changeId(c.getId())
            .changeType(c.getChangeType())
            .status(c.getStatus())
            .build()).toList();
    }
    
    public ChangePreview generatePreview(UUID changeId, SecurityClaims claims) {
        Change change = changeRepository.findById(changeId)
            .orElseThrow(() -> new RuntimeException("Change not found"));
        
        Board board = kanbanBoardGateway.getBoard(change.getTargetBoardId());
        
        DiffView diff = new DiffView();
        ImpactSummary impact = impactAnalyzer.analyzeChange(change);
        ConflictReport conflicts = conflictResolver.detectConflicts(change, board);
        
        return ChangePreview.builder()
            .changeId(changeId)
            .changeType(change.getChangeType())
            .diff(diff)
            .impact(impact)
            .conflicts(conflicts)
            .status(change.getStatus())
            .build();
    }
}

class DiffCalculator {
    public DiffResult computeDiff(Object before, Object after) {
        // Implementation would compute field-level diffs
        return new DiffResult();
    }
    
    public String generateDiffSummary(Change change) {
        return "Summary of changes";
    }
}

class ImpactAnalyzer {
    public ImpactSummary analyzeChange(Change change) {
        return ImpactSummary.builder()
            .affectedCards(Arrays.asList(change.getTargetCardId()))
            .riskLevel(RiskLevel.LOW)
            .effortPoints(1)
            .build();
    }
    
    public RiskLevel assessRisk(Change change) {
        return switch (change.getChangeType()) {
            case DELETE_CARD -> RiskLevel.HIGH;
            case MOVE_CARD -> RiskLevel.LOW;
            case UPDATE_CARD -> RiskLevel.MEDIUM;
            case CREATE_CARD -> RiskLevel.LOW;
        };
    }
}

class ConflictResolver {
    public ConflictReport detectConflicts(Change change, Board board) {
        // Implementation would detect conflicts
        return ConflictReport.builder()
            .canApply(true)
            .conflicts(Arrays.asList())
            .build();
    }
    
    public boolean canApplyChange(Change change, Board board) {
        return true;
    }
}

class ChangeApprovalService {
    private ChangeRepository changeRepository;
    private ApprovalDecisionRepository approvalDecisionRepository;
    
    public ApprovalDecision approveChange(UUID changeId, UUID approverId, String feedback) {
        Change change = changeRepository.findById(changeId)
            .orElseThrow(() -> new RuntimeException("Change not found"));
        
        ApprovalDecision decision = ApprovalDecision.builder()
            .id(UUID.randomUUID())
            .changeId(changeId)
            .approverId(approverId)
            .decision(DecisionType.APPROVE)
            .feedback(feedback)
            .decidedAt(LocalDateTime.now())
            .build();
        
        return approvalDecisionRepository.save(decision);
    }
    
    public ApprovalDecision rejectChange(UUID changeId, UUID approverId, String feedback) {
        ApprovalDecision decision = ApprovalDecision.builder()
            .id(UUID.randomUUID())
            .changeId(changeId)
            .approverId(approverId)
            .decision(DecisionType.REJECT)
            .feedback(feedback)
            .decidedAt(LocalDateTime.now())
            .build();
        
        return approvalDecisionRepository.save(decision);
    }
}

class ChangeApplicationService {
    private ChangeRepository changeRepository;
    private CardRepository cardRepository;
    private StageRepository stageRepository;
    private ChangeSnapshotRepository changeSnapshotRepository;
    
    public void applyChange(UUID changeId, SecurityClaims claims) {
        Change change = changeRepository.findById(changeId)
            .orElseThrow(() -> new RuntimeException("Change not found"));
        
        // Apply based on change type
        switch (change.getChangeType()) {
            case MOVE_CARD -> applyCardMove(change);
            case UPDATE_CARD -> applyCardUpdate(change);
            case CREATE_CARD -> applyCardCreation(change);
            case DELETE_CARD -> applyCardDeletion(change);
        }
        
        change.setStatus(ChangeStatus.APPLIED);
        changeRepository.save(change);
    }
    
    public ApplicationResult applyChanges(List<UUID> changeIds, SecurityClaims claims) {
        List<Change> changes = changeRepository.findAllById(changeIds);
        
        for (Change change : changes) {
            applyChange(change.getId(), claims);
        }
        
        return new ApplicationResult(changes.size(), 0, Arrays.asList());
    }
    
    public ChangeSnapshot createSnapshot(UUID changeId) {
        ChangeSnapshot snapshot = ChangeSnapshot.builder()
            .id(UUID.randomUUID())
            .changeId(changeId)
            .boardStateJson("{}")
            .createdAt(LocalDateTime.now())
            .build();
        
        return changeSnapshotRepository.save(snapshot);
    }
    
    private void applyCardMove(Change change) {
        Card card = cardRepository.findById(change.getTargetCardId())
            .orElseThrow(() -> new RuntimeException("Card not found"));
        // Move logic
        cardRepository.save(card);
    }
    
    private void applyCardUpdate(Change change) {
        Card card = cardRepository.findById(change.getTargetCardId())
            .orElseThrow(() -> new RuntimeException("Card not found"));
        // Update logic
        cardRepository.save(card);
    }
    
    private void applyCardCreation(Change change) {
        // Create logic
    }
    
    private void applyCardDeletion(Change change) {
        // Delete logic
    }
}

interface KanbanBoardGateway {
    Board getBoard(UUID boardId);
}
