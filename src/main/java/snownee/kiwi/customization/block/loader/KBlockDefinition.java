package snownee.kiwi.customization.block.loader;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import snownee.kiwi.customization.block.component.KBlockComponent;
import snownee.kiwi.customization.block.KBlockSettings;
import snownee.kiwi.customization.shape.ChoicesShape;
import snownee.kiwi.customization.shape.DirectionalShape;
import snownee.kiwi.customization.shape.HorizontalShape;
import snownee.kiwi.customization.shape.MouldingShape;
import snownee.kiwi.customization.shape.ShapeGenerator;
import snownee.kiwi.customization.shape.ShapeStorage;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import snownee.kiwi.Kiwi;
import snownee.kiwi.util.VanillaActions;
import snownee.kiwi.util.VoxelUtil;

public record KBlockDefinition(ConfiguredBlockTemplate template, BlockDefinitionProperties properties) {
	public KBlockDefinition(ConfiguredBlockTemplate template, BlockDefinitionProperties properties) {
		this.template = template;
		this.properties = template.template().properties().map(properties::merge).orElse(properties);
	}

	public static Codec<KBlockDefinition> codec(
			Map<ResourceLocation, KBlockTemplate> templates,
			MapCodec<Optional<KMaterial>> materialCodec) {
		KBlockTemplate defaultTemplate = templates.get(new ResourceLocation("block"));
		Preconditions.checkNotNull(defaultTemplate);
		ConfiguredBlockTemplate defaultConfiguredTemplate = new ConfiguredBlockTemplate(defaultTemplate);
		return RecordCodecBuilder.create(instance -> instance.group(
				LoaderExtraCodecs.strictOptionalField(
								ConfiguredBlockTemplate.codec(templates),
								"template",
								defaultConfiguredTemplate)
						.forGetter(KBlockDefinition::template),
				BlockDefinitionProperties.mapCodec(materialCodec).forGetter(KBlockDefinition::properties)
		).apply(instance, KBlockDefinition::new));
	}

	public KBlockSettings.Builder createSettings(ResourceLocation id, ShapeStorage shapes) {
		KBlockSettings.Builder builder = KBlockSettings.builder();
		properties.glassType().ifPresent(builder::glassType);
		BlockDefinitionProperties.PartialVanillaProperties vanilla = properties.vanillaProperties();
		builder.configure($ -> {
			vanilla.lightEmission().ifPresent(i -> $.lightLevel($$ -> i));
			vanilla.pushReaction().ifPresent($::pushReaction);
			vanilla.emissiveRendering().ifPresent($::emissiveRendering);
			vanilla.hasPostProcess().ifPresent($::hasPostProcess);
			vanilla.isRedstoneConductor().ifPresent($::isRedstoneConductor);
			vanilla.isSuffocating().ifPresent($::isSuffocating);
			vanilla.isViewBlocking().ifPresent($::isViewBlocking);
			vanilla.isValidSpawn().ifPresent($::isValidSpawn);
			vanilla.offsetType().ifPresent($::offsetType);
			if (vanilla.noCollision().orElse(false)) {
				$.noCollission();
			}
			if (vanilla.noOcclusion().orElse(false)) {
				$.noOcclusion();
			}
			if (vanilla.isRandomlyTicking().orElse(false)) {
				$.randomTicks();
			}
			if (vanilla.dynamicShape().orElse(false)) {
				$.dynamicShape();
			}
			if (vanilla.replaceable().orElse(false)) {
				$.replaceable();
			}
		});
		properties.material().ifPresent(mat -> {
			builder.configure($ -> {
				$.strength(mat.destroyTime(), mat.explosionResistance());
				$.sound(mat.soundType());
				$.instrument(mat.instrument());
				$.mapColor(mat.defaultMapColor());
				if (mat.ignitedByLava()) {
					$.ignitedByLava();
				}
				if (mat.requiresCorrectToolForDrops()) {
					$.requiresCorrectToolForDrops();
				}
			});
		});
		properties.canSurviveHandler().ifPresent(builder::canSurviveHandler);
		for (Either<KBlockComponent, String> component : properties.components()) {
			if (component.left().isPresent()) {
				builder.component(component.left().get());
			} else {
				String s = component.right().orElseThrow();
				boolean remove = s.startsWith("-");
				if (remove) {
					s = s.substring(1);
				}
				KBlockComponent.Type<?> type = LoaderExtraRegistries.BLOCK_COMPONENT.get(new ResourceLocation(s));
				if (remove) {
					builder.removeComponent(type);
				} else {
					builder.component(KBlockComponents.getSimpleInstance(type));
				}
			}
		}
		deriveAndSet(shapes, builder, properties.shape(), builder::shape);
		deriveAndSet(shapes, builder, properties.collisionShape(), builder::collisionShape);
		deriveAndSet(shapes, builder, properties.interactionShape(), builder::interactionShape);
		return builder;
	}

	public Block createBlock(ResourceLocation id, ShapeStorage shapes) {
		KBlockSettings.Builder builder = createSettings(id, shapes);
		Block block = template.template().createBlock(builder.get(), template.json());
		properties.material().ifPresent(mat -> {
			VanillaActions.setFireInfo(block, mat.igniteOdds(), mat.burnOdds());
		});
		return block;
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void deriveAndSet(
			ShapeStorage shapes,
			KBlockSettings.Builder builder, Optional<ResourceLocation> shapeId,
			Consumer<ShapeGenerator> setter) {
		if (shapeId.isEmpty()) {
			return;
		}
		ShapeGenerator shapeGenerator = shapes.get(shapeId.get());
		if (shapeGenerator == null) {
			Kiwi.LOGGER.warn("Shape {} is not registered", shapeId);
			return;
		}
		if (shapeGenerator.getClass() != ShapeGenerator.Unit.class) {
			setter.accept(shapeGenerator);
		} else if (builder.hasComponent(KBlockComponents.HORIZONTAL.getOrCreate())) {
			setter.accept(HorizontalShape.create(shapeGenerator));
		} else if (builder.hasComponent(KBlockComponents.DIRECTIONAL.getOrCreate())) {
			setter.accept(DirectionalShape.create(shapeGenerator, "facing"));
		} else if (builder.hasComponent(KBlockComponents.MOULDING.getOrCreate())) {
			setter.accept(MouldingShape.create(shapeGenerator));
		} else if (builder.hasComponent(KBlockComponents.FRONT_AND_TOP.getOrCreate())) {
			setter.accept(DirectionalShape.create(shapeGenerator, "orientation"));
		} else if (builder.hasComponent(KBlockComponents.HORIZONTAL_AXIS.getOrCreate())) {
			setter.accept(ChoicesShape.chooseOneProperty(
					BlockStateProperties.HORIZONTAL_AXIS,
					Map.of(
							Direction.Axis.X,
							shapeGenerator,
							Direction.Axis.Z,
							ShapeGenerator.unit(VoxelUtil.rotateHorizontal(
									ShapeGenerator.Unit.unboxOrThrow(shapeGenerator),
									Direction.EAST)))));
		} else {
			setter.accept(shapeGenerator);
		}
	}
}