package org.teacon.xkdeco.block.loader;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.teacon.xkdeco.block.setting.GlassType;
import org.teacon.xkdeco.block.setting.KBlockComponent;
import org.teacon.xkdeco.block.setting.KBlockSettings;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.kiwi.KiwiModule;

public record KBlockDefinition(
		ConfiguredBlockTemplate template,
		List<Either<KBlockComponent, String>> components,
		Optional<KMaterial> material,
		Optional<GlassType> glassType,
		Optional<KiwiModule.RenderLayer.Layer> renderType,
		Optional<ResourceLocation> shape,
		Optional<ResourceLocation> collisionShape,
		Optional<ResourceLocation> interactionShape,
		boolean sustainsPlant) {
	public static Codec<KBlockDefinition> codec(
			Map<ResourceLocation, KBlockTemplate> templates,
			MapCodec<Optional<KMaterial>> materialCodec) {
		KBlockTemplate defaultTemplate = templates.get(new ResourceLocation("block"));
		Preconditions.checkNotNull(defaultTemplate);
		ConfiguredBlockTemplate defaultConfiguredTemplate = new ConfiguredBlockTemplate(defaultTemplate);
		return RecordCodecBuilder.create(instance -> instance.group(
				ConfiguredBlockTemplate.codec(templates)
						.optionalFieldOf("template", defaultConfiguredTemplate)
						.forGetter(KBlockDefinition::template),
				Codec.either(KBlockComponent.DIRECT_CODEC, Codec.STRING)
						.listOf()
						.optionalFieldOf("components", List.of())
						.forGetter(KBlockDefinition::components),
				materialCodec.forGetter(KBlockDefinition::material),
				LoaderExtraCodecs.GLASS_TYPE_CODEC.optionalFieldOf("glass_type").forGetter(KBlockDefinition::glassType),
				LoaderExtraCodecs.RENDER_TYPE.optionalFieldOf("render_type").forGetter(KBlockDefinition::renderType),
				ResourceLocation.CODEC.optionalFieldOf("shape").forGetter(KBlockDefinition::shape),
				ResourceLocation.CODEC.optionalFieldOf("collision_shape").forGetter(KBlockDefinition::collisionShape),
				ResourceLocation.CODEC.optionalFieldOf("interaction_shape").forGetter(KBlockDefinition::interactionShape),
				Codec.BOOL.optionalFieldOf("sustains_plant", false).forGetter(KBlockDefinition::sustainsPlant)
		).apply(instance, KBlockDefinition::new));
	}

	public Block createBlock() {
		KBlockSettings.Builder builder = KBlockSettings.builder();
		glassType.ifPresent(builder::glassType);
		shape.ifPresent(builder::shape);
		collisionShape.ifPresent(builder::collisionShape);
		interactionShape.ifPresent(builder::interactionShape);
		if (sustainsPlant) {
			builder.sustainsPlant();
		}
		for (Either<KBlockComponent, String> component : components) {
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
		return template.template().createBlock(builder.get(), template.json());
	}
}
