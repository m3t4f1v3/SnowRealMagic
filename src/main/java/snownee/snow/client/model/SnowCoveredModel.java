package snownee.snow.client.model;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import java.util.concurrent.atomic.AtomicBoolean;
import snownee.snow.CoreModule;
import snownee.snow.block.SnowVariant;
import snownee.snow.block.WatcherSnowVariant;
import snownee.snow.block.entity.SnowBlockEntity;
import snownee.snow.client.SnowClientConfig;
import snownee.snow.client.SnowClient;
import snownee.snow.util.ClientProxy;

public class SnowCoveredModel extends BakedModelWrapper<BakedModel> {

	private static final ThreadLocal<RenderContext> CONTEXT = new ThreadLocal<>();

	private static final AtomicBoolean DIAG_LOGGED = new AtomicBoolean(false);

	public SnowCoveredModel(BakedModel model) {
		super(model);
	}

	@Override
	public ModelData getModelData(
			BlockAndTintGetter blockView,
			BlockPos pos,
			BlockState state,
			ModelData modelData) {
		ModelData resolved = super.getModelData(blockView, pos, state, modelData);
		CONTEXT.set(new RenderContext(blockView, pos, state, resolved));
		return resolved;
	}

	@Override
	public List<BakedQuad> getQuads(
			BlockState state,
			@Nullable Direction side,
			RandomSource randomSource,
			ModelData modelData,
			@Nullable RenderType renderType) {
		RenderContext context = CONTEXT.get();
		if (context == null || context.modelData == null || modelData == null || (!modelData.has(SnowBlockEntity.BLOCKSTATE) && !modelData.has(SnowBlockEntity.OPTIONS))) {
			return super.getQuads(state, side, randomSource, modelData, renderType);
		}
		ModelData resolvedData = modelData;
		BlockState camo = resolvedData.has(SnowBlockEntity.BLOCKSTATE) ? resolvedData.get(SnowBlockEntity.BLOCKSTATE) : Blocks.AIR.defaultBlockState();
		SnowBlockEntity.Options options = resolvedData.has(SnowBlockEntity.OPTIONS) ? resolvedData.get(SnowBlockEntity.OPTIONS) : SnowClient.fallbackOptions;
		return buildQuads(context, state, camo, options, side, randomSource, renderType, resolvedData);
	}

	@Override
	public ItemTransforms getTransforms() {
		return ItemTransforms.NO_TRANSFORMS;
	}

	private List<BakedQuad> buildQuads(
			RenderContext context,
			BlockState state,
			BlockState camo,
			SnowBlockEntity.Options options,
			@Nullable Direction side,
			RandomSource randomSource,
			@Nullable RenderType layer,
			ModelData modelData) {
		List<BakedQuad> quads = new ArrayList<>();
		boolean useVariant = false;
		boolean isTileBlock = CoreModule.TILE_BLOCK.is(state);
		boolean full = state.hasProperty(SnowLayerBlock.LAYERS) && state.getValue(SnowLayerBlock.LAYERS) == 8;
		BlockAndTintGetter world = context.world;
		BlockPos pos = context.pos;
		SnowVariant snowVariant = (SnowVariant) state.getBlock();
		if (state.getBlock() instanceof WatcherSnowVariant watcher) {
			watcher.updateOptions(state, world, pos, options);
		}
		if (!full && !camo.isAir() && camo.getRenderShape() == RenderShape.MODEL) {
			BakedModel model = ClientProxy.getBlockModel(camo);
			if (SnowClientConfig.snowVariants && SnowClient.overrideBlocks.contains(camo.getBlock())) {
				useVariant = true;
			}
			double yOffset = camo.is(CoreModule.OFFSET_Y) ? 0.101 : 0;
			appendQuads(quads, model, camo, world, pos, side, randomSource, layer, modelData, yOffset);
		}
		BlockState snow = snowVariant.getSnowState(state, world, pos);
		if (!snow.isAir()) {
			BakedModel model;
			if (snow == Blocks.SNOW.defaultBlockState()) {
				if (SnowClient.cachedSnowModel == null) {
					SnowClient.cachedSnowModel = ClientProxy.getBlockModel(snow);
				}
				model = SnowClient.cachedSnowModel;
			} else {
				model = ClientProxy.getBlockModel(snow);
			}
			double yOffset = CoreModule.SLAB.is(state) ? 0.5 : 0;
			appendQuads(quads, model, snow, world, pos, side, randomSource, layer, modelData, yOffset);
		}
		if (options.renderOverlay && (layer == null || layer == RenderType.cutoutMipped()) && (!useVariant || isTileBlock)) {
			BlockPos pos2 = pos;
			double yOffset;
			BakedModel model;
			if (isTileBlock || CoreModule.SLAB.is(state)) {
				if (SnowClient.cachedOverlayModel == null) {
					SnowClient.cachedOverlayModel = ClientProxy.getBlockModel(SnowClient.OVERLAY_MODEL);
				}
				model = SnowClient.cachedOverlayModel;
				if (CoreModule.SLAB.is(state)) {
					yOffset = -0.375;
				} else {
					yOffset = -1;
					pos2 = pos.below();
				}
			} else {
				yOffset = snowVariant.getYOffset();
				model = ClientProxy.getBlockModel(state);
			}
			if (snowVariant.layers(state, world, pos) == 8) {
				yOffset -= 0.002;
			}
			appendQuads(quads, model, state, world, pos2, side, randomSource, layer, modelData, yOffset);
		}
		return quads;
	}

	private void appendQuads(
			List<BakedQuad> quads,
			BakedModel model,
			BlockState sourceState,
			BlockAndTintGetter world,
			BlockPos pos,
			@Nullable Direction side,
			RandomSource randomSource,
			@Nullable RenderType layer,
			ModelData modelData,
			double yOffset) {
		model = unwrap(model);
		if (layer != null && !model.getRenderTypes(sourceState, randomSource, modelData).contains(layer)) {
			return;
		}
		List<BakedQuad> sourceQuads = model.getQuads(sourceState, side, randomSource, modelData, layer);
		Vec3 offset = sourceState.getOffset(world, pos);
		if (yOffset != 0) {
			offset = offset.add(0, yOffset, 0);
		}
		if (sourceState.is(Blocks.SNOW) && side == Direction.DOWN && yOffset != 0) {
			return;
		}
		for (BakedQuad quad : sourceQuads) {
			quads.add(transformQuad(quad, sourceState, world, pos, offset));
		}
	}

	public static BakedModel unwrap(BakedModel model) {
		if (model instanceof SnowCoveredModel snowCoveredModel) {
			return snowCoveredModel.originalModel;
		}
		return model;
	}

	private BakedQuad transformQuad(BakedQuad quad, BlockState state, BlockAndTintGetter world, BlockPos pos, Vec3 offset) {
		int[] vertices = quad.getVertices().clone();
		boolean translate = offset.x != 0 || offset.y != 0 || offset.z != 0;
		if (translate) {
			for (int vertex = 0; vertex < 4; ++vertex) {
				int base = vertex * IQuadTransformer.STRIDE;
				vertices[base + IQuadTransformer.POSITION] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[base + IQuadTransformer.POSITION]) + (float) offset.x);
				vertices[base + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[base + IQuadTransformer.POSITION + 1]) + (float) offset.y);
				vertices[base + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[base + IQuadTransformer.POSITION + 2]) + (float) offset.z);
			}
		}
		int tintIndex = quad.getTintIndex();
		if (tintIndex != -1) {
			int color = Minecraft.getInstance().getBlockColors().getColor(state, world, pos, tintIndex);
			int abgr = QuadTransformers.toABGR(color);
			for (int vertex = 0; vertex < 4; ++vertex) {
				int base = vertex * IQuadTransformer.STRIDE;
				vertices[base + IQuadTransformer.COLOR] = abgr;
			}
			tintIndex = -1;
		}
		return new BakedQuad(vertices, tintIndex, quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
	}

	private static final class RenderContext {
		private final BlockAndTintGetter world;
		private final BlockPos pos;
		private final BlockState state;
		private final ModelData modelData;

		private RenderContext(BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData modelData) {
			this.world = world;
			this.pos = pos;
			this.state = state;
			this.modelData = modelData;
		}
	}

}
