/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.client.model;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import net.dries007.tfc.util.Helpers;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.ElementsModel;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

public class MoldsModelLoader implements IGeometryLoader<ElementsModel> {

    @Override
    public ElementsModel read(JsonObject json, JsonDeserializationContext deserializationContext)
            throws JsonParseException {
        final JsonArray pattern = json.getAsJsonArray("pattern");

        final int height = pattern.size();
        if (height != 14) {
            throw new JsonSyntaxException("Invalid pattern: must have 14 rows (has " + height + ")");
        }

        boolean[][] full = new boolean[14][14];

        for (int r = 0; r < 14; ++r) {
            String row = GsonHelper.convertToString(pattern.get(r), "pattern[" + r + "]");
            final int width = row.length();
            if (width != 14)
                throw new JsonSyntaxException(
                        "Invalid pattern: must have 14 columns (has " + width + " in row " + r + ")");

            for (int c = 0; c < 14; c++) {
                full[r][c] = row.charAt(c) != ' ';
            }
        }

        return new ElementsModel(generateBlockElementsFromPattern(full));
    }

    public static List<BlockElement> generateBlockElementsFromPattern(boolean[][] pattern) {
        ArrayList<BlockElement> elements = new ArrayList<>();

        int from_y = 1;
        int to_y = 2;
        for (int r = 0; r < 14; ++r) {
            int from_x = r + 1;
            int to_x = r + 2;
            for (int c = 0; c < 14; c++) {
                if (!pattern[r][c])
                    continue;

                int from_z = c + 1;
                int to_z = c + 2;

                Vector3f from = new Vector3f(from_x, from_y, from_z);
                Vector3f to = new Vector3f(to_x, to_y, to_z);

                elements.add(new BlockElement(
                        from, to,
                        Helpers.mapOf(Direction.class,
                                direction -> new BlockElementFace(
                                        null, -1, "#0",
                                        new BlockFaceUV(autoRelativeUV(direction, from, to), 0))),
                        null,
                        true));
            }
        }
        return elements;
    }

    private static float[] autoRelativeUV(Direction direction, Vector3f from, Vector3f to) {
        switch (direction) {
            case NORTH:
                return new float[] { 16 - to.x, 16 - to.y, 16 - from.x, 16 - from.y };
            case SOUTH:
                return new float[] { from.x, 16 - to.y, to.x, 16 - from.y };
            case WEST:
                return new float[] { from.z, 16 - to.y, to.z, 16 - from.y };
            case EAST:
                return new float[] { 16 - to.z, 16 - to.y, 16 - from.z, 16 - from.y };
            case UP:
                return new float[] { from.x, from.z, to.x, to.z };
            case DOWN:
                return new float[] { from.x, 16 - to.z, to.x, 16 - from.z };
        }

        return new float[] {};
    }
}
