package com.contextapi.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contexts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Context {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

        @NotBlank(message = "Content cannot be blank")
    @Size(min = 1, max = 1000, message = "Content must be between 1 and 1000 characters")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String aiAnalysis;

    @Version
    private Long version;
}
