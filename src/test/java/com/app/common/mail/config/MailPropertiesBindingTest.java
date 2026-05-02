package com.app.common.mail.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import com.app.modules.mail.config.MailProperties;

@SpringBootTest(classes = MailPropertiesBindingTest.TestConfig.class)
@TestPropertySource(
        properties = {
            "app.mail.from-address=noreply@test.com",
            "app.mail.from-name=Test Sender",
            "app.mail.app-name=TestApp",
            "app.mail.frontend-base-url=http://test.local"
        })
class MailPropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties(MailProperties.class)
    static class TestConfig {}

    @Autowired private MailProperties mailProperties;

    @Test
    void fieldsAreCorrectlyBound() {
        assertThat(mailProperties.getFromAddress()).isEqualTo("noreply@test.com");
        assertThat(mailProperties.getFromName()).isEqualTo("Test Sender");
        assertThat(mailProperties.getAppName()).isEqualTo("TestApp");
        assertThat(mailProperties.getFrontendBaseUrl()).isEqualTo("http://test.local");
    }
}
