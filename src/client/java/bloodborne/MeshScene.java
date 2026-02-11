package bloodborne;

import java.lang.System.Logger;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryUtil;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;


public class MeshScene implements AutoCloseable {
    public static final RenderPipeline RENDER_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(Bloodborne.MOD_ID, "pipelines/mesh_model"))
                    .withVertexFormat(IrisVertexFormats.TERRAIN, VertexFormat.Mode.TRIANGLES)
                    .build());

    public ByteBuffer vertexBuffer;
    public ByteBuffer indexBuffer;

    public GpuBuffer vertexGpu = null;
    public GpuBuffer indexGpu = null;

    public Identifier albedoId;
    public AbstractTexture albedoTexture;

    private static long putRgba(long addr, float r, float g, float b, float a) {        
        MemoryUtil.memPutByte(addr+0, (byte)(Mth.clamp(r, 0.0f, 1.0f)*255));
        MemoryUtil.memPutByte(addr+1, (byte)(Mth.clamp(g, 0.0f, 1.0f)*255));
        MemoryUtil.memPutByte(addr+2, (byte)(Mth.clamp(b, 0.0f, 1.0f)*255));
        MemoryUtil.memPutByte(addr+3, (byte)(Mth.clamp(a, 0.0f, 1.0f)*255));
        return 4;
    }
    
    private static long putVector3(long addr, float x, float y, float z) {        
        MemoryUtil.memPutFloat(addr+0*Float.BYTES, x);
        MemoryUtil.memPutFloat(addr+1*Float.BYTES, y);
        MemoryUtil.memPutFloat(addr+2*Float.BYTES, z);
        return 3*Float.BYTES;
    }
    
    private static long putVector2(long addr, float x, float y) {        
        MemoryUtil.memPutFloat(addr+0*Float.BYTES, x);
        MemoryUtil.memPutFloat(addr+1*Float.BYTES, y);
        return 2*Float.BYTES;
    }

    private static byte normalIntValue(float f) {
		return (byte)((int)(Mth.clamp(f, -1.0F, 1.0F) * 127.0F) & 0xFF);
	}
    
    private static long putNormal(long addr, float x, float y, float z) {        
        MemoryUtil.memPutByte(addr+0, normalIntValue(x));
        MemoryUtil.memPutByte(addr+1, normalIntValue(y));
        MemoryUtil.memPutByte(addr+2, normalIntValue(z));
        return 3;
    }

    private static long putFloatUV(long addr, float x, float y) {        
        MemoryUtil.memPutFloat(addr+0*Float.BYTES, x);
        MemoryUtil.memPutFloat(addr+1*Float.BYTES, y);
        return 2*Float.BYTES;
    }

    private static long putShortUV(long addr, int x, int y) {
        MemoryUtil.memPutShort(addr+0*Short.BYTES, (short)x);
        MemoryUtil.memPutShort(addr+1*Short.BYTES, (short)y);
        return 2*Short.BYTES;
    }
    
    public void setScene(AIScene scene) {
        this.close();
        long vertexSize = RENDER_PIPELINE.getVertexFormat().getVertexSize();

        if (scene == null || scene.address() == 0L) {
            throw new IllegalArgumentException("AIScene is null/invalid");
        }

        int meshCount = scene.mNumMeshes();
        PointerBuffer meshPtr = scene.mMeshes();

        int totalVertices = 0;
        int totalTriangles = 0;

        for (int mi = 0; mi < meshCount; mi++) {
            long meshAddr = meshPtr.get(mi);
            AIMesh aiMesh = AIMesh.create(meshAddr);
            totalVertices += aiMesh.mNumVertices();
            totalTriangles += aiMesh.mNumFaces();
        }
        
        if (totalVertices == 0 || totalTriangles == 0) {
            Assimp.aiReleaseImport(scene);
            throw new IllegalStateException("No geometry found in AIScene");
        }

        int totalIndices = totalTriangles * 3;

        ByteBuffer vbb = MemoryUtil.memAlloc((int)(totalVertices * vertexSize));
        ByteBuffer ibb = MemoryUtil.memAlloc(totalIndices * Integer.BYTES);

        int vertexBase = 0;

        long baseVertexAddr = MemoryUtil.memAddress(vbb);
        long meshIndexBufferAddress = MemoryUtil.memAddress(ibb);
        
        for (int mi = 0; mi < meshCount; mi++) {
            long meshAddr = meshPtr.get(mi);
            AIMesh aiMesh = AIMesh.create(meshAddr);

            int numVerts = aiMesh.mNumVertices();
            int numFaces = aiMesh.mNumFaces();

            AIVector3D.Buffer verts = aiMesh.mVertices();
            AIVector3D.Buffer norms = aiMesh.mNormals();
            AIVector3D.Buffer tex0 = aiMesh.mTextureCoords(0);

            for (int vi = 0; vi < numVerts; vi++) {
                long addr = baseVertexAddr + ((long) (vertexBase + vi)) * vertexSize;
                long elementAddr = addr;
                AIVector3D pos = verts.get(vi);
                elementAddr += putVector3(elementAddr, pos.x(), pos.y(), pos.z());
                AIVector3D n = norms.get(vi);
                AIVector3D uv = tex0.get(vi);
                elementAddr += putRgba(elementAddr, 1f, 1f, 1f, 1f);
                elementAddr += putFloatUV(elementAddr, uv.x(), uv.y());
                elementAddr += putShortUV(elementAddr, 0, 0);
                elementAddr += putNormal(elementAddr, n.x(), n.y(), n.z());
            }

            AIFace.Buffer faces = aiMesh.mFaces();
            for (int fi = 0; fi < numFaces; fi++) {
                long faceIndexBufferAddress = meshIndexBufferAddress + fi * 3 * Integer.BYTES;
                AIFace face = faces.get(fi);
                int idxCount = face.mNumIndices();
                if (idxCount != 3) {
                    System.err.println("MeshScene: skipping non-triangle face (indices=" + idxCount + ")");
                    continue;
                }
                IntBuffer faceIdx = face.mIndices();
                MemoryUtil.memPutInt(faceIndexBufferAddress+0*Integer.BYTES, vertexBase + faceIdx.get(0));
                MemoryUtil.memPutInt(faceIndexBufferAddress+1*Integer.BYTES, vertexBase + faceIdx.get(1));
                MemoryUtil.memPutInt(faceIndexBufferAddress+2*Integer.BYTES, vertexBase + faceIdx.get(2));
            }
            meshIndexBufferAddress += numFaces * 3 * Integer.BYTES;
            vertexBase += numVerts;
        }

        vbb.position(0);
        vbb.limit((int)(totalVertices * vertexSize));
        ibb.position(0);
        ibb.limit(totalIndices * Integer.BYTES);

        this.vertexBuffer = vbb;
        this.indexBuffer = ibb;
    }

    public void setAlbedo(Identifier id) {
        albedoId = id;
    }
    
    public void upload() {
        if (vertexGpu != null) {
            vertexGpu.close();
            vertexGpu = null;
        }
        if (indexGpu != null) {
            indexGpu.close();
            indexGpu = null;
        }

        if (albedoId != null) {
            albedoTexture = Minecraft.getInstance().getTextureManager().getTexture(albedoId);
            Bloodborne.LOGGER.info("loaded texture {}", albedoId);
        }

        GpuDevice gpuDevice = RenderSystem.getDevice();        
        vertexGpu = gpuDevice.createBuffer(() -> "Immediate vertex buffer for mesh models", 40, vertexBuffer);
        indexGpu = gpuDevice.createBuffer(() -> "Immediate index buffer for mesh models", 72, indexBuffer);
        
    }

    private boolean isReady() {
        if (indexGpu == null) {
            return false;
        }
        if (vertexGpu == null) {
            return false;
        }
        if (indexBuffer == null) {
            return false;
        }
        if (vertexBuffer == null) {
            return false;
        }
        return true;
    }
    
    public void draw(Minecraft client, WorldRenderContext context) {
        if (!isReady()) {
            return;
        }
        
        Vector3f camera = context.worldState().cameraRenderState.pos.toVector3f().negate();
        Matrix4f model = new Matrix4f(RenderSystem.getModelViewMatrix()).translate(new Vector3f(0,100,0)).translate(camera);        
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
			.writeTransform(model, new Vector4f(1,1,1,1), new Vector3f(), new Matrix4f());
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> Bloodborne.MOD_ID + " render pipeline",
                        client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(RENDER_PIPELINE);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            renderPass.setVertexBuffer(0, vertexGpu);
            renderPass.setIndexBuffer(indexGpu, VertexFormat.IndexType.INT);
            if (albedoTexture != null) {
                renderPass.bindTexture("Sampler0", albedoTexture.getTextureView(), albedoTexture.getSampler());
            }
            
            renderPass.drawIndexed(0, 0, indexBuffer.limit()/Integer.BYTES, 1);
        }
    }

    @Override
    public void close() {
        if (vertexGpu != null) {
            vertexGpu.close();
            vertexGpu = null;
        }
        if (indexGpu != null) {
            indexGpu.close();
            indexGpu = null;
        }
        if (vertexBuffer != null && MemoryUtil.memAddress(vertexBuffer) != 0L) {
            MemoryUtil.memFree(vertexBuffer);
            vertexBuffer = null;
        }
        if (indexBuffer != null && MemoryUtil.memAddress(indexBuffer) != 0L) {
            MemoryUtil.memFree(indexBuffer);
            indexBuffer = null;
        }
    }
}
