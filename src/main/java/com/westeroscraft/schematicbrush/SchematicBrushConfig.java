package com.westeroscraft.schematicbrush;

import java.util.List;
import java.util.ArrayList;

// Top level container for schembrush.json (populated using GSON)
public class SchematicBrushConfig {
  public List<SchematicSet> schematicsets;

  public SchematicBrushConfig() {
    this.schematicsets = new ArrayList<SchematicSet>();
  }
}
