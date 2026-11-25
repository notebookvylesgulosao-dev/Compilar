package dev.questbook.mixin;

import dev.questbook.server.QuestServer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Detecta fabricação (bancada, fornalha, cortador de pedra...) por jogadores. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "onCraftByPlayer", at = @At("HEAD"))
	private void questbook$onCraft(World world, PlayerEntity player, int amount, CallbackInfo ci) {
		if (player instanceof ServerPlayerEntity sp && amount > 0) {
			QuestServer.onCraft(sp, (ItemStack) (Object) this, amount);
		}
	}
}
