package ru.mgcveqbj.pearlnavigator;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PearlNavigator {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("pearlnavigator1165.json");

    static PearlNavigatorConfig config = PearlNavigatorConfig.load(CONFIG_PATH);

    private static float lastSentYaw;
    private static float lastSentPitch;
    private static boolean lastSentOnGround;

    private static AimSolution cachedBestAim;
    private static long lastSearchTick;
    private static boolean lastInWindow;
    private static long lastCueTick;
    private static long lastAutoThrowTick;
    private static long lastAutoSwitchTick;

    private static PearlNavigatorPrediction.Result currentPrediction;
    private static double currentDistance;
    private static boolean lastTargetVisible;
    private static ClipAnchor lastClipAnchor;
    private static ClipAnchor[] lastAnchors = new ClipAnchor[0];

    private PearlNavigator() {
    }

    static void init() {
        // Config already loaded in static init.
    }

    static void toggle(MinecraftClient client) {
        config.enabled = !config.enabled;
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage(new LiteralText("Pearl Navigator: " + (config.enabled ? "ON" : "OFF")), true);
        }
        saveConfig();
    }

    static void toggleAimAssist(MinecraftClient client) {
        config.aimAssist = !config.aimAssist;
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage(new LiteralText("Pearl aim assist: " + (config.aimAssist ? "ON" : "OFF")), true);
        }
        saveConfig();
    }

    static void toggleAutoMode(MinecraftClient client) {
        config.autoMode = !config.autoMode;
        ClientPlayerEntity player = client.player;
        if (player != null) {
            player.sendMessage(new LiteralText("Pearl auto mode: " + (config.autoMode ? "ON" : "OFF")), true);
        }
        saveConfig();
    }

    static void onClientTick(MinecraftClient client) {
        if (!config.enabled) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        boolean zoneActive = config.zoneMode && config.targetEnabled;
        boolean autoActive = config.autoMode && config.targetEnabled && !zoneActive;
        boolean assistActive = config.aimAssist && !zoneActive;
        if (autoActive && config.autoSwitchPearl) {
            ensurePearlSelected(player);
        }

        boolean holdingPearl = isHoldingPearl(player);
        if ((holdingPearl || autoActive || zoneActive) && config.targetEnabled) {
            long time = client.world.getTime();
            if (time - lastSearchTick >= Math.max(1, config.searchIntervalTicks)) {
                cachedBestAim = findBestAim(client);
                lastSearchTick = time;
            }
        }

        if (!zoneActive && holdingPearl && cachedBestAim != null) {
            if (autoActive && config.autoFullControl) {
                applyLook(client, cachedBestAim.yaw, cachedBestAim.pitch);
            } else if (assistActive || autoActive) {
                applySmoothedAim(client, cachedBestAim);
            }
        }

        if (holdingPearl || autoActive || zoneActive) {
            currentPrediction = updatePrediction(client);
            currentDistance = distanceToGoal(currentPrediction);
        } else {
            currentPrediction = null;
            currentDistance = 0.0;
        }

        if (autoActive) {
            attemptAutoThrow(client, player);
        }

        handleWindowCue(client);
    }

    public static void applyBeforeUse(MinecraftClient client) {
        if (!config.enabled || config.zoneMode || (!config.aimAssist && !config.autoMode)) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player == null || !isHoldingPearl(player)) {
            return;
        }
        if (cachedBestAim == null && config.targetEnabled) {
            cachedBestAim = findBestAim(client);
        }
        if (cachedBestAim == null) {
            return;
        }
        applyLook(client, cachedBestAim.yaw, cachedBestAim.pitch);
    }

    public static void applyNudge(ClientPlayerEntity player) {
        if (!config.enabled) {
            return;
        }
        boolean autoActive = config.autoMode && config.targetEnabled;
        if (autoActive && config.autoFullControl && !config.autoMove) {
            player.input.movementForward = 0.0f;
            player.input.movementSideways = 0.0f;
            player.input.jumping = false;
            player.input.sneaking = false;
        }

        if (autoActive && config.autoMove) {
            applyAutoMovement(player);
        }

        if (autoActive && config.autoCrawl && shouldAutoCrawl(player)) {
            player.input.sneaking = true;
        }

        boolean deepInside = isDeepInsideWall(player);
        if (config.insideBoost && deepInside) {
            applyInsideBoost(player);
        }

        boolean allowNudge = (config.nudgeEnabled || (autoActive && config.autoNudge)) && config.targetEnabled;
        if (!allowNudge || !deepInside) {
            return;
        }
        if (autoActive && deepInside) {
            player.setSprinting(true);
            if (player.age % 4 == 0) {
                player.input.jumping = true;
            }
        }
        Vec3d target = getTarget();
        Vec3d toTarget = target.subtract(player.getPos());
        if (toTarget.lengthSquared() < 0.0001) {
            return;
        }
        Vec3d dir = toTarget.normalize();

        float yawRad = (float) Math.toRadians(player.yaw);
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
        Vec3d right = new Vec3d(MathHelper.cos(yawRad), 0.0, MathHelper.sin(yawRad));

        float forwardAmount = (float) forward.dotProduct(dir) * config.nudgeStrength;
        float sidewaysAmount = (float) right.dotProduct(dir) * config.nudgeStrength;

        player.input.movementForward = MathHelper.clamp(player.input.movementForward + forwardAmount, -1.0f, 1.0f);
        player.input.movementSideways = MathHelper.clamp(player.input.movementSideways + sidewaysAmount, -1.0f, 1.0f);

        if (Math.abs(dir.y) > 0.2) {
            int interval = Math.max(1, Math.round(1.0f / Math.max(0.01f, config.nudgeVerticalStrength)));
            if (player.age % interval == 0) {
                if (dir.y > 0.0) {
                    player.input.jumping = true;
                } else {
                    player.input.sneaking = true;
                }
            }
        }
    }

    private static void applyAutoMovement(ClientPlayerEntity player) {
        Vec3d moveDir = null;
        boolean sprint = false;

        if (lastClipAnchor != null && lastClipAnchor.side != null) {
            Vec3d desired = desiredStandPos(player, lastClipAnchor);
            Vec3d delta = desired.subtract(player.getPos());
            Vec3d flat = new Vec3d(delta.x, 0.0, delta.z);
            if (flat.lengthSquared() > 0.001) {
                moveDir = flat.normalize();
            }
            sprint = moveDir != null;
        } else if (config.targetEnabled) {
            Vec3d toTarget = getTarget().subtract(player.getPos());
            Vec3d flat = new Vec3d(toTarget.x, 0.0, toTarget.z);
            if (flat.lengthSquared() > 0.05) {
                moveDir = flat.normalize();
            }
        }

        if (moveDir == null) {
            player.input.movementForward = 0.0f;
            player.input.movementSideways = 0.0f;
            return;
        }

        float yawRad = (float) Math.toRadians(player.yaw);
        Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
        Vec3d right = new Vec3d(MathHelper.cos(yawRad), 0.0, MathHelper.sin(yawRad));

        float forwardAmount = (float) forward.dotProduct(moveDir) * config.autoMoveStrength;
        float sidewaysAmount = (float) right.dotProduct(moveDir) * config.autoMoveStrength;

        player.input.movementForward = MathHelper.clamp(forwardAmount, -1.0f, 1.0f);
        player.input.movementSideways = MathHelper.clamp(sidewaysAmount, -1.0f, 1.0f);
        if (sprint) {
            player.setSprinting(true);
        }
    }

    private static void applyInsideBoost(ClientPlayerEntity player) {
        Vec3d toTarget = getTarget().subtract(player.getPos());
        Vec3d flat = new Vec3d(toTarget.x, 0.0, toTarget.z);
        if (flat.lengthSquared() > 0.0005) {
            Vec3d dir = flat.normalize();
            float yawRad = (float) Math.toRadians(player.yaw);
            Vec3d forward = new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad));
            Vec3d right = new Vec3d(MathHelper.cos(yawRad), 0.0, MathHelper.sin(yawRad));

            float forwardAmount = (float) forward.dotProduct(dir);
            float sidewaysAmount = (float) right.dotProduct(dir);

            player.input.movementForward = MathHelper.clamp(forwardAmount, -1.0f, 1.0f);
            player.input.movementSideways = MathHelper.clamp(sidewaysAmount, -1.0f, 1.0f);
        }
        player.setSprinting(true);
        if (player.age % 4 == 0) {
            player.input.jumping = true;
        }
    }

    private static Vec3d desiredStandPos(ClientPlayerEntity player, ClipAnchor anchor) {
        double stand = config.autoWallStandDistance;
        double x = player.getX();
        double z = player.getZ();
        if (anchor.side == Direction.EAST) {
            x = anchor.faceCoord - stand;
            z = anchor.anchor.z;
        } else if (anchor.side == Direction.WEST) {
            x = anchor.faceCoord + stand;
            z = anchor.anchor.z;
        } else if (anchor.side == Direction.SOUTH) {
            z = anchor.faceCoord - stand;
            x = anchor.anchor.x;
        } else if (anchor.side == Direction.NORTH) {
            z = anchor.faceCoord + stand;
            x = anchor.anchor.x;
        }
        return new Vec3d(x, player.getY(), z);
    }

    private static boolean isDeepInsideWall(ClientPlayerEntity player) {
        if (!player.isInsideWall()) {
            return false;
        }
        if (lastClipAnchor == null || lastClipAnchor.side == null) {
            return true;
        }
        double delta = wallPlaneDelta(player, lastClipAnchor);
        double sign = outsideSign(lastClipAnchor.side);
        return sign * delta < -0.05;
    }

    private static double outsideSign(Direction side) {
        return (side == Direction.EAST || side == Direction.NORTH) ? 1.0 : -1.0;
    }

    private static boolean shouldAutoCrawl(ClientPlayerEntity player) {
        if (lastClipAnchor == null || lastClipAnchor.side == null) {
            return player.isInsideWall();
        }
        double wallDist = wallPlaneDistance(player, lastClipAnchor);
        return wallDist <= config.autoWallStandDistance + config.autoWallStandTolerance + 0.1;
    }

    private static boolean isThrowPositionReady(ClientPlayerEntity player) {
        if (lastClipAnchor == null || lastClipAnchor.side == null) {
            return true;
        }
        double wallDist = wallPlaneDistance(player, lastClipAnchor);
        double stand = config.autoWallStandDistance;
        double tol = config.autoWallStandTolerance;
        return wallDist <= stand + tol + 0.12;
    }

    private static double wallPlaneDistance(ClientPlayerEntity player, ClipAnchor anchor) {
        return Math.abs(wallPlaneDelta(player, anchor));
    }

    private static double wallPlaneDelta(ClientPlayerEntity player, ClipAnchor anchor) {
        if (anchor.side.getAxis() == Direction.Axis.X) {
            return anchor.faceCoord - player.getX();
        }
        return anchor.faceCoord - player.getZ();
    }

    private static Vec3d directionToWall(ClipAnchor anchor, double delta) {
        double sign = Math.signum(delta);
        if (sign == 0.0) {
            return null;
        }
        if (anchor.side.getAxis() == Direction.Axis.X) {
            return new Vec3d(sign, 0.0, 0.0);
        }
        return new Vec3d(0.0, 0.0, sign);
    }

    static boolean shouldShowHud() {
        return config.showHud;
    }

    static boolean shouldShowPrediction() {
        return config.showPrediction;
    }

    static PearlNavigatorPrediction.Result getCurrentPrediction() {
        return currentPrediction;
    }

    static double getCurrentDistance() {
        return currentDistance;
    }

    static AimSolution getBestAim() {
        return cachedBestAim;
    }

    static Vec3d getTarget() {
        BlockPos blockPos = getTargetBlockPos();
        return new Vec3d(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
    }

    static boolean hasTarget() {
        return config.targetEnabled;
    }

    static double getTolerance() {
        return config.tolerance;
    }

    static boolean shouldShowWorldTarget() {
        return config.showWorldTarget;
    }

    static boolean isZoneMode() {
        return config.zoneMode;
    }

    static Vec3d[] getAnchorPoints() {
        if (lastAnchors == null || lastAnchors.length == 0) {
            return new Vec3d[0];
        }
        Vec3d[] points = new Vec3d[lastAnchors.length];
        for (int i = 0; i < lastAnchors.length; i++) {
            points[i] = lastAnchors[i].anchor;
        }
        return points;
    }

    static Vec3d getActiveAnchor() {
        return lastClipAnchor == null ? null : lastClipAnchor.anchor;
    }

    static double getAnchorTolerance() {
        return config.anchorTolerance;
    }

    static BlockPos getTargetBlockPos() {
        return new BlockPos(config.targetX, config.targetY, config.targetZ);
    }

    private static void handleWindowCue(MinecraftClient client) {
        if (!config.targetEnabled || currentPrediction == null) {
            lastInWindow = false;
            return;
        }
        double cueDistance = currentDistance;
        double cueTolerance = config.tolerance;
        if (lastClipAnchor != null && currentPrediction.type == HitResult.Type.BLOCK) {
            cueDistance = currentPrediction.hitPos.distanceTo(lastClipAnchor.anchor);
            cueTolerance = Math.max(0.1, config.anchorTolerance);
        }
        boolean inWindow = cueDistance <= cueTolerance;
        if (config.audioCue && inWindow && !lastInWindow) {
            long time = client.world == null ? 0 : client.world.getTime();
            if (time - lastCueTick >= config.audioCooldownTicks) {
                ClientPlayerEntity player = client.player;
                if (player != null) {
                    player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 0.6f, 1.6f);
                }
                lastCueTick = time;
            }
        }
        lastInWindow = inWindow;
    }

    private static PearlNavigatorPrediction.Result updatePrediction(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return null;
        }
        Vec3d startPos = getPearlStartPos(player);
        Vec3d shooterVelocity = getPearlStartVelocity(player);
        float yaw = player.yaw;
        float pitch = player.pitch;
        return PearlNavigatorPrediction.simulate(client, startPos, shooterVelocity, yaw, pitch,
            Math.max(40, config.predictionSteps), config.useMultiRay);
    }

    private static double distanceToGoal(PearlNavigatorPrediction.Result prediction) {
        if (prediction == null || !config.targetEnabled) {
            return 0.0;
        }
        if (lastClipAnchor != null && prediction.type == HitResult.Type.BLOCK) {
            return prediction.hitPos.distanceTo(lastClipAnchor.anchor);
        }
        return distanceToTargetBlock(prediction.hitPos);
    }

    private static double distanceToTargetBlock(Vec3d point) {
        BlockPos blockPos = getTargetBlockPos();
        return distanceToBlock(point, blockPos);
    }

    private static double distanceToBlock(Vec3d point, BlockPos blockPos) {
        double minX = blockPos.getX();
        double maxX = blockPos.getX() + 1.0;
        double minY = blockPos.getY();
        double maxY = blockPos.getY() + 1.0;
        double minZ = blockPos.getZ();
        double maxZ = blockPos.getZ() + 1.0;
        double dx = Math.max(Math.max(minX - point.x, 0.0), point.x - maxX);
        double dy = Math.max(Math.max(minY - point.y, 0.0), point.y - maxY);
        double dz = Math.max(Math.max(minZ - point.z, 0.0), point.z - maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static AimSolution findBestAim(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return null;
        }
        Vec3d startPos = getPearlStartPos(player);
        Vec3d shooterVelocity = getPearlStartVelocity(player);
        Vec3d target = getTarget();
        int minAnchorY = MathHelper.floor(player.getBoundingBox().minY + 0.001);

        BlockHitResult directHit = raycastBlock(client, startPos, target);
        BlockHitResult closeHit = findCloseWallHit(client, player, startPos, target);
        boolean closeWall = closeHit != null && closeHit.getSide().getAxis().isHorizontal();
        lastTargetVisible = directHit == null && !closeWall;
        lastClipAnchor = null;

        ClipAnchor[] anchors = null;
        if (closeWall) {
            anchors = buildClipAnchors(client, closeHit, true, minAnchorY, target, player);
        } else if (directHit != null) {
            anchors = buildClipAnchors(client, directHit, false, minAnchorY, target, player);
        }
        lastAnchors = anchors == null ? new ClipAnchor[0] : anchors;
        if (anchors != null && anchors.length > 0) {
            AimSolution best = null;
            for (ClipAnchor anchor : anchors) {
                AimSolution candidate = findBestForAnchor(client, startPos, shooterVelocity, target, anchor);
                if (candidate == null) {
                    continue;
                }
                if (best == null || candidate.score < best.score) {
                    best = candidate;
                    lastClipAnchor = anchor;
                }
            }
            return best;
        }
        lastAnchors = new ClipAnchor[0];

        Vec3d toTarget = target.subtract(startPos);
        double horiz = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        float baseYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
        float basePitch = (float) MathHelper.clamp(Math.toDegrees(-Math.atan2(toTarget.y, horiz)), -89.0, 89.0);

        float yawMin = baseYaw - config.searchYawRange;
        float yawMax = baseYaw + config.searchYawRange;
        float pitchMin = MathHelper.clamp(basePitch - config.searchPitchRange, -89.0f, 89.0f);
        float pitchMax = MathHelper.clamp(basePitch + config.searchPitchRange, -89.0f, 89.0f);
        float step = Math.max(0.05f, config.searchStep);

        AimSolution best = null;
        for (float yaw = yawMin; yaw <= yawMax; yaw += step) {
            for (float pitch = pitchMin; pitch <= pitchMax; pitch += step) {
                PearlNavigatorPrediction.Result result = PearlNavigatorPrediction.simulate(client, startPos, shooterVelocity, yaw, pitch,
                    Math.max(40, config.predictionSteps), config.useMultiRay);
                if (result == null) {
                    continue;
                }
                double distance = distanceToTargetBlock(result.hitPos);
                double edgeDistance = edgeDistance(result);
                double score = distance;
                if (result.type == HitResult.Type.BLOCK) {
                    score += edgeDistance * config.autoEdgeWeight;
                }
                if (!lastTargetVisible) {
                    if (result.type == HitResult.Type.MISS) {
                        score += config.autoMissPenaltyHidden;
                    } else if (result.type == HitResult.Type.BLOCK) {
                        score += bottomDistance(result) * config.autoLowBias;
                        if (!isVerticalFace(result)) {
                            score += config.autoWallBias;
                        }
                    }
                }
                if (best == null || score < best.score) {
                    best = new AimSolution(yaw, pitch, distance, score, edgeDistance, result);
                }
            }
        }
        if (best != null && step > 0.12f) {
            float fineStep = Math.max(0.05f, step * 0.25f);
            float fineYawMin = best.yaw - step;
            float fineYawMax = best.yaw + step;
            float finePitchMin = MathHelper.clamp(best.pitch - step, -89.0f, 89.0f);
            float finePitchMax = MathHelper.clamp(best.pitch + step, -89.0f, 89.0f);
            for (float yaw = fineYawMin; yaw <= fineYawMax; yaw += fineStep) {
                for (float pitch = finePitchMin; pitch <= finePitchMax; pitch += fineStep) {
                    PearlNavigatorPrediction.Result result = PearlNavigatorPrediction.simulate(client, startPos, shooterVelocity, yaw, pitch,
                        Math.max(40, config.predictionSteps), config.useMultiRay);
                    if (result == null) {
                        continue;
                    }
                    double distance = distanceToTargetBlock(result.hitPos);
                    double edgeDistance = edgeDistance(result);
                    double score = distance;
                    if (result.type == HitResult.Type.BLOCK) {
                        score += edgeDistance * config.autoEdgeWeight;
                    }
                    if (!lastTargetVisible) {
                        if (result.type == HitResult.Type.MISS) {
                            score += config.autoMissPenaltyHidden;
                        } else if (result.type == HitResult.Type.BLOCK) {
                            score += bottomDistance(result) * config.autoLowBias;
                            if (!isVerticalFace(result)) {
                                score += config.autoWallBias;
                            }
                        }
                    }
                    if (score < best.score) {
                        best = new AimSolution(yaw, pitch, distance, score, edgeDistance, result);
                    }
                }
            }
        }
        return best;
    }

    private static void applySmoothedAim(MinecraftClient client, AimSolution aim) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        float currentYaw = player.yaw;
        float currentPitch = player.pitch;

        float nextYaw = approachAngle(currentYaw, aim.yaw, config.aimMaxDelta);
        float nextPitch = approachAngle(currentPitch, aim.pitch, config.aimMaxDelta);
        applyLook(client, nextYaw, nextPitch);
    }

    private static float approachAngle(float current, float target, float maxDelta) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (delta > maxDelta) {
            delta = maxDelta;
        } else if (delta < -maxDelta) {
            delta = -maxDelta;
        }
        return current + delta;
    }

    private static void applyLook(MinecraftClient client, float yaw, float pitch) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        player.yaw = yaw;
        player.pitch = pitch;
        player.headYaw = yaw;
        if (yaw != lastSentYaw || pitch != lastSentPitch || player.isOnGround() != lastSentOnGround) {
            player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookOnly(yaw, pitch, player.isOnGround()));
            lastSentYaw = yaw;
            lastSentPitch = pitch;
            lastSentOnGround = player.isOnGround();
        }
    }

    private static boolean isHoldingPearl(ClientPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        return mainHand.getItem() == Items.ENDER_PEARL || offHand.getItem() == Items.ENDER_PEARL;
    }

    private static void ensurePearlSelected(ClientPlayerEntity player) {
        if (isHoldingPearl(player)) {
            return;
        }
        PlayerInventory inventory = player.inventory;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.getItem() == Items.ENDER_PEARL) {
                if (inventory.selectedSlot != slot) {
                    inventory.selectedSlot = slot;
                    lastAutoSwitchTick = player.age;
                }
                break;
            }
        }
    }

    private static void attemptAutoThrow(MinecraftClient client, ClientPlayerEntity player) {
        if (!config.autoThrow || cachedBestAim == null || currentPrediction == null) {
            return;
        }
        boolean anchorReady = false;
        if (lastClipAnchor != null && currentPrediction.type == HitResult.Type.BLOCK) {
            double anchorDist = currentPrediction.hitPos.distanceTo(lastClipAnchor.anchor);
            double allowDist = Math.max(0.1, config.anchorTolerance);
            anchorReady = anchorDist <= allowDist;
        }
        if (isDeepInsideWall(player)) {
            return;
        }
        if (!anchorReady && !isThrowPositionReady(player)) {
            return;
        }
        double threshold = config.autoThrowDistance + (lastTargetVisible ? 0.0 : config.autoHiddenBonus);
        if (currentDistance > threshold && !anchorReady) {
            return;
        }
        if (!isHoldingPearl(player)) {
            return;
        }
        if (player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL)) {
            return;
        }
        if (client.interactionManager == null) {
            return;
        }
        long time = player.age;
        if (time - lastAutoSwitchTick < 2) {
            return;
        }
        if (time - lastAutoThrowTick < config.autoThrowCooldownTicks) {
            return;
        }
        if (config.autoMove) {
            player.setSprinting(true);
        }
        Hand hand = player.getOffHandStack().getItem() == Items.ENDER_PEARL ? Hand.OFF_HAND : Hand.MAIN_HAND;
        client.interactionManager.interactItem(player, client.world, hand);
        player.swingHand(hand);
        lastAutoThrowTick = time;
    }

    private static Vec3d getPearlStartPos(ClientPlayerEntity player) {
        return new Vec3d(player.getX(), player.getEyeY() - 0.10000000149011612, player.getZ());
    }

    private static Vec3d getPearlStartVelocity(ClientPlayerEntity player) {
        Vec3d vel = player.getVelocity();
        double y = player.isOnGround() ? 0.0 : vel.y;
        return new Vec3d(vel.x, y, vel.z);
    }

    private static BlockHitResult raycastBlock(MinecraftClient client, Vec3d start, Vec3d end) {
        if (client.world == null || client.player == null) {
            return null;
        }
        HitResult hit = client.world.raycast(new net.minecraft.world.RaycastContext(
            start,
            end,
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            client.player
        ));
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult) {
            return (BlockHitResult) hit;
        }
        return null;
    }

    private static BlockHitResult findCloseWallHit(MinecraftClient client, ClientPlayerEntity player, Vec3d start, Vec3d target) {
        if (client.world == null || player == null) {
            return null;
        }
        Direction fallback = horizontalDirectionTo(start, target);
        if (fallback == null) {
            fallback = horizontalDirectionFromYaw(player.yaw);
        }
        if (fallback != null) {
            BlockPos feet = getFeetBlockPos(player);
            BlockPos front = feet.offset(fallback);
            Direction side = fallback.getOpposite();
            Vec3d hitPos = faceCenter(front, side, start.y);
            if (isSolidBlock(client, front) && hitPos.distanceTo(start) <= config.autoCloseWallDistance + 0.3) {
                return new BlockHitResult(hitPos, side, front, false);
            }
        }

        Vec3d delta = target.subtract(start);
        double length = delta.length();
        if (length < 1.0e-4) {
            return null;
        }
        Vec3d end = start.add(delta.multiply((config.autoCloseWallDistance + 0.2) / length));
        BlockHitResult hit = raycastBlock(client, start, end);
        if (hit != null) {
            return hit;
        }
        if (fallback == null) {
            return null;
        }
        BlockPos base = new BlockPos(start);
        BlockPos front = base.offset(fallback);
        if (!isSolidBlock(client, front)) {
            return null;
        }
        Direction side = fallback.getOpposite();
        Vec3d hitPos = faceCenter(front, side, start.y);
        return new BlockHitResult(hitPos, side, front, false);
    }

    private static Direction horizontalDirectionTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (Math.abs(dx) < 1.0e-4 && Math.abs(dz) < 1.0e-4) {
            return null;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0.0 ? Direction.EAST : Direction.WEST;
        }
        return dz > 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static Direction horizontalDirectionFromYaw(float yaw) {
        double rad = Math.toRadians(yaw);
        double x = -Math.sin(rad);
        double z = Math.cos(rad);
        return horizontalDirectionTo(Vec3d.ZERO, new Vec3d(x, 0.0, z));
    }

    private static BlockPos getFeetBlockPos(ClientPlayerEntity player) {
        return new BlockPos(player.getX(), player.getBoundingBox().minY + 0.01, player.getZ());
    }

    private static Vec3d faceCenter(BlockPos pos, Direction side, double y) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (side == Direction.EAST) {
            x = pos.getX() + 1.0;
        } else if (side == Direction.WEST) {
            x = pos.getX();
        } else if (side == Direction.SOUTH) {
            z = pos.getZ() + 1.0;
        } else if (side == Direction.NORTH) {
            z = pos.getZ();
        }
        return new Vec3d(x, y, z);
    }

    private static boolean isSolidBlock(MinecraftClient client, BlockPos pos) {
        if (client.world == null) {
            return false;
        }
        return !client.world.getBlockState(pos).getCollisionShape(client.world, pos).isEmpty();
    }

    private static ClipAnchor[] buildClipAnchors(MinecraftClient client, BlockHitResult blockHit, boolean closeWall,
                                                 int minAnchorY, Vec3d target, ClientPlayerEntity player) {
        if (blockHit == null) {
            return new ClipAnchor[0];
        }
        BlockPos blockPos = blockHit.getBlockPos();
        Direction side = blockHit.getSide();
        if (!side.getAxis().isHorizontal()) {
            return new ClipAnchor[0];
        }
        List<ClipAnchor> anchors = new ArrayList<>(6);
        Vec3d preferredPos = closeWall ? player.getPos() : target;
        double preferred = preferredLateralCoord(preferredPos, blockPos, side);
        appendAnchors(anchors, blockPos, side, closeWall, preferred);
        BlockPos lowered = pickLowerWallBlock(client, blockPos, minAnchorY);
        if (!lowered.equals(blockPos)) {
            appendAnchors(anchors, lowered, side, closeWall, preferred);
        }
        return anchors.toArray(new ClipAnchor[0]);
    }

    private static void appendAnchors(List<ClipAnchor> anchors, BlockPos blockPos, Direction side,
                                      boolean closeWall, double preferredCoord) {
        double y = blockPos.getY() + 0.01;
        double edge = 0.01;
        if (side == Direction.EAST || side == Direction.WEST) {
            double faceX = blockPos.getX() + (side == Direction.EAST ? 1.0 : 0.0);
            anchors.add(new ClipAnchor(new Vec3d(faceX, y, blockPos.getZ() + preferredCoord), blockPos, side, faceX, closeWall));
            if (!closeWall) {
                anchors.add(new ClipAnchor(new Vec3d(faceX, y, blockPos.getZ() + 0.5), blockPos, side, faceX, closeWall));
                anchors.add(new ClipAnchor(new Vec3d(faceX, y, blockPos.getZ() + edge), blockPos, side, faceX, closeWall));
                anchors.add(new ClipAnchor(new Vec3d(faceX, y, blockPos.getZ() + 1.0 - edge), blockPos, side, faceX, closeWall));
            }
            return;
        }
        double faceZ = blockPos.getZ() + (side == Direction.SOUTH ? 1.0 : 0.0);
        anchors.add(new ClipAnchor(new Vec3d(blockPos.getX() + preferredCoord, y, faceZ), blockPos, side, faceZ, closeWall));
        if (!closeWall) {
            anchors.add(new ClipAnchor(new Vec3d(blockPos.getX() + 0.5, y, faceZ), blockPos, side, faceZ, closeWall));
            anchors.add(new ClipAnchor(new Vec3d(blockPos.getX() + edge, y, faceZ), blockPos, side, faceZ, closeWall));
            anchors.add(new ClipAnchor(new Vec3d(blockPos.getX() + 1.0 - edge, y, faceZ), blockPos, side, faceZ, closeWall));
        }
    }

    private static double preferredLateralCoord(Vec3d pos, BlockPos blockPos, Direction side) {
        double edge = 0.01;
        if (pos == null) {
            return 0.5;
        }
        double coord = side.getAxis() == Direction.Axis.X
            ? pos.z - blockPos.getZ()
            : pos.x - blockPos.getX();
        return MathHelper.clamp(coord, edge, 1.0 - edge);
    }

    private static BlockPos pickLowerWallBlock(MinecraftClient client, BlockPos blockPos, int minAnchorY) {
        if (client.world == null) {
            return blockPos;
        }
        int hitY = blockPos.getY();
        if (minAnchorY >= hitY) {
            return blockPos;
        }
        int startY = Math.max(0, minAnchorY);
        BlockPos.Mutable mutable = new BlockPos.Mutable(blockPos.getX(), startY, blockPos.getZ());
        for (int y = startY; y <= hitY; y++) {
            mutable.setY(y);
            if (isSolidBlock(client, mutable)) {
                return mutable.toImmutable();
            }
        }
        return blockPos;
    }

    private static double edgeDistance(PearlNavigatorPrediction.Result result) {
        if (result.blockPos == null || result.hitPos == null) {
            return 1.0;
        }
        double fx = result.hitPos.x - result.blockPos.getX();
        double fy = result.hitPos.y - result.blockPos.getY();
        double fz = result.hitPos.z - result.blockPos.getZ();
        double dx = Math.min(fx, 1.0 - fx);
        double dy = Math.min(fy, 1.0 - fy);
        double dz = Math.min(fz, 1.0 - fz);
        return Math.min(dx, Math.min(dy, dz));
    }

    private static double bottomDistance(PearlNavigatorPrediction.Result result) {
        if (result.blockPos == null || result.hitPos == null) {
            return 1.0;
        }
        double fy = result.hitPos.y - result.blockPos.getY();
        if (fy < 0.0) {
            return 0.0;
        }
        if (fy > 1.0) {
            return 1.0;
        }
        return fy;
    }

    private static boolean isVerticalFace(PearlNavigatorPrediction.Result result) {
        return result.side != null && result.side.getAxis().isHorizontal();
    }

    private static double clipAlignmentPenalty(PearlNavigatorPrediction.Result result, ClipAnchor anchor) {
        if (result.hitPos == null || anchor == null) {
            return 0.0;
        }
        double penalty = result.hitPos.distanceTo(anchor.anchor);
        penalty += Math.abs(result.hitPos.y - anchor.anchor.y) * config.autoLowBias;
        if (anchor.closeWall) {
            double high = Math.max(0.0, result.hitPos.y - (anchor.anchor.y + 0.02));
            penalty += high * config.autoCloseWallHighPenalty;
        }
        if (anchor.side != null) {
            if (anchor.side == net.minecraft.util.math.Direction.EAST
                || anchor.side == net.minecraft.util.math.Direction.WEST) {
                penalty += Math.abs(result.hitPos.x - anchor.faceCoord) * 4.0;
            } else {
                penalty += Math.abs(result.hitPos.z - anchor.faceCoord) * 4.0;
            }
            if (result.side != null && result.side != anchor.side) {
                penalty += config.autoWallBias * 2.0;
            }
        }
        return penalty;
    }

    private static AimSolution findBestForAnchor(MinecraftClient client, Vec3d startPos, Vec3d shooterVelocity,
                                                 Vec3d target, ClipAnchor anchor) {
        Vec3d toAnchor = anchor.anchor.subtract(startPos);
        double horiz = Math.sqrt(toAnchor.x * toAnchor.x + toAnchor.z * toAnchor.z);
        float baseYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(toAnchor.z, toAnchor.x)) - 90.0);
        float basePitch = (float) MathHelper.clamp(Math.toDegrees(-Math.atan2(toAnchor.y, horiz)), -89.0, 89.0);

        float yawMin = baseYaw - config.searchYawRange;
        float yawMax = baseYaw + config.searchYawRange;
        float pitchMin = MathHelper.clamp(basePitch - config.searchPitchRange, -89.0f, 89.0f);
        float pitchMax = MathHelper.clamp(basePitch + config.searchPitchRange, -89.0f, 89.0f);
        if (anchor.closeWall && config.autoCloseWallPitchLock) {
            pitchMin = Math.max(pitchMin, basePitch);
        }
        float step = Math.max(0.05f, config.searchStep);

        AimSolution best = null;
        for (float yaw = yawMin; yaw <= yawMax; yaw += step) {
            for (float pitch = pitchMin; pitch <= pitchMax; pitch += step) {
                PearlNavigatorPrediction.Result result = PearlNavigatorPrediction.simulate(client, startPos, shooterVelocity, yaw, pitch,
                    Math.max(40, config.predictionSteps), config.useMultiRay);
                if (result == null) {
                    continue;
                }
                double distance = distanceToTargetBlock(result.hitPos);
                double edgeDistance = edgeDistance(result);
                double score = clipAlignmentPenalty(result, anchor) + distance * 0.05;
                if (result.type == HitResult.Type.MISS) {
                    score += config.autoMissPenaltyHidden;
                } else if (result.type == HitResult.Type.BLOCK) {
                    score += edgeDistance * config.autoEdgeWeight;
                }
                if (best == null || score < best.score) {
                    best = new AimSolution(yaw, pitch, distance, score, edgeDistance, result);
                }
            }
        }
        if (best != null && step > 0.12f) {
            float fineStep = Math.max(0.05f, step * 0.25f);
            float fineYawMin = best.yaw - step;
            float fineYawMax = best.yaw + step;
            float finePitchMin = Math.max(pitchMin, MathHelper.clamp(best.pitch - step, -89.0f, 89.0f));
            float finePitchMax = Math.min(pitchMax, MathHelper.clamp(best.pitch + step, -89.0f, 89.0f));
            for (float yaw = fineYawMin; yaw <= fineYawMax; yaw += fineStep) {
                for (float pitch = finePitchMin; pitch <= finePitchMax; pitch += fineStep) {
                    PearlNavigatorPrediction.Result result = PearlNavigatorPrediction.simulate(client, startPos, shooterVelocity, yaw, pitch,
                        Math.max(40, config.predictionSteps), config.useMultiRay);
                    if (result == null) {
                        continue;
                    }
                    double distance = distanceToTargetBlock(result.hitPos);
                    double edgeDistance = edgeDistance(result);
                    double score = clipAlignmentPenalty(result, anchor) + distance * 0.05;
                    if (result.type == HitResult.Type.MISS) {
                        score += config.autoMissPenaltyHidden;
                    } else if (result.type == HitResult.Type.BLOCK) {
                        score += edgeDistance * config.autoEdgeWeight;
                    }
                    if (score < best.score) {
                        best = new AimSolution(yaw, pitch, distance, score, edgeDistance, result);
                    }
                }
            }
        }
        return best;
    }

    static void saveConfig() {
        PearlNavigatorConfig.save(CONFIG_PATH, config);
    }

    static final class AimSolution {
        final float yaw;
        final float pitch;
        final double distance;
        final double score;
        final double edgeDistance;
        final PearlNavigatorPrediction.Result result;

        AimSolution(float yaw, float pitch, double distance, double score, double edgeDistance, PearlNavigatorPrediction.Result result) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
            this.score = score;
            this.edgeDistance = edgeDistance;
            this.result = result;
        }
    }

    private static final class ClipAnchor {
        final Vec3d anchor;
        final BlockPos blockPos;
        final net.minecraft.util.math.Direction side;
        final double faceCoord;
        final boolean closeWall;

        private ClipAnchor(Vec3d anchor, BlockPos blockPos, net.minecraft.util.math.Direction side,
                           double faceCoord, boolean closeWall) {
            this.anchor = anchor;
            this.blockPos = blockPos;
            this.side = side;
            this.faceCoord = faceCoord;
            this.closeWall = closeWall;
        }
    }
}
