package com.example.nearaction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class NearActionClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("near-action.json");

    // Fixed scan interval: 200 client ticks = approximately 10 seconds. Not a user setting.
    private static final int SCAN_INTERVAL_TICKS = 200;
    private static final int RESPONSE_TIMEOUT_TICKS = 80;

    private static Config config;
    private static KeyMapping toggleKey;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("near_action", "general")
    );
    private static boolean monitoring = false;
    private static boolean waitingForNearResponse = false;
    private static boolean playerWasNearby = false;
    private static int scanTicks = 0;
    private static int responseTicks = 0;

    @Override
    public void onInitializeClient() {
        config = loadConfig();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.near_action.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(NearActionClient::onClientTick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleIncomingText(message.getString()));

        System.out.println("[Near Action] Loaded. Press J to start/stop monitoring.");
    }

    private static void onClientTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            monitoring = !monitoring;
            waitingForNearResponse = false;
            playerWasNearby = false;
            scanTicks = 0;
            responseTicks = 0;
            notifyPlayer(client, monitoring
                    ? "Near Action enabled. Press J to stop."
                    : "Near Action disabled.");
        }

        if (!monitoring || client.player == null || client.getConnection() == null) {
            return;
        }

        if (waitingForNearResponse) {
            responseTicks++;
            if (responseTicks > RESPONSE_TIMEOUT_TICKS) {
                waitingForNearResponse = false;
            }
            return;
        }

        scanTicks++;
        if (scanTicks >= SCAN_INTERVAL_TICKS) {
            scanTicks = 0;
            ClientPacketListener connection = client.getConnection();
            if (connection != null) {
                connection.sendCommand(stripLeadingSlash(config.nearCommand));
                waitingForNearResponse = true;
                responseTicks = 0;
            }
        }
    }

    private static void handleIncomingText(String text) {
        if (!monitoring || !waitingForNearResponse || text == null || text.isBlank()) {
            return;
        }

        if (matches(config.negativeResponseRegex, text)) {
            playerWasNearby = false;
            waitingForNearResponse = false;
            return;
        }

        if (matches(config.positiveResponseRegex, text)) {
            waitingForNearResponse = false;
            if (!playerWasNearby) {
                playerWasNearby = true;
                runConfiguredCommand();
            }
        }
    }

    private static boolean matches(String regex, String text) {
        try {
            return regex != null && !regex.isBlank() && Pattern.compile(regex).matcher(text).find();
        } catch (PatternSyntaxException ex) {
            System.err.println("[Near Action] Invalid regex in config: " + regex);
            return false;
        }
    }

    private static void runConfiguredCommand() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) {
            return;
        }

        String command = stripLeadingSlash(config.commandToRun);
        if (command.isBlank()) {
            notifyPlayer(client, "Nearby-player response detected, but commandToRun is empty in config/near-action.json.");
            return;
        }

        client.getConnection().sendCommand(command);
        notifyPlayer(client, "Nearby-player response detected; configured command sent.");
    }

    private static String stripLeadingSlash(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1).trim();
        }
        return result;
    }

    private static void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[Near Action] " + message), false);
        }
    }

    private static Config loadConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                Config loaded = GSON.fromJson(Files.readString(CONFIG_PATH), Config.class);
                if (loaded != null) {
                    if (loaded.nearCommand == null) loaded.nearCommand = "near";
                    if (loaded.commandToRun == null) loaded.commandToRun = "";
                    if (loaded.positiveResponseRegex == null) loaded.positiveResponseRegex = Config.DEFAULT_POSITIVE_REGEX;
                    if (loaded.negativeResponseRegex == null) loaded.negativeResponseRegex = Config.DEFAULT_NEGATIVE_REGEX;
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException ex) {
            System.err.println("[Near Action] Could not read config; writing defaults. " + ex.getMessage());
        }

        Config defaults = new Config();
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(defaults));
        } catch (IOException ex) {
            System.err.println("[Near Action] Could not write config: " + ex.getMessage());
        }
        return defaults;
    }

    private static final class Config {
        private static final String DEFAULT_POSITIVE_REGEX = "(?i).*(?:nearby\\s+players?\\s*[:=-]\\s*\\S+|players?\\s+(?:near|within)\\b.*|found\\s+\\d+\\s+players?\\b.*|рядом\\s*[:=-]\\s*\\S+|игроки\\s+(?:рядом|поблизости).*).*";
        private static final String DEFAULT_NEGATIVE_REGEX = "(?i).*(?:no players|nobody|none nearby|нет игроков|никого рядом|никого поблизости|не найдено|не обнаружено).*";

        private String nearCommand = "near";
        private String commandToRun = "";
        private String positiveResponseRegex = DEFAULT_POSITIVE_REGEX;
        private String negativeResponseRegex = DEFAULT_NEGATIVE_REGEX;
    }
}
