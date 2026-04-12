package com.flowboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboard.dto.AddStageRequest;
import com.flowboard.dto.CardDTO;
import com.flowboard.dto.CardRequest;
import com.flowboard.dto.MoveCardRequest;
import com.flowboard.dto.StageDTO;
import com.flowboard.service.JWTService;
import com.flowboard.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BoardController.class)
@AutoConfigureMockMvc(addFilters = false)
class BoardControllerUserStory1ApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private JWTService jwtService;

    @Test
    void addStage_returnsOk_andDelegatesToBoardModuleApi() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();

        AddStageRequest request = AddStageRequest.builder()
            .title("Review")
            .color("#22AA66")
            .build();

        StageDTO response = StageDTO.builder()
            .id(stageId)
            .title("Review")
            .color("#22AA66")
            .build();

        when(jwtService.extractUserIdFromAuthHeader("Bearer valid-token")).thenReturn(userId);
        when(projectService.addStage(boardId, "Review", "#22AA66", userId)).thenReturn(response);

        mockMvc.perform(post("/boards/{boardId}/stages", boardId)
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(stageId.toString()))
            .andExpect(jsonPath("$.title").value("Review"));

        verify(projectService).addStage(boardId, "Review", "#22AA66", userId);
    }

    @Test
    void createAndMoveCard_coverCoreUserStory1BoardOperations() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID targetStageId = UUID.randomUUID();

        CardRequest createCardRequest = CardRequest.builder()
            .title("Create wireframes")
            .description("Design low-fidelity wireframes")
            .priority("HIGH")
            .build();

        CardDTO createdCard = CardDTO.builder()
            .id(cardId)
            .title("Create wireframes")
            .description("Design low-fidelity wireframes")
            .priority("HIGH")
            .stageId(stageId)
            .build();

        CardDTO movedCard = CardDTO.builder()
            .id(cardId)
            .title("Create wireframes")
            .description("Design low-fidelity wireframes")
            .priority("HIGH")
            .stageId(targetStageId)
            .build();

        when(jwtService.extractUserIdFromAuthHeader("Bearer valid-token")).thenReturn(userId);
        when(projectService.createCard(stageId, "Create wireframes", "Design low-fidelity wireframes", "HIGH", null, userId))
            .thenReturn(createdCard);
        when(projectService.moveCard(cardId, targetStageId, userId)).thenReturn(movedCard);

        mockMvc.perform(post("/boards/stages/{stageId}/cards", stageId)
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCardRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(cardId.toString()))
            .andExpect(jsonPath("$.column_id").value(stageId.toString()));

        mockMvc.perform(put("/boards/cards/{cardId}/move", cardId)
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new MoveCardRequest(targetStageId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.column_id").value(targetStageId.toString()));

        verify(projectService).createCard(stageId, "Create wireframes", "Design low-fidelity wireframes", "HIGH", null, userId);
        verify(projectService).moveCard(cardId, targetStageId, userId);
    }
}
