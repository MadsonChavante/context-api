package com.contextapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração CORS para permitir requisições do frontend
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
                // allowedOriginPatterns é obrigatório com allowCredentials(true) no Spring Framework 7
        registry.addMapping("/**")
                .allowedOriginPatterns(
                    "http://localhost:4200",
                    "http://localhost:8080",
                    "https://localhost:4200",
                    "https://coont.netlify.app"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
