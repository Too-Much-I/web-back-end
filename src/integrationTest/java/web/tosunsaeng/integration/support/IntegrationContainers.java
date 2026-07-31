package web.tosunsaeng.integration.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public final class IntegrationContainers {

    public static final String MONGO_IMAGE = "mongo:7.0";
    public static final String REDIS_IMAGE = "redis:7.2-alpine";

    private IntegrationContainers() {
    }

    public static MongoDBContainer mongo() {
        return new MongoDBContainer(DockerImageName.parse(MONGO_IMAGE))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    public static GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(2));
    }
}
