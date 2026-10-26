package com.nachiketa.scheduler.api.config;

import com.nachiketa.scheduler.api.converter.SmithyJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(
            HttpMessageConverters.ServerBuilder builder
    ) {
        builder.addCustomConverter(
                new SmithyJsonHttpMessageConverter()
        );
    }
}
