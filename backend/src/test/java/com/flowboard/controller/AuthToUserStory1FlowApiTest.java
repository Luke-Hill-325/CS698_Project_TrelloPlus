package com.flowboard.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.dto.AuthResponse;
import com.flowboard.dto.CreateProjectRequest;
import com.flowboard.dto.LoginRequest;
import com.flowboard.dto.ProjectDTO;
import com.flowboard.dto.RegisterRequest;
import com.flowboard.dto.UserDTO;
import com.flowboard.entity.User;
import com.flowboard.repository.UserRepository;
import com.flowboard.service.AuthService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, ProjectController.class})
@AutoConfigureMockMvc(addFilters = false)
class AuthToUserStory1FlowApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private JWTService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    void register_thenLogin_thenCreateProject_flowWorksForUserStory1Path() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        RegisterRequest registerRequest = RegisterRequest.builder()
            .email("pm.flow@flowboard.com")
            .password("StrongPass1!")
            .username("pm_flow")
            .fullName("PM Flow")
            .build();

        LoginRequest loginRequest = LoginRequest.builder()
            .email("pm.flow@flowboard.com")
            .password("StrongPass1!")
            .build();

        CreateProjectRequest createProjectRequest = CreateProjectRequest.builder()
            .name("Website Redesign")
            .description("Redesign our website with modern UI UX and clear navigation")
            .generateTasks(true)
            .build();

        UserDTO user = UserDTO.builder()
            .id(userId)
            .email("pm.flow@flowboard.com")
            .username("pm_flow")
            .fullName("PM Flow")
            .role("MANAGER")
            .build();

        AuthResponse registerResponse = AuthResponse.builder()
            .token("register-token")
            .expiresIn(3600000L)
            .user(user)
            .build();

        AuthResponse loginResponse = AuthResponse.builder()
            .token("login-token")
            .expiresIn(3600000L)
            .user(user)
            .build();

        User owner = User.builder()
            .id(userId)
            .email("pm.flow@flowboard.com")
            .username("pm_flow")
            .role(User.UserRole.MANAGER)
            .build();

        ProjectDTO createdProject = ProjectDTO.builder()
            .id(projectId)
            .name("Website Redesign")
            .description("Redesign our website with modern UI UX and clear navigation")
            .boardId(boardId)
            .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(registerResponse);
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);
        when(jwtService.extractUserIdFromAuthHeader("Bearer login-token")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(projectService.createProject(any(CreateProjectRequest.class), eq(owner))).thenReturn(createdProject);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.email").value("pm.flow@flowboard.com"));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("login-token"))
            .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String bearerToken = loginJson.get("token").asText();
        assertThat(bearerToken).isEqualTo("login-token");

        mockMvc.perform(post("/projects")
                .header("Authorization", "Bearer " + bearerToken)
                .header("Idempotency-Key", "flow-us1-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createProjectRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(projectId.toString()))
            .andExpect(jsonPath("$.board_id").value(boardId.toString()));

        verify(authService).register(any(RegisterRequest.class));
        verify(authService).login(any(LoginRequest.class));
        verify(projectService).createProject(any(CreateProjectRequest.class), eq(owner));
    }
}
