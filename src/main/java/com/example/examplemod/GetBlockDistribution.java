package com.github.jprier.getBlockDistribution;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import java.util.Collection;
import java.util.stream.Stream;
import java.util.concurrent.ThreadLocalRandom;


@Mod(GetBlockDistribution.MODID)
public class GetBlockDistribution {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final String MODID = "getblockdistribution";

    private static final String COMMAND = "getblockdist";

    // Data Collection Parameters
    private static final int sampleNumber = 10;
    private static final int playerY = 50;
    private static final int offset = 250;
    private static final int sampleSize = (offset*2)*(offset*2)*playerY*sampleNumber;
    private static final int sample25 = (int) Math.floor((sampleSize*25)/100);
    private static final int sample50 = (int) Math.floor((sampleSize*50)/100);
    private static final int sample75 = (int) Math.floor((sampleSize*75)/100);

    public GetBlockDistribution() {
        LOGGER.info("Registering Block Dist");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void serverStarting(RegisterCommandsEvent event) {
        LOGGER.info("Registering Commands");
         LiteralArgumentBuilder<CommandSource> command = Commands
                 .literal(COMMAND).executes(this::executeCommandForPlayer);
         event.getDispatcher().register(command);
     }

     private int executeCommandForPlayer(CommandContext<CommandSource> ctx) throws CommandSyntaxException {

         ServerPlayerEntity player = ctx.getSource().asPlayer();
         World world = player.world;
         UUID uuid = UUID.randomUUID();
         int totalCount = 0;

        try {

          // Create Directory if doesnt exist -- Doesnt work for some reason
          // new File("/distributionLogs").mkdirs();

          // Notify player command has begun
          sendMessageToPlayer(player, "Looking for a good location...");

          // Get file
          String fileNumber = String.valueOf(ThreadLocalRandom.current().nextInt(0, 10001));
          FileWriter data = new FileWriter("distributionLogs/" + fileNumber + "-data.csv");
          data.write("X, Y, Z, Block, sample, playerX, playerY, playerZ");
          data.write("\n");

          double posX = 0;
          double posY = this.playerY;
          double posZ = 0;

          player.setPositionAndUpdate(posX, posY, posZ);

          String msg;
          int count;
          for (int i = 0; i < this.sampleNumber; i++) {

              posX = posX + ThreadLocalRandom.current().nextInt(-10000, 10001);
              posZ = posZ + ThreadLocalRandom.current().nextInt(-10000, 10001);

              // Teleport player
              player.setPositionAndUpdate(posX, posY, posZ);
              // wait for world to load to avoid overloading server
              try {
                Thread.sleep(1000);
              } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
              }
              // notify player of the teleport
              msg = String.format("For Sample #%d you teleported to (%f, %f, %f)", i, posX, posY, posZ);
              sendMessageToPlayer(player, msg);

              BlockPos corner1 = new BlockPos(posX-this.offset, 0, posZ-this.offset);
              BlockPos corner2 = new BlockPos(posX+this.offset, this.playerY, posZ+this.offset);
              Iterable<BlockPos> blockPosIter = BlockPos.getAllInBox(corner1, corner2)::iterator;

              count = 0;
              for (BlockPos blockPos : blockPosIter) {
                // For each blockPos log the positon, block info, and sample number
                data.write(String.valueOf(blockPos.getX()) + ", " +
                           String.valueOf(blockPos.getY()) + ", " +
                           String.valueOf(blockPos.getZ()) + ", " +
                           world.getBlockState(new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ())).toString() + ", " +
                           String.valueOf(i) + ", " +
                           String.valueOf(posX) + ", " +
                           String.valueOf(posY) + ", " +
                           String.valueOf(posZ) + "\n");
                notifyPlayerOfCompletion(player, count, i);
                count++;
              }
              totalCount = totalCount + count;

              // Notify player that sample is complete
              msg = String.format("Sample #%d complete", i);
              sendMessageToPlayer(player, msg);

              data.write("\n");
          }

          // Notify Player that data collection is complete
          msg = String.format("\n\nData Collection Complete, data in %s-data.csv \nGuessed %d blocks \nCounted %d blocks", fileNumber, this.sampleSize*this.sampleNumber, totalCount);
          sendMessageToPlayer(player, msg);

          data.close();
        } catch (IOException err) {
          LOGGER.error("Failed writing to file");
          err.printStackTrace();
          sendMessageToPlayer(player, "File Failure");
        } catch (Exception err) {
          LOGGER.error("Failed with error");
          err.printStackTrace();
          sendMessageToPlayer(player, "Failure");
        }

        return 0;
    }

    private void notifyPlayerOfCompletion(ServerPlayerEntity player, int count, int sample) {
      if (count == this.sample75) {
        int left = this.sampleSize - count;
        sendMessageToPlayer(player, String.format("Sample #%d is 75%% complete with %d blocks left", sample, left));
      } else if (count == this.sample50) {
        int left = this.sampleSize - count;
        sendMessageToPlayer(player, String.format("Sample #%d is 50%% complete with %d blocks left", sample, left));
      } else if (count == this.sample25) {
        int left = this.sampleSize - count;
        sendMessageToPlayer(player, String.format("Sample #%d is 25%% complete with %d blocks left", sample, left));
      }
    }

    private void sendMessageToPlayer(ServerPlayerEntity player, String msg) {
      player.sendMessage(new StringTextComponent(msg), UUID.randomUUID());
    }
}
