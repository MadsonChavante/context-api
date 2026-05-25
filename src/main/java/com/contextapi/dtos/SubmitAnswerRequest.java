package com.contextapi.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitAnswerRequest {

    @NotNull(message = "Exercise id is required")
    private Long exerciseId;

    @NotBlank(message = "Answer cannot be blank")
    private String answer;
}
