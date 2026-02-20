package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.impl.UpdateEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {
    private final Setting<Float> range = register(new Setting<>("Range", 4.5f, 3.0f, 6.0f));
    private final Setting<Integer> delay = register(new Setting<>("Delay", 300, 0, 1000));
    private final Setting<Boolean> rotate = register(new Setting<>("Rotate", true));
    private final Setting<Boolean> rayTrace = register(new Setting<>("RayTrace", true));
    private final Setting<Boolean> players = register(new Setting<>("Players", true));
    private final Setting<Boolean> mobs = register(new Setting<>("Mobs", false));
    private final Setting<Boolean> animals = register(new Setting<>("Animals", false));
    private final Setting<Boolean> onlySword = register(new Setting<>("OnlySword", true));

    private Entity target = null;
    private long lastAttackTime = 0;
    private int attackTick = 0;

    public KillAura() {
        super("KillAura", "Automatically attacks entities", Category.COMBAT);
    }

    @Subscribe
    private void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        // Check weapon condition
        if (onlySword.getValue() && !(mc.player.getMainHandItem().getItem() instanceof SwordItem)) {
            target = null;
            return;
        }

        // Find target
        target = findTarget();
        if (target == null) return;

        // Attack timing
        attackTick++;
        if (attackTick >= delay.getValue() / 50) {
            attack(target);
            attackTick = 0;
        }

        // Rotate to target
        if (rotate.getValue() && target != null) {
            rotateToTarget(target);
        }
    }

    private Entity findTarget() {
        AABB box = mc.player.getBoundingBox().inflate(range.getValue());
        List<Entity> entities = mc.level.getEntities(mc.player, box, this::isValidTarget);

        if (entities.isEmpty()) return null;

        return entities.stream()
                .min(Comparator.comparingDouble(e -> mc.player.distanceTo(e)))
                .orElse(null);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == mc.player) return false;
        if (!entity.isAlive()) return false;

        // Player check
        if (entity instanceof Player player) {
            if (!players.getValue()) return false;
            if (player.isCreative() || player.isSpectator()) return false;
            return true;
        }

        // Living entity check
        if (entity instanceof LivingEntity living) {
            // Hostile mob check
            if (mobs.getValue() && living.getType().getCategory().isHostile()) return true;
            // Animal check
            if (animals.getValue() && living.getType().getCategory().isPeaceful()) return true;
        }

        return false;
    }

    private void attack(Entity target) {
        if (!canAttack(target)) return;

        // Send attack packet
        mc.player.connection.send(ServerboundInteractPacket.createAttackPacket(target, mc.player.isShiftKeyDown()));

        // Swing arm
        mc.player.connection.send(new ServerboundSwingPacket(mc.player.getUsedItemHand()));
        mc.player.swing(mc.player.getUsedItemHand());

        // Reset sprint for potential crit
        mc.player.setSprinting(false);
    }

    private boolean canAttack(Entity target) {
        if (mc.player.distanceTo(target) > range.getValue()) return false;

        if (rayTrace.getValue()) {
            Vec3 eyes = mc.player.getEyePosition();
            Vec3 targetVec = target.getBoundingBox().getCenter();
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                    eyes,
                    targetVec,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    mc.player
            ));
            if (hit != null && hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;
        }

        return true;
    }

    private void rotateToTarget(Entity target) {
        Vec3 targetVec = target.getBoundingBox().getCenter();
        Vec3 eyes = mc.player.getEyePosition();

        double diffX = targetVec.x - eyes.x;
        double diffY = targetVec.y - eyes.y;
        double diffZ = targetVec.z - eyes.z;

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }

    @Override
    public String getDisplayInfo() {
        return target != null ? target.getName().getString() : null;
    }
                                                        }
