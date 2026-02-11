
package bloodborne;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import org.apache.commons.io.IOUtils;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public class MeshSceneLoader extends SimplePreparableReloadListener<MeshSceneRegistry> implements AutoCloseable {
    private static final FileToIdConverter MODEL3D_DEFINITION = new FileToIdConverter("model3d", ".glb");
    private static final int ASSIMP_FLAGS =
//        Assimp.aiProcess_Triangulate |
//        Assimp.aiProcess_JoinIdenticalVertices |
//        Assimp.aiProcess_CalcTangentSpace |
        Assimp.aiProcess_PreTransformVertices |
        Assimp.aiProcess_FlipUVs;
    
    @Override
    protected MeshSceneRegistry prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        Builder<Identifier, MeshScene> builder = ImmutableMap.builder();
        for (Entry<Identifier, Resource> entry : MODEL3D_DEFINITION.listMatchingResources(resourceManager).entrySet()) {
            Identifier identifier = MODEL3D_DEFINITION.fileToId(entry.getKey());
            Resource resource = entry.getValue();
            try (InputStream is = resource.open()) {
                byte[] data = IOUtils.toByteArray(is);
                
                ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
                buffer.order(ByteOrder.nativeOrder());

                buffer.put(data);
                buffer.flip();
                
                Bloodborne.LOGGER.info("Assimp Loading scene {}", identifier.getPath());           
                AIScene aiscene = Assimp.aiImportFileFromMemory(buffer, ASSIMP_FLAGS, ".glb");
                Bloodborne.LOGGER.info("Assimp Loaded scene {}", identifier.getPath());           

                Bloodborne.LOGGER.info("Loading scene {}", identifier.getPath());
                MeshScene scene = new MeshScene();
                scene.setScene(aiscene);
                // TODO: Load textures based on the scene
                scene.setAlbedo(Identifier.fromNamespaceAndPath(Bloodborne.MOD_ID, "textures/model3d/miku.png"));

                Assimp.aiReleaseImport(aiscene);
                MemoryUtil.memFree(buffer);

                builder.put(identifier, scene);
            } catch (IOException e) {
                Bloodborne.LOGGER.error("Failed to load model {}", identifier);
            }
            Bloodborne.LOGGER.info("Loaded scene {}", identifier.getPath());
        }
        MeshSceneRegistry.getInstance().setRegistry(builder.build());
        
        return MeshSceneRegistry.getInstance();
    }

    @Override
    protected void apply(MeshSceneRegistry object, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
        for (var scene : object.values()) {
            scene.upload();
        }           
    }
    

    @Override
    public void close() throws Exception {
    }
}
