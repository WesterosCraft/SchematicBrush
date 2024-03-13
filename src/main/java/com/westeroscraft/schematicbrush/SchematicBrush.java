package com.westeroscraft.schematicbrush;

import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;

import com.mojang.brigadier.CommandDispatcher;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.ForgePlayer;
import com.sk89q.worldedit.forge.ForgeWorldEdit;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.util.io.file.FilenameException;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

import com.westeroscraft.schematicbrush.commands.SCHMIGRATECommand;
import com.westeroscraft.schematicbrush.commands.SCHBRCommand;
import com.westeroscraft.schematicbrush.commands.SCHSETCommand;
import com.westeroscraft.schematicbrush.commands.SCHLISTCommand;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(SchematicBrush.MOD_ID)
public class SchematicBrush {
	public static final String MOD_ID = "schematicbrush";

	// Directly reference a log4j logger.
	public static final Logger log = LogManager.getLogger();

	// Says where the client and server 'proxy' code is loaded.
	public static Proxy proxy = DistExecutor.safeRunForDist(() -> ClientProxy::new, () -> Proxy::new);

	public static Path modConfigDir;
	public static String modConfigFilename;

	public static ModContainer we;
	public static ForgeWorldEdit wep;
	public static WorldEdit worldEdit;

	public static final String SCHEMATIC_EXT = "schem";

	// Schematic tree cache - used during initialization
	private Map<File, List<String>> treecache = new HashMap<File, List<String>>();

	private static final Random rnd = new Random();

	public SchematicBrushConfig config;
	public HashMap<String, SchematicSet> sets = new HashMap<String, SchematicSet>();
	   
	public SchematicBrush() {
		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);

		// Create the config folder
		Path configPath = FMLPaths.CONFIGDIR.get();
		modConfigDir = Paths.get(configPath.toAbsolutePath().toString(), MOD_ID);
		try {
			Files.createDirectory(modConfigDir);
		} catch (FileAlreadyExistsException e) {
			// Do nothing
		} catch (IOException e) {
			log.error("Failed to create schematicbrush config directory", e);
		}
		modConfigFilename = modConfigDir + "/schembrush.json";
	}

	private boolean ticking;
	private int ticks = 0;
	private List<Callable<Boolean>> pending = new ArrayList<Callable<Boolean>>();
	
	public void addJob(Callable<Boolean> job) {
		pending.add(job);
		ticking = true;
	}
	@SubscribeEvent
	public void countTicks(ServerTickEvent event) {
		if ((!ticking) || (event.phase != TickEvent.Phase.END))
			return;
		
		ticks++;
		if (ticks >= 5) {
			Iterator<Callable<Boolean>> iter = pending.iterator();
			while (iter.hasNext()) {
				Callable<Boolean> r = iter.next();
				Boolean rslt;
				try {
					rslt = r.call();
				} catch (Exception x) {
					rslt = Boolean.FALSE;
				}
				if (!rslt) {
					iter.remove();
				}
			}
			if (pending.size() == 0)
				ticking = false;
			
			ticks = 0;
		}
	}
	
	@SubscribeEvent
	public void onRegisterCommandEvent(RegisterCommandsEvent event) {
		CommandDispatcher<CommandSourceStack> commandDispatcher = event.getDispatcher();
		// SCHMIGRATECommand.register(this, commandDispatcher);
		SCHBRCommand.register(this, commandDispatcher);
		SCHSETCommand.register(this, commandDispatcher);
		SCHLISTCommand.register(this, commandDispatcher);
	}

	@SubscribeEvent
	public void serverStopping(ServerStoppingEvent event) {
		
	}
	
	public static void crash(Exception x, String msg) {
		throw new ReportedException(new CrashReport(msg, x));
	}

	public static void crash(String msg) {
		crash(new Exception(), msg);
	}

	@SubscribeEvent
	public void onServerStartingEvent(ServerStartingEvent event) {
		ModContainer ourMod = ModList.get().getModContainerById(MOD_ID).get();
		log.info("SchematicBrush v" + ourMod.getModInfo().getVersion() + " loaded");

		Optional<? extends ModContainer> worldedit = ModList.get().getModContainerById("worldedit");
		if (!worldedit.isPresent()) {
				log.error("WorldEdit not found!!");
			return;
		}
		we = worldedit.get();
		wep = (ForgeWorldEdit) we.getMod();        
		worldEdit = WorldEdit.getInstance();
		log.info("Found worldedit " + we.getModInfo().getVersion());

		// Load existing schematics
		try {
			config = loadConfig(modConfigFilename);
		} catch (ConfigNotFoundException | JsonSyntaxException | JsonIOException ex) {
			log.warn("schembrush.json missing or could not be read; overwriting with new config.");
			config = new SchematicBrushConfig();
			saveSchematicSets(config, modConfigFilename);
		}
		loadSchematicSets(config);
		log.info("Schemsets initialized");

		// Disable cache
		treecache = null;
	}

	public File getSchemDirectory() {
		return new File(wep.getWorkingDir().toFile(), wep.getPlatform().getConfiguration().saveDir);
	}

	private static class ConfigNotFoundException extends Exception {
		public ConfigNotFoundException() {
		}
		public ConfigNotFoundException(String message) {
			super(message);
		}
	}

	/*
	 * Load schematic sets config from external JSON.
	 */
	private static SchematicBrushConfig loadConfig(String filename) throws ConfigNotFoundException, JsonParseException {
		SchematicBrushConfig config;
		File configFile = new File(filename);
		InputStream in;
		try {
			in = new FileInputStream(configFile);
		} catch (FileNotFoundException iox) {
			in = null;
		}
		if (in == null) {
			throw new ConfigNotFoundException();
		}
		BufferedReader rdr = new BufferedReader(new InputStreamReader(in));
		Gson gson = new Gson();
		try {
			config = gson.fromJson(rdr, SchematicBrushConfig.class);
		} catch (JsonParseException iox) {
			throw iox;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException iox) {
				}
				;
				in = null;
			}
			if (rdr != null) {
				try {
					rdr.close();
				} catch (IOException iox) {
				}
				;
				rdr = null;
			}
		}
		if (config == null) throw new ConfigNotFoundException();
		return config;
	}

	/*
	 * Load and store schematic sets as hash map.
	 */
	private void loadSchematicSets(SchematicBrushConfig config) {
		sets.clear(); // Reset sets

		for (SchematicSet set : config.schematicsets) {
			sets.put(set.name, set);
		}
	}

	/*
	 * Save schematic sets to external JSON.
	 */
	public void saveSchematicSets() {
		saveSchematicSets(config, modConfigFilename);
	}

	private void saveSchematicSets(SchematicBrushConfig config, String filename) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			FileWriter writer = new FileWriter(filename);
			gson.toJson(config, writer);
			writer.flush();
      writer.close();
		} catch (IOException e) {
			log.error("Error writing config to schembrush.json");
			return;
		}
	}

	/*
	 * Update config and schematic set map with new schematic set.
	 */
	public void addSchematicSet(SchematicSet set) {
		config.schematicsets.add(set);
		sets.put(set.name, set);
	}

	/*
	 * Remove schematic set from config and schematic set map.
	 */
	public void removeSchematicSet(SchematicSet set) {
		config.schematicsets.remove(set);
		sets.remove(set.name);
	}

	/*
	 * Recursively build list of files within a given directory tree.
	 */
	private void buildTree(File dir, List<String> rslt, String path) {
		File[] lst = dir.listFiles();
		for (File f : lst) {
			String n = (path == null) ? f.getName() : (path + "/" + f.getName());
			if (f.isDirectory()) {
				buildTree(f, rslt, n);
			} else {
				rslt.add(n);
			}
		}
	}

	/*
	 * Get all files in a given directory matching a regex pattern.
	 */
	public List<String> getMatchingFiles(File dir, Pattern p) {
		ArrayList<String> matches = new ArrayList<String>();
		getMatchingFiles(matches, dir, p, null);
		return matches;
	}

	private void getMatchingFiles(List<String> rslt, File dir, final Pattern p, final String path) {
		List<String> flist = null;

		// See if cached
		if (treecache != null) {
			flist = treecache.get(dir);
		}

		// If not cached or dir not in treecache, recursively find all files in tree
		if (flist == null) {
			flist = new ArrayList<String>();
			buildTree(dir, flist, null);
			if (treecache != null) {
				treecache.put(dir, flist);
			}
		}
		
		// Select all matching files
		for (String fn : flist) {
			if (p.matcher(fn).matches()) {
				rslt.add(fn);
			}
		}
	}

	/*
	 * Resolve name to loadable name - if contains wildcards, pick random matching file.
	 */
	public String resolveName(Actor player, File dir, String fname, final String ext) {
		// If command-line style wildcards
		if ((!fname.startsWith("^")) && ((fname.indexOf('*') >= 0) || (fname.indexOf('?') >= 0))) {
			// Compile to regex
			fname = "^" + fname.replace(".", "\\.").replace("*", ".*").replace("?", ".");
		}
		if (fname.startsWith("^")) { // If marked as regex
			final int extlen = ext.length();
			try {
				final Pattern p = Pattern.compile(fname + "\\." + ext);
				List<String> files = getMatchingFiles(dir, p);
				if (files.isEmpty() == false) { // Multiple choices?
					String n = files.get(rnd.nextInt(files.size()));
					n = n.substring(0, n.length() - extlen - 1);
					return n;
				} else {
					return null;
				}
			} catch (PatternSyntaxException x) {
				player.printError("Invalid filename pattern - " + fname + " - " + x.getMessage());
				return null;
			}
		}
		return fname;
	}

	/*
	 * Validate that a schematic definition corresponds to a valid schematic.
	 */
	public boolean validateSchematicDef(Actor player, SchematicDef def) {
		File dir = getSchemDirectory();
		try {
			String fname = this.resolveName(player, dir, def.name, SCHEMATIC_EXT);
			if (fname == null) {
				return false;
			}
			File f = worldEdit.getSafeOpenFile(null, dir, fname, SCHEMATIC_EXT);
			if (!f.exists()) {
				return false;
			}

			return true;

		} catch (FilenameException fx) {
			return false;
		}
	}

	/*
	 * Load a schematic name from a file into the player's clipboard.
	 */
	public String loadSchematicIntoClipboard(Actor player, LocalSession sess, String fname, int[] bottomY) {
		File dir = getSchemDirectory();
		if (dir == null) {
			player.printError("Schematic directory for '" + fname + "' missing");
			return null;
		}
		String name = resolveName(player, dir, fname, SCHEMATIC_EXT);
		if (name == null) {
			player.printError("Schematic '" + fname + "' file not found");
			return null;
		}
		File f;
		boolean rslt = false;
		Closer closer = Closer.create();
		try {
			f = worldEdit.getSafeOpenFile(null, dir, name, SCHEMATIC_EXT);
			if (!f.exists()) {
				player.printError("Schematic '" + name + "' file not found");
				return null;
			}

			ClipboardFormat fmt = ClipboardFormats.findByFile(f);

			if (fmt == null) {
				player.printError("Schematic '" + name + "' format not found");
				return null;
			}
			if (!fmt.isFormat(f)) {
				player.printError("Schematic '" + name + "' is not correct format (" + fmt + ")");
				return null;
			}
			String filePath = f.getCanonicalPath();
			String dirPath = dir.getCanonicalPath();

			if (!filePath.substring(0, dirPath.length()).equals(dirPath)) {
				return null;
			} else {
				FileInputStream fis = closer.register(new FileInputStream(f));
				BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
				ClipboardReader reader = fmt.getReader(bis);

				Clipboard cc = reader.read();
				if (cc != null) {
					Region reg = cc.getRegion();
					int minY = reg.getHeight() - 1;
					for (int y = 0; (minY == -1) && (y < reg.getHeight()); y++) {
						for (int x = 0; (minY == -1) && (x < reg.getWidth()); x++) {
							for (int z = 0; (minY == -1) && (z < reg.getLength()); z++) {
								if (cc.getBlock(BlockVector3.at(x, y, z)) != null) {
									minY = y;
									break;
								}
							}
						}
					}
					bottomY[0] = minY;
					sess.setClipboard(new ClipboardHolder(cc));
					rslt = true;
				}
			}

		} catch (FilenameException e1) {
			player.printError(e1.getMessage());
		} catch (IOException e) {
			player.printError("Error reading schematic '" + name + "' - " + e.getMessage());
		} finally {
			try {
				closer.close();
			} catch (IOException ignored) {
			}
		}

		return (rslt) ? name : null;
	}

	/* 
	 * Validate that actor is server player and has permissions; otherwise return null.
	 */
	public static Actor validateActor(CommandSourceStack source, String permissionGroup) {
		if (source.getEntity() instanceof ServerPlayer) {
			ServerPlayer player = (ServerPlayer) source.getEntity();
      Actor actor = ForgeAdapter.adaptPlayer(player);

			// Test for command access
			if ((permissionGroup != null) && !actor.hasPermission(permissionGroup)) {
        source.sendFailure(new TextComponent("You do not have access to this command"));
        return null;
			}

			return actor;

		} else {
			source.sendFailure(new TextComponent("Only usable by server player"));
			return null;
		}
	}
	
	public static void debugLog(String msg) {
		log.info(msg);
	}
}
