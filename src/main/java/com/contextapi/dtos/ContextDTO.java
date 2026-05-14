package com.contextapi.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextDTO {

        private Long id;

    @NotBlank(message = "Content cannot be blank")
    private String content;

    private String aiAnalysis;
}
