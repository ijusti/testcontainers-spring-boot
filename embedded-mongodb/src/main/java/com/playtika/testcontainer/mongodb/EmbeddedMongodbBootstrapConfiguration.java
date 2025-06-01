package com.playtika.testcontainer.mongodb;

import com.playtika.testcontainer.common.spring.DockerPresenceBootstrapConfiguration;
import com.playtika.testcontainer.common.utils.ContainerUtils;
import com.playtika.testcontainer.toxiproxy.condition.ConditionalOnToxiProxyEnabled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.util.Optional;

import static com.playtika.testcontainer.common.utils.ContainerUtils.configureCommonsAndStart;
import static com.playtika.testcontainer.mongodb.MongodbProperties.BEAN_NAME_EMBEDDED_MONGODB;

@Slf4j
@Configuration
@ConditionalOnExpression("${embedded.containers.enabled:true}")
@AutoConfigureAfter(DockerPresenceBootstrapConfiguration.class)
@ConditionalOnProperty(
        name = "embedded.mongodb.enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MongodbProperties.class)
public class EmbeddedMongodbBootstrapConfiguration {

    private static final String MONGODB_NETWORK_ALIAS = "mongodb.testcontainer.docker";

    @Bean
    @ConditionalOnToxiProxyEnabled(module = "mongodb")
    public ToxiproxyContainer.ContainerProxy mongodbContainerProxy(ToxiproxyContainer toxiproxyContainer,
                                                                  @Qualifier(BEAN_NAME_EMBEDDED_MONGODB) GenericContainer<?> mongodb,
                                                                  MongodbProperties properties) {
        return toxiproxyContainer.getProxy(mongodb, properties.getPort());
    }

    @Bean(value = BEAN_NAME_EMBEDDED_MONGODB, destroyMethod = "stop")
    public GenericContainer<?> mongodb(MongodbProperties properties,
                                       MongodbStatusCheck mongodbStatusCheck,
                                       Optional<Network> network) {
        GenericContainer<?> mongodb =
                new GenericContainer<>(ContainerUtils.getDockerImageName(properties))
                        .withEnv("MONGO_INITDB_ROOT_USERNAME", properties.getUsername())
                        .withEnv("MONGO_INITDB_ROOT_PASSWORD", properties.getPassword())
                        .withEnv("MONGO_INITDB_DATABASE", properties.getDatabase())
                        .withExposedPorts(properties.getPort())
                        .waitingFor(new LogMessageWaitStrategy().withRegEx(".*mongod startup complete.*"))
                        .withNetworkAliases(MONGODB_NETWORK_ALIAS);

        network.ifPresent(mongodb::withNetwork);

        mongodb = configureCommonsAndStart(mongodb, properties, log);
        return mongodb;
    }

    @Bean
    public DynamicPropertyRegistrar mongodbDynamicPropertyRegistrar(
            @Qualifier(BEAN_NAME_EMBEDDED_MONGODB) GenericContainer<?> mongodb,
            MongodbProperties properties) {
        return registry -> {
            registry.add("embedded.mongodb.port", () -> mongodb.getMappedPort(properties.getPort()));
            registry.add("embedded.mongodb.host", mongodb::getHost);
            registry.add("embedded.mongodb.username", properties::getUsername);
            registry.add("embedded.mongodb.password", properties::getPassword);
            registry.add("embedded.mongodb.database", properties::getDatabase);
            registry.add("embedded.mongodb.networkAlias", () -> MONGODB_NETWORK_ALIAS);
            registry.add("embedded.mongodb.internalPort", properties::getPort);
        };
    }

    @Bean
    @ConditionalOnToxiProxyEnabled(module = "mongodb")
    public DynamicPropertyRegistrar mongodbToxiProxyDynamicPropertyRegistrar(
            @Qualifier("mongodbContainerProxy") ToxiproxyContainer.ContainerProxy proxy) {
        return registry -> {
            registry.add("embedded.mongodb.toxiproxy.host", proxy::getContainerIpAddress);
            registry.add("embedded.mongodb.toxiproxy.port", proxy::getProxyPort);
            registry.add("embedded.mongodb.toxiproxy.proxyName", proxy::getName);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    MongodbStatusCheck mongodbStartupCheckStrategy(MongodbProperties properties) {
        return new MongodbStatusCheck(properties);
    }
}
