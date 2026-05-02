package com.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        })
@ActiveProfiles("test")
class ApplicationTests {

    @Test
    void contextLoads() {}
}
