package glam.ardor.roleplayers_atlas.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;

public class CodecUtil {
	public static <T> Codec<Set<T>> set(Codec<T> codec) {
		return codec.listOf().xmap(HashSet::new, ArrayList::new);
	}

	public static <T extends Enum<T>> Codec<T> ofEnum(Class<T> enumClass) {
		return Codec.STRING.flatXmap(id -> {
			try {
				return DataResult.success(Enum.valueOf(enumClass, id.toUpperCase(Locale.ROOT)));
			} catch (Exception e) {
				return DataResult.error(() -> "Unknown type: " + id);
			}
		}, value -> DataResult.success(value.name()));
	}

	public static <T> MetadataSectionType<T> metadataSerializer(Codec<T> codec, Identifier id) {
		return new MetadataSectionType<>(id.toString(), codec);
	}
}
