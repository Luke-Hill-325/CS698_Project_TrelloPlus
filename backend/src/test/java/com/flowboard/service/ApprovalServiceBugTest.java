package com.flowboard.service;

import com.flowboard.dto.SubmitApprovalRequest;
import com.flowboard.entity.*;
import com.flowboard.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug-finding tests for ApprovalService.
 *
 * BUG: submitApproval() calls approveAllSummaryItems() immediately when a
 * SINGLE user approves (line 68-69), even though the consensus model documented
 * at line 124 says "all must approve". This means action items and decisions
 * are marked APPROVED after just one person approves, before the rest of the
 * team has responded.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceBugTest {

    @Mock private ApprovalRequestSummaryRepository approvalRequestRepository;
    @Mock private ApprovalResponseSummaryRepository approvalResponseRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private ActionItemRepository actionItemRepository;
    @Mock private DecisionRepository decisionRepository;

    private ApprovalService approvalService;

    private UUID meetingId;
    private Meeting meeting;
    private User user1;
    private User user2;
    private ApprovalRequestSummary approvalRequest;
    private ActionItem actionItem;
    private Decision decision;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(
            approvalRequestRepository,
            approvalResponseRepository,
            meetingRepository,
            meetingMemberRepository,
            actionItemRepository,
            decisionRepository
        );

        meetingId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        user1 = User.builder().id(UUID.randomUUID()).username("user1").email("user1@test.com").build();
        user2 = User.builder().id(UUID.randomUUID()).username("user2").email("user2@test.com").build();

        Project project = Project.builder().id(projectId).name("Test").owner(user1).build();

        meeting = Meeting.builder()
            .id(meetingId)
            .project(project)
            .title("Sprint Planning")
            .status(Meeting.MeetingStatus.PENDING_APPROVAL)
            .createdBy(user1)
            .build();

        approvalRequest = ApprovalRequestSummary.builder()
            .id(UUID.randomUUID())
            .meeting(meeting)
            .requiredApprovals(2)
            .build();

        actionItem = ActionItem.builder()
            .id(UUID.randomUUID())
            .meeting(meeting)
            .description("Implement feature X")
            .approvalStatus(ActionItem.ApprovalStatus.PENDING)
            .build();

        decision = Decision.builder()
            .id(UUID.randomUUID())
            .meeting(meeting)
            .description("Use Spring Boot")
            .approvalStatus(Decision.ApprovalStatus.PENDING)
            .build();
    }

    @Test
    @DisplayName("BUG: First approval should NOT mark all items as APPROVED when other members haven't responded yet")
    void firstApproval_shouldNotApproveAllItems_whenOtherMembersHaveNotResponded() {
        // Two-member meeting: user1 approves, user2 hasn't responded yet.
        // Expected: items stay PENDING until ALL members approve.
        // Actual (bug): items are immediately set to APPROVED after first approval.

        ApprovalResponseSummary user1Response = ApprovalResponseSummary.builder()
            .id(UUID.randomUUID())
            .approvalRequest(approvalRequest)
            .user(user1)
            .response(ApprovalResponseSummary.ApprovalResponse.PENDING)
            .build();

        ApprovalResponseSummary user2Response = ApprovalResponseSummary.builder()
            .id(UUID.randomUUID())
            .approvalRequest(approvalRequest)
            .user(user2)
            .response(ApprovalResponseSummary.ApprovalResponse.PENDING)
            .build();

        // user1 is a meeting member
        when(meetingMemberRepository.existsByMeetingIdAndUserId(meetingId, user1.getId())).thenReturn(true);
        when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(meeting));
        when(approvalRequestRepository.findByMeetingId(meetingId)).thenReturn(Optional.of(approvalRequest));
        when(approvalResponseRepository.findByApprovalRequestIdAndUserId(approvalRequest.getId(), user1.getId()))
            .thenReturn(Optional.of(user1Response));
        when(approvalResponseRepository.save(any(ApprovalResponseSummary.class)))
            .thenAnswer(i -> i.getArgument(0));

        // After user1 approves, user2 is still PENDING — not all have responded
        when(approvalResponseRepository.findByApprovalRequestId(approvalRequest.getId()))
            .thenReturn(List.of(user1Response, user2Response));

        when(actionItemRepository.findByMeetingId(meetingId)).thenReturn(List.of(actionItem));
        when(decisionRepository.findByMeetingId(meetingId)).thenReturn(List.of(decision));

        // Submit user1's APPROVED response
        SubmitApprovalRequest request = SubmitApprovalRequest.builder()
            .response("APPROVED")
            .comments("Looks good")
            .build();

        approvalService.submitApproval(meetingId, user1, request);

        // ASSERTION: Items should still be PENDING because user2 hasn't approved yet.
        // This test FAILS because of the bug on line 68-69 that immediately approves all items.
        assertThat(actionItem.getApprovalStatus())
            .as("Action item should stay PENDING until ALL members approve, not just the first one")
            .isEqualTo(ActionItem.ApprovalStatus.PENDING);

        assertThat(decision.getApprovalStatus())
            .as("Decision should stay PENDING until ALL members approve, not just the first one")
            .isEqualTo(Decision.ApprovalStatus.PENDING);

        // approveAllSummaryItems should NOT have been called yet
        verify(actionItemRepository, never()).saveAll(any());
        verify(decisionRepository, never()).saveAll(any());
    }
}
