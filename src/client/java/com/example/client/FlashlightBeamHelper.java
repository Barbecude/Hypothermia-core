package com.example.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Helper to emit IRL Flashlight beams for entities during Flashback replay and video export.
 * Uses reflective access to IRL Flashlight and IRL-core to avoid compile-time mapping conflicts.
 */
public final class FlashlightBeamHelper {
    private static final long BASE_LIGHT_ID = -1034401435397128191L;

    private static boolean initialized = false;
    private static boolean available = false;

    private static Method isOnMethod;
    private static Object configInstance;
    private static Field outerAngleField;
    private static Field innerAngleField;
    private static Field colorRField;
    private static Field colorGField;
    private static Field colorBField;
    private static Field intensityField;
    private static Field distanceField;
    private static Field beamStrengthField;
    private static Field castShadowsField;
    private static Field cookieField;
    private static Field cookieScaleField;

    private static Method cookieLayerMethod;
    private static Method mathConeMethod;
    private static Method coneCosOuterMethod;
    private static Method coneCosInnerMethod;
    private static Method registerSpotMethod;

    private static Method flashbackIsInReplayMethod;

    private static void init() {
        if (initialized) return;
        initialized = true;

        try {
            if (!FabricLoader.getInstance().isModLoaded("irlights_flashlight")) {
                return;
            }

            Class<?> stateClass = Class.forName("com.irlights.flashlight.FlashlightItemState");
            for (Method m : stateClass.getMethods()) {
                if (m.getName().equals("isOn") && m.getParameterCount() == 1) {
                    isOnMethod = m;
                    break;
                }
            }

            Class<?> cfgClass = Class.forName("com.irlights.flashlight.client.FlashlightConfig");
            configInstance = cfgClass.getField("INSTANCE").get(null);
            outerAngleField = cfgClass.getField("outerAngleDeg");
            innerAngleField = cfgClass.getField("innerAngleDeg");
            colorRField = cfgClass.getField("colorR");
            colorGField = cfgClass.getField("colorG");
            colorBField = cfgClass.getField("colorB");
            intensityField = cfgClass.getField("intensity");
            distanceField = cfgClass.getField("distance");
            beamStrengthField = cfgClass.getField("beamStrength");
            castShadowsField = cfgClass.getField("castShadows");
            cookieField = cfgClass.getField("cookie");
            cookieScaleField = cfgClass.getField("cookieScale");

            Class<?> cookieClass = Class.forName("com.irlights.flashlight.client.FlashlightCookie");
            cookieLayerMethod = cookieClass.getMethod("layer");

            Class<?> mathClass = Class.forName("org.qualet.irl.light.LightMath");
            mathConeMethod = mathClass.getMethod("cone", float.class, float.class);

            Class<?> registryClass = Class.forName("org.qualet.irl.light.LightRegistry");
            for (Method m : registryClass.getMethods()) {
                if (m.getName().equals("registerSpot") && m.getParameterCount() == 25) {
                    registerSpotMethod = m;
                    break;
                }
            }

            if (FabricLoader.getInstance().isModLoaded("flashback")) {
                try {
                    Class<?> fbClass = Class.forName("com.moulberry.flashback.Flashback");
                    flashbackIsInReplayMethod = fbClass.getMethod("isInReplay");
                } catch (Throwable ignored) {}
            }

            available = (isOnMethod != null && configInstance != null && registerSpotMethod != null && mathConeMethod != null);
            if (available) {
                System.out.println("[HypothermiaCore] FlashlightBeamHelper initialized successfully for Flashback replay support!");
            }
        } catch (Throwable t) {
            System.err.println("[HypothermiaCore] Failed to initialize FlashlightBeamHelper: " + t.getMessage());
            t.printStackTrace();
            available = false;
        }
    }

    public static void emitBeams(float delta) {
        init();
        if (!available) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean inReplay = false;
        if (flashbackIsInReplayMethod != null) {
            try {
                inReplay = (boolean) flashbackIsInReplayMethod.invoke(null);
            } catch (Throwable ignored) {}
        }

        for (Player player : mc.level.players()) {
            // In normal gameplay, mc.player is already handled by FlashlightBeam.emit
            if (player == mc.player && !inReplay) {
                continue;
            }

            ItemStack held = getHeldFlashlight(player);
            if (held == null || !isFlashlightOn(held)) {
                continue;
            }

            try {
                Vec3 eyePos = player.getEyePosition(delta);
                Vec3 rotVec = player.getViewVector(delta);

                double muzzleOffset = 0.55D;
                Vec3 targetMuzzle = eyePos.add(rotVec.scale(muzzleOffset));
                BlockHitResult hit = mc.level.clip(new ClipContext(
                        eyePos, targetMuzzle, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player
                ));
                if (hit != null && hit.getType() != HitResult.Type.MISS) {
                    muzzleOffset = Math.max(0.05D, eyePos.distanceTo(hit.getLocation()) - 0.05D);
                }
                Vec3 lightPos = eyePos.add(rotVec.scale(muzzleOffset));

                float outer = outerAngleField.getFloat(configInstance);
                float inner = innerAngleField.getFloat(configInstance);
                Object cone = mathConeMethod.invoke(null, outer, inner);
                if (coneCosOuterMethod == null) {
                    coneCosOuterMethod = cone.getClass().getMethod("cosOuter");
                    coneCosInnerMethod = cone.getClass().getMethod("cosInner");
                }
                float cosOuter = ((Number) coneCosOuterMethod.invoke(cone)).floatValue();
                float cosInner = ((Number) coneCosInnerMethod.invoke(cone)).floatValue();

                boolean cookie = cookieField.getBoolean(configInstance);
                float cookieLayer = -1.0F;
                if (cookie && cookieLayerMethod != null) {
                    cookieLayer = (float) ((Number) cookieLayerMethod.invoke(null)).intValue();
                }

                float r = colorRField.getFloat(configInstance);
                float g = colorGField.getFloat(configInstance);
                float b = colorBField.getFloat(configInstance);
                float intensity = intensityField.getFloat(configInstance);
                float distance = distanceField.getFloat(configInstance);
                float beamStrength = beamStrengthField.getFloat(configInstance);
                boolean castShadows = castShadowsField.getBoolean(configInstance);
                float cookieScale = cookieScaleField.getFloat(configInstance);

                long lightId = BASE_LIGHT_ID ^ (player.getUUID().getMostSignificantBits() != 0
                        ? player.getUUID().getMostSignificantBits()
                        : (long) player.getId());

                registerSpotMethod.invoke(null,
                        lightPos.x, lightPos.y, lightPos.z,
                        (float) rotVec.x, (float) rotVec.y, (float) rotVec.z,
                        r, g, b,
                        intensity, distance,
                        cosOuter, cosInner,
                        false, false,
                        0.5F, 0.04F,
                        beamStrength, 0.0F,
                        castShadows,
                        cookieLayer,
                        0.0F, cookieScale, 0.0F,
                        lightId
                );
            } catch (Throwable t) {
                // Ignore per-player emission errors to avoid crashing the render loop
            }
        }
    }

    private static ItemStack getHeldFlashlight(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isFlashlightItem(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isFlashlightItem(off)) return off;
        return null;
    }

    private static boolean isFlashlightItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "irlights_flashlight".equals(id.getNamespace()) && "flashlight".equals(id.getPath());
    }

    private static boolean isFlashlightOn(ItemStack stack) {
        if (isOnMethod == null) return false;
        try {
            return (boolean) isOnMethod.invoke(null, stack);
        } catch (Throwable t) {
            return false;
        }
    }
}
