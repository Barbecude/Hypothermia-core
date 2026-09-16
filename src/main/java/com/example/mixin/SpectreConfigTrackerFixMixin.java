package com.example.mixin;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.illusivesoulworks.spectrelib.config.SpectreConfig;
import com.illusivesoulworks.spectrelib.config.SpectreConfigSpec;
import com.illusivesoulworks.spectrelib.config.SpectreConfigTracker;
import com.illusivesoulworks.spectrelib.platform.Services;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Mixin(value = SpectreConfigTracker.class, remap = false)
public abstract class SpectreConfigTrackerFixMixin {
    @Shadow @Final
    private ConcurrentHashMap<String, SpectreConfig> files;

    @Shadow
    abstract Function<SpectreConfig, CommentedFileConfig> read(Path path);

    @Inject(method = "loadServerConfigs", at = @At("TAIL"))
    private void hypothermia$ensureServerConfigsLoaded(MinecraftServer server, CallbackInfo ci) {
        try {
            Path serverConfigPath = Services.CONFIG.getServerConfigPath(server);
            for (SpectreConfig config : this.files.values()) {
                if (config.getType() == SpectreConfig.Type.SERVER) {
                    SpectreConfigSpec spec = config.getSpec();
                    if (spec != null && !spec.isLoaded()) {
                        CommentedFileConfig fileConfig = this.read(serverConfigPath).apply(config);
                        config.setConfigData(SpectreConfig.InstanceType.SERVER, fileConfig, false);
                        config.fireLoad(false);
                        config.save(SpectreConfig.InstanceType.SERVER);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
