package com.westeroscraft.schematicbrush;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.regex.Pattern;

import com.google.gson.annotations.SerializedName;

/*
 * A single schematic definition associated with a name, rotation/flip, weight, and offset.
 */
public class SchematicDef {
  public String name;
  public Rotation rotation;
  public Flip flip;
  public int weight; // If -1, equal weight with other -1 schematics
  public int offset;

  public static final int DEFAULT_WEIGHT = -1;

	public static enum Flip {
    @SerializedName("none")
		NONE,
    @SerializedName("ns")
    NS,
    @SerializedName("ew")
    EW,
    @SerializedName("random")
    RANDOM;
	}

	public static enum Rotation {
    @SerializedName("0")
		ROT0(0),
    @SerializedName("90")
    ROT90(90),
    @SerializedName("180")
    ROT180(180),
    @SerializedName("270")
    ROT270(270),
    @SerializedName("random")
    RANDOM(-1);

		final int deg;

		Rotation(int deg) {
			this.deg = deg;
		}
	}

	public static enum Placement {
    @SerializedName("center")
		CENTER,
    @SerializedName("bottom")
    BOTTOM,
    @SerializedName("drop")
    DROP
	}

  private static final Pattern schsplit = Pattern.compile("[@:#^]");
  private static final Random rnd = new Random();

  @Override
  public boolean equals(Object o) {
    if (o instanceof SchematicDef) {
      SchematicDef sd = (SchematicDef) o;
      return sd.name.equals(this.name) && (sd.rotation == this.rotation) && (sd.flip == this.flip)
          && (sd.weight == this.weight) && (sd.offset == this.offset);
    }
    return false;
  }

  @Override
  public String toString() {
    String n = this.name;
    if ((rotation != Rotation.ROT0) || (flip != Flip.NONE)) {
      n += "@";
      if (rotation == Rotation.RANDOM)
        n += '*';
      else
        n += (90 * rotation.ordinal());
    }
    if (flip == Flip.RANDOM) {
      n += '*';
    } else if (flip == Flip.NS) {
      n += 'N';
    } else if (flip == Flip.EW) {
      n += 'E';
    }
    if (weight >= 0) {
      n += ":" + weight;
    }
    if (offset != 0) {
      n += "^" + offset;
    }
    return n;
  }

  public Rotation getRotation() {
    if (rotation == Rotation.RANDOM) {
      return Rotation.values()[rnd.nextInt(4)];
    }
    return rotation;
  }

  public Flip getFlip() {
    if (flip == Flip.RANDOM) {
      return Flip.values()[rnd.nextInt(3)];
    }
    return flip;
  }

  public int getOffset() {
    return offset;
  }

	/*
	 * Parse a schematic string and create a new SchematicDef.
	 */
	public static SchematicDef parseSchematic(String sch) {
		String[] toks = schsplit.split(sch, 0);
		final String name = toks[0]; // Name is first
		Rotation rot = Rotation.ROT0;
		Flip flip = Flip.NONE;
		int wt = DEFAULT_WEIGHT;
		int offset = 0;

		for (int i = 1, off = toks[0].length(); i < toks.length; i++) {
			char sep = sch.charAt(off);
			off = off + 1 + toks[i].length();
			if (sep == '@') { // Rotation/flip?
				String v = toks[i];
				if (v.startsWith("*")) { // random rotate?
					rot = Rotation.RANDOM;
					v = v.substring(1);
				} else { // Else, must be number
					rot = Rotation.ROT0;
					int coff;
					int val = 0;
					for (coff = 0; coff < v.length(); coff++) {
						if (Character.isDigit(v.charAt(coff))) {
							val = (val * 10) + (v.charAt(coff) - '0');
						} else {
							break;
						}
					}
					// If not multiple of 90, error
					if ((val % 90) != 0) {
						return null;
					}
					rot = Rotation.values()[((val / 90) % 4)]; // Clamp to 0-270
					v = v.substring(coff);
				}
				if (v.length() == 0) {
					flip = Flip.NONE;
				} else {
					char c = v.charAt(0);
					switch (c) {
					case '*':
						flip = Flip.RANDOM;
						break;
					case 'N':
					case 'S':
					case 'n':
					case 's':
						flip = Flip.NS;
						break;
					case 'E':
					case 'W':
					case 'e':
					case 'w':
						flip = Flip.EW;
						break;
					default:
						return null;
					}
				}
			} else if (sep == ':') { // weight
				try {
					wt = Integer.parseInt(toks[i]);
				} catch (NumberFormatException nfx) {
					return null;
				}
			} else if (sep == '^') { // Offset
				try {
					offset = Integer.parseInt(toks[i]);
				} catch (NumberFormatException nfx) {
					return null;
				}
			}
		}

    SchematicDef schematic = new SchematicDef();
    schematic.name = name;
    schematic.rotation = rot;
    schematic.flip = flip;
    schematic.weight = wt;
    schematic.offset = offset;
    return schematic;
	}
}