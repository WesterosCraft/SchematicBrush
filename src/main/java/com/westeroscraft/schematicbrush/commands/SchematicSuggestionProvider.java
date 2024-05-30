package com.westeroscraft.schematicbrush.commands;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;

import com.westeroscraft.schematicbrush.SchematicBrush;

public class SchematicSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
    private SchematicBrush sb;
    private boolean prefix;

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

        if (prefix && !remaining.contains("&")) {
            return Suggestions.empty();
        }

        for (String set : sb.sets.keySet()) {
            String candidate = (prefix) ? "&"+set : set;
            if (candidate.startsWith(remaining)) {
                builder.suggest(candidate);
            }
        }

        return builder.buildFuture();
    }
}