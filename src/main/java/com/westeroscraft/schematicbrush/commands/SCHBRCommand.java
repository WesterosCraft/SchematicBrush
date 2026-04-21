package com.westeroscraft.schematicbrush.commands;

import com.westeroscraft.schematicbrush.SchematicBrush;
import com.westeroscraft.schematicbrush.SchematicBrushInstance;
import com.westeroscraft.schematicbrush.SchematicDef;
import com.westeroscraft.schematicbrush.SchematicDef.Placement;
import com.westeroscraft.schematicbrush.SchematicSet;

import java.util.ArrayList;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;


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
  @SuppressWarnings("static-access")
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
      try {
        var itemType = sbi.player.getItemInHand(HandSide.MAIN_HAND).getType();
        BrushTool brushTool = session.getBrush(itemType);
        if (brushTool == null) {
          brushTool = new BrushTool(sbi, "schematicbrush.brush.use");
          session.setTool(itemType, brushTool);
        } else {
          brushTool.setBrush(sbi, "schematicbrush.brush.use");
        }
        actor.printInfo(TextComponent.of("Schematic brush set"));
      } catch (InvalidToolBindException e) {
        actor.printError(TextComponent.of(e.getMessage()));
      }
    }

		return 1;
	}
}
