package glam.ardor.roleplayers_atlas.mixin;

import folk.sisby.surveyor.structure.JigsawPieceSummary;
import folk.sisby.surveyor.structure.RegionStructureSummary;
import folk.sisby.surveyor.structure.StructurePieceSummary;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructurePieceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes a Surveyor 1.2.1+1.22 bug: readStructurePieceNbt compares
 * NbtCompound#getString (which returns Optional since 1.21.5) against a plain
 * String, so deserialized pieces are never detected as jigsaw pieces and
 * structures (villages, outposts) lose their map tiles after rejoining.
 */
@Mixin(value = RegionStructureSummary.class, remap = false)
public class MixinRegionStructureSummary {
	@Inject(method = "readStructurePieceNbt", at = @At("HEAD"), cancellable = true)
	private static void roleplayers_atlas$fixJigsawPieceDetection(NbtCompound nbt, CallbackInfoReturnable<StructurePieceSummary> cir) {
		String jigsawId = Registries.STRUCTURE_PIECE.getId(StructurePieceType.JIGSAW).toString();
		// Data saved while the detection was broken lacks the element type key –
		// leave those pieces generic instead of crashing the constructor.
		if (nbt.getString("id").map(jigsawId::equals).orElse(false) && JigsawPieceSummary.TYPE_KEYS.keySet().stream().anyMatch(nbt::contains)) {
			cir.setReturnValue(new JigsawPieceSummary(nbt));
		}
	}
}
