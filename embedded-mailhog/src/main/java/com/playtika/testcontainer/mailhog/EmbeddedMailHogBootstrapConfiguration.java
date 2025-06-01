package com.playtika.testcontainer.mailhog;

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
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Optional;

import static com.playtika.testcontainer.common.utils.ContainerUtils.configureCommonsAndStart;
import static com.playtika.testcontainer.mailhog.MailHogProperties.BEAN_NAME_EMBEDDED_MAILHOG;

@Slf4j
@Configuration
@ConditionalOnExpression("${embedded.containers.enabled:true}")
@AutoConfigureAfter(DockerPresenceBootstrapConfiguration.class)
@ConditionalOnProperty(name = "embedded.mailhog.enabled", matchIfMissing = true)
@EnableConfigurationProperties(MailHogProperties.class)
public class EmbeddedMailHogBootstrapConfiguration {

    private static final String MAILHOG_NETWORK_ALIAS = "mailhog.testcontainer.docker";

    @Bean
    @ConditionalOnToxiProxyEnabled(module = "mailhog")
    public ToxiproxyContainer.ContainerProxy mailhogSmtpContainerProxy(ToxiproxyContainer toxiproxyContainer,
                                                                @Qualifier(BEAN_NAME_EMBEDDED_MAILHOG) GenericContainer<?> mailhogContainer,
                                                                MailHogProperties properties) {
        ToxiproxyContainer.ContainerProxy proxy = toxiproxyContainer.getProxy(mailhogContainer, properties.getSmtpPort());
        log.info("Started MailHog SMTP ToxiProxy connection details host={}, port={}, proxyName={}", proxy.getContainerIpAddress(), proxy.getProxyPort(), proxy.getName());
        return proxy;
    }

    @Bean
    @ConditionalOnToxiProxyEnabled(module = "mailhog")
    public DynamicPropertyRegistrar mailhogSmtpToxiProxyDynamicPropertyRegistrar(@Qualifier("mailhogSmtpContainerProxy") ToxiproxyContainer.ContainerProxy proxy) {
        return registry -> {
            registry.add("embedded.mailhog.smtp.toxiproxy.host", proxy::getContainerIpAddress);
            registry.add("embedded.mailhog.smtp.toxiproxy.port", proxy::getProxyPort);
            registry.add("embedded.mailhog.smtp.toxiproxy.proxyName", proxy::getName);
        };
    }

    @ConditionalOnMissingBean(name = BEAN_NAME_EMBEDDED_MAILHOG)
    @Bean(name = BEAN_NAME_EMBEDDED_MAILHOG, destroyMethod = "stop")
    public GenericContainer<?> mailHog(MailHogProperties properties, Optional<Network> network) {
        GenericContainer<?> mailHog = new GenericContainer<>(ContainerUtils.getDockerImageName(properties))
                .withExposedPorts(properties.getSmtpPort(), properties.getHttpPort())
                .withNetworkAliases(MAILHOG_NETWORK_ALIAS)
                .waitingFor(Wait.forListeningPort());

        network.ifPresent(mailHog::withNetwork);

        mailHog = configureCommonsAndStart(mailHog, properties, log);
        return mailHog;
    }

    @Bean
    public DynamicPropertyRegistrar mailhogDynamicPropertyRegistrar(@Qualifier(BEAN_NAME_EMBEDDED_MAILHOG) GenericContainer<?> mailHog, MailHogProperties properties) {
        return registry -> {
            Integer smtpMappedPort = mailHog.getMappedPort(properties.getSmtpPort());
            Integer httpMappedPort = mailHog.getMappedPort(properties.getHttpPort());
            registry.add("embedded.mailhog.host", mailHog::getHost);
            registry.add("embedded.mailhog.smtp-port", () -> smtpMappedPort);
            registry.add("embedded.mailhog.http-port", () -> httpMappedPort);
            registry.add("embedded.mailhog.networkAlias", () -> MAILHOG_NETWORK_ALIAS);
            registry.add("embedded.mailhog.internalSmtpPort", properties::getSmtpPort);
            registry.add("embedded.mailhog.internalHttpPort", properties::getHttpPort);
        };
    }

}
