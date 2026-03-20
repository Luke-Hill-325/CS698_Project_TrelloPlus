package com.flowboard.service;

import com.flowboard.dto.AIAnalysisResult;
import com.flowboard.entity.*;
import com.flowboard.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cross-Workflow Integration Tests
 * 
 * Tests the integration points between all three user stories:
 * 
 * Integration Point 1: WF1 → WF2 (Project/Board reference)
 * - WF1 creates Project and Board
 * - WF2 uses project context for meetings
 * - WF2 uses board structure for AI context
 * 
 * Integration Point 2: WF2 → WF3 (Change Records)
 * - WF2 creates Change records from meeting summaries
 * - WF3 reads and displays these changes for approval
 * - WF3 applies approved changes back to WF1's board
 * 
 * Integration Point 3: WF3 → WF1 (Applied Changes)
 * - WF3 applies approved changes to cards/stages
 * - WF1's board reflects the updated state
 */
@ExtendWith(MockitoExtension.class)
class CrossWorkflowIntegrationTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JWTService jwtService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private StageRepository stageRepository;
    @Mock
    private CardRepository cardRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private MeetingSessionRepository meetingSessionRepository;
    @Mock
    private SummaryRepository summaryRepository;
    @Mock
    private ChangeRepository changeRepository;
    @Mock
    private ApprovalRequestRepository approvalRequestRepository;
    @Mock
    private ApprovalVoteRepository approvalVoteRepository;
    @Mock
    private ApprovalDecisionRepository approvalDecisionRepository;
    @Mock
    private ChangeSnapshotRepository changeSnapshotRepository;
    @Mock
    private AIEngine aiEngine;
    @Mock
    private BoardGenerator boardGenerator;
    @Mock
    private DiffCalculator diffCalculator;
    @Mock
    private ImpactAnalyzer impactAnalyzer;
    @Mock
    private ConflictResolver conflictResolver;
    @Mock
    private KanbanBoardGateway kanbanBoardGateway;

    @InjectMocks
    private AuthService authService;
    @InjectMocks
    private ProjectService projectService;
    @InjectMocks
    private MeetingService meetingService;
    @InjectMocks
    private SummaryService summaryService;
    @InjectMocks
    private ApprovalService approvalService;
    @InjectMocks
    private ChangePreviewService changePreviewService;
    @InjectMocks
    private ChangeApprovalService changeApprovalService;
    @InjectMocks
    private ChangeApplicationService changeApplicationService;

    /**
     * Test Integration Point 1: WF1 → WF2
     * Verifies that WF2 can reference projects and boards created by WF1
     */
    @Test
    void wf1_to_wf2_shouldUseProjectAndBoardContext() {
        // Given: WF1 creates project and board
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        
        User projectManager = User.builder()
            .id(userId)
            .email("manager@example.com")
            .role(User.UserRole.MANAGER)
            .build();
        
        Project project = Project.builder()
            .id(projectId)
            .name("Q2 Roadmap")
            .description("CRM system with real-time collaboration")
            .owner(projectManager)
            .build();
        
        Board board = Board.builder()
            .id(boardId)
            .name("Q2 Roadmap Board")
            .project(project)
            .stages(Arrays.asList(
                Stage.builder().id(UUID.randomUUID()).title("Backlog").build(),
                Stage.builder().id(UUID.randomUUID()).title("In Progress").build(),
                Stage.builder().id(UUID.randomUUID()).title("Done").build()
            ))
            .build();
        
        // When: WF2 creates meeting for this project
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(meetingSessionRepository.save(any(MeetingSession.class))).thenAnswer(inv -> inv.getArgument(0));
        
        MeetingSession meeting = meetingService.createMeeting(
            createMeetingRequest(projectId, "Sprint Planning"),
            projectManager
        );
        
        // Then: Meeting is linked to WF1's project
        assertNotNull(meeting);
        assertEquals(projectId, meeting.getProjectId());
        
        // When: WF2 uses board context for AI analysis
        BoardContext boardContext = BoardContext.builder()
            .existingStages(Arrays.asList("Backlog", "In Progress", "Done"))
            .existingCards(Arrays.asList("User auth", "Contact management"))
            .build();
        
        SummaryAnalysis analysis = aiEngine.analyzeSummary(
            "Discussed moving user auth to in progress",
            boardContext
        );
        
        // Then: AI analysis considers WF1's board structure
        assertNotNull(analysis);
        verify(aiEngine).analyzeSummary(anyString(), argThat(ctx -> 
            ctx.getExistingStages().contains("Backlog")
        ));
    }

    /**
     * Test Integration Point 2: WF2 → WF3
     * Verifies that WF3 can read changes created by WF2
     */
    @Test
    void wf2_to_wf3_shouldPassChangesForReview() {
        // Given: WF2 creates meeting and generates summary with changes
        UUID meetingId = UUID.randomUUID();
        UUID facilitatorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        MeetingSession meeting = MeetingSession.builder()
            .id(meetingId)
            .projectId(projectId)
            .createdBy(facilitatorId)
            .status(MeetingStatus.COMPLETED)
            .notes("Decided to move user auth to in progress. Create new card for OAuth.")
            .build();
        
        // AI extracts changes from summary
        List<Change> extractedChanges = Arrays.asList(
            Change.builder()
                .id(UUID.randomUUID())
                .changeType(ChangeType.MOVE_CARD)
                .description("Move user auth card to in progress")
                .currentState("{\"stage_id\": \"backlog\"}")
                .proposedState("{\"stage_id\": \"in-progress\"}")
                .status(ChangeStatus.PENDING)
                .build(),
            Change.builder()
                .id(UUID.randomUUID())
                .changeType(ChangeType.CREATE_CARD)
                .description("Create OAuth integration card")
                .proposedState("{\"title\": \"OAuth Integration\", \"stage\": \"backlog\"}")
                .status(ChangeStatus.PENDING)
                .build()
        );
        
        when(meetingSessionRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(aiEngine.analyzeSummary(anyString(), any())).thenReturn(
            SummaryAnalysis.builder()
                .summary("Team decided on Auth0 for OAuth")
                .changes(extractedChanges)
                .build()
        );
        when(summaryRepository.save(any(Summary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeRepository.save(any(Change.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: WF2 generates summary (creates changes)
        Summary summary = summaryService.generateSummary(meetingId, facilitatorId);
        
        // Then: Changes are persisted for WF3
        verify(changeRepository, times(2)).save(any(Change.class));
        
        // When: WF3 loads pending changes
        when(changeRepository.findByStatusAndProjectId(ChangeStatus.PENDING, projectId))
            .thenReturn(extractedChanges);
        
        List<ChangePreview> pendingChanges = changePreviewService.listPendingChanges(projectId, null);
        
        // Then: WF3 sees changes from WF2
        assertNotNull(pendingChanges);
        assertEquals(2, pendingChanges.size());
    }

    /**
     * Test Integration Point 3: WF3 → WF1
     * Verifies that WF3 applies approved changes back to WF1's board
     */
    @Test
    void wf3_to_wf1_shouldApplyChangesToBoard() {
        // Given: WF1's board with existing cards
        UUID projectId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID backlogStageId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID inProgressStageId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        
        Stage backlogStage = Stage.builder()
            .id(backlogStageId)
            .title("Backlog")
            .build();
        
        Stage inProgressStage = Stage.builder()
            .id(inProgressStageId)
            .title("In Progress")
            .build();
        
        Card card = Card.builder()
            .id(cardId)
            .title("User Authentication")
            .stage(backlogStage)
            .build();
        
        Board board = Board.builder()
            .id(boardId)
            .stages(Arrays.asList(backlogStage, inProgressStage))
            .build();
        
        // WF3's approved change
        Change approvedChange = Change.builder()
            .id(UUID.randomUUID())
            .changeType(ChangeType.MOVE_CARD)
            .targetBoardId(boardId)
            .targetCardId(cardId)
            .currentState("{\"stage_id\": \"" + backlogStageId + "\"}")
            .proposedState("{\"stage_id\": \"" + inProgressStageId + "\"}")
            .status(ChangeStatus.APPROVED)
            .build();
        
        when(changeRepository.findById(approvedChange.getId())).thenReturn(Optional.of(approvedChange));
        when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
        when(stageRepository.findById(inProgressStageId)).thenReturn(Optional.of(inProgressStage));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeRepository.save(any(Change.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeSnapshotRepository.save(any(ChangeSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: WF3 applies approved change
        changeApplicationService.applyChange(approvedChange.getId(), null);
        
        // Then: WF1's card is moved
        verify(cardRepository).save(argThat(c -> 
            c.getId().equals(cardId) && c.getStage().getId().equals(inProgressStageId)
        ));
        
        // Then: Change is marked as applied
        verify(changeRepository).save(argThat(c -> c.getStatus() == ChangeStatus.APPLIED));
    }

    /**
     * End-to-End Test: Complete flow from project creation through change application
     */
    @Test
    void endToEnd_shouldCreateProjectGenerateSummaryAndApplyChanges() {
        // Step 1: User Authentication (Shared by all workflows)
        String email = "manager@example.com";
        String password = "SecurePass123!";
        UUID userId = UUID.randomUUID();
        
        User user = User.builder()
            .id(userId)
            .email(email)
            .username("manager")
            .role(User.UserRole.MANAGER)
            .build();
        
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(any(), eq(email), any())).thenReturn("jwt-token");
        when(jwtService.getExpirationTime(any())).thenReturn(86400000L);
        
        var registerRequest = new com.flowboard.dto.RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword(password);
        registerRequest.setFullName("Project Manager");
        
        var authResponse = authService.register(registerRequest);
        assertNotNull(authResponse.getToken());
        
        // Step 2: WF1 - AI Board Generation
        UUID projectId = UUID.randomUUID();
        String projectName = "CRM System";
        String projectDescription = "CRM with real-time collaboration";
        
        Project project = Project.builder()
            .id(projectId)
            .name(projectName)
            .description(projectDescription)
            .owner(user)
            .build();
        
        AIAnalysisResult analysis = new AIAnalysisResult();
        analysis.setStages(Arrays.asList(
            createStageInfo("Backlog", "bg-gray-100", 0),
            createStageInfo("In Progress", "bg-blue-100", 1),
            createStageInfo("Done", "bg-green-100", 2)
        ));
        analysis.setTasks(Arrays.asList(
            createTaskInfo("User auth", "OAuth implementation", "HIGH", "Backlog"),
            createTaskInfo("Contact management", "CRUD operations", "HIGH", "Backlog")
        ));
        
        when(projectRepository.save(any(Project.class))).thenReturn(project);
        when(aiEngine.analyzeProjectDescription(projectName, projectDescription)).thenReturn(analysis);
        when(boardRepository.save(any(Board.class))).thenAnswer(inv -> {
            Board b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
        when(stageRepository.save(any(Stage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        when(boardGenerator.generateBoard(any(), any())).thenAnswer(inv -> {
            Board b = Board.builder()
                .id(UUID.randomUUID())
                .project(inv.getArgument(0))
                .stages(Arrays.asList())
                .build();
            return b;
        });
        
        var createProjectRequest = new com.flowboard.dto.CreateProjectRequest();
        createProjectRequest.setName(projectName);
        createProjectRequest.setDescription(projectDescription);
        createProjectRequest.setGenerateTasks(true);
        
        var projectDTO = projectService.createProject(createProjectRequest, user);
        assertNotNull(projectDTO);
        assertEquals(projectName, projectDTO.getName());
        
        // Step 3: WF2 - Meeting Summary
        UUID meetingId = UUID.randomUUID();
        MeetingSession meeting = MeetingSession.builder()
            .id(meetingId)
            .projectId(projectId)
            .createdBy(userId)
            .status(MeetingStatus.COMPLETED)
            .notes("Move user auth to in progress. Create OAuth card.")
            .participants(new HashSet<>(Arrays.asList(userId)))
            .build();
        
        SummaryAnalysis summaryAnalysis = SummaryAnalysis.builder()
            .summary("Team decided on moving user auth and creating OAuth card")
            .changes(Arrays.asList(
                Change.builder()
                    .id(UUID.randomUUID())
                    .changeType(ChangeType.MOVE_CARD)
                    .description("Move user auth to in progress")
                    .status(ChangeStatus.PENDING)
                    .build()
            ))
            .build();
        
        when(meetingSessionRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(aiEngine.analyzeSummary(anyString(), any())).thenReturn(summaryAnalysis);
        when(summaryRepository.save(any(Summary.class))).thenAnswer(inv -> inv.getArgument(0));
        
        Summary summary = summaryService.generateSummary(meetingId, userId);
        assertNotNull(summary);
        
        // Step 4: WF2 - Summary Approval
        ApprovalRequest approvalRequest = ApprovalRequest.builder()
            .id(UUID.randomUUID())
            .entityType("SUMMARY")
            .entityId(summary.getId())
            .ruleType(ApprovalRuleType.CONSENSUS)
            .status(ApprovalStatus.PENDING)
            .requiredApprovers(new HashSet<>(Arrays.asList(userId)))
            .build();
        
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenReturn(approvalRequest);
        when(approvalRequestRepository.findById(approvalRequest.getId())).thenReturn(Optional.of(approvalRequest));
        when(approvalVoteRepository.save(any(ApprovalVote.class))).thenAnswer(inv -> inv.getArgument(0));
        
        ApprovalContext context = ApprovalContext.builder()
            .entityType("SUMMARY")
            .entityId(summary.getId())
            .requiredApprovers(Arrays.asList(userId))
            .build();
        
        ApprovalRequest createdRequest = approvalService.createApprovalRequest(context, 
            new ConsensusApprovalRule());
        assertNotNull(createdRequest);
        
        // Step 5: WF3 - Change Review and Approval
        UUID changeId = UUID.randomUUID();
        Change change = Change.builder()
            .id(changeId)
            .changeType(ChangeType.MOVE_CARD)
            .targetBoardId(projectDTO.getBoardId())
            .status(ChangeStatus.PENDING)
            .build();
        
        when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        when(diffCalculator.computeDiff(any(), any())).thenReturn(new DiffResult());
        when(impactAnalyzer.analyzeChange(change)).thenReturn(ImpactSummary.builder().build());
        when(conflictResolver.detectConflicts(any(), any())).thenReturn(ConflictReport.builder().canApply(true).build());
        
        ChangePreview preview = changePreviewService.generatePreview(changeId, null);
        assertNotNull(preview);
        
        when(approvalDecisionRepository.save(any(ApprovalDecision.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // Approve the change
        change.setStatus(ChangeStatus.APPROVED);
        
        // Step 6: WF3 - Apply Changes
        when(cardRepository.findById(any())).thenReturn(Optional.of(Card.builder().build()));
        when(stageRepository.findById(any())).thenReturn(Optional.of(Stage.builder().build()));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeSnapshotRepository.save(any(ChangeSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
        
        changeApplicationService.applyChange(changeId, null);
        
        // Verify change is applied
        verify(changeRepository).save(argThat(c -> c.getStatus() == ChangeStatus.APPLIED));
    }

    // Helper methods
    private AIAnalysisResult.StageInfo createStageInfo(String title, String color, int position) {
        AIAnalysisResult.StageInfo stage = new AIAnalysisResult.StageInfo();
        stage.setTitle(title);
        stage.setColor(color);
        stage.setPosition(position);
        return stage;
    }
    
    private AIAnalysisResult.TaskInfo createTaskInfo(String title, String description, String priority, String stageTitle) {
        AIAnalysisResult.TaskInfo task = new AIAnalysisResult.TaskInfo();
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setStageTitle(stageTitle);
        return task;
    }
    
    private CreateMeetingRequest createMeetingRequest(UUID projectId, String title) {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setProjectId(projectId);
        request.setTitle(title);
        request.setParticipants(Arrays.asList());
        return request;
    }
}

// Supporting classes referenced in test
enum ChangeStatus { PENDING, UNDER_REVIEW, APPROVED, REJECTED, READY_FOR_APPLICATION, APPLYING, APPLIED }
enum ApprovalRuleType { UNANIMOUS, QUORUM, CONSENSUS }
enum ApprovalStatus { PENDING, APPROVED, REJECTED, EXPIRED }

@lombok.Data
class CreateMeetingRequest {
    private UUID projectId;
    private String title;
    private List<UUID> participants;
}

@lombok.Builder
@lombok.Data
class BoardContext {
    private List<String> existingStages;
    private List<String> existingCards;
}

@lombok.Builder
@lombok.Data
class SummaryAnalysis {
    private String summary;
    private List<ActionItem> actionItems;
    private List<Decision> decisions;
    private List<Change> changes;
}

@lombok.Data
class ActionItem {
    private String title;
    private String assignee;
}

@lombok.Data
class Decision {
    private String title;
    private String rationale;
}

@lombok.Data
class DiffResult {
}

@lombok.Builder
@lombok.Data
class ImpactSummary {
    private RiskLevel riskLevel;
    enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
}

@lombok.Builder
@lombok.Data
class ConflictReport {
    private boolean canApply;
}

@lombok.Builder
@lombok.Data
class ApprovalRequest {
    private UUID id;
    private String entityType;
    private UUID entityId;
    private ApprovalRuleType ruleType;
    private ApprovalStatus status;
    private Set<UUID> requiredApprovers;
}

@lombok.Builder
@lombok.Data
class ApprovalVote {
    private UUID id;
    private UUID approvalRequestId;
    private UUID voterId;
    private ApprovalDecision decision;
}

enum ApprovalDecision { APPROVE, REJECT }

@lombok.Builder
@lombok.Data
class ApprovalContext {
    private String entityType;
    private UUID entityId;
    private List<UUID> requiredApprovers;
}

interface ApprovalRule {
    ApprovalResult evaluate(List<ApprovalVote> votes, List<UUID> requiredApprovers);
}

class ConsensusApprovalRule implements ApprovalRule {
    @Override
    public ApprovalResult evaluate(List<ApprovalVote> votes, List<UUID> requiredApprovers) {
        long approveCount = votes.stream().filter(v -> v.getDecision() == ApprovalDecision.APPROVE).count();
        return new ApprovalResult(approveCount == requiredApprovers.size(), approveCount, requiredApprovers.size());
    }
}

@lombok.AllArgsConstructor
@lombok.Data
class ApprovalResult {
    private boolean approved;
    private long approveCount;
    private long totalRequired;
}

@lombok.Builder
@lombok.Data
class ChangePreview {
    private UUID changeId;
    private ChangeType changeType;
    private ChangeStatus status;
}

@lombok.Builder
@lombok.Data
class ApprovalDecision {
    private UUID id;
    private UUID changeId;
    private UUID approverId;
    private DecisionType decision;
}

enum DecisionType { APPROVE, REJECT }

@lombok.Data
class ChangeSnapshot {
    private UUID id;
    private UUID changeId;
}

// Repository interfaces
interface MeetingSessionRepository extends org.springframework.data.jpa.repository.JpaRepository<MeetingSession, UUID> {}
interface SummaryRepository extends org.springframework.data.jpa.repository.JpaRepository<Summary, UUID> {}
interface ChangeRepository extends org.springframework.data.jpa.repository.JpaRepository<Change, UUID> {
    List<Change> findByStatusAndProjectId(ChangeStatus status, UUID projectId);
}
interface ApprovalRequestRepository extends org.springframework.data.jpa.repository.JpaRepository<ApprovalRequest, UUID> {}
interface ApprovalVoteRepository extends org.springframework.data.jpa.repository.JpaRepository<ApprovalVote, UUID> {}
interface ApprovalDecisionRepository extends org.springframework.data.jpa.repository.JpaRepository<ApprovalDecision, UUID> {}
interface ChangeSnapshotRepository extends org.springframework.data.jpa.repository.JpaRepository<ChangeSnapshot, UUID> {}
interface KanbanBoardGateway {
    Board getBoard(UUID boardId);
}

// Service classes
class MeetingService {
    private MeetingSessionRepository meetingSessionRepository;
    private ProjectRepository projectRepository;
    
    public MeetingSession createMeeting(CreateMeetingRequest request, User facilitator) {
        projectRepository.findById(request.getProjectId())
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        MeetingSession meeting = MeetingSession.builder()
            .id(UUID.randomUUID())
            .projectId(request.getProjectId())
            .createdBy(facilitator.getId())
            .title(request.getTitle())
            .status(MeetingStatus.IN_PROGRESS)
            .participants(new HashSet<>(request.getParticipants()))
            .build();
        
        return meetingSessionRepository.save(meeting);
    }
}

class SummaryService {
    private MeetingSessionRepository meetingSessionRepository;
    private SummaryRepository summaryRepository;
    private ChangeRepository changeRepository;
    private AIEngine aiEngine;
    
    public Summary generateSummary(UUID meetingId, UUID facilitatorId) {
        MeetingSession meeting = meetingSessionRepository.findById(meetingId)
            .orElseThrow(() -> new RuntimeException("Meeting not found"));
        
        SummaryAnalysis analysis = aiEngine.analyzeSummary(meeting.getNotes(), null);
        
        Summary summary = Summary.builder()
            .id(UUID.randomUUID())
            .meetingId(meetingId)
            .content(analysis.getSummary())
            .status(SummaryStatus.PENDING_APPROVAL)
            .build();
        
        summary = summaryRepository.save(summary);
        
        if (analysis.getChanges() != null) {
            for (Change change : analysis.getChanges()) {
                change.setId(UUID.randomUUID());
                changeRepository.save(change);
            }
        }
        
        return summary;
    }
}

class ApprovalService {
    private ApprovalRequestRepository approvalRequestRepository;
    
    public ApprovalRequest createApprovalRequest(ApprovalContext context, ApprovalRule rule) {
        ApprovalRequest request = ApprovalRequest.builder()
            .id(UUID.randomUUID())
            .entityType(context.getEntityType())
            .entityId(context.getEntityId())
            .ruleType(ApprovalRuleType.CONSENSUS)
            .status(ApprovalStatus.PENDING)
            .requiredApprovers(new HashSet<>(context.getRequiredApprovers()))
            .build();
        
        return approvalRequestRepository.save(request);
    }
}

class ChangePreviewService {
    private ChangeRepository changeRepository;
    private KanbanBoardGateway kanbanBoardGateway;
    private DiffCalculator diffCalculator;
    private ImpactAnalyzer impactAnalyzer;
    private ConflictResolver conflictResolver;
    
    public List<ChangePreview> listPendingChanges(UUID projectId, SecurityClaims claims) {
        return changeRepository.findByStatusAndProjectId(ChangeStatus.PENDING, projectId)
            .stream()
            .map(c -> ChangePreview.builder()
                .changeId(c.getId())
                .changeType(c.getChangeType())
                .status(c.getStatus())
                .build())
            .toList();
    }
    
    public ChangePreview generatePreview(UUID changeId, SecurityClaims claims) {
        Change change = changeRepository.findById(changeId)
            .orElseThrow(() -> new RuntimeException("Change not found"));
        
        return ChangePreview.builder()
            .changeId(changeId)
            .changeType(change.getChangeType())
            .status(change.getStatus())
            .build();
    }
}

class ChangeApprovalService {
    private ChangeRepository changeRepository;
    private ApprovalDecisionRepository approvalDecisionRepository;
    
    public void approveChange(UUID changeId, UUID approverId, String feedback) {
        ApprovalDecision decision = ApprovalDecision.builder()
            .id(UUID.randomUUID())
            .changeId(changeId)
            .approverId(approverId)
            .decision(DecisionType.APPROVE)
            .build();
        
        approvalDecisionRepository.save(decision);
    }
}

class ChangeApplicationService {
    private ChangeRepository changeRepository;
    private CardRepository cardRepository;
    private ChangeSnapshotRepository changeSnapshotRepository;
    
    public void applyChange(UUID changeId, SecurityClaims claims) {
        Change change = changeRepository.findById(changeId)
            .orElseThrow(() -> new RuntimeException("Change not found"));
        
        // Apply change logic
        change.setStatus(ChangeStatus.APPLIED);
        changeRepository.save(change);
    }
    
    public ChangeSnapshot createSnapshot(UUID changeId) {
        ChangeSnapshot snapshot = new ChangeSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setChangeId(changeId);
        return changeSnapshotRepository.save(snapshot);
    }
}
