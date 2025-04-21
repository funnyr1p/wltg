package com.example.whitelistmanager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TelegramBot extends TelegramLongPollingBot {
    private final String botToken;
    private final WhitelistManager plugin;
    private final Map<Long, String> pendingRequests; // chatId -> playerName
    private final Map<String, Long> playerChatIds; // playerName -> chatId
    private final String adminChatId;

    public TelegramBot(String botToken, String adminChatId, WhitelistManager plugin) {
        this.botToken = botToken;
        this.adminChatId = adminChatId;
        this.plugin = plugin;
        this.pendingRequests = new HashMap<>();
        this.playerChatIds = new HashMap<>();
    }

    @Override
    public String getBotUsername() {
        return "WhitelistManagerBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            // Проверяем, что команда пришла от администратора
            if (chatId != Long.parseLong(adminChatId)) {
                if (messageText.startsWith("/start")) {
                    sendMessage(chatId, "Привет! Я бот для управления вайтлистом сервера Minecraft.\n" +
                            "Используйте /request <ник> для отправки запроса на добавление в вайтлист.");
                } else if (messageText.startsWith("/request")) {
                    handleRequest(chatId, messageText);
                }
                return;
            }

            // Команды для администратора
            if (messageText.startsWith("/online")) {
                handleOnlineCommand(chatId);
            } else if (messageText.startsWith("/list")) {
                handleListCommand(chatId);
            } else if (messageText.startsWith("/del ")) {
                handleDelCommand(chatId, messageText);
            } else if (messageText.startsWith("/add ")) {
                handleAddCommand(chatId, messageText);
            } else if (messageText.startsWith("/start")) {
                sendMessage(chatId, "Привет! Я бот для управления вайтлистом сервера Minecraft.\n" +
                        "Доступные команды:\n" +
                        "/online - список онлайн игроков\n" +
                        "/list - список всех игроков в вайтлисте\n" +
                        "/add <ник> - добавить игрока в вайтлист\n" +
                        "/del <ник> - удалить игрока из вайтлиста");
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String playerName = pendingRequests.get(chatId);

            // Проверяем, что запрос пришел от администратора
            if (chatId == Long.parseLong(adminChatId) && playerName != null) {
                if (callbackData.equals("accept")) {
                    handleAccept(playerName, chatId);
                } else if (callbackData.equals("deny")) {
                    handleDeny(playerName, chatId);
                }
                pendingRequests.remove(chatId);
            }
        }
    }

    private void handleOnlineCommand(long chatId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<String> onlinePlayers = Bukkit.getOnlinePlayers().stream()
                    .map(player -> "• " + player.getName())
                    .collect(Collectors.toList());

            if (onlinePlayers.isEmpty()) {
                sendMessage(chatId, "🔄 На сервере нет онлайн игроков.");
            } else {
                String message = "🔄 Онлайн игроки (" + onlinePlayers.size() + "):\n" +
                        String.join("\n", onlinePlayers);
                sendMessage(chatId, message);
            }
        });
    }

    private void handleListCommand(long chatId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<String> whitelistedPlayers = Bukkit.getWhitelistedPlayers().stream()
                    .map(player -> "• " + player.getName())
                    .collect(Collectors.toList());

            if (whitelistedPlayers.isEmpty()) {
                sendMessage(chatId, "📋 Вайтлист пуст.");
            } else {
                String message = "📋 Игроки в вайтлисте (" + whitelistedPlayers.size() + "):\n" +
                        String.join("\n", whitelistedPlayers);
                sendMessage(chatId, message);
            }
        });
    }

    private void handleDelCommand(long chatId, String messageText) {
        String[] parts = messageText.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, "❌ Используйте: /del <ник>");
            return;
        }

        String playerName = parts[1];
        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            if (player.isWhitelisted()) {
                player.setWhitelisted(false);
                sendMessage(chatId, "✅ Игрок " + playerName + " удален из вайтлиста.");
            } else {
                sendMessage(chatId, "❌ Игрок " + playerName + " не найден в вайтлисте.");
            }
        });
    }

    private void handleAddCommand(long chatId, String messageText) {
        String[] parts = messageText.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, "❌ Используйте: /add <ник>");
            return;
        }

        String playerName = parts[1];
        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            if (!player.isWhitelisted()) {
                Bukkit.getServer().setWhitelist(true);
                player.setWhitelisted(true);
                sendMessage(chatId, "✅ Игрок " + playerName + " добавлен в вайтлист.");
            } else {
                sendMessage(chatId, "❌ Игрок " + playerName + " уже в вайтлисте.");
            }
        });
    }

    private void handleRequest(long chatId, String messageText) {
        String[] parts = messageText.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(chatId, "❌ Используйте: /request <ник>");
            return;
        }

        String playerName = parts[1];
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        
        pendingRequests.put(Long.parseLong(adminChatId), playerName);
        playerChatIds.put(playerName, chatId);
        sendRequestToAdmins(playerName);
        sendMessage(chatId, "✅ Ваш запрос отправлен администраторам. Ожидайте ответа.");
    }

    private void sendRequestToAdmins(String playerName) {
        SendMessage message = new SendMessage();
        message.setChatId(adminChatId);
        message.setText("🔄 Новый запрос на добавление в вайтлист:\nИгрок: " + playerName);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();

        InlineKeyboardButton acceptButton = new InlineKeyboardButton();
        acceptButton.setText("✅ Принять");
        acceptButton.setCallbackData("accept");

        InlineKeyboardButton denyButton = new InlineKeyboardButton();
        denyButton.setText("❌ Отклонить");
        denyButton.setCallbackData("deny");

        rowInline.add(acceptButton);
        rowInline.add(denyButton);
        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleAccept(String playerName, long chatId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            Bukkit.getServer().setWhitelist(true);
            player.setWhitelisted(true);
            
            // Отправляем сообщение администратору
            sendMessage(chatId, "✅ Игрок " + playerName + " добавлен в вайтлист!");
            
            // Отправляем сообщение игроку в Telegram
            Long playerChatId = playerChatIds.get(playerName);
            if (playerChatId != null) {
                sendMessage(playerChatId, "✅ Ваш запрос на добавление в вайтлист был принят!\nТеперь вы можете зайти на сервер.");
                playerChatIds.remove(playerName);
            }
        });
    }

    private void handleDeny(String playerName, long chatId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Отправляем сообщение администратору
            sendMessage(chatId, "❌ Запрос игрока " + playerName + " отклонен.");
            
            // Отправляем сообщение игроку в Telegram
            Long playerChatId = playerChatIds.get(playerName);
            if (playerChatId != null) {
                sendMessage(playerChatId, "❌ Ваш запрос на добавление в вайтлист был отклонен.\nПожалуйста, свяжитесь с администрацией для уточнения причин.");
                playerChatIds.remove(playerName);
            }
        });
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
} 