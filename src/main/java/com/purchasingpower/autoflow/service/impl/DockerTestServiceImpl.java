/*
package com.purchasingpower.autoflow.service.impl;



import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;

import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.purchasingpower.autoflow.service.DockerTestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DockerTestServiceImpl implements DockerTestService {

    private final DockerClient dockerClient;

    public DockerTestServiceImpl() {
        // 1. Config
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        // 2. Transport (✅ SWITCHED TO ZERODEP)
        // Uses native Java HTTP client, no Apache conflicts
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .build();

        // 3. Instance
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    @Override
    public void runTests(File workspaceDir) {
        log.info("Initializing Docker Test Runner for: {}", workspaceDir.getName());

        String imageName = "maven:3.9-eclipse-temurin-17";

        try {
            // Pull Image
            log.info("Pulling image...");
            dockerClient.pullImageCmd(imageName).start().awaitCompletion();

            // Run Container
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withBinds(Bind.parse(workspaceDir.getAbsolutePath() + ":/app"));

            CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                    .withWorkingDir("/app")
                    .withHostConfig(hostConfig)
                    .withCmd("mvn", "-B", "-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn", "clean", "test")
                    .exec();

            String containerId = container.getId();
            log.info("Running tests in container: {}", containerId);

            try {
                dockerClient.startContainerCmd(containerId).exec();

                Long exitCode = dockerClient.waitContainerCmd(containerId)
                        .start()
                        .awaitStatusCode(15, TimeUnit.MINUTES)
                        .longValue();

                if (exitCode != 0) {
                    throw new RuntimeException("Tests Failed (Exit Code " + exitCode + ")");
                }
                log.info("Tests Passed!");

            } finally {
                dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Docker Error: " + e.getMessage(), e);
        }
    }
}*/
