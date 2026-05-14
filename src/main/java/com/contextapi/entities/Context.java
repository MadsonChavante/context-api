package com.contextapi.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
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
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String aiAnalysis;

    @Version
    private Long version;
}
