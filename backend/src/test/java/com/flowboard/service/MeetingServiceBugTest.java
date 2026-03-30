package com.flowboard.service;

import com.flowboard.entity.*;
import com.flowboard.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Bug-finding tests for MeetingService.
 *
 * BUG: deleteMeeting() only verifies that the actor is a project member.
 * It does NOT check that the actor is the project owner or the meeting creator.
 * This means any project member — even a viewer with no edit privileges — can
 * delete any SCHEDULED meeting in the project.
 */
@ExtendWith(MockitoExtension.class)
class MeetingServiceBugTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingMemberRepository meetingMemberRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserRepository userRepository;

    private MeetingService meetingService;

    private User owner;
    private User viewer;
    private Project project;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meetingService = new MeetingService(
            meetingRepository,
            meetingMemberRepository,
            projectRepository,
            projectMemberRepository,
            userRepository
        );

        owner = User.builder().id(UUID.randomUUID()).username("owner").email("owner@test.com").build();
        viewer = User.builder().id(UUID.randomUUID()).username("viewer").email("viewer@test.com").build();

        project = Project.builder()
            .id(UUID.randomUUID())
            .name("Test Project")
            .owner(owner)
            .build();

        meeting = Meeting.builder()
            .id(UUID.randomUUID())
            .project(project)
            .title("Sprint Planning")
            .meetingDate(LocalDate.now().plusDays(7))
            .status(Meeting.MeetingStatus.SCHEDULED)
            .createdBy(owner)
            .build();
    }

    @Test
    @DisplayName("BUG: Viewer-role project member should NOT be able to delete meetings")
    void deleteMeeting_shouldRejectViewerRole() {
        // The viewer is a project member with "viewer" role — no edit privileges.
        // They should NOT be allowed to delete a meeting.

        when(meetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        // Viewer is a project member with viewer role
        when(projectMemberRepository.findMemberRole(project.getId(), viewer.getId()))
            .thenReturn(Optional.of("viewer"));

        // ASSERTION: Should throw FORBIDDEN because viewers can't delete meetings.
        // This test FAILS because deleteMeeting() only checks project membership,
        // not the member's role. Any project member can delete any scheduled meeting.
        assertThatThrownBy(() -> meetingService.deleteMeeting(meeting.getId(), viewer))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Forbidden");
    }

    @Test
    @DisplayName("BUG: Non-creator project member should NOT be able to delete meetings they didn't create")
    void deleteMeeting_shouldRejectNonCreator_evenIfEditor() {
        // An editor-role project member who did NOT create the meeting
        // tries to delete it. Only the creator or owner should be able to.
        User editor = User.builder().id(UUID.randomUUID()).username("editor").email("editor@test.com").build();

        when(meetingRepository.findById(meeting.getId())).thenReturn(Optional.of(meeting));
        when(projectMemberRepository.findMemberRole(project.getId(), editor.getId()))
            .thenReturn(Optional.of("editor"));

        // ASSERTION: Editor who isn't the meeting creator or project owner should be rejected.
        // This test FAILS because deleteMeeting() allows any project member to delete.
        assertThatThrownBy(() -> meetingService.deleteMeeting(meeting.getId(), editor))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Forbidden");
    }
}
