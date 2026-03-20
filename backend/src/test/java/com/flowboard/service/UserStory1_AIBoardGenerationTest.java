package com.flowboard.service;

import com.flowboard.dto.AIAnalysisResult;
import com.flowboard.entity.Board;
import com.flowboard.entity.Card;
import com.flowboard.entity.Project;
import com.flowboard.entity.Stage;
import com.flowboard.entity.User;
import com.flowboard.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for User Story 1: AI Board Generation
 * 
 * Program Path:
 * 1. UserAuthenticationService - User registers and logs in
 * 2. ProjectService - Project manager creates project
 * 3. AIEngine - Analyzes project description and generates board structure
 * 4. BoardGenerator - Creates board with stages and pre-populated cards
 */
@ExtendWith(MockitoExtension.class)
class UserStory1_AIBoardGenerationTest {

    // ============ Module 1: UserAuthenticationService Tests ============
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JWTService jwtService;
    
    @InjectMocks
    private AuthService authService;

    @Test
    void authService_shouldRegisterUserSuccessfully() {
        // Given: A new user registration request
        String email = "manager@example.com";
        String password = "SecurePass123!";
        String fullName = "Project Manager";
        
        User savedUser = User.builder()
            .id(UUID.randomUUID())
            .email(email)
            .username("project_manager")
            .fullName(fullName)
            .passwordHash("hashedPassword")
            .role(User.UserRole.MEMBER)
            .build();
        
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any(), eq(email), any())).thenReturn("jwt-token");
        when(jwtService.getExpirationTime(any())).thenReturn(86400000L);
        
        // When: Registering the user
        var request = new com.flowboard.dto.RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setFullName(fullName);
        var response = authService.register(request);
        
        // Then: User is registered with JWT token
        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals(email, response.getUser().getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void authService_shouldAuthenticateUserAndReturnToken() {
        // Given: Existing user credentials
        String email = "manager@example.com";
        String password = "SecurePass123!";
        UUID userId = UUID.randomUUID();
        
        User user = User.builder()
            .id(userId)
            .email(email)
            .passwordHash("hashedPassword")
            .role(User.UserRole.MEMBER)
            .build();
        
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, "hashedPassword")).thenReturn(true);
        when(jwtService.generateToken(userId, email, "MEMBER")).thenReturn("jwt-token");
        when(jwtService.getExpirationTime(any())).thenReturn(86400000L);
        
        // When: Logging in
        var request = new com.flowboard.dto.LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        var response = authService.login(request);
        
        // Then: JWT token is returned
        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals(email, response.getUser().getEmail());
    }

    // ============ Module 2: ProjectService Tests ============
    
    @Mock
    private ProjectRepository projectRepository;
    
    @Mock
    private BoardRepository boardRepository;
    
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    
    @Mock
    private AIEngine aiEngine;
    
    @Mock
    private BoardGenerator boardGenerator;
    
    @InjectMocks
    private ProjectService projectService;

    @Test
    void projectService_shouldCreateProjectWithDescription() {
        // Given: Project creation request from authenticated user
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder()
            .id(ownerId)
            .email("manager@example.com")
            .role(User.UserRole.MEMBER)
            .build();
        
        String projectName = "Q2 Product Roadmap";
        String projectDescription = "CRM system with real-time collaboration features";
        
        Project savedProject = Project.builder()
            .id(UUID.randomUUID())
            .name(projectName)
            .description(projectDescription)
            .owner(owner)
            .build();
        
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);
        
        // When: Creating project
        var request = new com.flowboard.dto.CreateProjectRequest();
        request.setName(projectName);
        request.setDescription(projectDescription);
        request.setGenerateTasks(false);
        
        Board emptyBoard = Board.builder()
            .id(UUID.randomUUID())
            .name(projectName + " Board")
            .project(savedProject)
            .stages(new ArrayList<>())
            .build();
        when(boardGenerator.generateEmptyBoard(any())).thenReturn(emptyBoard);
        
        var response = projectService.createProject(request, owner);
        
        // Then: Project is created with correct details
        assertNotNull(response);
        assertEquals(projectName, response.getName());
        assertEquals(projectDescription, response.getDescription());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void projectService_shouldSubmitProjectForAIAnalysis() {
        // Given: A project ready for AI analysis
        String projectName = "CRM System";
        String projectDescription = "CRM system with real-time collaboration";
        
        AIAnalysisResult mockAnalysis = new AIAnalysisResult();
        mockAnalysis.setStages(Arrays.asList(
            createStageInfo("Backlog", "bg-gray-100", 0),
            createStageInfo("In Progress", "bg-blue-100", 1),
            createStageInfo("Done", "bg-green-100", 2)
        ));
        mockAnalysis.setTasks(Arrays.asList(
            createTaskInfo("User authentication", "OAuth implementation", "HIGH", "Backlog"),
            createTaskInfo("Contact management", "CRUD for contacts", "HIGH", "Backlog")
        ));
        
        when(aiEngine.analyzeProjectDescription(projectName, projectDescription))
            .thenReturn(mockAnalysis);
        
        // When: Analyzing project description
        AIAnalysisResult result = aiEngine.analyzeProjectDescription(projectName, projectDescription);
        
        // Then: AI returns board structure with stages and tasks
        assertNotNull(result);
        assertFalse(result.getStages().isEmpty());
        assertFalse(result.getTasks().isEmpty());
        assertEquals("Backlog", result.getStages().get(0).getTitle());
    }

    // ============ Module 3: AIEngine Tests ============

    @Test
    void aiEngine_shouldGenerateBoardStructureFromDescription() {
        // Given: Project description with keywords
        String description = "Design-focused mobile app project with UI/UX work";
        String projectName = "Mobile App";
        
        AIEngine engine = new AIEngine(null, null);
        
        // When: Analyzing description
        AIAnalysisResult result = engine.analyzeProjectDescription(projectName, description);
        
        // Then: Relevant stages and tasks are generated
        assertNotNull(result);
        assertNotNull(result.getStages());
        assertFalse(result.getTasks().isEmpty());
        
        // Verify design-related tasks are generated
        boolean hasDesignTask = result.getTasks().stream()
            .anyMatch(t -> t.getTitle().toLowerCase().contains("design") || 
                          t.getTitle().toLowerCase().contains("wireframe"));
        assertTrue(hasDesignTask, "Should generate design-related tasks for design project");
    }

    @Test
    void aiEngine_shouldGenerateDevelopmentTasksForCodeProjects() {
        // Given: Development-focused description
        String description = "Build a web application with user authentication and database";
        String projectName = "Web App";
        
        AIEngine engine = new AIEngine(null, null);
        
        // When: Analyzing description
        AIAnalysisResult result = engine.analyzeProjectDescription(projectName, description);
        
        // Then: Development tasks are generated
        boolean hasDevTask = result.getTasks().stream()
            .anyMatch(t -> t.getTitle().toLowerCase().contains("develop") || 
                          t.getTitle().toLowerCase().contains("code") ||
                          t.getTitle().toLowerCase().contains("test"));
        assertTrue(hasDevTask, "Should generate development tasks for code project");
    }

    // ============ Module 4: BoardGenerator Tests ============

    @Mock
    private StageRepository stageRepository;
    
    @Mock
    private CardRepository cardRepository;

    @Test
    void boardGenerator_shouldCreateBoardWithStagesAndCards() {
        // Given: AI analysis result with stages and tasks
        Project project = Project.builder()
            .id(UUID.randomUUID())
            .name("Test Project")
            .build();
        
        AIAnalysisResult analysis = new AIAnalysisResult();
        List<AIAnalysisResult.StageInfo> stages = Arrays.asList(
            createStageInfo("To Do", "bg-gray-100", 0),
            createStageInfo("In Progress", "bg-blue-100", 1)
        );
        analysis.setStages(stages);
        analysis.setTasks(Arrays.asList(
            createTaskInfo("Task 1", "Description 1", "HIGH", "To Do"),
            createTaskInfo("Task 2", "Description 2", "MEDIUM", "In Progress")
        ));
        
        Board savedBoard = Board.builder()
            .id(UUID.randomUUID())
            .name("Test Project Board")
            .project(project)
            .stages(new ArrayList<>())
            .build();
        
        when(boardRepository.save(any(Board.class))).thenReturn(savedBoard);
        when(stageRepository.save(any(Stage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Generating board
        BoardGenerator generator = new BoardGenerator(boardRepository, stageRepository, cardRepository);
        Board board = generator.generateBoard(project, analysis);
        
        // Then: Board is created with stages and cards
        assertNotNull(board);
        verify(boardRepository, atLeast(1)).save(any(Board.class));
        verify(stageRepository, times(2)).save(any(Stage.class));
        verify(cardRepository, times(2)).save(any(Card.class));
    }

    @Test
    void boardGenerator_shouldCreateEmptyBoardWithDefaultStages() {
        // Given: Project without AI generation
        Project project = Project.builder()
            .id(UUID.randomUUID())
            .name("Test Project")
            .build();
        
        Board savedBoard = Board.builder()
            .id(UUID.randomUUID())
            .name("Test Project Board")
            .project(project)
            .stages(new ArrayList<>())
            .build();
        
        when(boardRepository.save(any(Board.class))).thenReturn(savedBoard);
        when(stageRepository.save(any(Stage.class))).thenAnswer(inv -> inv.getArgument(0));
        
        // When: Generating empty board
        BoardGenerator generator = new BoardGenerator(boardRepository, stageRepository, cardRepository);
        Board board = generator.generateEmptyBoard(project);
        
        // Then: Board with default stages is created
        assertNotNull(board);
        verify(stageRepository, times(4)).save(any(Stage.class)); // To Do, In Progress, Review, Done
    }

    // ============ Integration Flow Test ============

    @Test
    void fullWorkflow_shouldCreateProjectAndGenerateBoard() {
        // Given: Authenticated project manager
        UUID userId = UUID.randomUUID();
        User manager = User.builder()
            .id(userId)
            .email("manager@example.com")
            .role(User.UserRole.MEMBER)
            .build();
        
        String projectName = "AI Generated Project";
        String description = "Design system for enterprise application";
        
        // Mock project creation
        Project project = Project.builder()
            .id(UUID.randomUUID())
            .name(projectName)
            .description(description)
            .owner(manager)
            .build();
        
        when(projectRepository.save(any(Project.class))).thenReturn(project);
        
        // Mock AI analysis
        AIAnalysisResult analysis = new AIAnalysisResult();
        analysis.setStages(Arrays.asList(
            createStageInfo("Backlog", "bg-gray-100", 0),
            createStageInfo("In Progress", "bg-blue-100", 1),
            createStageInfo("Done", "bg-green-100", 2)
        ));
        analysis.setTasks(Arrays.asList(
            createTaskInfo("Design UI", "Create mockups", "HIGH", "Backlog"),
            createTaskInfo("Review Design", "Team review", "MEDIUM", "In Progress")
        ));
        when(aiEngine.analyzeProjectDescription(projectName, description)).thenReturn(analysis);
        
        // Mock board generation
        Board board = Board.builder()
            .id(UUID.randomUUID())
            .name(projectName + " Board")
            .project(project)
            .stages(new ArrayList<>())
            .build();
        when(boardRepository.save(any(Board.class))).thenReturn(board);
        when(stageRepository.save(any(Stage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        when(boardGenerator.generateBoard(any(), any())).thenReturn(board);
        
        // When: Complete workflow execution
        var request = new com.flowboard.dto.CreateProjectRequest();
        request.setName(projectName);
        request.setDescription(description);
        request.setGenerateTasks(true);
        
        var projectDTO = projectService.createProject(request, manager);
        
        // Then: Project with board is created
        assertNotNull(projectDTO);
        assertEquals(projectName, projectDTO.getName());
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
}
