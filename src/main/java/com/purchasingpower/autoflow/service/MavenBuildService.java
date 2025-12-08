package com.purchasingpower.autoflow.service;

import java.io.File;

public interface MavenBuildService {
    /**
     * Runs 'mvn clean install'.
     * @param projectDir The directory containing pom.xml
     * @return true if build success, false if failed
     */
    void buildAndVerify(File projectDir);
}