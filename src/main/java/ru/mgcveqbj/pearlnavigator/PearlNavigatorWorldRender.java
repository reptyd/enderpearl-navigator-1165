package ru.mgcveqbj.pearlnavigator;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class PearlNavigatorWorldRender {
    private PearlNavigatorWorldRender() {
    }

    static void render(WorldRenderContext context) {
        boolean showTarget = PearlNavigator.shouldShowWorldTarget() && PearlNavigator.hasTarget();
        boolean showZones = PearlNavigator.isZoneMode() && PearlNavigator.hasTarget();
        if (!showTarget && !showZones) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();

        if (showTarget) {
            Vec3d target = PearlNavigator.getTarget();
            double r = PearlNavigator.getTolerance();
            Box targetBox = new Box(
                target.x - r,
                target.y - r,
                target.z - r,
                target.x + r,
                target.y + r,
                target.z + r
            ).offset(-camera.x, -camera.y, -camera.z);

            WorldRenderer.drawBox(context.matrixStack(), consumers.getBuffer(RenderLayer.getLines()), targetBox,
                0.2f, 0.9f, 0.9f, 0.8f);
        }

        if (showZones) {
            Vec3d[] anchors = PearlNavigator.getAnchorPoints();
            Vec3d active = PearlNavigator.getActiveAnchor();
            double zone = Math.max(0.06, PearlNavigator.getAnchorTolerance());
            for (Vec3d anchor : anchors) {
                Box anchorBox = new Box(
                    anchor.x - zone,
                    anchor.y - zone,
                    anchor.z - zone,
                    anchor.x + zone,
                    anchor.y + zone,
                    anchor.z + zone
                ).offset(-camera.x, -camera.y, -camera.z);
                boolean isActive = active != null && anchor.squaredDistanceTo(active) < 1.0e-6;
                if (isActive) {
                    WorldRenderer.drawBox(context.matrixStack(), consumers.getBuffer(RenderLayer.getLines()), anchorBox,
                        0.2f, 0.95f, 0.3f, 0.9f);
                } else {
                    WorldRenderer.drawBox(context.matrixStack(), consumers.getBuffer(RenderLayer.getLines()), anchorBox,
                        0.9f, 0.6f, 0.2f, 0.7f);
                }
            }
        }

        PearlNavigatorPrediction.Result prediction = PearlNavigator.getCurrentPrediction();
        if (prediction != null) {
            Vec3d hit = prediction.hitPos;
            Box hitBox = new Box(
                hit.x - 0.15,
                hit.y - 0.15,
                hit.z - 0.15,
                hit.x + 0.15,
                hit.y + 0.15,
                hit.z + 0.15
            ).offset(-camera.x, -camera.y, -camera.z);
            WorldRenderer.drawBox(context.matrixStack(), consumers.getBuffer(RenderLayer.getLines()), hitBox,
                0.9f, 0.4f, 0.2f, 0.9f);
        }

        consumers.draw();
    }
}
