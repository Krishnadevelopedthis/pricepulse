package com.pricepulse.web;

import com.pricepulse.config.PricePulseProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final PricePulseProperties props;

    public WebConfig(PricePulseProperties props) {
        this.props = props;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ClientIdInterceptor())
                .addPathPatterns("/api/products/**", "/api/notifications/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = props.corsAllowedOriginPatterns().split("\\s*,\\s*");
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Client-Id")
                .maxAge(3600);
    }
}
