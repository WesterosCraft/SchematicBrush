package com.westeroscraft.schematicbrush;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;

/*
 * A named set of schematics.
 */
public class SchematicSet {
  public String name;
  public String desc;
  public List<SchematicDef> schematics;

  private static final Random rnd = new Random();

  public SchematicSet(String n, String d, List<SchematicDef> sch) {
    this.name = n;
    this.desc = (d == null) ? "" : d;
    this.schematics = (sch == null) ? (new ArrayList<SchematicDef>()) : sch;
  }

  public int getTotalWeights() {
    int wt = 0;
    for (SchematicDef sd : schematics) {
      if (sd.weight > 0)
        wt += sd.weight;
    }
    return wt;
  }

  public int getEqualWeightCount() {
    int cnt = 0;
    for (SchematicDef sd : schematics) {
      if (sd.weight <= 0) {
        cnt++;
      }
    }
    return cnt;
  }

  public SchematicDef getRandomSchematic() {
    int total = getTotalWeights();
    int cnt = getEqualWeightCount();
    int rndval = 0;
    // If any fixed weights
    if (total > 0) {
      // If total fixed more than 100, or equal weight count is zero
      if ((total > 100) || (cnt == 0)) {
        rndval = rnd.nextInt(total); // Random from 0 to total-1
      } else {
        rndval = rnd.nextInt(100); // From 0 to 100
      }
      if (rndval < total) { // Fixed weight match
        for (SchematicDef def : schematics) {
          if (def.weight > 0) {
            rndval -= def.weight;
            if (rndval < 0) { // Match?
              return def;
            }
          }
        }
      }
    }
    if (cnt > 0) {
      rndval = rnd.nextInt(cnt); // Pick from equal weight values
      for (SchematicDef def : schematics) {
        if (def.weight < 0) {
          rndval--;
          if (rndval < 0) {
            return def;
          }
        }
      }
    }
    return null;
  }
}