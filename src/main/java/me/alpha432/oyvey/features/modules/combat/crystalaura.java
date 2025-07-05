package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.Timer;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

public class AutoCrystal extends Module {
    private final Timer placeTimer = new Timer();
    private final Timer attackTimer = new Timer();

    public Setting<Float> placeRange = register(new Setting<>("PlaceRange", 5.0f, 1.0f, 6.0f));
    public Setting<Float> attackRange = register(new Setting<>("AttackRange", 5.5f, 1.0f, 6.0f));
    public Setting<Integer> placeDelay = register(new Setting<>("PlaceDelay", 100, 0, 1000));
    public Setting<Integer> attackDelay = register(new Setting<>("AttackDelay", 100, 0, 1000));
    public Setting<Boolean> rotate = register(new Setting<>("Rotate", true));
    public Setting<Boolean> silentRotate = register(new Setting<>("SilentRotate", false));
    public Setting<Boolean> playersOnly = register(new Setting<>("PlayersOnly", true));

    public AutoCrystal() {
        super("AutoCrystal", "Automatically places and attacks crystals", Category.COMBAT, true, false, false);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        attackCrystals();
        placeCrystals();
    }

    private void attackCrystals() {
        if (!attackTimer.passedMs(attackDelay.getValue())) return;

        List<Entity> crystals = mc.world.getEntities().stream()
                .filter(e -> e instanceof EndCrystalEntity)
                .filter(e -> mc.player.distanceTo(e) <= attackRange.getValue())
                .toList();

        Entity targetCrystal = null;

        if (playersOnly.getValue()) {
            targetCrystal = crystals.stream()
                    .filter(crystal -> mc.world.getEntities().stream()
                            .filter(e -> e instanceof LivingEntity && e != mc.player)
                            .filter(LivingEntity::isAlive)
                            .anyMatch(player -> player.distanceTo(crystal) <= 4.0))
                    .min(Comparator.comparingDouble(mc.player::distanceTo))
                    .orElse(null);
        } else {
            targetCrystal = crystals.stream()
                    .min(Comparator.comparingDouble(mc.player::distanceTo))
                    .orElse(null);
        }

        if (targetCrystal != null) {
            if (rotate.getValue()) faceTarget(targetCrystal, silentRotate.getValue());
            mc.player.networkHandler.sendPacket(
                    new PlayerInteractEntityC2SPacket(targetCrystal, Hand.MAIN_HAND)
            );
            mc.player.swingHand(Hand.MAIN_HAND);
            attackTimer.reset();
        }
    }

    private void placeCrystals() {
        if (!placeTimer.passedMs(placeDelay.getValue())) return;

        BlockPos bestPos = findCrystalPos();

        if (bestPos != null) {
            if (rotate.getValue()) facePos(bestPos, silentRotate.getValue());

            mc.player.networkHandler.sendPacket(
                    new PlayerInteractBlockC2SPacket(
                            Hand.MAIN_HAND,
                            bestPos,
                            net.minecraft.util.math.Direction.UP
                    )
            );
            mc.player.swingHand(Hand.MAIN_HAND);
            placeTimer.reset();
        }
    }

    private BlockPos findCrystalPos() {
        BlockPos playerPos = mc.player.getBlockPos();

        for (int y = -1; y <= 1; y++) {
            for (int x = -(int) placeRange.getValue(); x <= (int) placeRange.getValue(); x++) {
                for (int z = -(int) placeRange.getValue(); z <= (int) placeRange.getValue(); z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (canPlaceCrystal(pos)) return pos;
                }
            }
        }
        return null;
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        if (!(mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN || mc.world.getBlockState(pos).getBlock() == Blocks.BEDROCK))
            return false;

        BlockPos above = pos.up();
        BlockPos twoAbove = pos.up(2);

        if (!mc.world.isAir(above) || !mc.world.isAir(twoAbove)) return false;

        List<Entity> entities = mc.world.getOtherEntities(null, new Box(above));
        if (!entities.isEmpty()) return false;

        return true;
    }

    private void faceTarget(Entity target, boolean silent) {
        double diffX = target.getX() - mc.player.getX();
        double diffY = (target.getY() + target.getStandingEyeHeight()) - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double diffZ = target.getZ() - mc.player.getZ();
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        if (silent) {
            sendSilentRotation(yaw, pitch);
        } else {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            mc.player.setHeadYaw(yaw);
            mc.player.setBodyYaw(yaw);
        }
    }

    private void facePos(BlockPos pos, boolean silent) {
        double diffX = pos.getX() + 0.5 - mc.player.getX();
        double diffY = pos.getY() + 0.5 - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double diffZ = pos.getZ() + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        if (silent) {
            sendSilentRotation(yaw, pitch);
        } else {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            mc.player.setHeadYaw(yaw);
            mc.player.setBodyYaw(yaw);
        }
    }

    private void sendSilentRotation(float yaw, float pitch) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            yaw,
            pitch,
            mc.player.isOnGround(),
            mc.player.horizontalCollision
        ));
    }

    @Override
    public String getDisplayInfo() {
        return String.format("Place: %.1f Attack: %.1f", placeRange.getValue(), attackRange.getValue());
    }
}
