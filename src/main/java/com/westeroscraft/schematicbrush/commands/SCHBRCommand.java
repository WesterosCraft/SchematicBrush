package com.westeroscraft.schematicbrush.commands;

import com.westeroscraft.schematicbrush.SchematicBrush;
import com.westeroscraft.schematicbrush.SchematicBrushInstance;
import com.westeroscraft.schematicbrush.SchematicDef;
import com.westeroscraft.schematicbrush.SchematicDef.Placement;
import com.westeroscraft.schematicbrush.SchematicSet;

import java.util.ArrayList;
import java.util.Arrays;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;


public class SCHBRCommand {
	private static SchematicBrush sb;

	/*
	 * Register the /schbr command. Flags and schematic specs are passed as a single greedy
	 * string and parsed in schBr(), so flags may appear in any order:
	 *
	 *   /schbr [-incair] [-replaceall] [-yoff <n>] [-place <center|bottom|drop>] <schematic...|&set>
	 */
	public static void register(SchematicBrush mod, CommandDispatcher<CommandSourceStack> source) {
		sb = mod;
		SchematicSuggestionProvider suggestedSchematics = new SchematicSuggestionProvider(sb, true);
		SchematicBrush.log.info("Register schbr");

		source.register(Commands.literal("schbr")
			.then(Commands.argument("args", StringArgumentType.greedyString()).suggests(suggestedSchematics)
				.executes(ctx -> schBr(StringArgumentType.getString(ctx, "args"), ctx.getSource()))));
	}

	/*
	 * Apply the schembrush to a WorldEdit brush.
	 */
	@SuppressWarnings("static-access")
	public static int schBr(String args, CommandSourceStack source) {
		Actor actor = sb.validateActor(source, "schematicbrush.brush.use");
		if (actor == null) {
			return 1;
		}

		// Parse leading flags in any order, stopping at the first schematic spec
		boolean incair = false;
		boolean replaceall = false;
		int yoff = 0;
		Placement placement = Placement.CENTER;

		String[] toks = args.trim().split("\\s+");
		int i = 0;
		for (; i < toks.length; i++) {
			String tok = toks[i];
			if (tok.equals("-incair")) {
				incair = true;
			} else if (tok.equals("-replaceall")) {
				replaceall = true;
			} else if (tok.equals("-yoff")) {
				if ((i + 1) >= toks.length) {
					actor.printError(TextComponent.of("-yoff requires a numeric value"));
					return 1;
				}
				try {
					yoff = Integer.parseInt(toks[++i]);
				} catch (NumberFormatException nfx) {
					actor.printError(TextComponent.of("Invalid -yoff value: " + toks[i]));
					return 1;
				}
			} else if (tok.equals("-place")) {
				if ((i + 1) >= toks.length) {
					actor.printError(TextComponent.of("-place requires a value (center, bottom, or drop)"));
					return 1;
				}
				String pval = toks[++i].toUpperCase();
				try {
					placement = Placement.valueOf(pval);
				} catch (IllegalArgumentException iax) {
					placement = Placement.CENTER;
					actor.printError(TextComponent.of("Bad place value (" + pval + ") - using CENTER"));
				}
			} else if (tok.startsWith("-")) {
				actor.printError(TextComponent.of("Unknown flag: " + tok));
				return 1;
			} else {
				break; // First non-flag token - start of schematic specs
			}
		}

		// Remaining tokens are the schematic specs / set reference
		if (i >= toks.length) {
			actor.printError(TextComponent.of("No schematic or schematic set specified"));
			return 1;
		}
		String[] schemids = Arrays.copyOfRange(toks, i, toks.length);

		SchematicSet ss = null;

		// Single set ID
		if ((schemids.length == 1) && schemids[0].startsWith("&")) {
			String setid = schemids[0].substring(1);
			ss = sb.sets.get(setid);
			if (ss == null) {
				actor.printError(TextComponent.of("Schematic set '" + setid + "' not found - '&' is only for sets created with /schset; for a single schematic, omit the '&'"));
				return 1;
			}
		}
		// Otherwise, list of schematics
		else {
			ArrayList<SchematicDef> defs = new ArrayList<SchematicDef>();
			for (int j = 0; j < schemids.length; j++) {
				if (schemids[j].startsWith("&")) {
					actor.printError(TextComponent.of("Mixing multiple schemsets with individual schematics is currently unsupported"));
					return 1;
				}
				SchematicDef def = SchematicDef.parseSchematic(schemids[j]);
				if ((def == null) || !sb.validateSchematicDef(actor, def)) {
					actor.printError(TextComponent.of("Invalid schematic definition: " + schemids[j]));
					return 1;
				}
				defs.add(def);
			}
			ss = new SchematicSet(null, null, defs);
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
				brushTool = new BrushTool("schematicbrush.brush.use");
				session.setTool(itemType, brushTool);
			}
			brushTool.setBrush(sbi, "schematicbrush.brush.use");
			actor.printInfo(TextComponent.of("Schematic brush set"));
		} catch (InvalidToolBindException e) {
			actor.printError(TextComponent.of(e.getMessage()));
		}

		return 1;
	}
}
