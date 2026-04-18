package com.westeroscraft.schematicbrush.commands;

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

import com.mojang.brigadier.CommandDispatcher;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.westeroscraft.schematicbrush.SchematicBrush;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;


public class SCHMIGRATECommand {
	private static SchematicBrush sb;

	public static void register(SchematicBrush mod, CommandDispatcher<CommandSourceStack> source) {
		sb = mod;
		SchematicBrush.log.info("Register schmigrate");
		source.register(Commands.literal("schmigrate").executes((ctx) -> {
			return schMigrate(ctx.getSource());
		}));

	}

	private static class SchRecord {
		public String fname;
		int originX, originY, originZ;
		int minX, minY, minZ;
		int maxX, maxY, maxZ;
		public String toString() {
			return String.format("%s: origin=%d:%d:%d, min=%d:%d:%d, max=%d:%d:%d", fname, originX, originY, originZ,
				minX, minY, minZ, maxX, maxY, maxZ);
		}
	};

    private static List<String> getMatchingFiles(File dir, Pattern p) {
        ArrayList<String> matches = new ArrayList<String>();
        getMatchingFiles(matches, dir, p, null);
        return matches;
    }

    private static void buildTree(File dir, List<String> rslt, String path) {
    	File[] lst = dir.listFiles();
    	for (File f : lst) {
            String n = (path == null) ? f.getName() : (path + "/" + f.getName());
    		if (f.isDirectory()) {
    			buildTree(f, rslt, n);
    		}
    		else {
    			rslt.add(n);
    		}
    	}
    }

    private static void getMatchingFiles(List<String> rslt, File dir, final Pattern p, final String path) {
    	List<String> flist = null;
    	if (flist == null) {
    		flist = new ArrayList<String>();
    		buildTree(dir, flist, null);
    	}
    	for (String fn : flist) {
    		if (p.matcher(fn).matches()) {
                rslt.add(fn);
            }
        }
    }

	public static int schMigrate(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer) {
			ServerPlayer player = (ServerPlayer) source.getEntity();
			// Read schapplyall.txt
			File logf = new File("schapplyall.txt");
			if (!logf.canRead()) {
				source.sendFailure(Component.literal("Cannot find schapplyall.txt"));
				return 1;
			}
			List<SchRecord> list = new ArrayList<SchRecord>();
			try (BufferedReader rdr = new BufferedReader(new FileReader(logf))) {
				String line;
				while ((line = rdr.readLine()) != null) {
					int idx = line.indexOf(':');	// Find end of name
					String fname = line.substring(0, idx);
					// Prune extension
					idx = fname.lastIndexOf('.');
					fname = fname.substring(0, idx);
					line = line.substring(idx+1).strip();	// Remainder of line
					String tok[] = line.split(",");	// Split on commas
					if (tok.length >= 3) {
						SchRecord rec = new SchRecord();
						rec.fname = fname;
						String origin[] = tok[0].substring(tok[0].indexOf('=')+1).split(":");
						rec.originX = Integer.parseInt(origin[0]);
						rec.originY = Integer.parseInt(origin[1]);
						rec.originZ = Integer.parseInt(origin[2]);
						String min[] = tok[1].substring(tok[1].indexOf('=')+1).split(":");
						rec.minX = Integer.parseInt(min[0]);
						rec.minY = Integer.parseInt(min[1]);
						rec.minZ = Integer.parseInt(min[2]);
						String max[] = tok[2].substring(tok[2].indexOf('=')+1).split(":");
						rec.maxX = Integer.parseInt(max[0]);
						rec.maxY = Integer.parseInt(max[1]);
						rec.maxZ = Integer.parseInt(max[2]);
						list.add(rec);
						//SchematicBrush.log.info(rec);
					}
				}
				SchematicBrush.log.info(String.format("Loaded %d schematic records", list.size()));

				LocalConfiguration config = SchematicBrush.worldEdit.getConfiguration();
		        File dir = SchematicBrush.worldEdit.getWorkingDirectoryPath(config.saveDir).toFile();
	    		final Pattern p = Pattern.compile(".*\\.schem");
	    		List<String> files = getMatchingFiles(dir, p);
	    		Collections.sort(files);
				SchematicBrush.log.info(String.format("Found %d existing schem files", files.size()));
	    		HashSet<String> fset = new HashSet<String>();
	    		for (String v : files) {
	    			String s = v.substring(0, v.lastIndexOf('.'));
	    			fset.add(s);
	    		}

				ApplyAllJob job = new ApplyAllJob();
				job.recs = list;
				job.existing = fset;
				job.player = player;
				job.level = player.serverLevel();
				job.actor = FabricAdapter.adaptPlayer(player);
				job.world = FabricAdapter.adapt(player.serverLevel());
				job.session = SchematicBrush.worldEdit.getSessionManager().get(job.actor);
				job.editSession = job.session.createEditSession(job.actor);
		        job.loadPending();
		        job.pendingf.delete();
				sb.addJob(job);
			} catch (IOException iox) {
				source.sendFailure(Component.literal("Error reading schapplyall.txt"));
				return 1;
			}
			source.sendSuccess(() -> Component.literal("Migrate started"), true);
		} else {
			source.sendFailure(Component.literal("Cannot be used by console"));
		}

		return 1;
	}
	public static class ApplyAllJob implements Callable<Boolean> {
		public World world;
		public ServerLevel level;
		HashSet<String> existing;
		File pendingf = new File("schmigrate.pending");
		File skippedf = new File("schmigrate.skipped");
		List<SchRecord> recs;
		LocalSession session;
		EditSession editSession;
		int idx = 0;
		Actor actor;
		ServerPlayer player;
		boolean doMove = true;

		@SuppressWarnings("static-access")
		@Override
		public Boolean call() throws Exception {
			while (idx < recs.size()) {
				SchRecord rec = recs.get(idx);
				if (existing.contains(rec.fname)) {
					idx++;
				}
				else {
					break;
				}
			}
			if (idx >= recs.size()) {
				editSession.close();
				session.clearHistory();
				pendingf.delete();
	            String msg = "Done!";
	            SchematicBrush.log.info(msg);
	            actor.printInfo(TextComponent.of(msg));
				return Boolean.FALSE;
			}
			SchRecord rec = recs.get(idx);
			if (doMove) {
				// Make sure all chunks in needed volume are loaded
				int minx = (rec.minX & 0xFFFFFFF0);
				int minz = (rec.minZ & 0xFFFFFFF0);
				int maxx = ((rec.maxX + 15) & 0xFFFFFFF0);
				int maxz = ((rec.maxZ + 15) & 0xFFFFFFF0);
				for (int x = minx; x <= maxx; x += 16) {
					for (int z = minz; z <= maxz; z += 16) {
					   @SuppressWarnings("unused")
						 ChunkAccess access = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
					}
				}
				doMove = false;
				return Boolean.TRUE;
			}
			else {
				doMove = true;
				idx++;
			}

			LocalConfiguration config = SchematicBrush.worldEdit.getConfiguration();

	        File dir = SchematicBrush.worldEdit.getWorkingDirectoryPath(config.saveDir).toFile();

	        ClipboardFormat format = ClipboardFormats.findByAlias("schem");
	        if (format == null) {
				actor.printInfo(TextComponent.of("Format error"));
	            return Boolean.FALSE;
	        }

	        File f = sb.worldEdit.getSafeSaveFile(actor, dir, rec.fname, format.getPrimaryFileExtension());

	        // Create parent directories
	        File parent = f.getParentFile();
	        if (parent != null && !parent.exists()) {
	            if (!parent.mkdirs()) {
	                throw new StopExecutionException(TranslatableComponent.of(
	                        "worldedit.schematic.save.failed-directory"));
	            }
	        }

	        //EditSession editSession = session.createEditSession(actor);
			RegionSelector rsel = session.getRegionSelector(world);
			rsel.clear();
			rsel.selectPrimary(BlockVector3.at(rec.minX, rec.minY, rec.minZ), PermissiveSelectorLimits.getInstance());
			rsel.selectSecondary(BlockVector3.at(rec.maxX, rec.maxY, rec.maxZ), PermissiveSelectorLimits.getInstance());
			rsel.learnChanges();
			Region region = rsel.getRegion();
	        BlockArrayClipboard cb = new BlockArrayClipboard(region);
	        cb.setOrigin(BlockVector3.at(rec.originX, rec.originY, rec.originZ));

	        ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, cb, region.getMinimumPoint());
	        copy.setCopyingEntities(false);
	        Operations.completeLegacy(copy);
	        Operations.completeLegacy(cb.commit());
	        boolean empty = true;
	        for (int x = rec.minX; (empty) && (x <= rec.maxX); x++) {
		        for (int y = rec.minY; (empty) && (y <= rec.maxY); y++) {
			        for (int z = rec.minZ; (empty) && (z <= rec.maxZ); z++) {
			        	BlockState bs = cb.getBlock(BlockVector3.at(x,  y, z));
			        	if (!bs.getBlockType().id().equals("minecraft:air")) {
			        		empty = false;
			        	}
			        }
		        }
	        }
	        ClipboardHolder holder = new ClipboardHolder(cb);
	        session.setClipboard(holder);

	        if (!empty) {
	            try (Closer closer = Closer.create()) {
	                FileOutputStream fos = closer.register(new FileOutputStream(f));
	                BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
	                ClipboardWriter writer = closer.register(format.getWriter(bos));
	                writer.write(cb);
	            } catch (IOException e) {
	                f.delete();
					actor.printInfo(TextComponent.of("Schematic save error - " + e.getMessage()));
					return Boolean.FALSE;
	            }
	            String msg = String.format("Wrote %s (%d bytes)", f.getAbsolutePath(), f.length());
	            SchematicBrush.log.info(msg);
	            actor.printInfo(TextComponent.of(msg));
	        }
	        else {
	            String msg = String.format("Empty clipboard for %s - skipped", f.getAbsolutePath());
	            SchematicBrush.log.info(msg);
	            actor.printInfo(TextComponent.of(msg));
	            saveSkipped(rec.fname);
	        }
            savePending();
	        //editSession.close();
            session.clearHistory();
            return Boolean.TRUE;
		}
		private void loadPending() {
			try (BufferedReader br = new BufferedReader(new FileReader(pendingf))) {
			    String line = br.readLine(); // Only one line
			    idx = Integer.parseInt(line.trim());
				SchematicBrush.log.info("Continuing pending schmigrate");
			} catch (IOException iox) {
			}
		}
		private void savePending() {
			try (FileWriter bw = new FileWriter(pendingf)) {
				String line = String.format("%d", idx);
				bw.write(line);
			} catch (IOException iox) {
			}
		}
		private void saveSkipped(String fname) {
			try (FileWriter bw = new FileWriter(skippedf, true)) {
				bw.write(fname + "\n");
			} catch (IOException iox) {
			}
		}
	}
}
