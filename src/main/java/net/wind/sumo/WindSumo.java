package net.wind.sumo;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import java.util.Collections;
public class WindSumo implements ModInitializer{
@SuppressWarnings("null")
@Override
public void onInitialize() {
UseBlockCallback.EVENT.register((player, world, hand, hitResult)->{
if(!world.isClient() && hand == Hand.MAIN_HAND){
if(world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.POLISHED_BLACKSTONE_BUTTON)){
SumoGameManager.onButtonClick((ServerPlayerEntity) player, world.getServer());
return ActionResult.SUCCESS;
  } 
}
return ActionResult.PASS;
});

ServerPlayConnectionEvents.JOIN.register((handler, sender, server)->{
ServerPlayerEntity player = handler.getPlayer();
player.changeGameMode(GameMode.ADVENTURE);
player.teleport(server.getOverworld(), SumoConfig.LOBBY_SPAWN.getX() + 0.5, SumoConfig.LOBBY_SPAWN.getY() + 1.0, SumoConfig.LOBBY_SPAWN.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
});
ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive)->{
newPlayer.changeGameMode(GameMode.ADVENTURE);
newPlayer.teleport(newPlayer.getCommandSource().getServer().getOverworld(), SumoConfig.LOBBY_SPAWN.getX() + 0.5, SumoConfig.LOBBY_SPAWN.getY() + 1.0, SumoConfig.LOBBY_SPAWN.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
});
ServerPlayConnectionEvents.DISCONNECT.register((handler, server)->{
SumoGameManager.onPlayerLeave(handler.getPlayer(), server);
});
ServerTickEvents.END_SERVER_TICK.register(server->{
for(ServerPlayerEntity player : server.getPlayerManager().getPlayerList()){
player.getHungerManager().setFoodLevel(20);
player.getHungerManager().setSaturationLevel(5.0f);
}
SumoGameManager.tick(server);
  });
}
}