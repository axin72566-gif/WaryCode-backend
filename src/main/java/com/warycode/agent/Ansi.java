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
        int visibleCharCount = (int) text.chars().filter(ch -> ch != ' ' && ch != '\n').count();
        StringBuilder coloredText = new StringBuilder(text.length() * 12);
        int visibleCharIndex = 0;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            for (char ch : lines[lineIndex].toCharArray()) {
                if (ch == ' ') {
                    coloredText.append(ch);
                    continue;
                }
                double ratio = visibleCharCount <= 1 ? 0 : (double) visibleCharIndex / (visibleCharCount - 1);
                coloredText.append("\u001B[38;2;").append(blend(BLUE[0], MINT[0], ratio))
                        .append(';').append(blend(BLUE[1], MINT[1], ratio))
                        .append(';').append(blend(BLUE[2], MINT[2], ratio))
                        .append('m').append(ch);
                visibleCharIndex++;
            }
            if (lineIndex < lines.length - 1) {
                coloredText.append('\n');
            }
        }
        return coloredText.append(RESET).toString();
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

    private static int blend(int from, int to, double ratio) {
        return (int) Math.round(from + (to - from) * ratio);
    }

    private static String wrap(String ansiCode, String text) {
        return ENABLED ? "\u001B[" + ansiCode + "m" + text + RESET : text;
    }
}
