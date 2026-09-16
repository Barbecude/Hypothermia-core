package com.simibubi.create.api.contraption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stub implementation of Create's BlockMovementChecks API.
 * 
 * When Create is not installed, Cold Sweat's CompatManager$ModEvents references
 * BlockMovementChecks$CheckResult in a lambda method signature, causing JVM
 * reflection (Class.getDeclaredMethods) to throw NoClassDefFoundError during FML
 * @EventBusSubscriber scanning.
 * 
 * Providing this stub satisfies JVM method descriptor resolution so Cold Sweat
 * can load safely without Create.
 */
public class BlockMovementChecks {

    public static void registerAttachedCheck(AttachedCheck check) {
        // No-op stub when Create is not installed
    }

    @FunctionalInterface
    public interface AttachedCheck {
        CheckResult isBlockAttachedTowards(BlockState state, Level level, BlockPos pos, Direction direction);
    }

    public static class CheckResult {
        public static final CheckResult PASS = new CheckResult();
        public static final CheckResult SUCCESS = new CheckResult();
        public static final CheckResult FAIL = new CheckResult();

        public static CheckResult of(boolean b) {
            return b ? SUCCESS : FAIL;
        }
    }
}
