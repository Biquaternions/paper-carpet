package me.biquaternions.carpet.fakes;

import me.biquaternions.carpet.utils.Tracer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of fabric-carpet's {@code EntityPlayerActionPack}, simulates the inputs of a player.
 */
public class PlayerActionPack {

    private final ServerPlayer player;

    private final Map<ActionType, Action> actions = new EnumMap<>(ActionType.class);

    private @Nullable BlockPos currentBlock;
    private int blockHitDelay;
    private boolean isHittingBlock;
    private float curBlockDamageMP;

    private boolean sneaking;
    private boolean sprinting;
    private float forward;
    private float strafing;

    private int itemUseCooldown;

    public PlayerActionPack(ServerPlayer playerIn) {
        this.player = playerIn;
        this.stopAll();
    }

    public void copyFrom(PlayerActionPack other) {
        this.actions.putAll(other.actions);
        this.currentBlock = other.currentBlock;
        this.blockHitDelay = other.blockHitDelay;
        this.isHittingBlock = other.isHittingBlock;
        this.curBlockDamageMP = other.curBlockDamageMP;

        this.sneaking = other.sneaking;
        this.sprinting = other.sprinting;
        this.forward = other.forward;
        this.strafing = other.strafing;

        this.itemUseCooldown = other.itemUseCooldown;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public PlayerActionPack start(ActionType type, @Nullable Action action) {
        Action previous = this.actions.remove(type);
        if (previous != null) {
            type.stop(this.player, previous);
        }
        if (action != null) {
            this.actions.put(type, action);
            type.start(this.player, action); // noop
        }
        return this;
    }

    public PlayerActionPack setSneaking(boolean doSneak) {
        this.sneaking = doSneak;
        this.player.setShiftKeyDown(doSneak);
        if (this.sprinting && this.sneaking) {
            this.setSprinting(false);
        }
        return this;
    }

    public PlayerActionPack setSprinting(boolean doSprint) {
        this.sprinting = doSprint;
        this.player.setSprinting(doSprint);
        if (this.sneaking && this.sprinting) {
            this.setSneaking(false);
        }
        return this;
    }

    public PlayerActionPack setForward(float value) {
        this.forward = value;
        return this;
    }

    public PlayerActionPack setStrafing(float value) {
        this.strafing = value;
        return this;
    }

    public PlayerActionPack look(Direction direction) {
        return switch (direction) {
            case NORTH -> this.look(180, 0);
            case SOUTH -> this.look(0, 0);
            case EAST -> this.look(-90, 0);
            case WEST -> this.look(90, 0);
            case UP -> this.look(this.player.getYRot(), -90);
            case DOWN -> this.look(this.player.getYRot(), 90);
        };
    }

    public PlayerActionPack look(Vec2 rotation) {
        return this.look(rotation.x, rotation.y);
    }

    public PlayerActionPack look(float yaw, float pitch) {
        this.player.setYRot(yaw % 360);
        this.player.setXRot(Math.clamp(pitch, -90, 90));
        return this;
    }

    public PlayerActionPack lookAt(Vec3 position) {
        this.player.lookAt(EntityAnchorArgument.Anchor.EYES, position);
        return this;
    }

    public PlayerActionPack turn(float yaw, float pitch) {
        return this.look(this.player.getYRot() + yaw, this.player.getXRot() + pitch);
    }

    public PlayerActionPack turn(Vec2 rotation) {
        return this.turn(rotation.x, rotation.y);
    }

    public PlayerActionPack stopMovement() {
        this.setSneaking(false);
        this.setSprinting(false);
        this.forward = 0.0F;
        this.strafing = 0.0F;
        return this;
    }

    public PlayerActionPack stopAll() {
        for (Map.Entry<ActionType, Action> entry : this.actions.entrySet()) {
            entry.getKey().stop(this.player, entry.getValue());
        }
        this.actions.clear();
        return this.stopMovement();
    }

    public PlayerActionPack mount(boolean onlyRideables) {
        List<Entity> entities;
        if (onlyRideables) {
            entities = this.player.level().getEntities(this.player, this.player.getBoundingBox().inflate(3.0D, 1.0D, 3.0D),
                    e -> e instanceof Minecart || e instanceof Boat || e instanceof AbstractHorse);
        } else {
            entities = this.player.level().getEntities(this.player, this.player.getBoundingBox().inflate(3.0D, 1.0D, 3.0D));
        }
        if (entities.isEmpty()) {
            return this;
        }
        Entity closest = null;
        double distance = Double.POSITIVE_INFINITY;
        Entity currentVehicle = this.player.getVehicle();
        for (Entity e : entities) {
            if (e == this.player || (currentVehicle == e)) {
                continue;
            }
            double dd = this.player.distanceToSqr(e);
            if (dd < distance) {
                distance = dd;
                closest = e;
            }
        }
        if (closest == null) {
            return this;
        }
        if (closest instanceof AbstractHorse horse && onlyRideables) {
            horse.mobInteract(this.player, InteractionHand.MAIN_HAND);
        } else {
            this.player.startRiding(closest, true, true);
        }
        return this;
    }

    public PlayerActionPack dismount() {
        this.player.stopRiding();
        return this;
    }

    public void onUpdate() {
        Map<ActionType, Boolean> actionAttempts = new HashMap<>();
        this.actions.values().removeIf(e -> e.done);
        for (Map.Entry<ActionType, Action> e : Map.copyOf(this.actions).entrySet()) {
            ActionType type = e.getKey();
            Action action = e.getValue();
            // skipping attack if use was successful
            if (!(actionAttempts.getOrDefault(ActionType.USE, false) && type == ActionType.ATTACK)) {
                Boolean actionStatus = action.tick(this, type);
                if (actionStatus != null) {
                    actionAttempts.put(type, actionStatus);
                }
            }
            // optionally retrying use after successful attack and unsuccessful use
            if (type == ActionType.ATTACK
                    && actionAttempts.getOrDefault(ActionType.ATTACK, false)
                    && !actionAttempts.getOrDefault(ActionType.USE, true)) {
                // according to MinecraftClient.handleInputEvents
                Action using = this.actions.get(ActionType.USE);
                if (using != null) { // this is always true - we know use worked, but just in case
                    using.retry(this, ActionType.USE);
                }
            }
        }
        float vel = this.sneaking ? 0.3F : 1.0F;
        // The != 0.0F checks are needed given else real players can't control minecarts, however it works with fakes and else they don't stop immediately
        if (this.forward != 0.0F || this.player instanceof FakePlayer) {
            this.player.zza = this.forward * vel;
        }
        if (this.strafing != 0.0F || this.player instanceof FakePlayer) {
            this.player.xxa = this.strafing * vel;
        }
    }

    static HitResult getTarget(ServerPlayer player) {
        double reach = player.gameMode.isCreative() ? 5 : 4.5f;
        return Tracer.rayTrace(player, 1, reach, false);
    }

    private void dropItemFromSlot(int slot, boolean dropAll) {
        Inventory inv = this.player.getInventory();
        if (!inv.getItem(slot).isEmpty()) {
            this.player.drop(inv.removeItem(slot, dropAll ? inv.getItem(slot).getCount() : 1), false, Prediction.SERVER_ONLY); // scatter, keep owner
        }
    }

    public PlayerActionPack drop(int selectedSlot, boolean dropAll) {
        Inventory inv = this.player.getInventory();
        if (selectedSlot == -2) { // all
            for (int i = inv.getContainerSize(); i >= 0; i--) {
                this.dropItemFromSlot(i, dropAll);
            }
        } else { // one slot
            if (selectedSlot == -1) {
                selectedSlot = inv.getSelectedSlot();
            }
            this.dropItemFromSlot(selectedSlot, dropAll);
        }
        return this;
    }

    public PlayerActionPack setSlot(int slot) {
        this.player.getInventory().setSelectedSlot(slot - 1);
        this.player.connection.send(new ClientboundSetHeldSlotPacket(slot - 1));
        return this;
    }

    public enum ActionType {
        USE(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                PlayerActionPack ap = ActionPacks.get(player);
                if (ap.itemUseCooldown > 0) {
                    ap.itemUseCooldown--;
                    return true;
                }
                if (player.isUsingItem()) {
                    return true;
                }
                HitResult hit = getTarget(player);
                for (InteractionHand hand : InteractionHand.values()) {
                    switch (hit.getType()) {
                        case BLOCK -> {
                            player.resetLastActionTime();
                            ServerLevel world = player.level();
                            BlockHitResult blockHit = (BlockHitResult) hit;
                            BlockPos pos = blockHit.getBlockPos();
                            Direction side = blockHit.getDirection();
                            if (pos.getY() < player.level().getMaxY() - (side == Direction.UP ? 1 : 0) && world.mayInteract(player, pos)) {
                                InteractionResult result = player.gameMode.useItemOn(player, world, player.getItemInHand(hand), hand, blockHit);
                                if (result instanceof InteractionResult.Success success) {
                                    if (success.swingSource() == InteractionResult.SwingSource.SERVER_ONLY) {
                                        player.swing(hand, SwingAnimation.DEFAULT, true);
                                    }
                                    ap.itemUseCooldown = 3;
                                    return true;
                                }
                            }
                        }
                        case ENTITY -> {
                            player.resetLastActionTime();
                            EntityHitResult entityHit = (EntityHitResult) hit;
                            Entity entity = entityHit.getEntity();
                            ItemStack handItem = player.getItemInHand(hand);
                            boolean handWasEmpty = handItem.isEmpty();
                            boolean itemFrameEmpty = (entity instanceof ItemFrame frame) && frame.getItem().isEmpty();
                            // client always consumes action with food on a horse, unless it's a skeleton horse.
                            boolean isFeedingHorse = entity instanceof AbstractHorse horse
                                    && horse.isAlive()
                                    && horse.isFood(handItem)
                                    && !(horse instanceof SkeletonHorse);
                            Vec3 relativeHitPos = entityHit.getLocation().subtract(entity.getX(), entity.getY(), entity.getZ());
                            if (entity.interact(player, hand, relativeHitPos).consumesAction()) {
                                ap.itemUseCooldown = 3;
                                return true;
                            }
                            // fix for SS itemframe always returns CONSUME even if no action is performed
                            InteractionResult interactionResult = player.interactOn(entity, hand, relativeHitPos);
                            if ((interactionResult.consumesAction() && !(handWasEmpty && itemFrameEmpty)) || isFeedingHorse) {
                                ap.itemUseCooldown = 3;
                                return true;
                            }
                        }
                        default -> {
                        }
                    }
                    ItemStack handItem = player.getItemInHand(hand);
                    if (player.gameMode.useItem(player, player.level(), handItem, hand).consumesAction()) {
                        ap.itemUseCooldown = 3;
                        return true;
                    }
                }
                return false;
            }

            @Override
            void inactiveTick(ServerPlayer player, @Nullable Action action) {
                PlayerActionPack ap = ActionPacks.get(player);
                ap.itemUseCooldown = 0;
                player.releaseUsingItem();
            }
        },
        ATTACK(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                HitResult hit = getTarget(player);
                switch (hit.getType()) {
                    case ENTITY -> {
                        EntityHitResult entityHit = (EntityHitResult) hit;
                        if (!action.isContinuous) {
                            player.attack(entityHit.getEntity());
                            player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                        }
                        player.resetAttackStrengthTicker();
                        player.resetLastActionTime();
                        return true;
                    }
                    case BLOCK -> {
                        PlayerActionPack ap = ActionPacks.get(player);
                        if (ap.blockHitDelay > 0) {
                            ap.blockHitDelay--;
                            return false;
                        }
                        BlockHitResult blockHit = (BlockHitResult) hit;
                        BlockPos pos = blockHit.getBlockPos();
                        Direction side = blockHit.getDirection();
                        if (player.blockActionRestricted(player.level(), pos, player.gameMode.getGameModeForPlayer())) {
                            return false;
                        }
                        if (ap.currentBlock != null && player.level().getBlockState(ap.currentBlock).isAir()) {
                            ap.currentBlock = null;
                            return false;
                        }
                        BlockState state = player.level().getBlockState(pos);
                        boolean blockBroken = false;
                        if (player.gameMode.getGameModeForPlayer().isCreative()) {
                            player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                            ap.blockHitDelay = 5;
                            blockBroken = true;
                        } else if (ap.currentBlock == null || !ap.currentBlock.equals(pos)) {
                            if (ap.currentBlock != null) {
                                player.gameMode.handleBlockBreakAction(ap.currentBlock, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                            }
                            player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                            boolean notAir = !state.isAir();
                            if (notAir && ap.curBlockDamageMP == 0) {
                                state.attack(player.level(), pos, player);
                            }
                            if (notAir && state.getDestroyProgress(player, player.level(), pos) >= 1) {
                                ap.currentBlock = null;
                                //instamine??
                                blockBroken = true;
                            } else {
                                ap.currentBlock = pos;
                                ap.curBlockDamageMP = 0;
                            }
                        } else {
                            ap.curBlockDamageMP += state.getDestroyProgress(player, player.level(), pos);
                            if (ap.curBlockDamageMP >= 1) {
                                player.gameMode.handleBlockBreakAction(pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, side, player.level().getMaxY(), -1);
                                ap.currentBlock = null;
                                ap.blockHitDelay = 5;
                                blockBroken = true;
                            }
                            player.level().destroyBlockProgress(-1, pos, (int) (ap.curBlockDamageMP * 10));
                        }
                        player.resetLastActionTime();
                        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                        return blockBroken;
                    }
                    default -> {
                    }
                }
                return false;
            }

            @Override
            void inactiveTick(ServerPlayer player, @Nullable Action action) {
                PlayerActionPack ap = ActionPacks.get(player);
                if (ap.currentBlock == null) {
                    return;
                }
                player.level().destroyBlockProgress(-1, ap.currentBlock, -1);
                player.gameMode.handleBlockBreakAction(ap.currentBlock, ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.DOWN, player.level().getMaxY(), -1);
                ap.currentBlock = null;
            }
        },
        JUMP(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                if (action.limit == 1) {
                    if (player.onGround()) {
                        player.jumpFromGround();
                    } else if (!player.onClimbable()) {
                        player.tryToStartFallFlying();
                    }
                } else {
                    player.setJumping(true);
                }
                return false;
            }

            @Override
            void inactiveTick(ServerPlayer player, @Nullable Action action) {
                player.setJumping(false);
            }
        },
        DROP_ITEM(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                player.resetLastActionTime();
                player.drop(false); // dropSelectedItem
                return false;
            }
        },
        DROP_STACK(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                player.resetLastActionTime();
                player.drop(true); // dropSelectedItem
                return false;
            }
        },
        SWAP_HANDS(true) {
            @Override
            boolean execute(ServerPlayer player, Action action) {
                player.resetLastActionTime();
                ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
                player.setItemInHand(InteractionHand.OFF_HAND, player.getItemInHand(InteractionHand.MAIN_HAND));
                player.setItemInHand(InteractionHand.MAIN_HAND, offhand);
                return false;
            }
        };

        public final boolean preventSpectator;

        ActionType(boolean preventSpectator) {
            this.preventSpectator = preventSpectator;
        }

        void start(ServerPlayer player, Action action) {
        }

        abstract boolean execute(ServerPlayer player, Action action);

        void inactiveTick(ServerPlayer player, @Nullable Action action) {
        }

        void stop(ServerPlayer player, @Nullable Action action) {
            this.inactiveTick(player, action);
        }
    }

    public static class Action {
        public boolean done = false;
        public final int limit;
        public final int interval;
        public final int offset;
        private int count;
        private int next;
        private final boolean isContinuous;

        private Action(int limit, int interval, int offset, boolean continuous) {
            this.limit = limit;
            this.interval = interval;
            this.offset = offset;
            this.next = interval + offset;
            this.isContinuous = continuous;
        }

        public static Action once() {
            return new Action(1, 1, 0, false);
        }

        public static Action continuous() {
            return new Action(-1, 1, 0, true);
        }

        public static Action interval(int interval) {
            return new Action(-1, interval, 0, false);
        }

        public static Action interval(int interval, int offset) {
            return new Action(-1, interval, offset, false);
        }

        @Nullable
        Boolean tick(PlayerActionPack actionPack, ActionType type) {
            this.next--;
            Boolean cancel = null;
            if (this.next <= 0) {
                if (this.interval == 1 && !this.isContinuous) {
                    // need to allow entity to tick, otherwise won't have effect (bow)
                    // actions are 20 tps, so need to clear status mid tick, allowing entities process it till next time
                    if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                        type.inactiveTick(actionPack.player, this);
                    }
                }

                if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                    cancel = type.execute(actionPack.player, this);
                }
                this.count++;
                if (this.count == this.limit) {
                    type.stop(actionPack.player, null);
                    this.done = true;
                    return cancel;
                }
                this.next = this.interval;
            } else {
                if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                    type.inactiveTick(actionPack.player, this);
                }
            }
            return cancel;
        }

        void retry(PlayerActionPack actionPack, ActionType type) {
            //assuming action run but was unsuccessful that tick, but opportunity emerged to retry it, lets retry it.
            if (!type.preventSpectator || !actionPack.player.isSpectator()) {
                type.execute(actionPack.player, this);
            }
            this.count++;
            if (this.count == this.limit) {
                type.stop(actionPack.player, null);
                this.done = true;
            }
        }
    }

}
