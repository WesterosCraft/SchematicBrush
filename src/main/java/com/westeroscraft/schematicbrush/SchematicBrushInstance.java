package com.westeroscraft.schematicbrush;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.PasteBuilder;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

import static com.westeroscraft.schematicbrush.SchematicDef.*;

public class SchematicBrushInstance implements Brush {
  public SchematicSet set;
  public Player player;
  public boolean skipair;
  public boolean replaceall;
  public int yoff;
  public Placement place;

  private SchematicBrush sb;

  public SchematicBrushInstance(SchematicBrush schembrush) {
    sb = schembrush;
  }

	private AffineTransform rotate2D(AffineTransform trans, Rotation rot) {
		return trans.rotateY(rot.ordinal() * 90);
	}

	private AffineTransform flip(AffineTransform trans, Direction dir) {
		return trans.scale(dir.toVector().abs().multiply(-2).add(1, 1, 1));
	}

	private AffineTransform doOffset(AffineTransform trans, BlockVector3 off) {
		return trans.translate(off.multiply(-1));
	}

  @Override
  public void build(EditSession editsession, BlockVector3 pos, Pattern mat, double size) throws MaxChangedBlocksException {
    SchematicDef def = set.getRandomSchematic(); // Pick schematic from set
    if (def == null)
      return;
    LocalSession sess = sb.worldEdit.getSessionManager().get(player);
    int[] minY = new int[1];
    String schfilename = sb.loadSchematicIntoClipboard(player, sess, def.name, minY);
    if (schfilename == null) {
      return;
    }
    ClipboardHolder cliph = null;
    Clipboard clip = null;
    try {
      cliph = sess.getClipboard();
    } catch (EmptyClipboardException e) {
      player.printError(TextComponent.of("Schematic is empty"));
      return;
    }
    AffineTransform trans = new AffineTransform();
    // Get rotation for clipboard
    Rotation rot = def.getRotation();
    if (rot != Rotation.ROT0) {
      trans = rotate2D(trans, rot);
    }
    // Get flip option
    Flip flip = def.getFlip();
    switch (flip) {
    case NS:
      trans = flip(trans, Direction.NORTH);
      break;
    case EW:
      trans = flip(trans, Direction.WEST);
      break;
    default:
      break;
    }
    cliph.setTransform(trans);

    clip = cliph.getClipboard();
    Region region = clip.getRegion();
    BlockVector3 clipOrigin = clip.getOrigin();
    BlockVector3 centerOffset = region.getCenter().toBlockPoint().subtract(clipOrigin);

    // And apply clipboard to edit session
    BlockVector3 ppos;
    if (place == Placement.DROP) {
      ppos = BlockVector3.at(centerOffset.getX(), -def.offset - yoff - minY[0] + 1, centerOffset.getZ());
    } else if (place == Placement.BOTTOM) {
      ppos = BlockVector3.at(centerOffset.getX(), -def.offset - yoff + 1, centerOffset.getZ());
    } else { // Else, default is CENTER (same as clipboard brush
      ppos = BlockVector3.at(centerOffset.getX(), centerOffset.getY() - def.offset - yoff + 1,
          centerOffset.getZ());
    }
    ppos = trans.apply(ppos.toVector3()).toBlockPoint();
    ppos = pos.subtract(ppos);

    if (!replaceall) {
      editsession.setMask(new BlockTypeMask(editsession, BlockTypes.AIR));
    }
    PasteBuilder pb = cliph.createPaste(editsession).to(ppos)
        .ignoreAirBlocks(skipair);
    Operations.completeLegacy(pb.build());
    player.printInfo(TextComponent.of("Applied '" + schfilename + "', flip=" + flip.name() + ", rot="
                                      + rot.deg + ", place=" + place.name()));
  }
}