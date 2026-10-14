package com.smartdroneinspection.assets.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssetProperties.class)
public class AssetsConfiguration {}
