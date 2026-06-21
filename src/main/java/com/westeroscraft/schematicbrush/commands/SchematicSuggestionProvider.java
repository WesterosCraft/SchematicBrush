package com.westeroscraft.schematicbrush.commands;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;

import com.westeroscraft.schematicbrush.SchematicBrush;

public class SchematicSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    private SchematicBrush sb;
    private boolean prefix;

    private static final String[] FLAGS = { "-incair", "-replaceall", "-yoff", "-place" };
    private static final Pattern SCHEM_PATTERN = Pattern.compile(".*\\." + SchematicBrush.SCHEMATIC_EXT);
    private static final int SCHEM_SUFFIX_LEN = SchematicBrush.SCHEMATIC_EXT.length() + 1; // ".schem"

    public SchematicSuggestionProvider(SchematicBrush schematicbrush) {
        this(schematicbrush, false);
    }

    public SchematicSuggestionProvider(SchematicBrush schematicbrush, boolean usePrefix) {
        sb = schematicbrush;
        prefix = usePrefix;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();

        // Only complete the final whitespace-separated token of the (possibly greedy) argument
        int lastSpace = remaining.lastIndexOf(' ');
        String token = (lastSpace < 0) ? remaining : remaining.substring(lastSpace + 1);
        SuggestionsBuilder b = (lastSpace < 0) ? builder : builder.createOffset(builder.getStart() + lastSpace + 1);

        // Set-id completion (e.g. /schset delete <setid>): suggest bare set names
        if (!prefix) {
            for (String set : sb.sets.keySet()) {
                if (set.startsWith(token)) {
                    b.suggest(set);
                }
            }
            return b.buildFuture();
        }

        // /schbr completion
        if (token.startsWith("&")) {
            // Schematic set reference
            for (String set : sb.sets.keySet()) {
                String candidate = "&" + set;
                if (candidate.startsWith(token)) {
                    b.suggest(candidate);
                }
            }
        } else if (token.startsWith("-")) {
            // Flags
            for (String flag : FLAGS) {
                if (flag.startsWith(token)) {
                    b.suggest(flag);
                }
            }
        } else {
            // Individual schematic file names (extension stripped)
            File dir = sb.getSchemDirectory();
            if (dir != null) {
                List<String> files = sb.getMatchingFiles(dir, SCHEM_PATTERN);
                for (String fn : files) {
                    String name = fn.substring(0, fn.length() - SCHEM_SUFFIX_LEN);
                    if (name.startsWith(token)) {
                        b.suggest(name);
                    }
                }
            }
        }

        return b.buildFuture();
    }
}
