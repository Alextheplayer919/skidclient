package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import java.util.Comparator;
import java.util.Optional;

public class AutoAttack extends Module {
    // Settings
    private final Setting<Float> range = register(new Setting<>("Range", 3.0f, 0.1f, 10.0f));
    private final Setting<Integer> delay = register(new Setting<>("Delay", 500, 0, 1000));
    private final Setting<Boolean> playersOnly = register(new Setting<>("PlayersOnly", true));
    private final Setting<Boolean> rotate = register(new Setting<>("Rotate", true));
    private final Setting<Boolean> silentRotate = register(new Setting<>("SilentRotate", false));
    
    private final Timer attackTimer = new Timer();
    private Entity target;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public AutoAttack() {
        super("AutoAttack", "Automatically attacks players", Module.Category.COMBAT, true, false, false);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        
        // Find closest valid target
        target = findBestTarget();
        
        if (target != null && attackTimer.passedMs(delay.getValue())) {
            if (rotate.getValue()) {
                faceTarget(target, silentRotate.getValue());
            }
            
            attack(target);
            attackTimer.reset();
        }
    }

    private Entity findBestTarget() {
        return mc.world.getEntities().stream()
            .filter(this::isValidEntity)
            .min(Comparator.comparingDouble(e -> mc.player.distanceTo(e)))
            .orElse(null);
    }

    private boolean isValidEntity(Entity entity) {
        if (entity == mc.player) return false;
        if (mc.player.distanceTo(entity) > range.getValue()) return false;
        if (playersOnly.getValue() && !(entity instanceof PlayerEntity)) return false;
        return true;
    }

    private void faceTarget(Entity target, boolean silent) {
        // Calculate rotations to look at target
        double diffX = target.getX() - mc.player.getX();
        double diffY = (target.getY() + target.getEyeHeight(target.getPose())) - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double diffZ = target.getZ() - mc.player.getZ();
        
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        
        float yaw = (float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float)-Math.toDegrees(Math.atan2(diffY, dist));
        
        if (silent) {
            // Send silent rotation packets
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        } else {
            // Normal rotation
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }
    }

    private void attack(Entity target) {
        if (mc.interactionManager == null || mc.player == null) return;
        
        // Simulate attack
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(mc.player.getActiveHand());
    }

    @Override
    public String getDisplayInfo() {
        return target != null ? String.format("%.1f", mc.player.distanceTo(target)) : null;
    }
}
