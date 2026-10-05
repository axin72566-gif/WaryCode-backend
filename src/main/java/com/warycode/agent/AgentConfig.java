package com.warycode.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 配置，从 config.properties 读取。 */
public record AgentConfig(String apiKey, String baseUrl, String model) {

    private static final Path FILE = Path.of("config.properties");

    public static AgentConfig load() throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
        }
        return new AgentConfig(
                props.getProperty("api.key").trim(),
                props.getProperty("base.url").trim(),
                props.getProperty("model").trim());
    }
}
