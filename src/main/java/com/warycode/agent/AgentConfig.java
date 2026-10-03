package com.warycode.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 配置加载：从 config.properties 读取（模板见 config.properties.example）。
 * 仅 api.key 必填，其余项有默认值；workspace 归一化是后续沙箱校验的基准。
 */
public record AgentConfig(String apiKey, String baseUrl, String model, Path workspace) {

    private static final Path FILE = Path.of("config.properties");
    private static final Path TEMPLATE = Path.of("config.properties.example");
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-5.5";

    public static AgentConfig load() throws IOException {
        if (Files.notExists(FILE)) {
            if (Files.exists(TEMPLATE)) {
                Files.copy(TEMPLATE, FILE);
                System.out.println("已生成 config.properties，请填写 api.key 后重新运行。");
            } else {
                System.out.println("缺少 config.properties（且未找到模板 config.properties.example）。");
            }
            System.exit(0);
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
        }
        String apiKey = props.getProperty("api.key", "").trim();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("config.properties 中 api.key 未填写");
        }
        String workspaceRaw = props.getProperty("workspace", "").trim();
        Path workspace = workspaceRaw.isEmpty()
                ? Path.of("").toAbsolutePath().normalize()
                : resolveWorkspace(workspaceRaw);
        return new AgentConfig(
                apiKey,
                defaultIfBlank(props.getProperty("base.url"), DEFAULT_BASE_URL),
                defaultIfBlank(props.getProperty("model"), DEFAULT_MODEL),
                workspace);
    }

    private static Path resolveWorkspace(String raw) {
        Path p = Path.of(raw).toAbsolutePath().normalize();
        if (Files.notExists(p)) {
            throw new IllegalStateException("workspace 目录不存在: " + p);
        }
        return p;
    }

    private static String defaultIfBlank(String value, String def) {
        return value == null || value.isBlank() ? def : value.trim();
    }
}
