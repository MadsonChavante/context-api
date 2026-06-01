package com.contextapi.services;

import com.contextapi.dtos.ContextDTO;
import com.contextapi.dynamics.RaptorDynamic;
import com.contextapi.entities.Context;
import com.contextapi.entities.ContextStats;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.repositories.ContextRepository;
import com.contextapi.repositories.ContextStatsRepository;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class ContextService {

    private static final String CONTEXT_NOT_FOUND = "Context not found with id: %d";

    private final ContextRepository contextRepository;
    private final ContextStatsRepository contextStatsRepository;
    private final RaptorDynamic raptorDynamic;

    public ContextService(ContextRepository contextRepository,
                          ContextStatsRepository contextStatsRepository,
                          @Lazy RaptorDynamic raptorDynamic) {
        this.contextRepository = contextRepository;
        this.contextStatsRepository = contextStatsRepository;
        this.raptorDynamic = raptorDynamic;
    }

    @Transactional(readOnly = true)
    public ContextDTO findById(Long id) {
        log.debug("Finding context by id: {}", id);
        Context context = contextRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CONTEXT_NOT_FOUND.formatted(id)));
        return mapToDTO(context);
    }

    @Transactional(readOnly = true)
    public Page<ContextDTO> findAll(Pageable pageable) {
        log.debug("Finding all contexts with pagination: {}", pageable);
        return contextRepository.findAll(pageable)
                .map(this::mapToDTO);
    }

    public List<Context> findAll() {
        log.debug("Finding all contexts");
        return contextRepository.findAll();
    }

    public ContextDTO create(ContextDTO contextDTO) {
        log.debug("Creating new context with content: {}", contextDTO.getContent());
        contextDTO.setContent(sanitizeContent(contextDTO.getContent()));
        Context context = mapToEntity(contextDTO);
        Context savedContext = contextRepository.save(context);
        log.info("Context created successfully with id: {}", savedContext.getId());

        String analysis = raptorDynamic.analyze(savedContext.getContent());
        savedContext.setAiAnalysis(analysis);
        savedContext = contextRepository.save(savedContext);

        return mapToDTO(savedContext);
    }

    public ContextDTO update(Long id, ContextDTO contextDTO) {
        log.debug("Updating context with id: {}", id);
        Context context = contextRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CONTEXT_NOT_FOUND.formatted(id)));

        String sanitizedContent = sanitizeContent(contextDTO.getContent());
        context.setContent(sanitizedContent);
        context.setAiAnalysis(null);
        Context updatedContext = contextRepository.save(context);
        log.info("Context updated successfully with id: {}", id);

        String analysis = raptorDynamic.analyze(updatedContext.getContent());
        updatedContext.setAiAnalysis(analysis);
        updatedContext = contextRepository.save(updatedContext);

        return mapToDTO(updatedContext);
    }

    public void delete(Long id) {
        log.debug("Deleting context with id: {}", id);
        Context context = contextRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(CONTEXT_NOT_FOUND.formatted(id)));
        contextRepository.delete(context);
        log.info("Context deleted successfully with id: {}", id);
    }

    private String sanitizeContent(String content) {
        if (content == null) {
            return "";
        }

        String sanitized = content
                .replaceAll("\\n\\n+", "\n")
                .replaceAll("[\\u0000-\\u001F\\u007F]", "")
                .trim();

        if (sanitized.length() > 1000) {
            log.warn("Content truncated from {} to 1000 characters", sanitized.length());
            sanitized = sanitized.substring(0, 1000);
        }

        return sanitized;
    }

    private ContextDTO mapToDTO(Context context) {
        return new ContextDTO(context.getId(), context.getContent(), context.getAiAnalysis());
    }

    private Context mapToEntity(ContextDTO contextDTO) {
        Context context = new Context();
        context.setContent(contextDTO.getContent());
        return context;
    }

    public ContextStats getContextStats(Long contextId) {
        return contextStatsRepository.findByContextId(contextId)
                .orElseGet(ContextStats::new);
    }
}
