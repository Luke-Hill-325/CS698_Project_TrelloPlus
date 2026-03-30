package com.flowboard.service;

import com.flowboard.dto.ChangeImpactDTO;
import com.flowboard.dto.ChangeDTO;
import com.flowboard.entity.*;
import com.flowboard.repository.ChangeAuditEntryRepository;
import com.flowboard.repository.ChangeRepository;
import com.flowboard.repository.MeetingMemberRepository;
import com.flowboard.repository.ProjectMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Bug-finding tests for ChangePreviewService.
 *
 * BUG 1: extractLikelyCardId() returns hardcoded placeholder strings like
 * "card-referenced-in-after-state" instead of parsing the actual card UUID
 * from the JSON payload. Clients receive useless data in the impact analysis.
 *
 * BUG 2: hasProjectAccess() only checks the project_members table via
 * projectMemberRepository.findMemberRole(). It does NOT check whether the
 * user is the Project.owner. If the owner doesn't have a project_members row,
 * they are locked out of their own project's changes.
 */
@ExtendWith(MockitoExtension.class)
class ChangePreviewServiceBugTest {

    @Mock private ChangeRepository changeRepository;
    @Mock private ChangeAuditEntryRepository changeAuditEntryRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private ChangePreviewService changePreviewService;

    private User owner;
    private User member;
    private Project project;
    private Meeting meeting;
    private UUID meetingId;

    @BeforeEach
    void setUp() {
        changePreviewService = new ChangePreviewService(
            changeRepository,
            changeAuditEntryRepository,
            meetingMemberRepository,
            projectMemberRepository
        );

        owner = User.builder().id(UUID.randomUUID()).username("owner").email("owner@test.com").build();
        member = User.builder().id(UUID.randomUUID()).username("member").email("member@test.com").build();

        project = Project.builder()
            .id(UUID.randomUUID())
            .name("Test Project")
            .owner(owner)
            .build();

        meetingId = UUID.randomUUID();
        meeting = Meeting.builder()
            .id(meetingId)
            .project(project)
            .title("Sprint")
            .status(Meeting.MeetingStatus.PENDING_APPROVAL)
            .createdBy(owner)
            .build();
    }

    @Test
    @DisplayName("BUG: getImpact() should return actual card UUID from JSON, not a hardcoded placeholder")
    void getImpact_shouldReturnActualCardId_notPlaceholderString() {
        // afterState contains a real card UUID, but extractLikelyCardId returns
        // "card-referenced-in-after-state" instead of the actual UUID.
        UUID realCardId = UUID.randomUUID();
        String afterState = "{\"id\":\"" + realCardId + "\",\"title\":\"Fix login\"}";

        Change change = Change.builder()
            .id(UUID.randomUUID())
            .meeting(meeting)
            .changeType(Change.ChangeType.UPDATE_CARD)
            .afterState(afterState)
            .status(Change.ChangeStatus.PENDING)
            .build();

        when(changeRepository.findById(change.getId())).thenReturn(Optional.of(change));
        when(meetingMemberRepository.existsByMeetingIdAndUserId(meetingId, member.getId())).thenReturn(true);

        ChangeImpactDTO impact = changePreviewService.getImpact(change.getId(), member.getId());

        // ASSERTION: affectedCards should contain the actual card UUID from the JSON.
        // This test FAILS because extractLikelyCardId returns "card-referenced-in-after-state".
        assertThat(impact.getAffectedCards())
            .as("Impact analysis should return the actual card UUID, not a hardcoded placeholder")
            .contains(realCardId.toString());

        assertThat(impact.getAffectedCards())
            .as("Should not contain placeholder strings")
            .noneMatch(s -> s.startsWith("card-referenced-in-"));
    }

    @Test
    @DisplayName("BUG: Project owner should be able to list changes even without a project_members row")
    void listChanges_byProject_shouldAllowProjectOwner_evenWithoutMemberRow() {
        // The project owner has NO entry in the project_members table.
        // hasProjectAccess() only checks projectMemberRepository.findMemberRole(),
        // so the owner is locked out of their own project's changes.

        UUID projectId = project.getId();

        // Owner has no project_members row
        when(projectMemberRepository.findMemberRole(projectId, owner.getId())).thenReturn(Optional.empty());

        Change change = Change.builder()
            .id(UUID.randomUUID())
            .meeting(meeting)
            .changeType(Change.ChangeType.CREATE_CARD)
            .afterState("{\"title\":\"New task\"}")
            .status(Change.ChangeStatus.PENDING)
            .build();

        when(changeRepository.findByMeetingProjectId(projectId)).thenReturn(List.of(change));

        // ASSERTION: Owner should have access to their own project's changes.
        // This test FAILS because hasProjectAccess() doesn't check the owner field.
        // It throws FORBIDDEN instead.
        List<ChangeDTO> changes = changePreviewService.listChanges(null, projectId, null, owner.getId());

        assertThat(changes)
            .as("Project owner should be able to list their project's changes")
            .hasSize(1);
    }
}
