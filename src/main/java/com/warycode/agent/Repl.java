package com.warycode.agent;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.warycode.agent.agent.CodingAgent;
import com.warycode.agent.skill.Skill;
import com.warycode.agent.skill.SkillRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

/**
 * 命令行交互循环：/ 开头的行按命令分发，其余作为任务交给 agent。
 * LLM 调用失败只报错不退出，历史保留可重试。
 */
public final class Repl {

    private final CodingAgent agent;
    private final SkillRegistry skillRegistry;

    public Repl(CodingAgent agent, SkillRegistry skillRegistry) {
        this.agent = agent;
        this.skillRegistry = skillRegistry;
    }

    /** 主循环，输入流结束或 /exit 时退出。 */
    public void run() throws IOException {
        BufferedReader inputReader = new BufferedReader(IoUtil.getBomReader(System.in));
        while (true) {
            System.out.print("\n" + Ansi.teal("> "));
            System.out.flush();

            String line = inputReader.readLine();
            if (line == null) {
                return;
            }
            line = StrUtil.trim(line);
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
        String command = StrUtil.subBefore(line, ' ', false);
        String commandArgs = StrUtil.trimToEmpty(StrUtil.subAfter(line, ' ', false));
        switch (command) {
            case "/exit", "/quit" -> {
                return false;
            }
            case "/clear" -> {
                agent.clear();
                System.out.println(Ansi.dim("(已清空对话)"));
            }
            case "/skill" -> handleSkill(commandArgs);
            case "/help" -> System.out.println("/exit /quit 退出 | /clear 清空对话 | /skill [名称] 查看或使用技能");
            default -> System.out.println("未知命令: " + command + "，/help 查看可用命令");
        }
        return true;
    }

    /** /skill：无参列出技能清单；带名执行技能，名后可附补充说明。 */
    private void handleSkill(String commandArgs) {
        if (commandArgs.isEmpty()) {
            if (skillRegistry.isEmpty()) {
                System.out.println(Ansi.dim("(无可用技能)"));
            } else {
                for (Skill skill : skillRegistry.all()) {
                    System.out.println("  " + Ansi.teal(skill.name()) + "  " + Ansi.dim(skill.description()));
                }
            }
            return;
        }
        String skillName = StrUtil.subBefore(commandArgs, ' ', false);
        String extraInstructions = StrUtil.trimToEmpty(StrUtil.subAfter(commandArgs, ' ', false));
        Optional<Skill> matchedSkill = skillRegistry.find(skillName);
        if (matchedSkill.isEmpty()) {
            System.out.println(Ansi.red("[未知技能] ") + skillName + "，可用: "
                    + skillRegistry.all().stream().map(Skill::name).toList());
            return;
        }
        String taskPrompt = "[技能指引: " + skillName + "]\n" + matchedSkill.get().body();
        runTask(extraInstructions.isEmpty() ? taskPrompt : taskPrompt + "\n\n" + extraInstructions);
    }

    /** 执行任务并打印回答；失败只报错不退出。 */
    private void runTask(String input) {
        try {
            String answer = agent.runTurn(input);
            System.out.println();
            System.out.println(Ansi.dim("  ========================== 回答 =========================="));
            System.out.println();
            System.out.println(answer);
            System.out.println();
            System.out.println(Ansi.dim("  ========================== 回答 =========================="));
        } catch (Exception e) {
            System.out.println(Ansi.red("[LLM 调用失败] ") + StrUtil.blankToDefault(e.getMessage(), e.toString()));
        }
    }
}
