package com.contextapi.dtos;

import com.contextapi.enums.LessonStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonDTO {
    private Long id;
    private String dynamicType;
    private LessonStatus status;
    private String intro;
    private String finalFeedback;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private List<LessonItemDTO> items;
}
