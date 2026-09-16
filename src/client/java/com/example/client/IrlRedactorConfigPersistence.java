package com.example.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles automatic persistence of IRL Redactor's settings (LightConfig).
 * Saves settings to config/irl-redactor/settings.json on world change,
 * editor close, and game exit; and loads them on startup and world join.
 */
public final class IrlRedactorConfigPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_MOD_ID = "irl-redactor";
    private static final String CONFIG_CLASS_NAME = "org.qualet.irlredactor.light.LightConfig";
    private static final String SCREEN_CLASS_NAME = "org.qualet.irlredactor.editor.LightEditorScreen";
    private static final String CLIENT_CLASS_NAME = "org.qualet.irlredactor.client.IRLRedactorClient";

    private static long lastSaveTime = 0;

    public static void init() {
        // Register shutdown hook so settings and world lights are saved even on abrupt exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                saveAll();
            } catch (Throwable ignored) {}
        }, "Hypothermia-IRLRedactor-ShutdownSave"));

        // Initial load
        loadConfig();
    }

    public static void saveAll() {
        saveConfig();
        saveWorldLights();
    }

    public static void saveConfig() {
        try {
            if (!FabricLoader.getInstance().isModLoaded(CONFIG_MOD_ID)) {
                return;
            }

            Class<?> configClass = Class.forName(CONFIG_CLASS_NAME);
            JsonObject json = new JsonObject();

            for (Field field : configClass.getFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) && !Modifier.isFinal(mods)) {
                    Class<?> type = field.getType();
                    if (type == boolean.class) {
                        json.addProperty(field.getName(), field.getBoolean(null));
                    } else if (type == int.class) {
                        json.addProperty(field.getName(), field.getInt(null));
                    } else if (type == float.class) {
                        json.addProperty(field.getName(), field.getFloat(null));
                    }
                }
            }

            Path configPath = getConfigFilePath();
            Files.createDirectories(configPath.getParent());

            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(json, writer);
            }

            lastSaveTime = System.currentTimeMillis();
            System.out.println("[HypothermiaCore] Saved IRL Redactor settings to " + configPath);
        } catch (Throwable t) {
            System.err.println("[HypothermiaCore] Failed to save IRL Redactor settings: " + t.getMessage());
        }
    }

    public static void loadConfig() {
        try {
            if (!FabricLoader.getInstance().isModLoaded(CONFIG_MOD_ID)) {
                return;
            }

            Path configPath = getConfigFilePath();
            if (!Files.exists(configPath)) {
                return;
            }

            JsonObject json;
            try (Reader reader = Files.newBufferedReader(configPath)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            Class<?> configClass = Class.forName(CONFIG_CLASS_NAME);
            for (Field field : configClass.getFields()) {
                int mods = field.getModifiers();
                if (Modifier.isStatic(mods) && !Modifier.isFinal(mods) && json.has(field.getName())) {
                    Class<?> type = field.getType();
                    if (type == boolean.class) {
                        field.setBoolean(null, json.get(field.getName()).getAsBoolean());
                    } else if (type == int.class) {
                        field.setInt(null, json.get(field.getName()).getAsInt());
                    } else if (type == float.class) {
                        field.setFloat(null, json.get(field.getName()).getAsFloat());
                    }
                }
            }

            // Sync with active UI panel if already instantiated
            syncPanelUI(configClass);
            System.out.println("[HypothermiaCore] Loaded IRL Redactor settings from " + configPath);
        } catch (Throwable t) {
            System.err.println("[HypothermiaCore] Failed to load IRL Redactor settings: " + t.getMessage());
        }
    }

    public static void saveWorldLights() {
        try {
            if (!FabricLoader.getInstance().isModLoaded(CONFIG_MOD_ID)) {
                return;
            }
            Class<?> clientClass = Class.forName(CLIENT_CLASS_NAME);
            Method saveMethod = clientClass.getMethod("saveCurrentWorld");
            saveMethod.invoke(null);
        } catch (Throwable ignored) {}
    }

    private static void syncPanelUI(Class<?> configClass) {
        try {
            Class<?> screenClass = Class.forName(SCREEN_CLASS_NAME);
            Field panelField = screenClass.getDeclaredField("PANEL");
            panelField.setAccessible(true);
            Object panel = panelField.get(null);
            if (panel == null) return;

            // Call syncPresetMirrors if available
            try {
                Method syncMethod = panel.getClass().getDeclaredMethod("syncPresetMirrors");
                syncMethod.setAccessible(true);
                syncMethod.invoke(panel);
            } catch (Throwable ignored) {}

            // Sync all fields on panel that mirror LightConfig
            for (Field pField : panel.getClass().getDeclaredFields()) {
                pField.setAccessible(true);
                String name = pField.getName();
                Object val = pField.get(panel);
                if (val == null) continue;

                // Handle ImBoolean
                if (val.getClass().getName().contains("ImBoolean")) {
                    String cfgName = mapPanelFieldToConfig(name);
                    if (cfgName != null) {
                        try {
                            Field cField = configClass.getField(cfgName);
                            if (cField.getType() == boolean.class) {
                                Method setMethod = val.getClass().getMethod("set", boolean.class);
                                setMethod.invoke(val, cField.getBoolean(null));
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                // Handle float[]
                else if (val instanceof float[] arr && arr.length > 0) {
                    String cfgName = mapPanelFieldToConfig(name);
                    if (cfgName != null) {
                        try {
                            Field cField = configClass.getField(cfgName);
                            if (cField.getType() == float.class) {
                                arr[0] = cField.getFloat(null);
                            } else if (cField.getType() == int.class) {
                                arr[0] = (float) cField.getInt(null);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String mapPanelFieldToConfig(String panelFieldName) {
        if (!panelFieldName.startsWith("cfg")) return null;
        String stripped = panelFieldName.substring(3);
        if (stripped.isEmpty()) return null;
        // e.g. cfgHoldOnJoin -> holdBakeOnJoin
        if (stripped.equalsIgnoreCase("HoldOnJoin")) return "holdBakeOnJoin";
        if (stripped.equalsIgnoreCase("HoldBake")) return "holdBake";
        if (stripped.equalsIgnoreCase("Cache")) return "shadowCache";
        if (stripped.equalsIgnoreCase("Blocks")) return "shadowBlocks";
        if (stripped.equalsIgnoreCase("ShadowsLive")) return "shadowsLive";
        if (stripped.equalsIgnoreCase("ShadowSoftness")) return "shadowSoftness";
        if (stripped.equalsIgnoreCase("Guides")) return "showGuides";
        if (stripped.equalsIgnoreCase("Radius")) return "shadowBlockRadius";
        if (stripped.equalsIgnoreCase("AutoLights")) return "autoLights";
        if (stripped.equalsIgnoreCase("AutoCulling")) return "autoLightCulling";
        if (stripped.equalsIgnoreCase("AutoShadows")) return "autoLightShadows";
        if (stripped.equalsIgnoreCase("AutoIntensity")) return "autoLightIntensity";
        if (stripped.equalsIgnoreCase("AutoReach")) return "autoLightReach";
        if (stripped.equalsIgnoreCase("AutoRadius")) return "autoLightRadius";
        if (stripped.equalsIgnoreCase("AutoMax")) return "autoLightMax";
        if (stripped.equalsIgnoreCase("VlIntensity")) return "vlIntensity";
        if (stripped.equalsIgnoreCase("VlSteps")) return "vlSteps";
        if (stripped.equalsIgnoreCase("VlMaxDist")) return "vlMaxDist";
        if (stripped.equalsIgnoreCase("VlShadows")) return "vlShadows";
        if (stripped.equalsIgnoreCase("VlShadowStride")) return "vlShadowStride";
        if (stripped.equalsIgnoreCase("VlTipBoost")) return "vlTipBoost";
        if (stripped.equalsIgnoreCase("VlTipRadius")) return "vlTipRadius";
        if (stripped.equalsIgnoreCase("VlNoise")) return "vlNoise";
        if (stripped.equalsIgnoreCase("VlNoiseAmount")) return "vlNoiseAmount";
        if (stripped.equalsIgnoreCase("VlNoiseScale")) return "vlNoiseScale";
        if (stripped.equalsIgnoreCase("VlNoiseSpeed")) return "vlNoiseSpeed";
        if (stripped.equalsIgnoreCase("VlNoiseMorph")) return "vlNoiseMorph";
        if (stripped.equalsIgnoreCase("VlNoiseStride")) return "vlNoiseStride";
        if (stripped.equalsIgnoreCase("VlDitherTemporal")) return "vlDitherTemporal";
        if (stripped.equalsIgnoreCase("Outline")) return "outline";
        if (stripped.equalsIgnoreCase("OutlineStrength")) return "outlineStrength";
        if (stripped.equalsIgnoreCase("OutlinePixelSize")) return "outlinePixelSize";
        if (stripped.equalsIgnoreCase("OutlineFresnel")) return "outlineFresnelPower";
        if (stripped.equalsIgnoreCase("OutlineBack")) return "outlineBack";
        if (stripped.equalsIgnoreCase("OutlineFront")) return "outlineFront";
        if (stripped.equalsIgnoreCase("OutlineFrontStr")) return "outlineFrontStrength";
        if (stripped.equalsIgnoreCase("OutlineGlow")) return "outlineGlow";
        if (stripped.equalsIgnoreCase("OutlineGlowStr")) return "outlineGlowStrength";

        // Generic fallback: uncapitalize first letter
        return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
    }

    private static Path getConfigFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("irl-redactor").resolve("settings.json");
    }

    public static void tickPeriodicSave() {
        long now = System.currentTimeMillis();
        // Periodic check every 5 seconds when editor is active
        if (now - lastSaveTime > 5000) {
            saveConfig();
        }
    }
}
