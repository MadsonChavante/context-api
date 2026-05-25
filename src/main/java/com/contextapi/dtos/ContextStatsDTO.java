package com.contextapi.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextStatsDTO {
    private Long contextId;
    private String contextContent;
    private int totalExercises;
    private int totalScore;
    private double averageScore;
}