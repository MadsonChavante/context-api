package com.contextapi.services;

import com.contextapi.dtos.ContextDTO;
import com.contextapi.entities.Context;
import com.contextapi.exceptions.ResourceNotFoundException;
import com.contextapi.repositories.ContextRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
@Transactional
public class ContextService {

    private final ContextRepository contextRepository;
    private final AiService aiService;

    @Transactional(readOnly = true)
    public ContextDTO findById(Long id) {
        log.debug("Finding context by id: {}", id);
        Context context = contextRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Context not found with id: %d", id)
                ));
        return mapToDTO(context);
    }

    @Transactional(readOnly = true)
    public Page<ContextDTO> findAll(Pageable pageable) {
        log.debug("Finding all contexts with pagination: {}", pageable);
        return contextRepository.findAll(pageable)
                .map(this::mapToDTO);
    }

    public ContextDTO create(ContextDTO contextDTO) {
        log.debug("Creating new context with content: {}", contextDTO.getContent());
        // Sanitizar conteúdo antes de salvar
        contextDTO.setContent(sanitizeContent(contextDTO.getContent()));
        Context context = mapToEntity(contextDTO);
        Context savedContext = contextRepository.save(context);
        log.info("Context created successfully with id: {}", savedContext.getId());

        String analysis = aiService.analyze(savedContext.getContent());
        savedContext.setAiAnalysis(analysis);
        savedContext = contextRepository.save(savedContext);

        return mapToDTO(savedContext);
    }

    public ContextDTO update(Long id, ContextDTO contextDTO) {
        log.debug("Updating context with id: {}", id);
        Context context = contextRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Context not found with id: %d", id)
                ));

        // Sanitizar conteúdo antes de salvar
        String sanitizedContent = sanitizeContent(contextDTO.getContent());
        context.setContent(sanitizedContent);
        context.setAiAnalysis(null);
        Context updatedContext = contextRepository.save(context);
        log.info("Context updated successfully with id: {}", id);

        String analysis = aiService.analyze(updatedContext.getContent());
        updatedContext.setAiAnalysis(analysis);
        updatedContext = contextRepository.save(updatedContext);

        return mapToDTO(updatedContext);
    }

    public void delete(Long id) {
        log.debug("Deleting context with id: {}", id);
        Context context = contextRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Context not found with id: %d", id)
                ));
        contextRepository.delete(context);
        log.info("Context deleted successfully with id: {}", id);
    }


    /**
     * Sanitiza e valida o conteúdo do contexto
     * Remove caracteres perigosos e limita o tamanho
     */
    private String sanitizeContent(String content) {
        if (content == null) {
            return "";
        }

        // Remover caracteres de controle e quebras de linha múltiplas
        String sanitized = content
                .replaceAll("\\n\\n+", "\n") // Múltiplas quebras de linha
                .replaceAll("[\\u0000-\\u001F\\u007F]", "") // Caracteres de controle
                .trim();

        // Limitar tamanho a 1000 caracteres
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
}
