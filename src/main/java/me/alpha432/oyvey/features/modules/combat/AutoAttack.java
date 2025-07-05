package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.setting.Setting; 
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;

import java.util.Comparator;
import java.util.stream.StreamSupport;

public class AutoAttack extends Module {
    private final Timer attackTimer = new Timer();

    public Setting<Float> range = register(new Setting<>("Range", 4.5f, 1.0f, 6.0f));
    public Setting<Integer> delay = register(new Setting<>("Delay", 500, 0, 1000));
    public Setting<Boolean> playersOnly = register(new Setting<>("PlayersOnly", true));
    public Setting<Boolean> rotate = register(new Setting<>("Rotate", false));
    public Setting<Boolean> silentRotate = register(new Setting<>("SilentRotate", false));

    public AutoAttack() {
        super("AutoAttack", "Automatically attacks players", Category.COMBAT, true, false, false);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        Entity target = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> e instanceof LivingEntity)
                .filter(e -> e != mc.player)
                .filter(e -> ((LivingEntity) e).isAlive())
                .filter(e -> mc.player.distanceTo(e) <= range.getValue())
                .filter(e -> !playersOnly.getValue() || e instanceof OtherClientPlayerEntity)
                .min(Comparator.comparingDouble(mc.player::distanceTo))
                .orElse(null);

        if (target != null && attackTimer.passedMs(delay.getValue())) {
            attack(target);
            attackTimer.reset();
        }
    }

    private void attack(Entity target) {
        if (silentRotate.getValue()) {
            float[] rotations = calculateRotations(target);
            float yaw = rotations[0];
            float pitch = rotations[1];
            mc.player.networkHandler.sendPacket(
                new PlayerInteractEntityC2SPacket.AttackWithRotation(target, yaw, pitch, mc.player.isSneaking())
            );
        } else if (rotate.getValue()) {
            float[] rotations = calculateRotations(target);
            mc.player.yaw = rotations[0];
            mc.player.pitch = rotations[1];
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        } else {
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        }
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private float[] calculateRotations(Entity target) {
        double diffX = target.getX() - mc.player.getX();
        double diffY = (target.getY() + target.getEyeHeight()) - (mc.player.getY() + mc.player.getEyeHeight());
        double diffZ = target.getZ() - mc.player.getZ();
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, dist));

        return new float[] {yaw, pitch};
    }

    @Override
    public String getDisplayInfo() {
        return String.format("%.1f", range.getValue());
    }
}
