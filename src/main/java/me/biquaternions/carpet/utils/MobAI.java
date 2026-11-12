package me.biquaternions.carpet.utils;

import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@UtilityClass
public class MobAI {

    private final Map<EntityType, Set<TrackingType>> AI_TRACKERS = new HashMap<>();

    public void resetTrackers() {
        AI_TRACKERS.clear();
    }

    public boolean isTracking(Entity e, TrackingType type) {
        Set<TrackingType> currentTrackers = AI_TRACKERS.get(e.getType());
        return currentTrackers != null && currentTrackers.contains(type);
    }

    public boolean isTrackingAnything() {
        return !AI_TRACKERS.isEmpty();
    }

    public void clearTracking(EntityType etype) {
        AI_TRACKERS.remove(etype);
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getType() == etype && e.customName() != null) {
                    e.setCustomNameVisible(false);
                    e.customName(null);
                }
            }
        }
    }

    public void startTracking(EntityType e, TrackingType type) {
        AI_TRACKERS.computeIfAbsent(e, k -> EnumSet.noneOf(TrackingType.class)).add(type);
    }

    public Stream<String> availableTypes() {
        Set<EntityType> types = new HashSet<>();
        for (TrackingType type : TrackingType.values()) {
            types.addAll(type.types);
        }
        return types.stream().map(t -> t.getKey().getKey());
    }

    public Stream<String> availableFor(EntityType entityType) {
        Set<TrackingType> availableOptions = new HashSet<>();
        for (TrackingType type : TrackingType.values()) {
            if (type.types.contains(entityType)) {
                availableOptions.add(type);
            }
        }
        return availableOptions.stream().map(t -> t.name().toLowerCase(Locale.ROOT));
    }

    public enum TrackingType {
        IRON_GOLEM_SPAWNING(Set.of(EntityType.VILLAGER)),
        BREEDING(Set.of(EntityType.VILLAGER));

        public final Set<EntityType> types;

        TrackingType(Set<EntityType> applicableTypes) {
            this.types = applicableTypes;
        }
    }

}
