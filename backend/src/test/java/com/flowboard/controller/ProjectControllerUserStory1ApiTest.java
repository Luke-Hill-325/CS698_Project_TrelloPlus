package com.flowboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.dto.CreateProjectRequest;
import com.flowboard.dto.ProjectDTO;
import com.flowboard.entity.User;
import com.flowboard.repository.UserRepository;
import com.flowboard.service.IdempotencyService;
import com.flowboard.service.JWTService;
import com.flowboard.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerUserStory1ApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private JWTService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    void createProject_returnsCreated_andDelegatesToUserStory1Path() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        CreateProjectRequest request = CreateProjectRequest.builder()
            .name("Website Redesign")
            .description("Redesign our website with modern UI and UX patterns")
            .generateTasks(true)
            .build();

        User owner = User.builder()
            .id(userId)
            .email("pm@flowboard.com")
            .username("pm")
            .role(User.UserRole.MANAGER)
            .build();

        ProjectDTO response = ProjectDTO.builder()
            .id(projectId)
            .name("Website Redesign")
            .description("Redesign our website with modern UI and UX patterns")
            .boardId(boardId)
            .build();

        when(jwtService.extractUserIdFromAuthHeader("Bearer valid-token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(projectService.createProject(any(CreateProjectRequest.class), eq(owner))).thenReturn(response);

        mockMvc.perform(post("/projects")
                .header("Authorization", "Bearer valid-token")
                .header("Idempotency-Key", "us1-create-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(projectId.toString()))
            .andExpect(jsonPath("$.name").value("Website Redesign"))
            .andExpect(jsonPath("$.board_id").value(boardId.toString()));

        verify(idempotencyService).ensureUnique(
            eq("project-create:" + userId + ":us1-create-001"),
            eq(Duration.ofHours(24)),
            eq("Duplicate project creation request detected")
        );
        verify(projectService).createProject(any(CreateProjectRequest.class), eq(owner));
    }

    @Test
    void createProject_withInvalidBody_returnsBadRequest() throws Exception {
        String invalidPayload = "{\"description\":\"missing name\"}";

        mockMvc.perform(post("/projects")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"));

        verify(projectService, never()).createProject(any(CreateProjectRequest.class), any(User.class));
    }
}
