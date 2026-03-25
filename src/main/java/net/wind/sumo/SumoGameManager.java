package net.wind.sumo;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.scoreboard.*;
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
private static final Map<UUID, Integer> playerKills = new HashMap<>();
private static final Map<UUID, UUID> lastAttacker = new HashMap<>();
private static int matchmakingTimer = -1;
private static int countdownTimer = -1;
private static int gameEndTimer = -1;
private static boolean isGameActive = false;
private static int currentRound = 1;
private static boolean r2IsSlime = false;
private static int shrinkTimer = -1;
private static int currentRadius = 8;
private static int initialPlayerCount = 0;
public static void onButtonClick(ServerPlayerEntity player, MinecraftServer server){
if(isGameActive || currentRound > 1){
player.sendMessage(Text.literal("§cThe round is still active!"), false);
return;
}
if(queue.contains(player) || queue.size() >= 3 || server == null)
return;
ServerWorld world = server.getOverworld();
queue.add(player);
playerKills.put(player.getUuid(), 0);
initialPlayerCount = queue.size();
int index = queue.size() - 1;
BlockPos spawnPos = switch (index)
{ 
case 0->SumoConfig.SPAWN_P1; case 1 -> SumoConfig.SPAWN_P2; default -> SumoConfig.SPAWN_P3; 
  };
buildCageForPlayer(player, world, spawnPos);
player.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
if(queue.size() == 1){ 
matchmakingTimer = 100; 
player.sendMessage(Text.literal("§eWaiting for opponent..."), false); 
}
else{ 
matchmakingTimer = -1; 
countdownTimer = 200; 
 }
}
public static void setLastAttacker(PlayerEntity victim, PlayerEntity attacker){
lastAttacker.put(victim.getUuid(), attacker.getUuid());
}
public static void onPlayerLeave(ServerPlayerEntity player, MinecraftServer server){
if(!queue.contains(player))
return;
removeSpecificCage(player, server.getOverworld());
queue.remove(player);
if(isGameActive){
checkWinCondition(server);
}
else if(countdownTimer > 0){
broadcastTitleAndSound(server, "§c§lNO PLAYERS", "§7Opponent disconnected!", SoundEvents.ENTITY_VILLAGER_NO, 1.0f);
resetToLobby(server);
}
else if(queue.size() < 2){
resetToLobby(server);
  }
}
public static void tick(MinecraftServer server){
if(matchmakingTimer > 0){
matchmakingTimer--;
if (matchmakingTimer == 0 && queue.size() < 2) { 
broadcastTitleAndSound(server, "§c§lNO PLAYERS", "§7No one joined the match.", SoundEvents.ENTITY_VILLAGER_NO, 1.0f);
resetToLobby(server); 
 }
}
if(countdownTimer > 0){
if(countdownTimer % 20 == 0){
int seconds = countdownTimer / 20;
broadcastTitleAndSound(server, (seconds <= 3 ? "§c" : "§e") + seconds, "§7Round " + currentRound + " - Prepare!", SoundEvents.BLOCK_NOTE_BLOCK_BIT, 1.0f);
}
countdownTimer--;
if(countdownTimer == 0){
broadcastTitleAndSound(server, "§a§lGO!", "§7The match has started!", SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f);
releaseAllPlayers(server);
if(currentRound == 3){
shrinkTimer = 100; currentRadius = 8; 
  }
 }
}
if(isGameActive && gameEndTimer == -1){
if(currentRound == 3 && shrinkTimer > 0){
shrinkTimer--;
if(shrinkTimer == 0 && currentRadius > 1){ 
shrinkPlatform(server.getOverworld()); 
currentRadius--; shrinkTimer = 100; 
 }
}

for(ServerPlayerEntity p : new ArrayList<>(queue)){
if(p.interactionManager.getGameMode() != GameMode.SPECTATOR){
boolean inWater = server.getOverworld().getFluidState(p.getBlockPos()).isStill() || p.isSubmergedInWater();
if(inWater){
p.changeGameMode(GameMode.SPECTATOR);
UUID killerUuid = lastAttacker.get(p.getUuid());
if(killerUuid != null){
playerKills.put(killerUuid, playerKills.getOrDefault(killerUuid, 0) + 1);
}
else if(queue.size() == 2){
for(ServerPlayerEntity other : queue){
if(!other.getUuid().equals(p.getUuid())){
playerKills.put(other.getUuid(), playerKills.getOrDefault(other.getUuid(), 0) + 1);
break;    
    }
}
}
lastAttacker.remove(p.getUuid());
sendIndividualTitle(p, "§c§lYOU LOSE", "§7Water is lava!", SoundEvents.ENTITY_WITHER_DEATH, 0.8f);
checkWinCondition(server);
}

if(p.isOnGround() && p.getInventory().count(Items.WIND_CHARGE) < 5){
p.getInventory().insertStack(new ItemStack(Items.WIND_CHARGE, 30));
  }
 }
   }
}
if(gameEndTimer > 0){
gameEndTimer--;
if(gameEndTimer == 0){
handleRoundEnd(server); 
 }
}
if(server.getTicks() % 20 == 0 && (isGameActive || countdownTimer > 0)){
updateScoreboard(server); 
 }
}
private static void handleRoundEnd(MinecraftServer server){
boolean shouldEnd;
if(initialPlayerCount == 3){
shouldEnd = (currentRound >= 3);
}
else{
shouldEnd = (currentRound == 2 && !hasTie()) || currentRound >= 3;
}

if(shouldEnd){
decideFinalWinner(server); 
 } 
else{ 
if(queue.size() < 2){
resetToLobby(server); return; 
}
currentRound++; 
startNextRound(server); 
 }
}

private static boolean hasTie(){
if(queue.size() < 2) 
return false;
return Objects.equals(playerKills.get(queue.get(0).getUuid()), playerKills.get(queue.get(1).getUuid()));
}
private static void startNextRound(MinecraftServer server){
isGameActive = false;
gameEndTimer = -1;
ServerWorld world = server.getOverworld();
setupRoundPlatform(world);
lastAttacker.clear();
for(int i = 0; i < queue.size(); i++){
ServerPlayerEntity p = queue.get(i);
p.getInventory().clear();
p.changeGameMode(GameMode.ADVENTURE);
BlockPos spawnPos = switch(i){
case 0->SumoConfig.SPAWN_P1;
case 1->SumoConfig.SPAWN_P2;
default->SumoConfig.SPAWN_P3; 
};
buildCageForPlayer(p, world, spawnPos);
p.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
 }
countdownTimer = 200;
}
private static void decideFinalWinner(MinecraftServer server){
int maxKills = queue.stream().mapToInt(p->playerKills.getOrDefault(p.getUuid(), 0)).max().orElse(0);
long winnersCount = queue.stream().filter(p -> playerKills.getOrDefault(p.getUuid(), 0) == maxKills).count();
if(winnersCount > 1 && initialPlayerCount == 3){
broadcastTitleAndSound(server, "§6§lTIE", "§7The duel ends in a draw!", SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
}
else{
ServerPlayerEntity winner = queue.stream().max(Comparator.comparingInt(p -> playerKills.getOrDefault(p.getUuid(), 0))).orElse(null);
if(winner != null){
for(ServerPlayerEntity p : queue){
if(p == winner)
sendIndividualTitle(p, "§a§lVICTORY", "§7Match Winner!", SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
else 
sendIndividualTitle(p, "§c§lDEFEAT", "§7Game Over!", SoundEvents.ENTITY_WITHER_DEATH, 0.8f);
  }
   }
}
server.getOverworld().getServer().execute(()->{
new Timer().schedule(new TimerTask(){
@Override
public void run(){
server.execute(()->resetToLobby(server)); }}, 5000);});
}
private static void updateScoreboard(MinecraftServer server){
Scoreboard sb = server.getScoreboard();
ScoreboardObjective obj = sb.getNullableObjective("SumoSide");
if(obj != null) sb.removeObjective(obj);
obj = sb.addObjective("SumoSide", ScoreboardCriterion.DUMMY, Text.literal("§6§lWIND SUMO"), ScoreboardCriterion.RenderType.INTEGER, true, null);
sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, obj);
int line = 10;
sb.getOrCreateScore(ScoreHolder.fromName("§7Round: §f" + currentRound), obj).setScore(line--);
sb.getOrCreateScore(ScoreHolder.fromName("§1 "), obj).setScore(line--);
for(ServerPlayerEntity p : queue){
String status = (p.interactionManager.getGameMode() == GameMode.SPECTATOR) ? "§7[DEAD] " : "§a";
sb.getOrCreateScore(ScoreHolder.fromName(status + p.getName().getString() + ": §e" + playerKills.getOrDefault(p.getUuid(), 0)), obj).setScore(line--);
 }
}
private static void setupRoundPlatform(ServerWorld world){
if(currentRound == 2)
r2IsSlime = new Random().nextBoolean();
var mat = (currentRound == 1) ? Blocks.WHITE_CONCRETE : (currentRound == 2) ? (r2IsSlime ? Blocks.SLIME_BLOCK : Blocks.HONEY_BLOCK) : (r2IsSlime ? Blocks.HONEY_BLOCK : Blocks.SLIME_BLOCK);
for(int x = -8; x <= 8; x++){
for(int z = -8; z <= 8; z++){
BlockPos pos = new BlockPos(x, 63, z);
world.setBlockState(pos, mat.getDefaultState());
if (Math.abs(x) == 8 || Math.abs(z) == 8) {
world.setBlockState(pos.up(), (currentRound == 1) ? Blocks.OAK_FENCE.getDefaultState() : Blocks.AIR.getDefaultState());
  }
 }
}
}
private static void checkWinCondition(MinecraftServer server){
List<ServerPlayerEntity> alive = queue.stream().filter(p -> p.interactionManager.getGameMode() != GameMode.SPECTATOR).toList();
if(alive.size() <= 1 && isGameActive){
if(!alive.isEmpty()){
sendIndividualTitle(alive.get(0), "§a§lROUND WON!", "§7Eliminated opponents!", SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
  }
gameEndTimer = 100;
}
}
public static void resetToLobby(MinecraftServer server){
ServerWorld world = server.getOverworld();
restoreArena(world);
Scoreboard sb = server.getScoreboard();
ScoreboardObjective obj = sb.getNullableObjective("SumoSide");
if (obj != null)
sb.removeObjective(obj);
for(ServerPlayerEntity p : server.getPlayerManager().getPlayerList()){
p.getInventory().clear();
p.changeGameMode(GameMode.ADVENTURE);
p.teleport(world, SumoConfig.LOBBY_SPAWN.getX() + 0.5, SumoConfig.LOBBY_SPAWN.getY() + 1.0, SumoConfig.LOBBY_SPAWN.getZ() + 0.5, Collections.emptySet(), 0.0f, 0.0f, true);
}
queue.clear(); playerCages.clear(); playerKills.clear(); lastAttacker.clear();
isGameActive = false; currentRound = 1; countdownTimer = -1; gameEndTimer = -1; initialPlayerCount = 0;
}
private static void buildCageForPlayer(ServerPlayerEntity player, ServerWorld world, BlockPos center){
List<BlockPos> blocks = new ArrayList<>();
for(int x = -1; x <= 1; x++){
for(int y = 0; y <= 3; y++){
for(int z = -1; z <= 1; z++){
if(y == 0 || y == 3 || x == -1 || x == 1 || z == -1 || z == 1){
BlockPos bp = center.add(x, y, z);
world.setBlockState(bp, Blocks.GLASS.getDefaultState());
blocks.add(bp);
   }
 } 
  }
}
playerCages.put(player.getUuid(), blocks);
}
private static void releaseAllPlayers(MinecraftServer server){
for(List<BlockPos> blocks : playerCages.values()){
for(BlockPos bp : blocks)
server.getOverworld().setBlockState(bp, Blocks.AIR.getDefaultState());
}
playerCages.clear();
for(ServerPlayerEntity p : queue) p.changeGameMode(GameMode.ADVENTURE);
isGameActive = true;
}
private static void restoreArena(ServerWorld world){
for(int x = -8; x <= 8; x++){
for(int z = -8; z <= 8; z++){
world.setBlockState(new BlockPos(x, 63, z), Blocks.WHITE_CONCRETE.getDefaultState());
world.setBlockState(new BlockPos(x, 64, z), (Math.abs(x) == 8 || Math.abs(z) == 8) ? Blocks.OAK_FENCE.getDefaultState() : Blocks.AIR.getDefaultState());
 }
}
}
private static void removeSpecificCage(ServerPlayerEntity player, ServerWorld world){
List<BlockPos> blocks = playerCages.remove(player.getUuid());
if (blocks != null) for (BlockPos bp : blocks)
world.setBlockState(bp, Blocks.AIR.getDefaultState());
}
private static void sendIndividualTitle(ServerPlayerEntity player, String title, String subtitle, SoundEvent sound, float pitch){
player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 40, 10));
player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));
player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle)));
player.playSound(sound, 1.0f, pitch);
}
private static void broadcastTitleAndSound(MinecraftServer server, String title, String subtitle, Object soundObj, float pitch){
SoundEvent sound =(soundObj instanceof net.minecraft.registry.entry.RegistryEntry<?> entry) ? (SoundEvent) entry.value() : (SoundEvent) soundObj;
for(ServerPlayerEntity player : queue){
player.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 15, 5));
player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));
player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle)));
player.playSound(sound, 1.0f, pitch);
 }
}
private static void shrinkPlatform(ServerWorld world){
for(int x = -8; x <= 8; x++){
for(int z = -8; z <= 8; z++){
if(Math.abs(x) == currentRadius || Math.abs(z) == currentRadius){
world.setBlockState(new BlockPos(x, 63, z), Blocks.AIR.getDefaultState());
 }
   }
 }
}
public static void onServerShutdown(MinecraftServer server){
resetToLobby(server); 
}
}