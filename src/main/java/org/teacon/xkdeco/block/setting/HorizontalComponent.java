package org.teacon.xkdeco.block.setting;

import org.teacon.xkdeco.block.loader.KBlockComponents;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public record HorizontalComponent() implements KBlockComponent {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final HorizontalComponent INSTANCE = new HorizontalComponent();

	public static HorizontalComponent getInstance() {
		return INSTANCE;
	}

	@Override
	public Type<?> type() {
		return KBlockComponents.HORIZONTAL.getOrCreate();
	}

	@Override
	public void injectProperties(Block block, StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState registerDefaultState(BlockState state) {
		return state.setValue(FACING, Direction.NORTH);
	}

	@Override
	public BlockState getStateForPlacement(KBlockSettings settings, BlockState state, BlockPlaceContext context) {
		if (settings.customPlacement) {
			return state;
		}
		for (Direction direction : context.getNearestLookingDirections()) {
			if (direction.getAxis().isVertical()) {
				continue;
			}
			BlockState blockstate = state.setValue(FACING, direction.getOpposite());
			if (blockstate.canSurvive(context.getLevel(), context.getClickedPos())) {
				return blockstate;
			}
		}
		return null;
	}

	@Override
	public BlockState rotate(BlockState pState, Rotation pRotation) {
		return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState pState, Mirror pMirror) {
		//noinspection deprecation
		return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
	}
}