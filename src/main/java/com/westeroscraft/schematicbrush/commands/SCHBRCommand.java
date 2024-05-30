package com.westeroscraft.schematicbrush.commands;

import com.westeroscraft.schematicbrush.SchematicBrush;
import com.westeroscraft.schematicbrush.SchematicBrushInstance;
import com.westeroscraft.schematicbrush.SchematicDef;
import com.westeroscraft.schematicbrush.SchematicSet;
import static com.westeroscraft.schematicbrush.SchematicDef.*;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.enginehub.piston.exception.StopExecutionException;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;


public class SCHBRCommand {
	private static SchematicBrush sb;
	
  /*
   * This is super ugly because Mojang's brigadier library doesn't have good support for optional flags.
   * So we need to account for the 16 following possibilities:
   * 
   * <none>
   * -incair
   * -incair -replaceall
   * -incair -replaceall -yoff
   * -incair -replaceall -yoff -place
   * -incair -replaceall -place
   * -incair -yoff
   * -incair -yoff -place
   * -incair -place
   * -replaceall
   * -replaceall -yoff
   * -replaceall -yoff -place
   * -replaceall -place
   * -yoff
   * -yoff -place
   * -place
   */
	public static void register(SchematicBrush mod, CommandDispatcher<CommandSourceStack> source) {
		sb = mod;
    SchematicSuggestionProvider suggestedSchematics = new SchematicSuggestionProvider(sb, true);
		SchematicBrush.log.info("Register schbr");

    source.register(Commands.literal("schbr")
      .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
        .executes(ctx -> schBr(false, false, 0, null,
                               StringArgumentType.getString(ctx, "args"), ctx.getSource())))
      .then(Commands.literal("-incair")
        .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
          .executes(ctx -> schBr(true, false, 0, null,
                                 StringArgumentType.getString(ctx, "args"), ctx.getSource())))
        .then(Commands.literal("-replaceall")
          .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
            .executes(ctx -> schBr(true, true, 0, null,
                                   StringArgumentType.getString(ctx, "args"), ctx.getSource())))
          .then(Commands.literal("-yoff")
            .then(Commands.argument("yoff", IntegerArgumentType.integer())
              .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
                .executes(ctx -> schBr(true, true, IntegerArgumentType.getInteger(ctx, "yoff"), null,
                                       StringArgumentType.getString(ctx, "args"), ctx.getSource())))
              .then(Commands.literal("-place")
                .then(Commands.argument("place", StringArgumentType.word())
                  .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
                    .executes(ctx -> schBr(true, true, IntegerArgumentType.getInteger(ctx, "yoff"), StringArgumentType.getString(ctx, "place"),
                                           StringArgumentType.getString(ctx, "args"), ctx.getSource())))))))
          .then(Commands.literal("-place")
            .then(Commands.argument("place", StringArgumentType.word())
              .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
                .executes(ctx -> schBr(true, true, 0, StringArgumentType.getString(ctx, "place"),
                                       StringArgumentType.getString(ctx, "args"), ctx.getSource()))))))
        .then(Commands.literal("-yoff")
          .then(Commands.argument("yoff", IntegerArgumentType.integer())
            .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
              .executes(ctx -> schBr(true, false, IntegerArgumentType.getInteger(ctx, "yoff"), null,
                                     StringArgumentType.getString(ctx, "args"), ctx.getSource())))
            .then(Commands.literal("-place")
              .then(Commands.argument("place", StringArgumentType.word())
                .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
                  .executes(ctx -> schBr(true, false, IntegerArgumentType.getInteger(ctx, "yoff"), StringArgumentType.getString(ctx, "place"),
                                         StringArgumentType.getString(ctx, "args"), ctx.getSource())))))))
        .then(Commands.literal("-place")
          .then(Commands.argument("place", StringArgumentType.word())
            .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
              .executes(ctx -> schBr(true, false, 0, StringArgumentType.getString(ctx, "place"),
                                     StringArgumentType.getString(ctx, "args"), ctx.getSource()))))))
      .then(Commands.literal("-replaceall")
        .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
          .executes(ctx -> schBr(false, true, 0, null,
                                 StringArgumentType.getString(ctx, "args"), ctx.getSource())))
        .then(Commands.literal("-yoff")
          .then(Commands.argument("yoff", IntegerArgumentType.integer())
            .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
              .executes(ctx -> schBr(false, true, IntegerArgumentType.getInteger(ctx, "yoff"), null,
                                     StringArgumentType.getString(ctx, "args"), ctx.getSource())))
            .then(Commands.literal("-place")
              .then(Commands.argument("place", StringArgumentType.word())
                .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
                  .executes(ctx -> schBr(false, true, IntegerArgumentType.getInteger(ctx, "yoff"), StringArgumentType.getString(ctx, "place"),
                                         StringArgumentType.getString(ctx, "args"), ctx.getSource())))))))
        .then(Commands.literal("-place")
          .then(Commands.argument("place", StringArgumentType.word())
            .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
              .executes(ctx -> schBr(false, true, 0, StringArgumentType.getString(ctx, "place"),
                                     StringArgumentType.getString(ctx, "args"), ctx.getSource()))))))
      .then(Commands.literal("-yoff")
        .then(Commands.argument("yoff", IntegerArgumentType.integer())
          .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
            .executes(ctx -> schBr(false, false, IntegerArgumentType.getInteger(ctx, "yoff"), null,
                                   StringArgumentType.getString(ctx, "args"), ctx.getSource())))
          .then(Commands.literal("-place")
            .then(Commands.argument("place", StringArgumentType.word())
              .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
                .executes(ctx -> schBr(false, false, IntegerArgumentType.getInteger(ctx, "yoff"), StringArgumentType.getString(ctx, "place"),
                                       StringArgumentType.getString(ctx, "args"), ctx.getSource())))))))
      .then(Commands.literal("-place")
        .then(Commands.argument("place", StringArgumentType.word())
          .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
            .executes(ctx -> schBr(false, false, 0, StringArgumentType.getString(ctx, "place"),
                                   StringArgumentType.getString(ctx, "args"), ctx.getSource()))))));
	}

  /*
   * Apply the schembrush to a WorldEdit brush.
   */
	public static int schBr(boolean incair, boolean replaceall, int yoff, String place, String args, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.brush.use");
    if (actor != null) {

      String[] schemids = args.split(" ");
      SchematicSet ss = null;

      // Single set ID
      if ((schemids.length == 1) && schemids[0].startsWith("&")) {
        String setid = schemids[0].substring(1);
        ss = sb.sets.get(setid);
        if (ss == null) {
          actor.printError(TextComponent.of("Schematic set '" + setid + "' not found"));
          return 1;
        }
      }
      // Otherwise, list of schematics
      else if (schemids.length >= 1) {
        ArrayList<SchematicDef> defs = new ArrayList<SchematicDef>();
        for (int i = 0; i < schemids.length; i++) {
          if (schemids[i].startsWith("&")) {
            actor.printError(TextComponent.of("Mixing multiple schemsets with individual schematics is currently unsupported"));
            return 1;
          }
          SchematicDef def = SchematicDef.parseSchematic(schemids[i]);
          if ((def == null) || !sb.validateSchematicDef(actor, def)) {
            actor.printError(TextComponent.of("Invalid schematic definition: " + schemids[i]));
            return 1;
          }
          defs.add(def);
        }
        ss = new SchematicSet(null, null, defs);
      }
 
      // Parse placement flag if given
      Placement placement = Placement.CENTER;
      if (place != null) {
        String pval = place.toUpperCase();
        placement = Placement.valueOf(pval);
        if (placement == null) {
          placement = Placement.CENTER;
          actor.printError(TextComponent.of("Bad place value (" + pval + ") - using CENTER"));
        }
      }

      // Connect to WorldEdit session
      LocalSession session = sb.worldEdit.getSessionManager().get(actor);

      // Initialize schematic brush instance
      SchematicBrushInstance sbi = new SchematicBrushInstance(sb);
      sbi.set = ss;
      sbi.player = (Player) actor;
      sbi.skipair = !incair;
      sbi.replaceall = replaceall;
      sbi.yoff = yoff;
      sbi.place = placement;

      // Get brush tool and set to schematic brush
      BrushTool tool;
      try {
        tool = session.getBrushTool(sbi.player.getItemInHand(HandSide.MAIN_HAND).getType());
        tool.setBrush(sbi, "schematicbrush.brush.use");
        actor.printInfo(TextComponent.of("Schematic brush set"));
      } catch (InvalidToolBindException e) {
        actor.printError(TextComponent.of(e.getMessage()));
      }
    }

		return 1;
	}
}
