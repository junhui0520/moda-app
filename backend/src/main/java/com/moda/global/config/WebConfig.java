package com.moda.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: MVP 기준 모든 Origin 허용 (docs/api-spec.md 1.1).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    /** 스타일 이미지 원본 폴더 서빙 (backend/ 에서 실행 기준, DB의 image_path가 각 폴더 기준 상대경로). */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/hair/**").addResourceLocations("file:../hair_images/");
        registry.addResourceHandler("/images/style/**").addResourceLocations("file:../style/");
    }
}
