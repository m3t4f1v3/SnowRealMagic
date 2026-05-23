package snownee.snow.client;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.snow.SnowRealMagic;
import snownee.snow.block.entity.SnowBlockEntity.Options;
import snownee.snow.client.model.ModelDefinition;

public final class SnowClient {

	public static final Options fallbackOptions = new Options();
	public static BakedModel cachedSnowModel;
	public static BakedModel cachedOverlayModel;

	public static final ResourceLocation OVERLAY_MODEL = new ResourceLocation(SnowRealMagic.MODID, "block/overlay");

	public static final Map<ResourceLocation, ModelDefinition> snowVariantMapping = Maps.newLinkedHashMap();
	public static final Set<Block> overrideBlocks = Sets.newHashSet();

}