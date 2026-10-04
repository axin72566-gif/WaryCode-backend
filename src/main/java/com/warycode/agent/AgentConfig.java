package com.warycode.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** 配置，从 config.properties 读取。workspace 归一化为绝对路径，作为文件工具沙箱基准。 */
public record AgentConfig(String apiKey, String baseUrl, String model, Path workspace) {

    private static final Path FILE = Path.of("config.properties");

    public static AgentConfig load() throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
        }
        String workspaceRaw = props.getProperty("workspace", "").trim();
        Path workspace = workspaceRaw.isEmpty()
                ? Path.of("").toAbsolutePath().normalize()
                : Path.of(workspaceRaw).toAbsolutePath().normalize();
        return new AgentConfig(
                props.getProperty("api.key").trim(),
                props.getProperty("base.url").trim(),
                props.getProperty("model").trim(),
                workspace);
    }
}
