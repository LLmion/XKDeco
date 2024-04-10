package snownee.kiwi.customization.place;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import snownee.kiwi.util.VoxelUtil;

public class PlaceDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final PlaceDebugRenderer INSTANCE = new PlaceDebugRenderer();
	private List<SlotRenderInstance> slots = List.of();

	public static PlaceDebugRenderer getInstance() {
		return INSTANCE;
	}

	private double lastUpdateTime = Double.MIN_VALUE;

	@Override
	public void render(PoseStack pPoseStack, MultiBufferSource pBuffer, double pCamX, double pCamY, double pCamZ) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.isPaused()) {
			return;
		}
		double nanos = (double) Util.getNanos();
		if (nanos - this.lastUpdateTime > 1.0E5D) {
			this.lastUpdateTime = nanos;
			Entity entity = mc.gameRenderer.getMainCamera().getEntity();
			Level level = entity.level();
			this.slots = BlockPos.betweenClosedStream(entity.getBoundingBox().inflate(4)).map(BlockPos::immutable).flatMap(pos -> {
				BlockState blockState = level.getBlockState(pos);
				return Direction.stream().flatMap(side -> PlaceSlot.find(blockState, side).stream().map(slot -> {
					return SlotRenderInstance.create(slot, pos, side);
				}));
			}).toList();
		}

		VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.lines());
		for (SlotRenderInstance slot : slots) {
			float r = ((slot.color >> 16) & 0xFF) / 255.0F;
			float g = ((slot.color >> 8) & 0xFF) / 255.0F;
			float b = (slot.color & 0xFF) / 255.0F;
			LevelRenderer.renderVoxelShape(
					pPoseStack,
					vertexconsumer,
					slot.shape,
					slot.pos.getX() - pCamX,
					slot.pos.getY() - pCamY,
					slot.pos.getZ() - pCamZ,
					r,
					g,
					b,
					1.0F,
					true);
		}
	}

	private record SlotRenderInstance(PlaceSlot slot, BlockPos pos, Direction side, VoxelShape shape, int color) {
		private static final VoxelShape SHAPE_DOWN = Block.box(6, -0.5, 6, 10, 0.5, 10);
		private static final VoxelShape[] SHAPES = Direction.stream()
				.map(side -> VoxelUtil.rotate(SHAPE_DOWN, side))
				.toArray(VoxelShape[]::new);

		private static SlotRenderInstance create(PlaceSlot slot, BlockPos pos, Direction side) {
			String tag = slot.primaryTag();
			int color = 0xFFFFFF;
			if (tag.endsWith("side")) {
				color = 0xFFAAAA;
			} else if (tag.endsWith("front") || tag.endsWith("top")) {
				color = 0xAAFFAA;
			} else if (tag.endsWith("back") || tag.endsWith("bottom")) {
				color = 0xAAAAFF;
			}
			return new SlotRenderInstance(slot, pos, side, SHAPES[side.ordinal()], color);
		}
	}
}