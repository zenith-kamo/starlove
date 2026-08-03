package com.zenith.starlove.client;

import com.mojang.logging.LogUtils;
import com.zenith.starlove.Starlove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Starlove.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntityResetManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityResetManager INSTANCE = new EntityResetManager();

    public static EntityResetManager getInstance() {
        return INSTANCE;
    }

    private enum State {
        IDLE,
        PENDING_DISCONNECT,
        WAITING_FOR_SERVER_STOP,
        WAITING_FOR_FILE_UNLOCK,
        DELETING_ENTITIES,
        RELOADING_WORLD
    }

    private static State currentState = State.IDLE;
    private static String targetLevelId = null;
    private static int waitTicks = 0;

    public boolean startResetSequence() {
        Minecraft mc = Minecraft.getInstance();

        if (currentState != State.IDLE) {
            LOGGER.warn("[EntityReset] Reset processing is already in progress.");
            return false;
        }

        if (!mc.isLocalServer() || mc.getSingleplayerServer() == null) {
            LOGGER.warn("[EntityReset] The single-player integrated server is not running.");
            return false;
        }

        targetLevelId = mc.getSingleplayerServer().getWorldData().getLevelName();
        LOGGER.info("[EntityReset] Retrieving target world folder name: {}", targetLevelId);

        currentState = State.PENDING_DISCONNECT;
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || currentState == State.IDLE) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        switch (currentState) {
            case PENDING_DISCONNECT:
                LOGGER.info("[EntityReset] Starting world disconnection process...");
                executeDisconnect(mc);
                currentState = State.WAITING_FOR_SERVER_STOP;
                waitTicks = 0;
                break;

            case WAITING_FOR_SERVER_STOP:
                waitTicks++;

                if (waitTicks % 10 == 0) {
                    LOGGER.info("[EntityReset] Waiting for the integrated server to stop... ({}/300 ticks)", waitTicks);
                }

                if (mc.getSingleplayerServer() == null || mc.getSingleplayerServer().isStopped()) {
                    LOGGER.info("[EntityReset] Confirmed complete stop of the integrated server. Waiting for file unlock...");

                    System.gc();

                    currentState = State.WAITING_FOR_FILE_UNLOCK;
                    waitTicks = 0;
                } else if (waitTicks > 300) {
                    LOGGER.error("[EntityReset] Waiting for the integrated server to stop has timed out. Aborting process.");
                    resetState();
                }
                break;

            case WAITING_FOR_FILE_UNLOCK:
                waitTicks++;
                if (waitTicks >= 10) {
                    currentState = State.DELETING_ENTITIES;
                }
                break;

            case DELETING_ENTITIES:
                LOGGER.info("[EntityReset] Moving to the entities folder deletion sequence...");
                boolean success = deleteEntitiesDirectoryWithRetry(targetLevelId, 3);
                if (success) {
                    LOGGER.info("[EntityReset] Successfully deleted entity data. Reloading the world.");
                    currentState = State.RELOADING_WORLD;
                    waitTicks = 0;
                } else {
                    LOGGER.error("[EntityReset] Failed to delete entity data. Aborting automatic reload.");
                    resetState();
                }
                break;

            case RELOADING_WORLD:
                waitTicks++;
                if (waitTicks >= 20) {
                    loadWorld(targetLevelId);
                    resetState();
                }
                break;

            default:
                break;
        }
    }

    private static void executeDisconnect(Minecraft mc) {
        if (mc.level != null) {
            LOGGER.info("[EntityReset] Disconnecting from the server...");
            mc.level.disconnect();
            mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        }
    }

    private static boolean deleteEntitiesDirectoryWithRetry(String levelId, int maxRetries) {
        for (int i = 1; i <= maxRetries; i++) {
            if (deleteEntitiesDirectory(levelId)) {
                return true;
            }
            LOGGER.warn("[EntityReset] Deletion attempt {} failed. Retrying after a short pause...", i);
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
            System.gc();
        }
        return false;
    }

    private static boolean deleteEntitiesDirectory(String levelId) {
        Minecraft mc = Minecraft.getInstance();
        LevelStorageSource levelSource = mc.getLevelSource();

        try {
            Path levelPath = levelSource.getBaseDir().resolve(levelId);
            Path entitiesPath = levelPath.resolve("entities");

            if (!Files.exists(entitiesPath)) {
                LOGGER.info("[EntityReset] Entities directory does not exist (deletion skipped): {}", entitiesPath.toAbsolutePath());
                return true;
            }

            LOGGER.info("[EntityReset] Starting deletion of the entities directory: {}", entitiesPath.toAbsolutePath());

            try (Stream<Path> pathStream = Files.walk(entitiesPath)) {
                pathStream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                LOGGER.warn("[EntityReset] Could not delete file/folder (locked?): {} -> {}", path.toAbsolutePath(), e.getMessage());
                            }
                        });
            }

            if (!Files.exists(entitiesPath)) {
                LOGGER.info("[EntityReset] Completed the complete deletion of the entities directory.");
                return true;
            } else {
                LOGGER.error("[EntityReset] Part of the entities directory remains.");
                return false;
            }

        } catch (IOException e) {
            LOGGER.error("[EntityReset] An exception occurred while deleting the entities directory: {}", e.getMessage(), e);
            return false;
        }
    }

    private static void loadWorld(String levelId) {
        Minecraft mc = Minecraft.getInstance();
        LOGGER.info("[EntityReset] Executing automatic world reload: {}", levelId);

        try {
            if (mc.getLevelSource().levelExists(levelId)) {
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(), levelId);
            } else {
                LOGGER.error("[EntityReset] Target world not found: {}", levelId);
            }
        } catch (Exception e) {
            LOGGER.error("[EntityReset] An error occurred while reloading the world: {}", e.getMessage(), e);
        }
    }

    private static void resetState() {
        currentState = State.IDLE;
        targetLevelId = null;
        waitTicks = 0;
    }
}