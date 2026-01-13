package com.vladisss.kingdomswar.client.renderer;

import com.vladisss.kingdomswar.KingdomsWarMod;
import com.vladisss.kingdomswar.entity.GuardEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class GuardRenderer extends HumanoidMobRenderer<GuardEntity, HumanoidModel<GuardEntity>> {

    // ✅ Правильный путь: modid, путь/к/текстуре
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(KingdomsWarMod.MODID, "textures/entity/guard.png");

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(GuardEntity entity) {
        return TEXTURE;
    }
}
