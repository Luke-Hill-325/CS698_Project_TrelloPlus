package com.flowboard.controller;

import com.flowboard.dto.*;
import com.flowboard.service.JWTService;
import com.flowboard.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "http://localhost:*")
public class BoardController {
    private final ProjectService projectService;
    private final JWTService jwtService;

    @PostMapping("/{boardId}/stages")
    public ResponseEntity<StageDTO> addStage(
        @PathVariable UUID boardId,
        @Valid @RequestBody AddStageRequest request,
        @RequestHeader("Authorization") String authHeader) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);
        
        return ResponseEntity.ok(projectService.addStage(
            boardId,
            request.getTitle(),
            request.getColor(),
            userId
        ));
    }

    @DeleteMapping("/stages/{stageId}")
    public ResponseEntity<Void> deleteStage(
        @PathVariable UUID stageId,
        @RequestHeader("Authorization") String authHeader) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);

        projectService.deleteStage(stageId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/stages/{stageId}")
    public ResponseEntity<StageDTO> renameStage(
        @PathVariable UUID stageId,
        @Valid @RequestBody RenameStageRequest request,
        @RequestHeader("Authorization") String authHeader) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);

        return ResponseEntity.ok(projectService.renameStage(
            stageId,
            request.getTitle(),
            userId
        ));
    }

    @PostMapping("/stages/{stageId}/cards")
    public ResponseEntity<CardDTO> createCard(
        @PathVariable UUID stageId,
        @Valid @RequestBody CardRequest request,
        @RequestHeader("Authorization") String authHeader) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);

        return ResponseEntity.ok(projectService.createCard(
            stageId,
            request.getTitle(),
            request.getDescription(),
            request.getPriority(),
            request.getAssigneeId(),
            userId
        ));
    }

    @PutMapping("/cards/{cardId}")
    public ResponseEntity<CardDTO> updateCard(
        @PathVariable UUID cardId,
        @Valid @RequestBody CardRequest request,
        @RequestHeader("Authorization") String authHeader) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);

        return ResponseEntity.ok(projectService.updateCard(
            cardId,
            request.getTitle(),
            request.getDescription(),
            request.getPriority(),
            request.getAssigneeId(),
            userId
        ));
    }

    @PutMapping("/cards/{cardId}/move")
    public ResponseEntity<CardDTO> moveCard(
        @PathVariable UUID cardId,
        @Valid @RequestBody MoveCardRequest request,
        @RequestHeader("Authorization") String authHeader) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);
        return ResponseEntity.ok(projectService.moveCard(cardId, request.getTargetStageId(), userId));
    }

    @DeleteMapping("/cards/{cardId}")
    public ResponseEntity<Void> deleteCard(
        @PathVariable UUID cardId,
        @RequestHeader("Authorization") String authHeader) {
        UUID userId = jwtService.extractUserIdFromAuthHeader(authHeader);

        projectService.deleteCard(cardId, userId);
        return ResponseEntity.noContent().build();
    }
}
