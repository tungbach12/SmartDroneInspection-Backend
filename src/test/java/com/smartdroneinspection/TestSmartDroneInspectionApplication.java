package com.smartdroneinspection;

import org.springframework.boot.SpringApplication;

public class TestSmartDroneInspectionApplication {

  public static void main(String[] args) {
    SpringApplication.from(SmartDroneInspectionApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
