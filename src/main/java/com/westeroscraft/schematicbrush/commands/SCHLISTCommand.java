package com.westeroscraft.schematicbrush.commands;

import com.westeroscraft.schematicbrush.SchematicBrush;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;


public class SCHLISTCommand {
	private static SchematicBrush sb;

  private static final int LINES_PER_PAGE = 10;

	public static void register(SchematicBrush mod, CommandDispatcher<CommandSourceStack> source) {
		sb = mod;
		SchematicBrush.log.info("Register schlist");
    source.register(Commands.literal("schlist")
      .executes(ctx -> schList(1, ctx.getSource()))
      .then(Commands.argument("page", IntegerArgumentType.integer())
        .executes(ctx -> schList(IntegerArgumentType.getInteger(ctx, "page"), ctx.getSource()))));
	}

	@SuppressWarnings("static-access")
	public static int schList(int page, CommandSourceStack source) {
    Actor actor = sb.validateActor(source, "schematicbrush.list");
    if (actor != null) {

      // Get schematic directory
      File dir = sb.getSchemDirectory();
			if (dir == null) {
        actor.printError(TextComponent.of("Server missing schematic directory"));
				return 1;
			}

      // Get all schematic files
			final Pattern p = Pattern.compile(".*\\." + sb.SCHEMATIC_EXT);
			List<String> files = sb.getMatchingFiles(dir, p);
			Collections.sort(files);
			int cnt = (files.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE; // Number of pages
			if (page > cnt)
				page = cnt;
			if (page < 1)
				page = 1;
			actor.printInfo(TextComponent.of("Page " + page + " of " + cnt + " (" + files.size() + " files)"));
			for (int i = (page - 1) * LINES_PER_PAGE; (i < (page * LINES_PER_PAGE)) && (i < files.size()); i++) {
				actor.printInfo(TextComponent.of(files.get(i)));
			}

    }

		return 1;
	}
}
