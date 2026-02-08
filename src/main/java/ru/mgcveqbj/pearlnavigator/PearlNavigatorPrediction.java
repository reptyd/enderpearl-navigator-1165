package ru.mgcveqbj.pearlnavigator;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

final class PearlNavigatorPrediction {
    private static final double PEARL_RADIUS = 0.125;
    private static final Vec3d[] RAY_OFFSETS = new Vec3d[] {
        Vec3d.ZERO,
        new Vec3d(PEARL_RADIUS, 0.0, 0.0),
        new Vec3d(-PEARL_RADIUS, 0.0, 0.0),
        new Vec3d(0.0, PEARL_RADIUS, 0.0),
        new Vec3d(0.0, -PEARL_RADIUS, 0.0),
        new Vec3d(0.0, 0.0, PEARL_RADIUS),
        new Vec3d(0.0, 0.0, -PEARL_RADIUS)
    };
    private static final Vec3d[] SINGLE_OFFSET = new Vec3d[] { Vec3d.ZERO };

    static final class Result {
        final HitResult.Type type;
        final Vec3d hitPos;
        final BlockPos blockPos;
        final Direction side;
        final int ticks;

        Result(HitResult.Type type, Vec3d hitPos, BlockPos blockPos, Direction side, int ticks) {
            this.type = type;
            this.hitPos = hitPos;
            this.blockPos = blockPos;
            this.side = side;
            this.ticks = ticks;
        }
    }

    private PearlNavigatorPrediction() {
    }

    static Result simulate(MinecraftClient client, Vec3d startPos, Vec3d shooterVelocity, float yaw, float pitch,
                           int maxSteps, boolean multiRay) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return null;
        }

        Vec3d pos = startPos;
        Vec3d velocity = initialVelocity(shooterVelocity, yaw, pitch);
        Vec3d[] offsets = multiRay ? RAY_OFFSETS : SINGLE_OFFSET;

        for (int i = 0; i < maxSteps; i++) {
            Vec3d nextPos = pos.add(velocity);
            HitResult bestHit = null;
            double bestDistance = Double.MAX_VALUE;
            for (Vec3d offset : offsets) {
                Vec3d from = pos.add(offset);
                Vec3d to = nextPos.add(offset);
                HitResult hit = client.world.raycast(new RaycastContext(
                    from,
                    to,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
                ));
                if (hit.getType() == HitResult.Type.MISS) {
                    continue;
                }
                double dist = hit.getPos().squaredDistanceTo(from);
                if (dist < bestDistance) {
                    bestHit = hit;
                    bestDistance = dist;
                }
            }
            if (bestHit != null) {
                BlockPos blockPos = null;
                Direction side = null;
                if (bestHit instanceof BlockHitResult) {
                    BlockHitResult blockHit = (BlockHitResult) bestHit;
                    blockPos = blockHit.getBlockPos();
                    side = blockHit.getSide();
                }
                return new Result(bestHit.getType(), bestHit.getPos(), blockPos, side, i + 1);
            }

            pos = nextPos;
            velocity = applyDragAndGravity(client, pos, velocity);
        }

        return new Result(HitResult.Type.MISS, pos, null, null, maxSteps);
    }

    private static Vec3d initialVelocity(Vec3d shooterVelocity, float yaw, float pitch) {
        Vec3d direction = Vec3d.fromPolar(pitch, yaw).normalize();
        return direction.multiply(1.5).add(shooterVelocity);
    }

    private static Vec3d applyDragAndGravity(MinecraftClient client, Vec3d pos, Vec3d velocity) {
        boolean inWater = false;
        if (client.world != null) {
            FluidState fluid = client.world.getFluidState(new BlockPos(pos));
            inWater = fluid != null && fluid.isIn(FluidTags.WATER);
        }
        double drag = inWater ? 0.8 : 0.99;
        Vec3d next = velocity.multiply(drag);
        return next.subtract(0.0, 0.03, 0.0);
    }
}
