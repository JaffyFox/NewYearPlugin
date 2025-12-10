package com.loma.plugin.utils;

import net.md_5.bungee.api.ChatColor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для работы с градиентными цветами и анимацией текста
 */
public class GradientUtils {

    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[0-9A-Fa-f]{6}):(#[0-9A-Fa-f]{6})>(.+?)</gradient>");
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([0-9A-Fa-f]{6})>");
    private static final Pattern BOLD_PATTERN = Pattern.compile("<bold>(.+?)</bold>");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("<italic>(.+?)</italic>");

    /**
     * Применяет градиент к тексту
     */
    public static String applyGradient(String text) {
        if (text == null || text.isEmpty()) return text;

        // Обработка bold/italic
        text = BOLD_PATTERN.matcher(text).replaceAll("§l$1§r");
        text = ITALIC_PATTERN.matcher(text).replaceAll("§o$1§r");

        // Обработка градиентов
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String content = matcher.group(3);

            String gradient = createGradient(content, startHex, endHex);
            matcher.appendReplacement(result, Matcher.quoteReplacement(gradient));
        }
        matcher.appendTail(result);

        // Обработка одиночных hex цветов
        text = result.toString();
        matcher = HEX_PATTERN.matcher(text);
        result = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(result, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(result);

        return MessageUtils.color(result.toString());
    }

    /**
     * Создаёт градиент между двумя цветами
     */
    private static String createGradient(String text, String startHex, String endHex) {
        // Убираем форматирование для подсчёта символов
        String cleanText = ChatColor.stripColor(text);
        if (cleanText.length() == 0) return text;

        Color startColor = Color.decode(startHex);
        Color endColor = Color.decode(endHex);

        StringBuilder result = new StringBuilder();
        int length = cleanText.length();

        for (int i = 0; i < length; i++) {
            char c = cleanText.charAt(i);
            if (c == ' ') {
                result.append(' ');
                continue;
            }

            double ratio = (double) i / (length - 1);
            int r = (int) (startColor.getRed() + ratio * (endColor.getRed() - startColor.getRed()));
            int g = (int) (startColor.getGreen() + ratio * (endColor.getGreen() - startColor.getGreen()));
            int b = (int) (startColor.getBlue() + ratio * (endColor.getBlue() - startColor.getBlue()));

            Color stepColor = new Color(r, g, b);
            String hexColor = String.format("#%02x%02x%02x", stepColor.getRed(), stepColor.getGreen(), stepColor.getBlue());

            result.append(ChatColor.of(hexColor)).append(c);
        }

        return result.toString();
    }

    /**
     * Создаёт анимированный градиент (сдвиг цветов)
     */
    public static List<String> createAnimatedGradient(String text, String startHex, String endHex, int frames) {
        List<String> animation = new ArrayList<>();
        
        String cleanText = ChatColor.stripColor(text);
        if (cleanText.length() == 0) {
            animation.add(text);
            return animation;
        }

        Color startColor = Color.decode(startHex);
        Color endColor = Color.decode(endHex);

        for (int frame = 0; frame < frames; frame++) {
            StringBuilder result = new StringBuilder();
            int length = cleanText.length();

            for (int i = 0; i < length; i++) {
                char c = cleanText.charAt(i);
                if (c == ' ') {
                    result.append(' ');
                    continue;
                }

                // Сдвиг градиента по кадрам
                double offset = (double) frame / frames;
                double ratio = ((double) i / length + offset) % 1.0;

                int r = (int) (startColor.getRed() + ratio * (endColor.getRed() - startColor.getRed()));
                int g = (int) (startColor.getGreen() + ratio * (endColor.getGreen() - startColor.getGreen()));
                int b = (int) (startColor.getBlue() + ratio * (endColor.getBlue() - startColor.getBlue()));

                Color stepColor = new Color(r, g, b);
                String hexColor = String.format("#%02x%02x%02x", stepColor.getRed(), stepColor.getGreen(), stepColor.getBlue());

                result.append(ChatColor.of(hexColor)).append(c);
            }

            animation.add(result.toString());
        }

        return animation;
    }

    /**
     * Парсит текст с поддержкой градиентов и возвращает анимацию
     */
    public static List<String> parseAnimatedText(String text, int frames) {
        if (text == null || text.isEmpty()) {
            List<String> single = new ArrayList<>();
            single.add(text);
            return single;
        }

        // Обработка bold/italic
        boolean isBold = text.contains("<bold>");
        boolean isItalic = text.contains("<italic>");
        
        text = text.replace("<bold>", "").replace("</bold>", "");
        text = text.replace("<italic>", "").replace("</italic>", "");

        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        
        if (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String content = matcher.group(3);

            List<String> animation = createAnimatedGradient(content, startHex, endHex, frames);
            
            // Применяем форматирование
            if (isBold || isItalic) {
                List<String> formatted = new ArrayList<>();
                for (String frame : animation) {
                    String result = frame;
                    if (isBold) result = "§l" + result;
                    if (isItalic) result = "§o" + result;
                    formatted.add(result);
                }
                return formatted;
            }
            
            return animation;
        }

        // Если градиента нет, возвращаем статичный текст
        List<String> single = new ArrayList<>();
        single.add(applyGradient(text));
        return single;
    }
}
