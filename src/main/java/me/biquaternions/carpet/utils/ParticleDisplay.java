package me.biquaternions.carpet.utils;

import lombok.experimental.UtilityClass;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

@NullMarked
@UtilityClass
public class ParticleDisplay {

    public record ParticleOptions<T>(Particle particle, @Nullable T data) {
        public static ParticleOptions<Void> of(Particle particle) {
            return new ParticleOptions<>(particle, null);
        }
    }

    private <T> void spawn(Player player, ParticleOptions<T> options, double x, double y, double z, int count, double spread) {
        player.spawnParticle(options.particle(), x, y, z, count, spread, spread, spread, 0.0, options.data(), true);
    }

    public void drawParticleLine(Player player, Vector from, Vector to, ParticleOptions<?> mainParticle, @Nullable ParticleOptions<?> accentParticle, int count, double spread) {
        if (accentParticle != null) {
            spawn(player, accentParticle, to.getX(), to.getY(), to.getZ(), count, spread);
        }

        double lineLengthSq = from.distanceSquared(to);
        if (lineLengthSq == 0) {
            return;
        }

        Vector incvec = to.clone().subtract(from).normalize();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Vector delta = new Vector(); delta.lengthSquared() < lineLengthSq; delta.add(incvec.clone().multiply(random.nextFloat()))) {
            spawn(player, mainParticle, delta.getX() + from.getX(), delta.getY() + from.getY(), delta.getZ() + from.getZ(), 1, 0.0);
        }
    }

}
