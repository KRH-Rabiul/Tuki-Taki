package com.marketbridge.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

// This class does three simple jobs:
// 1) Lets the browser (running on the same site) call our /api/... endpoints without CORS errors.
// 2) Makes the upload folder (where product/profile photos are saved) visible in the
//    browser at the URL /uploads/filename.jpg
// 3) Registers AuthInterceptor so every /api/** call gets checked for a valid login token.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    // Fixed, absolute path (see app.upload-dir in application.properties) -
    // deliberately NOT relative to the project folder, so uploaded photos
    // survive extracting a fresh project zip into a new folder.
    @Value("${app.upload-dir}")
    private String uploadDir;

    // Comma-separated list of origins allowed to call /api/**. Defaults to "*"
    // for easy local development. Since this app serves its own frontend from
    // the same origin as its API, "*" is mostly only a risk if this API is
    // ever called cross-origin by another site - but it's still best practice
    // to lock this to the real domain once deployed, e.g.
    // app.allowed-origins=https://your-deployed-domain.com
    @Value("${app.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File(uploadDir).getAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + File.separator);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}
