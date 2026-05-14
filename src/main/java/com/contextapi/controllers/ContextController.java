package com.contextapi.controllers;

import com.contextapi.dtos.ContextDTO;
import com.contextapi.services.ContextService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/contexts")
@AllArgsConstructor
public class ContextController {

    private final ContextService contextService;

    @GetMapping("/{id}")
    public ResponseEntity<ContextDTO> findById(@PathVariable Long id) {
        log.debug("GET request to find context by id: {}", id);
        ContextDTO contextDTO = contextService.findById(id);
        return ResponseEntity.ok(contextDTO);
    }

    @GetMapping
    public ResponseEntity<Page<ContextDTO>> findAll(Pageable pageable) {
        log.debug("GET request to find all contexts");
        Page<ContextDTO> contextsPage = contextService.findAll(pageable);
        return ResponseEntity.ok(contextsPage);
    }

    @PostMapping
    public ResponseEntity<ContextDTO> create(@Valid @RequestBody ContextDTO contextDTO) {
        log.debug("POST request to create new context");
        ContextDTO createdContext = contextService.create(contextDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdContext);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContextDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody ContextDTO contextDTO) {
        log.debug("PUT request to update context with id: {}", id);
        ContextDTO updatedContext = contextService.update(id, contextDTO);
        return ResponseEntity.ok(updatedContext);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.debug("DELETE request to delete context with id: {}", id);
        contextService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
