package net.wind.sumo;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.*;
public class SumoGameManager{
private static final List<ServerPlayerEntity> queue = new ArrayList<>();
private static final Map<UUID, List<BlockPos>> playerCages = new HashMap<>();
private static int matchmakingTimer = -1;
private static int countdownTimer = -1;
private static boolean isGameActive = false;
public static void onButtonClick(ServerPlayerEntity player, MinecraftServer server){
if (isGameActive || queue.contains(player) || queue.size() >= 3 || server == null) return;
ServerWorld world = server.getOverworld();
queue.add(player);
int index = queue.size() - 1;
BlockPos spawnPos = switch (index){
case 0 -> SumoConfig.SPAWN_P1;
case 1 -> SumoConfig.SPAWN_P2;
default -> SumoConfig.SPAWN_P3;
};
buildCageForPlayer(player, world, spawnPos);
player.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
if(queue.size() == 1){
matchmakingTimer = 100;
player.sendMessage(Text.literal("§eWaiting for opponent..."), false);
}
else if (queue.size() == 2){
matchmakingTimer = -1;
countdownTimer = 200;
}
else if (queue.size() == 3){
countdownTimer = 200;
 }
}
public static void onPlayerLeave(ServerPlayerEntity player, MinecraftServer server){
if(!queue.contains(player)) return;
removeSpecificCage(player, server.getOverworld());
queue.remove(player);
if(!isGameActive){
if(queue.size() < 2){
broadcastTitleAndSound(server, "§cCancelled!", "§7Not enough players!", SoundEvents.ENTITY_VILLAGER_NO, 1.0f);
resetToLobby(server);
}
else if(queue.size() == 2){
countdownTimer = 200;
broadcastTitleAndSound(server, "§eRestarting", "§7A player left the game...", SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.5f);
}
}
else{
if (queue.size() < 2){
broadcastTitleAndSound(server, "§cGame Ended", "§7Opponent disconnected!", SoundEvents.ENTITY_VILLAGER_NO, 1.0f);
resetToLobby(server);
  }
 }
}
public static void tick(MinecraftServer server){
if(matchmakingTimer > 0){
matchmakingTimer--;
if(matchmakingTimer == 0 && queue.size() < 2){
broadcastTitleAndSound(server, "§cMatch Cancelled", "§7Not enough players!", SoundEvents.ENTITY_VILLAGER_NO, 1.0f);
resetToLobby(server);
  }
}
if(countdownTimer > 0){
if(countdownTimer % 20 == 0){
int seconds = countdownTimer / 20;
String color = seconds <= 3 ? "§c" : "§e";
broadcastTitleAndSound(server, color + seconds, "§7Prepare to fight!", SoundEvents.BLOCK_NOTE_BLOCK_BIT, 1.0f);
}
countdownTimer--;
if(countdownTimer == 0){
broadcastTitleAndSound(server, "§a§lGO!", "§7The match has started!", SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f);
releaseAllPlayers(server);
  }
}

if(isGameActive){
for(ServerPlayerEntity p : queue){
if(p.isOnGround() && p.getInventory().count(Items.WIND_CHARGE) == 0){
p.getInventory().insertStack(new ItemStack(Items.WIND_CHARGE, 30));
  }
 }
}
 }
private static void buildCageForPlayer(ServerPlayerEntity player, ServerWorld world, BlockPos center){
List<BlockPos> blocks = new ArrayList<>();
for(int x = -1; x <= 1; x++){
for(int y = 0; y <= 3; y++){
for(int z = -1; z <= 1; z++){
if (y == 0 || y == 3 || x == -1 || x == 1 || z == -1 || z == 1){
BlockPos bp = center.add(x, y, z);
world.setBlockState(bp, Blocks.GLASS.getDefaultState());
blocks.add(bp);
    }
 }
}
    }
playerCages.put(player.getUuid(), blocks);
}
private static void removeSpecificCage(ServerPlayerEntity player, ServerWorld world){
List<BlockPos> blocks = playerCages.remove(player.getUuid());
if(blocks != null){
for(BlockPos bp : blocks){
world.setBlockState(bp, Blocks.AIR.getDefaultState());
  }
 }
}
private static void releaseAllPlayers(MinecraftServer server){
ServerWorld world = server.getOverworld();
for(List<BlockPos> blocks : playerCages.values()){
for(BlockPos bp : blocks){
world.setBlockState(bp, Blocks.AIR.getDefaultState());
 }
}
playerCages.clear();
for(ServerPlayerEntity p : queue){
p.changeGameMode(GameMode.SURVIVAL);
  }
isGameActive = true;
}
private static void broadcastTitleAndSound(MinecraftServer server, String title, String subtitle, Object soundObj, float pitch) {
SoundEvent sound;
if(soundObj instanceof net.minecraft.registry.entry.RegistryEntry<?> entry){
sound = (SoundEvent) entry.value();
}
else{
sound = (SoundEvent) soundObj;
}
for (ServerPlayerEntity player : queue){
player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 15, 5));
player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));
player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle)));
player.playSound(sound, 1.0f, pitch);
  }
}

public static void resetToLobby(MinecraftServer server){
ServerWorld world = server.getOverworld();
for(ServerPlayerEntity p : new ArrayList<>(queue)){
p.getInventory().clear();
p.changeGameMode(GameMode.ADVENTURE);
p.teleport(world, SumoConfig.LOBBY_SPAWN.getX() + 0.5, SumoConfig.LOBBY_SPAWN.getY() + 1.0, SumoConfig.LOBBY_SPAWN.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
 }
for(List<BlockPos> blocks : playerCages.values()){
for(BlockPos bp : blocks){
world.setBlockState(bp, Blocks.AIR.getDefaultState());
  }
}
playerCages.clear();
queue.clear();
matchmakingTimer = -1;
countdownTimer = -1;
isGameActive = false;
  }
}