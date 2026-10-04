package com.warycode.agent;

/** 终端着色工具；设 NO_COLOR 环境变量可整体关闭。 */
public final class Ansi {

    private static final String RESET = "\u001B[0m";

    private static final int[] BLUE = {86, 156, 214};   // 渐变起点
    private static final int[] MINT = {80, 250, 190};   // 渐变终点

    private static final boolean ENABLED = System.getenv("NO_COLOR") == null;

    private Ansi() {
    }

    /** 文本按字符位置做蓝→青绿渐变。 */
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

    public static String dim(String text) {
        return wrap("2", text);
    }

    public static String teal(String text) {
        return wrap("38;2;" + MINT[0] + ";" + MINT[1] + ";" + MINT[2], text);
    }

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
