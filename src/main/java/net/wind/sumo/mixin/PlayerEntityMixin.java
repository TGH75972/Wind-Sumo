package net.wind.sumo.mixin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.wind.sumo.SumoGameManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin{
@Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
private void cancelSumoFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir){
cir.setReturnValue(false);
}
@Inject(method = "damage", at = @At("HEAD"))
private void trackLastAttacker(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir){
Entity attacker = source.getAttacker();
if(attacker instanceof PlayerEntity playerAttacker){
SumoGameManager.setLastAttacker((PlayerEntity)(Object)this, playerAttacker);
  }
 }
}