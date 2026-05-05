package com.westeroscraft.schematicbrush;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.util.io.file.FilenameException;

import com.westeroscraft.schematicbrush.commands.SCHBRCommand;
import com.westeroscraft.schematicbrush.commands.SCHSETCommand;
import com.westeroscraft.schematicbrush.commands.SCHLISTCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class SchematicBrush implements ModInitializer {
	public static final String MOD_ID = "schematicbrush";

	// Directly reference a slf4j logger.
	public static final Logger log = LoggerFactory.getLogger(MOD_ID);

	public static Path modConfigDir;
	public static String modConfigFilename;

	public static ModContainer we;
	public static FabricWorldEdit wep;
	public static WorldEdit worldEdit;

	public static final String SCHEMATIC_EXT = "schem";

	// Schematic tree cache - used during initialization
	private Map<File, List<String>> treecache = new HashMap<File, List<String>>();

	private static final Random rnd = new Random();

	public SchematicBrushConfig config;
	public HashMap<String, SchematicSet> sets = new HashMap<String, SchematicSet>();

	private boolean ticking;
	private int ticks = 0;
	private List<Callable<Boolean>> pending = new ArrayList<Callable<Boolean>>();

	@Override
	public void onInitialize() {
		// Create the config folder
		Path configPath = FabricLoader.getInstance().getConfigDir();
		modConfigDir = configPath.resolve(MOD_ID);
		try {
			Files.createDirectory(modConfigDir);
		} catch (FileAlreadyExistsException e) {
			// Do nothing
		} catch (IOException e) {
			log.error("Failed to create schematicbrush config directory", e);
		}
		modConfigFilename = modConfigDir.resolve("schembrush.json").toString();

		// Register ourselves for server and other game events we are interested in
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// SCHMIGRATECommand.register(this, dispatcher);
			SCHBRCommand.register(this, dispatcher);
			SCHSETCommand.register(this, dispatcher);
			SCHLISTCommand.register(this, dispatcher);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!ticking) return;
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
		});

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Optional<ModContainer> ourMod = FabricLoader.getInstance().getModContainer(MOD_ID);
			log.info("SchematicBrush v" + ourMod.map(mc -> mc.getMetadata().getVersion().toString()).orElse("unknown") + " loaded");

			Optional<ModContainer> worldedit = FabricLoader.getInstance().getModContainer("worldedit");
			if (!worldedit.isPresent()) {
					log.error("WorldEdit not found!!");
				return;
			}
			we = worldedit.get();
			wep = FabricWorldEdit.inst;
			worldEdit = WorldEdit.getInstance();
			log.info("Found worldedit " + we.getMetadata().getVersion());

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
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
		});
	}

	public void addJob(Callable<Boolean> job) {
		pending.add(job);
		ticking = true;
	}

	public File getSchemDirectory() {
		return new File(wep.getWorkingDir().toFile(), WorldEdit.getInstance().getConfiguration().saveDir);
	}

	private static class ConfigNotFoundException extends Exception {
		public ConfigNotFoundException() {
		}
		@SuppressWarnings("unused")
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
				player.printError(TextComponent.of("Invalid filename pattern - " + fname + " - " + x.getMessage()));
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
			player.printError(TextComponent.of("Schematic directory for '" + fname + "' missing"));
			return null;
		}
		String name = resolveName(player, dir, fname, SCHEMATIC_EXT);
		if (name == null) {
			player.printError(TextComponent.of("Schematic '" + fname + "' file not found"));
			return null;
		}
		File f;
		boolean rslt = false;
		Closer closer = Closer.create();
		try {
			f = worldEdit.getSafeOpenFile(null, dir, name, SCHEMATIC_EXT);
			if (!f.exists()) {
				player.printError(TextComponent.of("Schematic '" + name + "' file not found"));
				return null;
			}

			ClipboardFormat fmt = ClipboardFormats.findByFile(f);

			if (fmt == null) {
				player.printError(TextComponent.of("Schematic '" + name + "' format not found"));
				return null;
			}
			if (!fmt.isFormat(f)) {
				player.printError(TextComponent.of("Schematic '" + name + "' is not correct format (" + fmt + ")"));
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
			player.printError(TextComponent.of(e1.getMessage()));
		} catch (IOException e) {
			player.printError(TextComponent.of("Error reading schematic '" + name + "' - " + e.getMessage()));
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
      Actor actor = FabricAdapter.adaptPlayer(player);

			// Test for command access
			if ((permissionGroup != null) && !actor.hasPermission(permissionGroup)) {
        source.sendFailure(Component.literal("You do not have access to this command"));
        return null;
			}

			return actor;

		} else {
			source.sendFailure(Component.literal("Only usable by server player"));
			return null;
		}
	}

	public static void debugLog(String msg) {
		log.info(msg);
	}
}
