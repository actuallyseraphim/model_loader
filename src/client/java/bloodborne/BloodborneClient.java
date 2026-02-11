package bloodborne;

import com.mojang.authlib.minecraft.client.MinecraftClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public class BloodborneClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new MeshSceneRegistry();
        ResourceLoader
            .get(PackType.CLIENT_RESOURCES)
            .registerReloader(Identifier.fromNamespaceAndPath(Bloodborne.MOD_ID, "model3d"),
                              new MeshSceneLoader());
        
		WorldRenderEvents.BEFORE_ENTITIES
            .register(this::renderMonke);
        
        IrisPipelines.assignPipeline(MeshScene.RENDER_PIPELINE, ShaderKey.TERRAIN_CUTOUT);
	}
	private void renderMonke(WorldRenderContext context) {
        MeshScene monke = MeshSceneRegistry.getInstance()
            .getMesh(Identifier.fromNamespaceAndPath(Bloodborne.MOD_ID, "scene"));
        monke.draw(Minecraft.getInstance(), context);
	}
}
