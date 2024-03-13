package com.westeroscraft.schematicbrush.commands;

import com.westeroscraft.schematicbrush.SchematicBrush;

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
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
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
