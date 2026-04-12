package com.quizapp.exam.config;

import com.quizapp.exam.enums.ExamStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, ExamStatus>() {
            @Override
            public ExamStatus convert(String source) {
                return ExamStatus.fromJson(source);
            }
        });
    }
}

