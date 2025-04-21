package com.example.whitelistmanager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class WhitelistManager extends JavaPlugin {
    private TelegramBot telegramBot;

    @Override
    public void onEnable() {
        // Сохраняем конфигурацию по умолчанию
        saveDefaultConfig();
        
        // Получаем настройки из конфига
        FileConfiguration config = getConfig();
        String botToken = config.getString("telegram.bot-token");
        String adminChatId = config.getString("telegram.admin-chat-id");

        if (botToken == null || adminChatId == null) {
            getLogger().severe("Не указаны настройки Telegram бота в config.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // Регистрируем бота
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBot = new TelegramBot(botToken, adminChatId, this);
            botsApi.registerBot(telegramBot);
            getLogger().info("Telegram бот успешно запущен!");
        } catch (Exception e) {
            getLogger().severe("Ошибка при запуске Telegram бота: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("WhitelistManager has been disabled!");
    }
} 