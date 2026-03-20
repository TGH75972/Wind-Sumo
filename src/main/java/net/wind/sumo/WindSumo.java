package net.wind.sumo;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
public class WindSumo implements ModInitializer{
@SuppressWarnings("null")
@Override
public void onInitialize(){
UseBlockCallback.EVENT.register((player, world, hand, hitResult)->{
if(!world.isClient() && hand == Hand.MAIN_HAND){
if(world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.POLISHED_BLACKSTONE_BUTTON)){
SumoGameManager.onButtonClick((ServerPlayerEntity) player, world.getServer());
return ActionResult.SUCCESS;
  }
 }
return ActionResult.PASS;
});
ServerTickEvents.END_SERVER_TICK.register(SumoGameManager::tick);
  }
}