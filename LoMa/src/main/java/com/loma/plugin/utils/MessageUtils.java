package com.loma.plugin.utils;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");
    // Глобальный префикс для сообщений игрокам (может быть пустым)
    private static String globalPrefix = "";

    /** Установить глобальный префикс сообщений игрокам */
    public static void setPrefix(String prefix) {
        globalPrefix = prefix == null ? "" : prefix;
    }

    /**
     * Конвертирует цветовые коды в сообщении
     */
    public static String color(String message) {
        if (message == null) return "";

        // Поддержка hex цветов (&#RRGGBB)
        Matcher matcher = HEX_PATTERN.matcher(message);
        while (matcher.find()) {
            String hexColor = matcher.group(1);
            // Use Bungee's ChatColor for hex colors; Paper translates it correctly to Spigot components
            String replacement = net.md_5.bungee.api.ChatColor.of("#" + hexColor).toString();
            message = message.replace("&#" + hexColor, replacement);
        }

        // Стандартные цветовые коды
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Конвертирует список строк с цветовыми кодами
     */
    public static List<String> color(List<String> messages) {
        List<String> colored = new ArrayList<>();
        for (String message : messages) {
            colored.add(color(message));
        }
        return colored;
    }

    /**
     * Отправляет сообщение отправителю команды
     */
    public static void send(CommandSender sender, String message) {
        // Добавляем префикс только игрокам и только для обычных чат-сообщений
        if (sender instanceof Player && globalPrefix != null && !globalPrefix.isEmpty()) {
            sender.sendMessage(color(globalPrefix + message));
        } else {
            sender.sendMessage(color(message));
        }
    }

    /**
     * Отправляет несколько сообщений
     */
    public static void send(CommandSender sender, List<String> messages) {
        for (String message : messages) {
            send(sender, message);
        }
    }

    /**
     * Отправляет сообщение с префиксом
     */
    public static void sendWithPrefix(CommandSender sender, String prefix, String message) {
        send(sender, prefix + message);
    }

    /**
     * Отправляет сообщение в консоль
     */
    public static void sendConsole(String message) {
        Bukkit.getConsoleSender().sendMessage(color(message));
    }

    /**
     * Отправляет сообщение всем игрокам
     */
    public static void broadcast(String message) {
        Bukkit.broadcastMessage(color(message));
    }

    /**
     * Отправляет сообщение всем игрокам с правами
     */
    public static void broadcast(String message, String permission) {
        String colored = color(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(colored);
            }
        }
    }

    /**
     * Отправляет ActionBar сообщение игроку
     */
    public static void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color(message)));
    }

    /**
     * Отправляет Title сообщение игроку
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
    }

    /**
     * Отправляет Title сообщение с настройками по умолчанию
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }

    /**
     * Центрирует текст в чате
     */
    public static String center(String message) {
        if (message == null || message.equals("")) return "";
        message = color(message);

        int messagePxSize = 0;
        boolean previousCode = false;
        boolean isBold = false;

        for (char c : message.toCharArray()) {
            if (c == '§') {
                previousCode = true;
            } else if (previousCode) {
                previousCode = false;
                isBold = c == 'l' || c == 'L';
            } else {
                DefaultFontInfo dFI = DefaultFontInfo.getDefaultFontInfo(c);
                messagePxSize += isBold ? dFI.getBoldLength() : dFI.getLength();
                messagePxSize++;
            }
        }

        int halvedMessageSize = messagePxSize / 2;
        int toCompensate = 154 - halvedMessageSize;
        int spaceLength = DefaultFontInfo.SPACE.getLength() + 1;
        int compensated = 0;
        StringBuilder sb = new StringBuilder();

        while (compensated < toCompensate) {
            sb.append(" ");
            compensated += spaceLength;
        }

        return sb.toString() + message;
    }

    /**
     * Создает прогресс бар
     */
    public static String createProgressBar(int current, int max, int totalBars, char symbol, ChatColor completedColor, ChatColor notCompletedColor) {
        float percent = (float) current / max;
        int progressBars = (int) (totalBars * percent);

        StringBuilder builder = new StringBuilder();
        builder.append(completedColor);

        for (int i = 0; i < progressBars; i++) {
            builder.append(symbol);
        }

        builder.append(notCompletedColor);

        for (int i = progressBars; i < totalBars; i++) {
            builder.append(symbol);
        }

        return builder.toString();
    }

    /**
     * Создает линию разделитель
     */
    public static String createLine(char symbol, int length, ChatColor color) {
        StringBuilder builder = new StringBuilder();
        builder.append(color);

        for (int i = 0; i < length; i++) {
            builder.append(symbol);
        }

        return builder.toString();
    }

    /**
     * Удаляет цветовые коды из текста
     */
    public static String stripColors(String message) {
        return ChatColor.stripColor(color(message));
    }

    /**
     * Enum для информации о шрифтах
     */
    private enum DefaultFontInfo {
        A('A', 5),
        a('a', 5),
        B('B', 5),
        b('b', 5),
        C('C', 5),
        c('c', 5),
        D('D', 5),
        d('d', 5),
        E('E', 5),
        e('e', 5),
        F('F', 5),
        f('f', 4),
        G('G', 5),
        g('g', 5),
        H('H', 5),
        h('h', 5),
        I('I', 3),
        i('i', 1),
        J('J', 5),
        j('j', 5),
        K('K', 5),
        k('k', 4),
        L('L', 5),
        l('l', 1),
        M('M', 5),
        m('m', 5),
        N('N', 5),
        n('n', 5),
        O('O', 5),
        o('o', 5),
        P('P', 5),
        p('p', 5),
        Q('Q', 5),
        q('q', 5),
        R('R', 5),
        r('r', 5),
        S('S', 5),
        s('s', 5),
        T('T', 5),
        t('t', 4),
        U('U', 5),
        u('u', 5),
        V('V', 5),
        v('v', 5),
        W('W', 5),
        w('w', 5),
        X('X', 5),
        x('x', 5),
        Y('Y', 5),
        y('y', 5),
        Z('Z', 5),
        z('z', 5),
        NUM_1('1', 5),
        NUM_2('2', 5),
        NUM_3('3', 5),
        NUM_4('4', 5),
        NUM_5('5', 5),
        NUM_6('6', 5),
        NUM_7('7', 5),
        NUM_8('8', 5),
        NUM_9('9', 5),
        NUM_0('0', 5),
        EXCLAMATION_POINT('!', 1),
        AT_SYMBOL('@', 6),
        NUM_SIGN('#', 5),
        DOLLAR_SIGN('$', 5),
        PERCENT('%', 5),
        UP_ARROW('^', 5),
        AMPERSAND('&', 5),
        ASTERISK('*', 5),
        LEFT_PARENTHESIS('(', 4),
        RIGHT_PARENTHESIS(')', 4),
        MINUS('-', 5),
        UNDERSCORE('_', 5),
        PLUS_SIGN('+', 5),
        EQUALS_SIGN('=', 5),
        LEFT_BRACKET('[', 3),
        RIGHT_BRACKET(']', 3),
        LEFT_BRACE('{', 4),
        RIGHT_BRACE('}', 4),
        COLON(':', 1),
        SEMI_COLON(';', 1),
        DOUBLE_QUOTE('"', 3),
        SINGLE_QUOTE('\'', 1),
        LEFT_ARROW('<', 4),
        RIGHT_ARROW('>', 4),
        QUESTION_MARK('?', 5),
        SLASH('/', 5),
        BACK_SLASH('\\', 5),
        LINE('|', 1),
        TILDE('~', 5),
        TICK('`', 2),
        PERIOD('.', 1),
        COMMA(',', 1),
        SPACE(' ', 3),
        DEFAULT('a', 5);

        private final char character;
        private final int length;

        DefaultFontInfo(char character, int length) {
            this.character = character;
            this.length = length;
        }

        public char getCharacter() {
            return this.character;
        }

        public int getLength() {
            return this.length;
        }

        public int getBoldLength() {
            if (this == DefaultFontInfo.SPACE) return this.getLength();
            return this.length + 1;
        }

        public static DefaultFontInfo getDefaultFontInfo(char c) {
            for (DefaultFontInfo dFI : DefaultFontInfo.values()) {
                if (dFI.getCharacter() == c) return dFI;
            }
            return DefaultFontInfo.DEFAULT;
        }
    }
}