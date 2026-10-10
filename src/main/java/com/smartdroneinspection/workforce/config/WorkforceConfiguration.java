package com.smartdroneinspection.workforce.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkforceProperties.class)
public class WorkforceConfiguration {}
