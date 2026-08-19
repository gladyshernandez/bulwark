package com.bulwark;

import com.bulwark.config.AuditProperties;
import com.bulwark.config.UpstreamProperties;
import com.bulwark.screening.ScreeningProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// DataSourceAutoConfiguration is excluded so the proxy starts with no database configured;
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties({UpstreamProperties.class, ScreeningProperties.class, AuditProperties.class})
public class BulwarkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BulwarkApplication.class, args);
    }
}
