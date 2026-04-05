package com.flowboard.service;

import com.flowboard.dto.ChangeDTO;
import com.flowboard.dto.ChangeDiffDTO;
import com.flowboard.dto.ChangeHistoryEntryDTO;
import com.flowboard.dto.ChangeImpactDTO;
import com.flowboard.entity.Change;
import com.flowboard.entity.ChangeAuditEntry;
import com.flowboard.entity.Meeting;
import com.flowboard.entity.Project;
import com.flowboard.entity.User;
import com.flowboard.repository.ChangeAuditEntryRepository;
import com.flowboard.repository.ChangeRepository;
import com.flowboard.repository.MeetingMemberRepository;
import com.flowboard.repository.ProjectMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePreviewServiceUserStory3Test {

    @Mock
    private ChangeRepository changeRepository;

    @Mock
    private ChangeAuditEntryRepository changeAuditEntryRepository;

    @Mock
    private MeetingMemberRepository meetingMemberRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    private ChangePreviewService service;

    private UUID userId;
    private UUID projectId;
    private UUID meetingId;
    private UUID changeId;
    private User owner;
    private Project project;
    private Meeting meeting;
    private Change change;

    @BeforeEach
    void setUp() {
        service = new ChangePreviewService(
            changeRepository,
            changeAuditEntryRepository,
            meetingMemberRepository,
            projectMemberRepository
        );

        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        meetingId = UUID.randomUUID();
        changeId = UUID.randomUUID();

        owner = User.builder()
            .id(userId)
            .email("owner@flowboard.com")
            .username("owner")
            .passwordHash("hash")
            .role(User.UserRole.MANAGER)
            .build();

        project = Project.builder()
            .id(projectId)
            .name("U3 Project")
            .owner(owner)
            .build();

        meeting = Meeting.builder()
            .id(meetingId)
            .project(project)
            .title("U3 Review")
            .createdBy(owner)
            .build();

        change = Change.builder()
            .id(changeId)
            .meeting(meeting)
            .changeType(Change.ChangeType.DELETE_CARD)
            .beforeState("{\"id\":\"card-1\",\"title\":\"Old title\"}")
            .afterState("{\"id\":\"card-1\",\"title\":\"Removed title\"}")
            .status(Change.ChangeStatus.PENDING)
            .createdAt(LocalDateTime.parse("2026-04-05T10:00:00"))
            .build();

        lenient().when(changeRepository.findById(changeId)).thenReturn(Optional.of(change));
        lenient().when(meetingMemberRepository.existsByMeetingIdAndUserId(meetingId, userId)).thenReturn(true);
        lenient().when(projectMemberRepository.findMemberRole(projectId, userId)).thenReturn(Optional.of("owner"));
    }

    @Test
    void listChanges_returnsMeetingScopedChangesForValidStatus() {
        when(changeRepository.findByMeetingIdAndStatus(meetingId, Change.ChangeStatus.PENDING)).thenReturn(List.of(change));

        List<ChangeDTO> result = service.listChanges(meetingId, null, "pending", userId);

        assertEquals(1, result.size());
        assertEquals(changeId, result.get(0).getId());
        assertEquals(meetingId, result.get(0).getMeetingId());
        assertEquals("DELETE_CARD", result.get(0).getChangeType());
        verify(changeRepository).findByMeetingIdAndStatus(meetingId, Change.ChangeStatus.PENDING);
    }

    @Test
    void listChanges_rejectsInvalidStatus() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.listChanges(meetingId, null, "not-a-real-status", userId)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Invalid status"));
    }

    @Test
    void getChange_forbidsUsersWithoutMeetingAccess() {
        when(meetingMemberRepository.existsByMeetingIdAndUserId(meetingId, userId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.getChange(changeId, userId)
        );

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void getDiffAndImpact_mapDeletionSpecificInformation() {
        ChangeDiffDTO diff = service.getDiff(changeId, userId);
        ChangeImpactDTO impact = service.getImpact(changeId, userId);

        assertEquals("{\"id\":\"card-1\",\"title\":\"Old title\"}", diff.getBeforeState());
        assertEquals("{\"id\":\"card-1\",\"title\":\"Removed title\"}", diff.getAfterState());
        assertEquals("Existing card was proposed for deletion", diff.getSummary());

        assertEquals("HIGH", impact.getRiskLevel());
        assertEquals(List.of("card-referenced-in-after-state"), impact.getAffectedCards());
        assertTrue(impact.getPotentialConflicts().isEmpty());
    }

    @Test
    void getHistory_mapsAuditEntriesAndActorDetails() {
        User actor = User.builder()
            .id(UUID.randomUUID())
            .email("reviewer@flowboard.com")
            .username("reviewer")
            .passwordHash("hash")
            .role(User.UserRole.MEMBER)
            .build();

        ChangeAuditEntry entry = ChangeAuditEntry.builder()
            .id(UUID.randomUUID())
            .change(change)
            .action(ChangeAuditEntry.AuditAction.APPROVED)
            .actor(actor)
            .details("{\"decision\":\"APPROVE\"}")
            .createdAt(LocalDateTime.parse("2026-04-05T10:10:00"))
            .build();

        when(changeAuditEntryRepository.findByChangeIdOrderByCreatedAtDesc(changeId)).thenReturn(List.of(entry));

        List<ChangeHistoryEntryDTO> result = service.getHistory(changeId, userId);

        assertEquals(1, result.size());
        assertEquals(entry.getId(), result.get(0).getId());
        assertEquals("APPROVED", result.get(0).getAction());
        assertEquals(actor.getId(), result.get(0).getActorId());
        assertEquals(actor.getUsername(), result.get(0).getActorName());
        assertEquals(entry.getDetails(), result.get(0).getDetails());
        assertEquals(entry.getCreatedAt(), result.get(0).getCreatedAt());
    }
}