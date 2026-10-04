package com.warycode.agent;

/**
 * 终端着色：蓝→青绿 truecolor 渐变与少量修饰色，供启动横幅、REPL 提示、工具日志和回答分隔线共用。
 * 设置环境变量 NO_COLOR 可整体关闭（输出重定向到文件时拿干净文本）。
 */
public final class Ansi {

    private static final String RESET = "\u001B[0m";

    /** 渐变起点：蓝 #569CD6。 */
    private static final int[] BLUE = {86, 156, 214};
    /** 渐变终点：青绿 #50FABE，兼作提示符主题色。 */
    private static final int[] MINT = {80, 250, 190};

    private static final boolean ENABLED = System.getenv("NO_COLOR") == null;

    private Ansi() {
    }

    /** 按整段文本的字符位置做蓝→绿渐变；多行时自然形成左上到右下的对角扫过。空格不着色省转义字节。 */
    public static String gradient(String text) {
        if (!ENABLED) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        int visible = (int) text.chars().filter(c -> c != ' ' && c != '\n').count();
        StringBuilder sb = new StringBuilder(text.length() * 12);
        int index = 0;
        for (int l = 0; l < lines.length; l++) {
            for (char c : lines[l].toCharArray()) {
                if (c == ' ') {
                    sb.append(c);
                    continue;
                }
                double t = visible <= 1 ? 0 : (double) index / (visible - 1);
                sb.append("\u001B[38;2;").append(blend(BLUE[0], MINT[0], t))
                        .append(';').append(blend(BLUE[1], MINT[1], t))
                        .append(';').append(blend(BLUE[2], MINT[2], t))
                        .append('m').append(c);
                index++;
            }
            if (l < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.append(RESET).toString();
    }

    /** 弱化灰，用于信息行的标签和提示文案。 */
    public static String dim(String text) {
        return wrap("2", text);
    }

    /** 青绿主题色，用于 REPL 提示符，与渐变终点呼应。 */
    public static String teal(String text) {
        return wrap("38;2;" + MINT[0] + ";" + MINT[1] + ";" + MINT[2], text);
    }

    /** 错误红，用于失败提示。 */
    public static String red(String text) {
        return wrap("38;2;248;113;115", text);
    }

    private static int blend(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    private static String wrap(String code, String text) {
        return ENABLED ? "\u001B[" + code + "m" + text + RESET : text;
    }
}
