package com.playtika.testcontainer.localstack;

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
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;

import java.util.Optional;

import static com.playtika.testcontainer.common.utils.ContainerUtils.configureCommonsAndStart;
import static com.playtika.testcontainer.localstack.LocalStackProperties.BEAN_NAME_EMBEDDED_LOCALSTACK;

@Slf4j
@Configuration
@ConditionalOnExpression("${embedded.containers.enabled:true}")
@AutoConfigureAfter(DockerPresenceBootstrapConfiguration.class)
@ConditionalOnProperty(name = "embedded.localstack.enabled", matchIfMissing = true)
@EnableConfigurationProperties(LocalStackProperties.class)
public class EmbeddedLocalStackBootstrapConfiguration {

    private static final String LOCALSTACK_NETWORK_ALIAS = "localstack.testcontainer.docker";

    @Bean
    @ConditionalOnToxiProxyEnabled(module = "localstack")
    ToxiproxyContainer.ContainerProxy localstackContainerProxy(ToxiproxyContainer toxiproxyContainer,
                                                          @Qualifier(BEAN_NAME_EMBEDDED_LOCALSTACK) LocalStackContainer localStack,
                                                          LocalStackProperties properties) {
        return toxiproxyContainer.getProxy(localStack, properties.getEdgePort());
    }

    @Bean
    @ConditionalOnToxiProxyEnabled(module = "localstack")
    public DynamicPropertyRegistrar localstackToxiProxyDynamicPropertyRegistrar(
        @Qualifier("localstackContainerProxy") ToxiproxyContainer.ContainerProxy proxy) {
        return registry -> {
            registry.add("embedded.localstack.toxiproxy.host", proxy::getContainerIpAddress);
            registry.add("embedded.localstack.toxiproxy.port", proxy::getProxyPort);
            registry.add("embedded.localstack.toxiproxy.proxyName", proxy::getName);
        };
    }

    @ConditionalOnMissingBean(name = BEAN_NAME_EMBEDDED_LOCALSTACK)
    @Bean(name = BEAN_NAME_EMBEDDED_LOCALSTACK, destroyMethod = "stop")
    public LocalStackContainer localStack(LocalStackProperties properties, Optional<Network> network) {
        LocalStackContainer localStack = new LocalStackContainer(ContainerUtils.getDockerImageName(properties))
                .withExposedPorts(properties.getEdgePort())
                .withNetworkAliases(LOCALSTACK_NETWORK_ALIAS);
        network.ifPresent(localStack::withNetwork);
        configureCommonsAndStart(localStack, properties, log);
        return localStack;
    }

    @Bean
    public DynamicPropertyRegistrar localStackDynamicPropertyRegistrar(
            @Qualifier(BEAN_NAME_EMBEDDED_LOCALSTACK) LocalStackContainer localStack,
            LocalStackProperties properties) {
        return registry -> {
            registry.add("embedded.localstack.host", localStack::getHost);
            registry.add("embedded.localstack.port", () -> localStack.getMappedPort(properties.getEdgePort()));
            registry.add("embedded.localstack.networkAlias", () -> LOCALSTACK_NETWORK_ALIAS);
            registry.add("embedded.localstack.internalPort", properties::getEdgePort);
        };
    }

}
