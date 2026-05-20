package com.contextapi.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonItemDTO {
    private Long id;
    private Long contextId;
    private Integer position;
    private String promptPt;
    private String suggestedAnswerEn;
    private String lastAnswer;
    private String teacherFeedback;
    private Integer score;
    private boolean completed;
}
