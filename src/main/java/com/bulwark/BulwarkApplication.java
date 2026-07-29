package com.bulwark;

import com.bulwark.config.UpstreamProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(UpstreamProperties.class)
public class BulwarkApplication {

    public static void main(String[] args) {
        SpringApplication.run(BulwarkApplication.class, args);
    }
}
