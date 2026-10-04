package com.warycode.agent;

import com.warycode.agent.agent.CodingAgent;
import com.warycode.agent.skill.Skill;
import com.warycode.agent.skill.SkillRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 命令行交互循环：/ 开头的行按命令分发，其余作为任务交给 agent。
 * LLM 调用失败只报错不退出，历史保留可重试。
 */
public final class Repl {

    private final CodingAgent agent;
    private final SkillRegistry skills;

    public Repl(CodingAgent agent, SkillRegistry skills) {
        this.agent = agent;
        this.skills = skills;
    }

    /** 主循环，输入流结束或 /exit 时退出。 */
    public void run() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("\n" + Ansi.teal("> "));
            System.out.flush();

            String line = in.readLine();
            if (line == null) {
                return;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("/")) {
                if (!handleCommand(line)) {
                    return;
                }
            } else {
                runTask(line);
            }
        }
    }

    /** 分发命令，返回 false 表示退出程序。 */
    private boolean handleCommand(String line) {
        int space = line.indexOf(' ');
        String command = space < 0 ? line : line.substring(0, space);
        String rest = space < 0 ? "" : line.substring(space + 1).trim();
        switch (command) {
            case "/exit", "/quit" -> {
                return false;
            }
            case "/clear" -> {
                agent.clear();
                System.out.println(Ansi.dim("(已清空对话)"));
            }
            case "/skill" -> handleSkill(rest);
            case "/help" -> System.out.println("/exit /quit 退出 | /clear 清空对话 | /skill [名称] 查看或使用技能");
            default -> System.out.println("未知命令: " + command + "，/help 查看可用命令");
        }
        return true;
    }

    /** /skill：无参列出技能清单；带名执行技能，名后可附补充说明。 */
    private void handleSkill(String rest) {
        if (rest.isEmpty()) {
            if (skills.isEmpty()) {
                System.out.println(Ansi.dim("(无可用技能)"));
            } else {
                for (Skill skill : skills.all()) {
                    System.out.println("  " + Ansi.teal(skill.name()) + "  " + Ansi.dim(skill.description()));
                }
            }
            return;
        }
        int space = rest.indexOf(' ');
        String name = space < 0 ? rest : rest.substring(0, space);
        String extra = space < 0 ? "" : rest.substring(space + 1).trim();
        Optional<Skill> found = skills.find(name);
        if (found.isEmpty()) {
            System.out.println(Ansi.red("[未知技能] ") + name + "，可用: "
                    + skills.all().stream().map(Skill::name).toList());
            return;
        }
        String task = "[技能指引: " + name + "]\n" + found.get().body();
        runTask(extra.isEmpty() ? task : task + "\n\n" + extra);
    }

    /** 执行任务并打印回答；失败只报错不退出。 */
    private void runTask(String input) {
        try {
            String answer = agent.runTurn(input);
            System.out.println();
            System.out.println(Ansi.dim("  ── 回答 ──────────────"));
            System.out.println(answer);
        } catch (Exception e) {
            System.out.println(Ansi.red("[LLM 调用失败] ") + (e.getMessage() != null ? e.getMessage() : e));
        }
    }
}
