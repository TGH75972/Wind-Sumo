package net.wind.sumo;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;
public class SumoGameManager{
private static final List<ServerPlayerEntity> queue = new ArrayList<>();
private static final List<BlockPos> cageBlocks = new ArrayList<>();
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
buildCage(world, spawnPos);
player.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
if(queue.size() == 1){
matchmakingTimer = 100;
player.sendMessage(Text.literal("§eWaiting 5s for an opponent..."), false);
}
else if(queue.size() == 2){
matchmakingTimer = -1;
countdownTimer = 200;
server.getPlayerManager().broadcast(Text.literal("§aGame starting in 10s!"), false);
} 
else if(queue.size() == 3){
countdownTimer = 200;
server.getPlayerManager().broadcast(Text.literal("§63rd player joined! Resetting to 10s..."), false);
  }
}

public static void tick(MinecraftServer server){
if(matchmakingTimer > 0){
matchmakingTimer--;
if(matchmakingTimer == 0 && queue.size() < 2){
server.getPlayerManager().broadcast(Text.literal("§cNo opponent found. Returning to lobby."), false);
resetToLobby(server);
  }
}

if(countdownTimer > 0){
countdownTimer--;
if(countdownTimer == 0){
releasePlayers(server);
 }
}
if (isGameActive){
for(ServerPlayerEntity p : queue){
if(p.isOnGround() && p.getInventory().count(Items.WIND_CHARGE) == 0){
    p.getInventory().insertStack(new ItemStack(Items.WIND_CHARGE, 30));
}
   }
  } 
}
private static void buildCage(ServerWorld world, BlockPos center){
for (int x = -1; x <= 1; x++) {
for (int y = 0; y <= 3; y++) {
for (int z = -1; z <= 1; z++) {
if(y == 0 || y == 3 || x == -1 || x == 1 || z == -1 || z == 1){
BlockPos bp = center.add(x, y, z);
world.setBlockState(bp, Blocks.GLASS.getDefaultState());
cageBlocks.add(bp);
    }
}
 }
  }
}

private static void releasePlayers(MinecraftServer server){
ServerWorld world = server.getOverworld();
for(BlockPos bp : cageBlocks){
world.setBlockState(bp, Blocks.AIR.getDefaultState());
}
cageBlocks.clear();
isGameActive = true;
server.getPlayerManager().broadcast(Text.literal("§6§lGO!"), false);
}
public static void resetToLobby(MinecraftServer server){
ServerWorld world = server.getOverworld();
for (ServerPlayerEntity p : new ArrayList<>(queue)) {
p.getInventory().clear();
p.teleport(world, SumoConfig.LOBBY_SPAWN.getX() + 0.5, SumoConfig.LOBBY_SPAWN.getY() + 1.0, SumoConfig.LOBBY_SPAWN.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
 }
for (BlockPos bp : cageBlocks){
world.setBlockState(bp, Blocks.AIR.getDefaultState());
 }
queue.clear();
cageBlocks.clear();
matchmakingTimer = -1;
countdownTimer = -1;
isGameActive = false;
 }
}