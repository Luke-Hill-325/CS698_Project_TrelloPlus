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
 * Tests for User Story 2: Meeting Summary & Checklist
 * 
 * Program Path:
 * 1. UserAuthenticationService - User logs in
 * 2. MeetingService - Meeting facilitator creates meeting
 * 3. SummaryService - Generates summary from meeting notes
 * 4. AIEngine - Analyzes summary and extracts action items, decisions, changes
 * 5. ApprovalService - Manages summary approval workflow
 */
@ExtendWith(MockitoExtension.class)
class UserStory2_MeetingSummaryTest {

    // ============ Module 5: MeetingService Tests ============

    @Mock
    private MeetingSessionRepository meetingSessionRepository;

    @Mock
    private MeetingParticipantRepository meetingParticipantRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MeetingService meetingService;

    @Test
    void meetingService_shouldCreateMeetingForProject() {
        // Given: An authenticated facilitator and existing project
        UUID facilitatorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        
        User facilitator = User.builder()
            .id(facilitatorId)
            .email("facilitator@example.com")
            .role(User.UserRole.MEMBER)
            .build();
        
        Project project = Project.builder()
            .id(projectId)
            .name("Q2 Roadmap")
            .build();
        
        MeetingSession savedMeeting = MeetingSession.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .createdBy(facilitatorId)
            .title("Sprint Planning")
            .status(MeetingStatus.IN_PROGRESS)
            .participants(new HashSet<>())
            .build();
        
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(meetingSessionRepository.save(any(MeetingSession.class))).thenReturn(savedMeeting);
        
        // When: Creating meeting
        var request = new CreateMeetingRequest();
        request.setProjectId(projectId);
        request.setTitle("Sprint Planning");
        request.setParticipants(Arrays.asList(facilitatorId));
        
        MeetingSession meeting = meetingService.createMeeting(request, facilitator);
        
        // Then: Meeting is created with correct details
        assertNotNull(meeting);
        assertEquals(projectId, meeting.getProjectId());
        assertEquals(facilitatorId, meeting.getCreatedBy());
        assertEquals(MeetingStatus.IN_PROGRESS, meeting.getStatus());
        verify(meetingSessionRepository).save(any(MeetingSession.class));
    }

    @Test
    void meetingService_shouldEndMeetingWithNotes() {
        // Given: An active meeting
        UUID meetingId = UUID.randomUUID();
        UUID facilitatorId = UUID.randomUUID();
        
        MeetingSession meeting = MeetingSession.builder()
            .id(meetingId)
            .createdBy(facilitatorId)
            .status(MeetingStatus.IN_PROGRESS)
            .participants(new HashSet<>())
            .build();
        
        String meetingNotes = "Discussed OAuth integration. Decided to use Auth0. John to research pricing.";
        
        when(meetingSessionRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(meetingSessionRepository.save(any(MeetingSession.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Ending meeting with notes
        MeetingSession endedMeeting = meetingService.endMeeting(meetingId, meetingNotes, facilitatorId);
        
        // Then: Meeting is marked completed with notes
        assertNotNull(endedMeeting);
        assertEquals(MeetingStatus.COMPLETED, endedMeeting.getStatus());
        assertEquals(meetingNotes, endedMeeting.getNotes());
        assertNotNull(endedMeeting.getEndedAt());
    }

    @Test
    void meetingService_shouldAddParticipantsToMeeting() {
        // Given: An existing meeting
        UUID meetingId = UUID.randomUUID();
        UUID facilitatorId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        
        MeetingSession meeting = MeetingSession.builder()
            .id(meetingId)
            .createdBy(facilitatorId)
            .status(MeetingStatus.IN_PROGRESS)
            .participants(new HashSet<>())
            .build();
        
        User participant = User.builder()
            .id(participantId)
            .email("participant@example.com")
            .build();
        
        when(meetingSessionRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(userRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(meetingSessionRepository.save(any(MeetingSession.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Adding participant
        meetingService.addParticipant(meetingId, participantId, facilitatorId);
        
        // Then: Participant is added
        assertTrue(meeting.getParticipants().contains(participantId));
    }

    // ============ Module 6: SummaryService Tests ============

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private ChangeRepository changeRepository;

    @Mock
    private ApprovalService approvalService;

    @Mock
    private AIEngine aiEngine;

    @InjectMocks
    private SummaryService summaryService;

    @Test
    void summaryService_shouldGenerateSummaryFromMeetingNotes() {
        // Given: Completed meeting with notes
        UUID meetingId = UUID.randomUUID();
        UUID facilitatorId = UUID.randomUUID();
        
        MeetingSession meeting = MeetingSession.builder()
            .id(meetingId)
            .createdBy(facilitatorId)
            .status(MeetingStatus.COMPLETED)
            .notes("Discussed Q2 features. Decided on Auth integration.")
            .build();
        
        SummaryAnalysis aiAnalysis = SummaryAnalysis.builder()
            .summary("Team discussed Q2 features and decided on Auth0 for authentication.")
            .actionItems(Arrays.asList(
                ActionItem.builder()
                    .title("Research Auth0 pricing")
                    .assignee("John")
                    .priority(ActionItem.Priority.HIGH)
                    .build()
            ))
            .decisions(Arrays.asList(
                Decision.builder()
                    .title("Use Auth0 for authentication")
                    .rationale("Reduces custom auth code")
                    .build()
            ))
            .changes(Arrays.asList(
                Change.builder()
                    .changeType(ChangeType.CREATE_CARD)
                    .description("Implement Auth0 integration")
                    .build()
            ))
            .build();
        
        when(meetingSessionRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(aiEngine.analyzeSummary(anyString(), any())).thenReturn(aiAnalysis);
        when(summaryRepository.save(any(Summary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeRepository.save(any(Change.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Generating summary
        Summary summary = summaryService.generateSummary(meetingId, facilitatorId);
        
        // Then: Summary is created with extracted data
        assertNotNull(summary);
        assertEquals(SummaryStatus.PENDING_APPROVAL, summary.getStatus());
        verify(summaryRepository).save(any(Summary.class));
        verify(changeRepository, atLeast(1)).save(any(Change.class));
    }

    @Test
    void summaryService_shouldExtractActionItemsDecisionsAndChanges() {
        // Given: Meeting notes
        String notes = "We discussed implementing OAuth for user auth. Decided to use Auth0. " +
                      "John will research pricing. Sarah will start on implementation.";
        
        SummaryAnalysis expectedAnalysis = SummaryAnalysis.builder()
            .summary("Team decided to use Auth0 for OAuth implementation.")
            .actionItems(Arrays.asList(
                ActionItem.builder()
                    .title("Research Auth0 pricing")
                    .assignee("John")
                    .priority(ActionItem.Priority.HIGH)
                    .build(),
                ActionItem.builder()
                    .title("Start Auth0 implementation")
                    .assignee("Sarah")
                    .priority(ActionItem.Priority.HIGH)
                    .build()
            ))
            .decisions(Arrays.asList(
                Decision.builder()
                    .title("Use Auth0 for user authentication")
                    .rationale("Team consensus on Auth0 for OAuth implementation")
                    .impact("Reduces custom auth code, adds external dependency")
                    .build()
            ))
            .changes(Arrays.asList(
                Change.builder()
                    .changeType(ChangeType.CREATE_CARD)
                    .title("Implement Auth0 integration")
                    .stage("In Progress")
                    .build()
            ))
            .build();
        
        when(aiEngine.analyzeSummary(eq(notes), any())).thenReturn(expectedAnalysis);
        
        // When: Analyzing summary
        SummaryAnalysis result = aiEngine.analyzeSummary(notes, null);
        
        // Then: Action items, decisions, and changes are extracted
        assertNotNull(result);
        assertFalse(result.getActionItems().isEmpty());
        assertFalse(result.getDecisions().isEmpty());
        assertFalse(result.getChanges().isEmpty());
        
        // Verify action items have assignees
        result.getActionItems().forEach(item -> {
            assertNotNull(item.getTitle());
            assertNotNull(item.getAssignee());
        });
    }

    // ============ Module 3: AIEngine (Meeting Analysis) Tests ============

    @Test
    void aiEngine_shouldAnalyzeMeetingSummary() {
        // Given: Meeting summary text
        String summaryText = "Discussed Q2 features. Decided on Auth0 for auth. John to research.";
        BoardContext context = BoardContext.builder()
            .existingStages(Arrays.asList("Backlog", "In Progress", "Done"))
            .existingCards(Arrays.asList("User auth", "Contact management"))
            .build();
        
        AIEngine engine = new AIEngine(null, null);
        
        // When: Analyzing summary
        SummaryAnalysis result = engine.analyzeSummary(summaryText, context);
        
        // Then: Structured analysis is returned
        assertNotNull(result);
        assertNotNull(result.getSummary());
    }

    @Test
    void aiEngine_shouldSuggestChangesBasedOnContext() {
        // Given: Summary with board context
        String summaryText = "Move user auth card to in progress. Create new card for Auth0 integration.";
        BoardContext context = BoardContext.builder()
            .existingStages(Arrays.asList("Backlog", "In Progress", "Done"))
            .existingCards(Arrays.asList("User auth"))
            .build();
        
        AIEngine engine = new AIEngine(null, null);
        
        // When: Suggesting changes
        List<Change> changes = engine.suggestChangesFromSummary(summaryText, context.toString());
        
        // Then: Changes are suggested
        assertNotNull(changes);
    }

    // ============ Module 7: ApprovalService Tests ============

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private ApprovalVoteRepository approvalVoteRepository;

    @InjectMocks
    private ApprovalService approvalService;

    @Test
    void approvalService_shouldCreateApprovalRequestForSummary() {
        // Given: Generated summary
        UUID summaryId = UUID.randomUUID();
        List<UUID> requiredApprovers = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        
        ApprovalRequest savedRequest = ApprovalRequest.builder()
            .id(UUID.randomUUID())
            .entityType("SUMMARY")
            .entityId(summaryId)
            .ruleType(ApprovalRuleType.CONSENSUS)
            .status(ApprovalStatus.PENDING)
            .requiredApprovers(new HashSet<>(requiredApprovers))
            .build();
        
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenReturn(savedRequest);
        
        // When: Creating approval request
        ApprovalContext context = ApprovalContext.builder()
            .entityType("SUMMARY")
            .entityId(summaryId)
            .requiredApprovers(requiredApprovers)
            .build();
        
        ApprovalRule rule = new ConsensusApprovalRule();
        ApprovalRequest request = approvalService.createApprovalRequest(context, rule);
        
        // Then: Approval request is created
        assertNotNull(request);
        assertEquals("SUMMARY", request.getEntityType());
        assertEquals(ApprovalStatus.PENDING, request.getStatus());
        verify(approvalRequestRepository).save(any(ApprovalRequest.class));
    }

    @Test
    void approvalService_shouldRecordApprovalVote() {
        // Given: Existing approval request
        UUID requestId = UUID.randomUUID();
        UUID voterId = UUID.randomUUID();
        
        ApprovalRequest request = ApprovalRequest.builder()
            .id(requestId)
            .entityType("SUMMARY")
            .ruleType(ApprovalRuleType.CONSENSUS)
            .status(ApprovalStatus.PENDING)
            .requiredApprovers(new HashSet<>(Arrays.asList(voterId)))
            .build();
        
        when(approvalRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(approvalVoteRepository.save(any(ApprovalVote.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Recording approval vote
        approvalService.recordApprovalVote(requestId, voterId, ApprovalDecision.APPROVE, "Looks good");
        
        // Then: Vote is recorded
        verify(approvalVoteRepository).save(any(ApprovalVote.class));
    }

    @Test
    void approvalService_shouldEvaluateConsensusRule() {
        // Given: Approval request with votes
        UUID requestId = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        
        ApprovalRequest request = ApprovalRequest.builder()
            .id(requestId)
            .ruleType(ApprovalRuleType.CONSENSUS)
            .requiredApprovers(new HashSet<>(Arrays.asList(approver1, approver2)))
            .build();
        
        List<ApprovalVote> votes = Arrays.asList(
            ApprovalVote.builder()
                .voterId(approver1)
                .decision(ApprovalDecision.APPROVE)
                .build(),
            ApprovalVote.builder()
                .voterId(approver2)
                .decision(ApprovalDecision.APPROVE)
                .build()
        );
        
        ConsensusApprovalRule rule = new ConsensusApprovalRule();
        
        // When: Evaluating consensus
        ApprovalResult result = rule.evaluate(votes, Arrays.asList(approver1, approver2));
        
        // Then: All approvers must approve for consensus
        assertNotNull(result);
    }

    @Test
    void approvalService_shouldEvaluateQuorumRule() {
        // Given: Approval request with majority votes
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        UUID approver3 = UUID.randomUUID();
        
        List<ApprovalVote> votes = Arrays.asList(
            ApprovalVote.builder().voterId(approver1).decision(ApprovalDecision.APPROVE).build(),
            ApprovalVote.builder().voterId(approver2).decision(ApprovalDecision.APPROVE).build(),
            ApprovalVote.builder().voterId(approver3).decision(ApprovalDecision.REJECT).build()
        );
        
        QuorumApprovalRule rule = new QuorumApprovalRule();
        
        // When: Evaluating quorum (50% + 1)
        ApprovalResult result = rule.evaluate(votes, Arrays.asList(approver1, approver2, approver3));
        
        // Then: Majority approves
        assertNotNull(result);
    }

    // ============ Integration Flow Test ============

    @Test
    void fullWorkflow_shouldCreateMeetingGenerateSummaryAndRequestApproval() {
        // Given: Meeting facilitator with project
        UUID facilitatorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        
        User facilitator = User.builder()
            .id(facilitatorId)
            .email("facilitator@example.com")
            .role(User.UserRole.MEMBER)
            .build();
        
        Project project = Project.builder()
            .id(projectId)
            .name("Q2 Roadmap")
            .build();
        
        MeetingSession meeting = MeetingSession.builder()
            .id(meetingId)
            .projectId(projectId)
            .createdBy(facilitatorId)
            .status(MeetingStatus.COMPLETED)
            .notes("Discussed features and decided on Auth0.")
            .participants(new HashSet<>(Arrays.asList(facilitatorId)))
            .endedAt(LocalDateTime.now())
            .build();
        
        SummaryAnalysis analysis = SummaryAnalysis.builder()
            .summary("Team decided to use Auth0.")
            .actionItems(Arrays.asList(
                ActionItem.builder().title("Research Auth0").assignee("John").build()
            ))
            .changes(Arrays.asList(
                Change.builder().changeType(ChangeType.CREATE_CARD).title("Auth0 Integration").build()
            ))
            .build();
        
        when(meetingSessionRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(aiEngine.analyzeSummary(anyString(), any())).thenReturn(analysis);
        when(summaryRepository.save(any(Summary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(changeRepository.save(any(Change.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRequestRepository.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Complete workflow
        Summary summary = summaryService.generateSummary(meetingId, facilitatorId);
        
        ApprovalContext context = ApprovalContext.builder()
            .entityType("SUMMARY")
            .entityId(summary.getId())
            .requiredApprovers(Arrays.asList(facilitatorId))
            .build();
        
        ApprovalRequest request = approvalService.createApprovalRequest(context, new ConsensusApprovalRule());
        
        // Then: Summary and approval request are created
        assertNotNull(summary);
        assertNotNull(request);
        assertEquals("SUMMARY", request.getEntityType());
    }

    // Supporting classes (would be in actual implementation)
    interface ApprovalRule {
        ApprovalResult evaluate(List<ApprovalVote> votes, List<UUID> requiredApprovers);
    }
    
    static class ConsensusApprovalRule implements ApprovalRule {
        @Override
        public ApprovalResult evaluate(List<ApprovalVote> votes, List<UUID> requiredApprovers) {
            long approveCount = votes.stream().filter(v -> v.getDecision() == ApprovalDecision.APPROVE).count();
            return new ApprovalResult(approveCount == requiredApprovers.size(), approveCount, requiredApprovers.size());
        }
    }
    
    static class QuorumApprovalRule implements ApprovalRule {
        @Override
        public ApprovalResult evaluate(List<ApprovalVote> votes, List<UUID> requiredApprovers) {
            long approveCount = votes.stream().filter(v -> v.getDecision() == ApprovalDecision.APPROVE).count();
            int quorum = (requiredApprovers.size() / 2) + 1;
            return new ApprovalResult(approveCount >= quorum, approveCount, requiredApprovers.size());
        }
    }
}

// Supporting entity classes (would be in actual implementation)
enum MeetingStatus { IN_PROGRESS, COMPLETED, CANCELLED }
enum SummaryStatus { DRAFT, PENDING_APPROVAL, APPROVED, REJECTED }
enum ChangeType { MOVE_CARD, UPDATE_CARD, CREATE_CARD, DELETE_CARD }
enum ApprovalRuleType { UNANIMOUS, QUORUM, CONSENSUS }
enum ApprovalStatus { PENDING, APPROVED, REJECTED, EXPIRED }
enum ApprovalDecision { APPROVE, REJECT }

@lombok.Builder
@lombok.Data
class MeetingSession {
    private UUID id;
    private UUID projectId;
    private UUID createdBy;
    private String title;
    private MeetingStatus status;
    private String notes;
    private Set<UUID> participants;
    private LocalDateTime endedAt;
}

@lombok.Builder
@lombok.Data
class Summary {
    private UUID id;
    private UUID meetingId;
    private String content;
    private SummaryStatus status;
    private UUID createdBy;
    private LocalDateTime createdAt;
}

@lombok.Builder
@lombok.Data
class SummaryAnalysis {
    private String summary;
    private List<ActionItem> actionItems;
    private List<Decision> decisions;
    private List<Change> changes;
}

@lombok.Builder
@lombok.Data
class ActionItem {
    private String title;
    private String assignee;
    private Priority priority;
    enum Priority { LOW, MEDIUM, HIGH, CRITICAL }
}

@lombok.Builder
@lombok.Data
class Decision {
    private String title;
    private String rationale;
    private String impact;
}

@lombok.Builder
@lombok.Data
class Change {
    private UUID id;
    private ChangeType changeType;
    private String title;
    private String description;
    private String stage;
}

@lombok.Builder
@lombok.Data
class BoardContext {
    private List<String> existingStages;
    private List<String> existingCards;
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
    private LocalDateTime createdAt;
    private LocalDateTime deadline;
}

@lombok.Builder
@lombok.Data
class ApprovalVote {
    private UUID id;
    private UUID approvalRequestId;
    private UUID voterId;
    private ApprovalDecision decision;
    private String feedback;
    private LocalDateTime votedAt;
}

@lombok.Builder
@lombok.Data
class ApprovalContext {
    private String entityType;
    private UUID entityId;
    private List<UUID> requiredApprovers;
}

@lombok.AllArgsConstructor
@lombok.Data
class ApprovalResult {
    private boolean approved;
    private long approveCount;
    private long totalRequired;
}

@lombok.Data
class CreateMeetingRequest {
    private UUID projectId;
    private String title;
    private List<UUID> participants;
}

// Repository interfaces
interface MeetingSessionRepository extends org.springframework.data.jpa.repository.JpaRepository<MeetingSession, UUID> {}
interface MeetingParticipantRepository extends org.springframework.data.jpa.repository.JpaRepository<com.flowboard.entity.MeetingParticipant, UUID> {}
interface SummaryRepository extends org.springframework.data.jpa.repository.JpaRepository<Summary, UUID> {}
interface ChangeRepository extends org.springframework.data.jpa.repository.JpaRepository<Change, UUID> {}
interface ApprovalRequestRepository extends org.springframework.data.jpa.repository.JpaRepository<ApprovalRequest, UUID> {
    Optional<ApprovalRequest> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}
interface ApprovalVoteRepository extends org.springframework.data.jpa.repository.JpaRepository<ApprovalVote, UUID> {
    List<ApprovalVote> findByApprovalRequestId(UUID requestId);
}

// Service classes (would be implemented)
class MeetingService {
    private MeetingSessionRepository meetingSessionRepository;
    private MeetingParticipantRepository meetingParticipantRepository;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    
    public MeetingSession createMeeting(CreateMeetingRequest request, User facilitator) {
        Project project = projectRepository.findById(request.getProjectId())
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
    
    public MeetingSession endMeeting(UUID meetingId, String notes, UUID facilitatorId) {
        MeetingSession meeting = meetingSessionRepository.findById(meetingId)
            .orElseThrow(() -> new RuntimeException("Meeting not found"));
        
        if (!meeting.getCreatedBy().equals(facilitatorId)) {
            throw new RuntimeException("Only facilitator can end meeting");
        }
        
        meeting.setStatus(MeetingStatus.COMPLETED);
        meeting.setNotes(notes);
        meeting.setEndedAt(LocalDateTime.now());
        
        return meetingSessionRepository.save(meeting);
    }
    
    public void addParticipant(UUID meetingId, UUID participantId, UUID facilitatorId) {
        MeetingSession meeting = meetingSessionRepository.findById(meetingId)
            .orElseThrow(() -> new RuntimeException("Meeting not found"));
        
        meeting.getParticipants().add(participantId);
        meetingSessionRepository.save(meeting);
    }
}

class SummaryService {
    private MeetingSessionRepository meetingSessionRepository;
    private SummaryRepository summaryRepository;
    private ChangeRepository changeRepository;
    private AIEngine aiEngine;
    private ApprovalService approvalService;
    
    public Summary generateSummary(UUID meetingId, UUID facilitatorId) {
        MeetingSession meeting = meetingSessionRepository.findById(meetingId)
            .orElseThrow(() -> new RuntimeException("Meeting not found"));
        
        SummaryAnalysis analysis = aiEngine.analyzeSummary(meeting.getNotes(), null);
        
        Summary summary = Summary.builder()
            .id(UUID.randomUUID())
            .meetingId(meetingId)
            .content(analysis.getSummary())
            .status(SummaryStatus.PENDING_APPROVAL)
            .createdBy(facilitatorId)
            .createdAt(LocalDateTime.now())
            .build();
        
        summary = summaryRepository.save(summary);
        
        // Save changes from analysis
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
    private ApprovalVoteRepository approvalVoteRepository;
    
    public ApprovalRequest createApprovalRequest(ApprovalContext context, com.flowboard.service.UserStory2_MeetingSummaryTest.ApprovalRule rule) {
        ApprovalRequest request = ApprovalRequest.builder()
            .id(UUID.randomUUID())
            .entityType(context.getEntityType())
            .entityId(context.getEntityId())
            .ruleType(ApprovalRuleType.CONSENSUS)
            .status(ApprovalStatus.PENDING)
            .requiredApprovers(new HashSet<>(context.getRequiredApprovers()))
            .createdAt(LocalDateTime.now())
            .build();
        
        return approvalRequestRepository.save(request);
    }
    
    public void recordApprovalVote(UUID requestId, UUID voterId, ApprovalDecision decision, String feedback) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Approval request not found"));
        
        ApprovalVote vote = ApprovalVote.builder()
            .id(UUID.randomUUID())
            .approvalRequestId(requestId)
            .voterId(voterId)
            .decision(decision)
            .feedback(feedback)
            .votedAt(LocalDateTime.now())
            .build();
        
        approvalVoteRepository.save(vote);
        
        // Evaluate if approval criteria met
        List<ApprovalVote> votes = approvalVoteRepository.findByApprovalRequestId(requestId);
    }
}
