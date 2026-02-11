package bloodborne;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;

import net.minecraft.resources.Identifier;

public final class MeshSceneRegistry {
	private static MeshSceneRegistry instance;
    private ImmutableMap<Identifier, MeshScene> registry;


    public MeshSceneRegistry() {
        instance = this;
    }
    
    public static MeshSceneRegistry getInstance() {
        return instance;
    }
    
    public void setRegistry(ImmutableMap<Identifier, MeshScene> registry) {
        this.registry = registry;
    }

    public ImmutableCollection<MeshScene> values() {
        return registry.values();
    }
    
    public MeshScene getMesh(Identifier identifier) {
        return registry.get(identifier);
    }
}
