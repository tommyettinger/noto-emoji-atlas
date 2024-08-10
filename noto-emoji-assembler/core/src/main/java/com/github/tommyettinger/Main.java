/*
 * Copyright (c) 2022 Tommy Ettinger.
 * The parent project is
 * https://github.com/tommyettinger/noto-emoji-atlas
 */

package com.github.tommyettinger;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.*;
import com.github.tommyettinger.anim8.Dithered;
import com.github.tommyettinger.anim8.PNG8;
import com.github.tommyettinger.anim8.QualityPalette;

import java.io.IOException;
import java.lang.StringBuilder;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * To thicken a black-line-only NotoEmoji image, use:
 * <pre>
 *     magick mogrify -channel RGBA -blur 0x0.8 -unsharp 0x3.0+3.0 "*.png"
 * </pre>
 * To scale thicker black-line-only NotoEmoji to mid-size (32x32), use:
 * <pre>
 *     magick mogrify -unsharp 0x0.75 -resize 32x32 -unsharp 0x0.5 "*.png"
 * </pre>
 * To scale non-thickened colorful NotoEmoji to mid-size (32x32), use:
 * <pre>
 *     magick mogrify -resize 32x32 -sharpen 0x2.0 "*.png"
 * </pre>
 * To scale thicker black-line-only NotoEmoji to small-size (24x24), use:
 * <pre>
 *     magick mogrify -unsharp 0x2.0+2.0 -resize 24x24 "*.png"
 * </pre>
 * To scale non-thickened colorful NotoEmoji to small-size (24x24), use:
 * <pre>
 *     magick mogrify -resize 24x24 -sharpen 0x2.0 "*.png"
 * </pre>
 * To thicken a black-line-only NotoEmoji image more (to "thickest" level), use:
 * <pre>
 *     magick mogrify -channel RGBA -blur 0x1.6 -unsharp 0x2.5+8.0 "*.png"
 * </pre>
 * To scale thickest black-line-only NotoEmoji to tiny-size (16x16), use:
 * <pre>
 *     magick mogrify -resize 16x16 -unsharp 0x1.0+1.0 "*.png"
 * </pre>
 * To scale non-thickened colorful NotoEmoji to tiny-size (16x16), use:
 * <pre>
 *     magick mogrify -resize 16x16 -sharpen 0x2.0 "*.png"
 * </pre>
 * To batch convert the SVG flags to PNGs of some appropriate size:
 * On Windows, using (old) rsvg-convert (downloaded from <a href="https://opensourcepack.blogspot.com/2012/06/rsvg-convert-svg-image-conversion-tool.html">this blog/SourceForge</a>):
 * Note, some of the SVG files are actually symlinks to other SVG files!
 * These can have the symlinked contents copied into the file that had been a symlink.
 * <pre>
 *     FOR %A IN (*.svg) DO "C:\path\to\rsvg-convert.exe" -w 72 -h 72 -a %A -o %~nA.png
 * </pre>
 * Weird regex for the zwj sequences:
 * <pre>
 *     [^\n;]+;[^;]+; (.+?)\s*\#.+\[1\] \(([^\)]+)\)
 *     map.put("$2", "$1");
 * </pre>
 */
public class Main extends ApplicationAdapter {
//    public static final String MODE = "MODIFY_CLDR"; // run this first
//    public static final String MODE = "MODIFY_ALIASES"; // run this next
//    public static final String MODE = "MODIFY_JSON"; // run this next?
//    public static final String MODE = "EMOJI_LARGE"; // run this once done modifying
//    public static final String MODE = "EMOJI_MID";
//    public static final String MODE = "EMOJI_SMALL";
//    public static final String MODE = "EMOJI_INOFFENSIVE"; // ugh, but needed
    public static final String MODE = "EMOJI_HTML";
//    public static final String MODE = "FLAG";
//    public static final String MODE = "WRITE_INFO";
//    public static final String MODE = "ALTERNATE_PALETTES";

    public static final String TYPE = "color";
//    public static final String TYPE = "black";
    public static final String RAW_DIR = "noto-emoji-72x72-" + TYPE;
    public static final String RAW_MID_DIR = "noto-emoji-32x32-" + TYPE;
    public static final String RAW_SMALL_DIR = "noto-emoji-24x24-" + TYPE;

    public static final String JSON = "noto-emoji-cleaned.json";

    @Override
    public void create() {
        JsonReader reader = new JsonReader();
        Json j = new Json(JsonWriter.OutputType.json);

        HashMap<String, String> zwjMap = makeZwjMap();
        LinkedHashMap<String, String> strippedToEmojiMap = j.fromJson(LinkedHashMap.class, String.class, Gdx.files.internal("stripped-to-emoji.json"));

        if ("MODIFY_CLDR".equals(MODE)) {
            //To locate any names with non-ASCII chars in emoji_15_1.json, use this regex:
            //"description": "[^"]*[^\u0000-\u007F][^"]*",
            //To locate any names with characters that could be a problem, use this regex (may need expanding):
            //"description": "[^"]*[^0-9a-zA-Z' ,!-][^"]*",
            //Might be useful for locating intermediate things that need replacement?
            //"description": "[^"]*[^0-9a-zA-Z' ,:\(\)!-][^"]*",

            LinkedHashMap<?, ?> cldr = j.fromJson(LinkedHashMap.class, Gdx.files.internal("names-cldr-raw.json"));
            LinkedHashMap<String, String> next = new LinkedHashMap<>(cldr.size());
            LinkedHashMap<String, String> toEmoji = new LinkedHashMap<>(cldr.size());
            for(Map.Entry<?, ?> ent : cldr.entrySet()){
                String name = "emoji_u" + ent.getKey().toString().replace('-', '_').toLowerCase(Locale.ROOT),
                        stripped = stripFE0F(name);
                next.put(stripped, ent.getValue().toString());
                toEmoji.put(stripped, codePointsToEmoji(name));
            }

            j.toJson(next, LinkedHashMap.class, String.class, Gdx.files.local("names-cldr.json"));
            j.toJson(toEmoji, LinkedHashMap.class, String.class, Gdx.files.local("stripped-to-emoji.json"));

        } else if ("MODIFY_ALIASES".equals(MODE)) {
            //To locate any names with non-ASCII chars in emoji_15_1.json, use this regex:
            //"description": "[^"]*[^\u0000-\u007F][^"]*",
            //To locate any names with characters that could be a problem, use this regex (may need expanding):
            //"description": "[^"]*[^0-9a-zA-Z' ,!-][^"]*",
            //Might be useful for locating intermediate things that need replacement?
            //"description": "[^"]*[^0-9a-zA-Z' ,:\(\)!-][^"]*",

            JsonValue json = reader.parse(Gdx.files.internal("shortcodes-emojibase-raw.json"));
//            JsonValue json = reader.parse(Gdx.files.internal("shortcodes-discord-raw.json"));
            LinkedHashMap<String, String[]> next = new LinkedHashMap<>(json.size);
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String name = stripFE0F("emoji_u" + entry.name.replace('-', '_').toLowerCase(Locale.ROOT));
                if(entry.isString()){
                    next.put(name, new String[]{entry.asString()});
                } else {
                    next.put(name, entry.asStringArray());
                }
            }
            j.toJson(next, LinkedHashMap.class, String[].class, Gdx.files.local("aliases_emojibase.json"));
//            j.toJson(next, LinkedHashMap.class, String[].class, Gdx.files.local("aliases_discord.json"));

        } else if ("MODIFY_JSON".equals(MODE)) {
            //To locate any names with non-ASCII chars in emoji_15_1.json, use this regex:
            //"description": "[^"]*[^\u0000-\u007F][^"]*",
            //To locate any names with characters that could be a problem, use this regex (may need expanding):
            //"description": "[^"]*[^0-9a-zA-Z' ,!-][^"]*",
            //Might be useful for locating intermediate things that need replacement?
            //"description": "[^"]*[^0-9a-zA-Z' ,:\(\)!-][^"]*",
            JsonValue json = reader.parse(Gdx.files.internal("emoji_15_1.json"));
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String name = removeAccents(entry.getString("description"))
                        .replace(':', ',').replace('“', '\'').replace('”', '\'').replace('’', '\'')
                        .replace(".", "").replace("&", "and");
                entry.addChild("name", new JsonValue(name));
                for (String s : new String[]{
                        "description", "subgroups", "tags",
                        "skintone", "skintone_combination", "skintone_base_emoji", "skintone_base_hexcode",
                        "unicode", "order", "unicode_version", "ios_version"}) {
                    entry.remove(s);
                }
            }

            Gdx.files.local(JSON).writeString(json.toJson(JsonWriter.OutputType.json).replace("{", "\n{"), false);
        } else if ("ALTERNATE_PALETTES".equals(MODE)) {
            FileHandle paletteDir = Gdx.files.local("../../alt-palette/");
            FileHandle[] paletteImages = paletteDir.list(".png");
            QualityPalette qp = new QualityPalette();
            PNG8 png = new PNG8();
            png.setCompression(7);
            png.setFlipY(false);
            for (FileHandle pi : paletteImages) {
                String paletteName = pi.nameWithoutExtension();
                System.out.println("Working on " + paletteName);
                FileHandle current = paletteDir.child(paletteName + "/");
                current.mkdirs();
                Pixmap pm = new Pixmap(pi);
                qp.exact(QualityPalette.colorsFrom(pm));
                pm.dispose();
                png.setDitherAlgorithm(Dithered.DitherAlgorithm.NONE);
                png.setDitherStrength(1f);
                png.setPalette(qp);
                Pixmap large = new Pixmap(Gdx.files.local("../../atlas/NotoEmoji" + TYPE + ".png"));
                png.write(current.child("atlas/NotoEmoji" + TYPE + ".png"), large, false, true);
                large.dispose();
                for (int i = 2; i <= 5; i++) {
                    Pixmap largeN = new Pixmap(Gdx.files.local("../../atlas/NotoEmoji" + TYPE + i + ".png"));
                    png.write(current.child("atlas/NotoEmoji" + TYPE + i + ".png"), largeN, false, true);
                    largeN.dispose();
                }
                Gdx.files.local("../../atlas/NotoEmoji.atlas").copyTo(current.child("atlas"));
                Pixmap mid = new Pixmap(Gdx.files.local("../../atlas-mid/NotoEmoji" + TYPE + ".png"));
                png.write(current.child("atlas-mid/NotoEmoji" + TYPE + ".png"), mid, false, true);
                mid.dispose();
                Gdx.files.local("../../atlas-mid/NotoEmoji" + TYPE + ".atlas").copyTo(current.child("atlas-mid"));
                Pixmap small = new Pixmap(Gdx.files.local("../../atlas-small/NotoEmoji" + TYPE + ".png"));
                png.write(current.child("atlas-small/NotoEmoji" + TYPE + ".png"), small, false, true);
                small.dispose();
                Gdx.files.local("../../atlas-small/NotoEmoji" + TYPE + ".atlas").copyTo(current.child("atlas-small"));
            }
        } else if ("WRITE_INFO".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal(JSON));
            ObjectSet<String> used = new ObjectSet<>(json.size);
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String name = entry.getString("name");
                if (used.add(name)) {
//                    name += ".png";
                    entry.remove("hexcode");
//                    FileHandle original = Gdx.files.local("../../scaled-mid-"+TYPE+"/name/" + name);
//                    if (original.exists()) {
//                        if(entry.has("emoji"))
//                            original.copyTo(Gdx.files.local("../../renamed-mid-"+TYPE+"/emoji/" + entry.getString("emoji") + ".png"));
//                        original.copyTo(Gdx.files.local("../../renamed-mid-"+TYPE+"/name/" + name));
//                    }
                } else {
                    entry.remove();
                }
            }
            Gdx.files.local("noto-emoji-info.json").writeString(json.toJson(JsonWriter.OutputType.json).replace("{", "\n{"), false);
        } else if ("EMOJI_SMALL".equals(MODE)) {
            HashMap<String, String> knownMap = j.fromJson(HashMap.class, String.class, Gdx.files.internal("names-cldr.json"));
            HashMap<String, String[]> aliasMap = j.fromJson(HashMap.class, String[].class, Gdx.files.internal("aliases.json"));
            FileHandle rawDir = Gdx.files.local("../../" + RAW_SMALL_DIR + "/");
            FileHandle[] files = rawDir.list(".png");
            for (FileHandle original : files) {
                String codename = original.nameWithoutExtension();
                String emoji = strippedToEmojiMap.get(codename);
                if(emoji == null) {
                    System.out.println("WHOOPS, codename " + codename + " has no emoji!");
                    continue;
                }
                original.copyTo(Gdx.files.local("../../renamed-small-" + TYPE + "/emoji/" + emoji + ".png"));
                String name = null;
                if(zwjMap.containsKey(emoji)){
                    name = zwjMap.get(emoji);
                } else if(knownMap.containsKey(codename)){
                    name = knownMap.get(codename);
                }
                if(name == null){
                    System.out.println("WHAT! Emoji '" + emoji + "' has no name, but has codename " + codename + ", reconstructed to " + emojiToCodePoints(emoji) + " .");
                    continue;
                }
                original.copyTo(Gdx.files.local("../../renamed-small-" + TYPE + "/name/" + name + ".png"));
                if (aliasMap.containsKey(codename)) {
                    for (String alias : aliasMap.get(codename)) {
                        original.copyTo(Gdx.files.local("../../renamed-small-" + TYPE + "/ignored/alias/" + alias + ".png"));
                    }
                }
            }
        } else if ("EMOJI_MID".equals(MODE)) {
            HashMap<String, String> knownMap = j.fromJson(HashMap.class, String.class, Gdx.files.internal("names-cldr.json"));
            HashMap<String, String[]> aliasMap = j.fromJson(HashMap.class, String[].class, Gdx.files.internal("aliases.json"));
            FileHandle rawDir = Gdx.files.local("../../" + RAW_MID_DIR + "/");
            FileHandle[] files = rawDir.list(".png");
            for (FileHandle original : files) {
                String codename = original.nameWithoutExtension();
                String emoji = strippedToEmojiMap.get(codename);
                if(emoji == null) {
                    System.out.println("WHOOPS, codename " + codename + " has no emoji!");
                    continue;
                }
                original.copyTo(Gdx.files.local("../../renamed-mid-" + TYPE + "/emoji/" + emoji + ".png"));
                String name = null;
                if(zwjMap.containsKey(emoji)){
                    name = zwjMap.get(emoji);
                } else if(knownMap.containsKey(codename)){
                    name = knownMap.get(codename);
                }
                if(name == null){
                    System.out.println("WHAT! Emoji '" + emoji + "' has no name, but has codename " + codename + ", reconstructed to " + emojiToCodePoints(emoji) + " .");
                    continue;
                }
                original.copyTo(Gdx.files.local("../../renamed-mid-" + TYPE + "/name/" + name + ".png"));
                if (aliasMap.containsKey(codename)) {
                    for (String alias : aliasMap.get(codename)) {
                        original.copyTo(Gdx.files.local("../../renamed-mid-" + TYPE + "/ignored/alias/" + alias + ".png"));
                    }
                }
            }
        } else if ("EMOJI_LARGE".equals(MODE)) {
            HashMap<String, String> knownMap = j.fromJson(HashMap.class, String.class, Gdx.files.internal("names-cldr.json"));
            HashMap<String, String[]> aliasMap = j.fromJson(HashMap.class, String[].class, Gdx.files.internal("aliases.json"));
            FileHandle rawDir = Gdx.files.local("../../" + RAW_DIR + "/");
            FileHandle[] files = rawDir.list(".png");
            for (FileHandle original : files) {
                String codename = original.nameWithoutExtension();
                String emoji = strippedToEmojiMap.get(codename);
                if(emoji == null) {
                    System.out.println("WHOOPS, codename " + codename + " has no emoji!");
                    continue;
                }
                original.copyTo(Gdx.files.local("../../renamed-" + TYPE + "/emoji/" + emoji + ".png"));
                String name = null;
                if(zwjMap.containsKey(emoji)){
                    name = zwjMap.get(emoji);
                } else if(knownMap.containsKey(codename)){
                    name = knownMap.get(codename);
                }
                if(name == null){
                    System.out.println("WHAT! Emoji '" + emoji + "' has no name, but has codename " + codename + ", reconstructed to " + emojiToCodePoints(emoji) + " .");
                    continue;
                }
                original.copyTo(Gdx.files.local("../../renamed-" + TYPE + "/name/" + name + ".png"));
                if (aliasMap.containsKey(codename)) {
                    for (String alias : aliasMap.get(codename)) {
                        original.copyTo(Gdx.files.local("../../renamed-" + TYPE + "/ignored/alias/" + alias + ".png"));
                    }
                }
            }
        } else if ("EMOJI_INOFFENSIVE".equals(MODE) || "EMOJI_INOFFENSIVE_MONO".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal(JSON));
            ObjectSet<String> used = new ObjectSet<>(json.size);
            String where = "EMOJI_INOFFENSIVE".equals(MODE) ? "/inoffensive-" : "/inoffensive-mono-";
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String name = entry.getString("name");
                if (name.endsWith("skin tone")) continue; // we're intending to make the images grayscale.
                if (name.contains("flag")) continue; // some false positives, but less politically sensitive stuff.
                if ("star of David".equals(name)) continue;
                if ("wheel of dharma".equals(name)) continue;
                if ("yin yang".equals(name)) continue;
                if ("latin cross".equals(name)) continue;
                if ("orthodox cross".equals(name)) continue;
                if ("star and crescent".equals(name)) continue;
                if ("menorah".equals(name)) continue;
                if ("dotted six-pointed star".equals(name)) continue;
                if ("khanda".equals(name)) continue;
                if ("red hair".equals(name)) continue;
                if ("curly hair".equals(name)) continue;
                if ("white hair".equals(name)) continue;
                if ("bald".equals(name)) continue;
                if ("no one under eighteen".equals(name)) continue;
                if ("no smoking".equals(name)) continue;
                if ("cigarette".equals(name)) continue;
                if ("bomb".equals(name)) continue;
                if ("church".equals(name)) continue;
                if ("mosque".equals(name)) continue;
                if ("hindu temple".equals(name)) continue;
                if ("synagogue".equals(name)) continue;
                if ("shinto shrine".equals(name)) continue;
                if ("kaaba".equals(name)) continue;
                if ("map of Japan".equals(name)) continue;
                if ("wedding".equals(name)) continue;
                if ("Tokyo tower".equals(name)) continue;
                if ("Statue of Liberty".equals(name)) continue;
                if ("sake".equals(name)) continue;
                if ("love hotel".equals(name)) continue;
                if ("breast-feeding".equals(name)) continue;
                if ("eggplant".equals(name)) continue;
                if ("peach".equals(name)) continue;
                if ("bottle with popping cork".equals(name)) continue;
                if ("wine glass".equals(name)) continue;
                if ("cocktail glass".equals(name)) continue;
                if ("tropical drink".equals(name)) continue;
                if ("beer mug".equals(name)) continue;
                if ("clinking beer mugs".equals(name)) continue;
                if ("clinking glasses".equals(name)) continue;
                if ("tumbler glass".equals(name)) continue;
                if ("drunk person".equals(name)) continue;
                if ("trump".equals(name)) continue;
                if ("Greta Thunberg".equals(name)) continue;
                if ("Twitter".equals(name)) continue;
                if ("pinterest".equals(name)) continue;
                if ("facebook".equals(name)) continue;
                if ("instagram".equals(name)) continue;
                if ("youtube".equals(name)) continue;
                if ("github".equals(name)) continue;
                if ("linkedin".equals(name)) continue;
                if ("android".equals(name)) continue;
                if ("musicbrainz".equals(name)) continue;
                if ("openfoodfact".equals(name)) continue;
                if ("openstreetmap".equals(name)) continue;
                if ("wikidata".equals(name)) continue;
                if ("Firefox".equals(name)) continue;
                if ("Safari".equals(name)) continue;
                if ("Opera".equals(name)) continue;
                if ("Chromium".equals(name)) continue;
                if ("Chrome".equals(name)) continue;
                if ("Netscape Navigator".equals(name)) continue;
                if ("Internet Explorer".equals(name)) continue;
                if ("Edge".equals(name)) continue;
                if ("iNaturalist".equals(name)) continue;
                if ("gitlab".equals(name)) continue;
                if ("mastodon".equals(name)) continue;
                if ("peertube".equals(name)) continue;
                if ("pixelfed".equals(name)) continue;
                if ("signal".equals(name)) continue;
                if ("element".equals(name)) continue;
                if ("jellyfin".equals(name)) continue;
                if ("reddit".equals(name)) continue;
                if ("discord".equals(name)) continue;
                if ("c".equals(name)) continue;
                if ("cplusplus".equals(name)) continue;
                if ("csharp".equals(name)) continue;
                if ("chrome canary".equals(name)) continue;
                if ("firefox developer".equals(name)) continue;
                if ("firefox nightly".equals(name)) continue;
                if ("javascript".equals(name)) continue;
                if ("typescript".equals(name)) continue;
                if ("webassembly".equals(name)) continue;
                if ("svg".equals(name)) continue;
                if ("markdown".equals(name)) continue;
                if ("winrar".equals(name)) continue;
                if ("ubuntu".equals(name)) continue;
                if ("windows".equals(name)) continue;
                if ("artstation".equals(name)) continue;
                if ("apple".equals(name)) continue;
                if (name.startsWith("family")) continue;
                if (name.startsWith("couple")) continue;
                if (name.startsWith("kiss")) continue;
                if (name.startsWith("pregnant")) continue;
                if (name.contains("holding hands")) continue;
                if (used.add(name)) {
                    String codename = entry.getString("hexcode");
                    name += ".png";
                    FileHandle original = Gdx.files.local("../../" + RAW_DIR + "/" + codename + ".png");
                    if (original.exists()) {
                        if (entry.has("emoji"))
                            original.copyTo(Gdx.files.local("../.." + where + TYPE + "/emoji/" + entry.getString("emoji") + ".png"));
                        original.copyTo(Gdx.files.local("../.." + where + TYPE + "/name/" + name));
                    }
                } else {
                    entry.remove();
                }
            }
            Gdx.files.local("noto-emoji-info-" + ("EMOJI_INOFFENSIVE".equals(MODE) ? "inoffensive" : "inoffensive-mono") + ".json").writeString(json.toJson(JsonWriter.OutputType.json).replace("{", "\n{"), false);
        } else if ("EMOJI_HTML".equals(MODE)) {
            StringBuilder sb = new StringBuilder(4096);
            sb.append("""
                    <!doctype html>
                    <html>
                    <head>
                    \t<title>Noto-Emoji Preview</title>
                    \t<meta http-equiv="content-type" content="text/html; charset=UTF-8">
                    \t<meta id="gameViewport" name="viewport" content="width=device-width initial-scale=1">
                    \t<link href="styles.css" rel="stylesheet" type="text/css">
                    </head>
                    
                    """);
            sb.append("<body>\n");
            sb.append("<h1>Noto-Emoji Preview</h1>\n");
            sb.append("<p>This shows all emoji supported by " +
                    "<a href=\"https://github.com/tommyettinger/noto-emoji-atlas\">NotoEmojiAtlas</a>, " +
                    "along with the names each can be looked up by.</p>\n");
//            if(TYPE.equals("color"))
//                sb.append("<p>These are the full-color emoji. There are also emoji that use only a black line "+
//                        "<a href=\"black").append(".html\">available here</a>.</p>\n");
//            else
//                sb.append("<p>These are the black-line-only emoji. There are also emoji that use full color "+
//                        "<a href=\"index").append(".html\">available here</a>.</p>\n");
            sb.append("<p>The atlases and all image assets are licensed under the " +
                    "<a href=\"https://github.com/tommyettinger/noto-emoji-atlas/blob/main/LICENSE.txt\">OFL 1.1</a>.</p>\n");
            sb.append("<p>Thanks to the entire <a href=\"https://github.com/googlefonts/noto-emoji/\">Noto Emoji project</a>!</p>\n");
            sb.append("<div class=\"box\">\n");


            HashMap<String, String> knownMap = j.fromJson(HashMap.class, String.class, Gdx.files.internal("names-cldr.json"));
            HashMap<String, String[]> aliasMap = j.fromJson(HashMap.class, String[].class, Gdx.files.internal("aliases.json"));
            FileHandle rawDir = Gdx.files.local("../../" + RAW_DIR + "/");
            FileHandle[] files = rawDir.list(".png");
            for (FileHandle original : files) {
                String codename = original.nameWithoutExtension();
                String emoji = strippedToEmojiMap.get(codename);
                if(emoji == null) {
                    continue;
                }
                String name = null;
                if(zwjMap.containsKey(emoji)){
                    name = zwjMap.get(emoji);
                } else if(knownMap.containsKey(codename)){
                    name = knownMap.get(codename);
                }
                if(name == null){
                    continue;
                }
                String emojiFile = "name/" + name + ".png";
                sb.append("\t<div class=\"item\">\n" +
                                "\t\t<img src=\"").append(TYPE).append('/')
                        .append(emojiFile).append("\" alt=\"").append(name).append("\" />\n");
                if (!emoji.isEmpty()) sb.append("\t\t<p>").append(emoji).append("</p>\n");
                sb.append("\t\t<p>").append(name).append("</p>\n");
                if (aliasMap.containsKey(codename)) {
                    for (String alias : aliasMap.get(codename)) {
                        if(alias.equals(name)) continue;
                        sb.append("\t\t<p>").append(alias).append("</p>\n");
                    }
                }
                sb.append("\t</div>\n");
            }
            sb.append("</div>\n</body>\n");
            sb.append("</html>\n");
            Gdx.files.local(TYPE.equals("color") ? "index.html" : "black.html")
                    .writeString(sb.toString(), false, "UTF8");
        } else if ("FLAG".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal(JSON));
            char[] buffer = new char[2];
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                if (!"Flags (country-flag)".equals(entry.getString("category"))) continue;

                String codename = entry.getString("hexcode") + ".png";
                String charString = entry.getString("emoji") + ".png";
                String name = entry.getString("name");
                String countryUnicode = entry.getString("emoji");
                buffer[0] = (char) (countryUnicode.codePointAt(1) - 56806 + 'A');
                buffer[1] = (char) (countryUnicode.codePointAt(3) - 56806 + 'A');
                String countryCode = String.valueOf(buffer);
                FileHandle original = Gdx.files.local("../../scaled-tiny/" + codename);
                if (original.exists()) {
                    original.copyTo(Gdx.files.local("../../flags-tiny/emoji/" + charString));
                    original.copyTo(Gdx.files.local("../../flags-tiny/name/" + name));
                    original.copyTo(Gdx.files.local("../../flags-tiny/code/" + countryCode + ".png"));
                }
            }
        }
    }

    private HashMap<String, String> makeZwjMap() {
        HashMap<String, String> map = new HashMap<>(1500);
        map.put("👨‍❤️‍👨", "couple with heart, man, man");
        map.put("👨‍❤️‍💋‍👨", "kiss, man, man");
        map.put("👨‍👦", "family, man, boy");
        map.put("👨‍👦‍👦", "family, man, boy, boy");
        map.put("👨‍👧", "family, man, girl");
        map.put("👨‍👧‍👦", "family, man, girl, boy");
        map.put("👨‍👧‍👧", "family, man, girl, girl");
        map.put("👨‍👨‍👦", "family, man, man, boy");
        map.put("👨‍👨‍👦‍👦", "family, man, man, boy, boy");
        map.put("👨‍👨‍👧", "family, man, man, girl");
        map.put("👨‍👨‍👧‍👦", "family, man, man, girl, boy");
        map.put("👨‍👨‍👧‍👧", "family, man, man, girl, girl");
        map.put("👨‍👩‍👦", "family, man, woman, boy");
        map.put("👨‍👩‍👦‍👦", "family, man, woman, boy, boy");
        map.put("👨‍👩‍👧", "family, man, woman, girl");
        map.put("👨‍👩‍👧‍👦", "family, man, woman, girl, boy");
        map.put("👨‍👩‍👧‍👧", "family, man, woman, girl, girl");
        map.put("👨🏻‍❤️‍👨🏻", "couple with heart, man, man, light skin tone");
        map.put("👨🏻‍❤️‍👨🏼", "couple with heart, man, man, light skin tone, medium-light skin tone");
        map.put("👨🏻‍❤️‍👨🏽", "couple with heart, man, man, light skin tone, medium skin tone");
        map.put("👨🏻‍❤️‍👨🏾", "couple with heart, man, man, light skin tone, medium-dark skin tone");
        map.put("👨🏻‍❤️‍👨🏿", "couple with heart, man, man, light skin tone, dark skin tone");
        map.put("👨🏻‍❤️‍💋‍👨🏻", "kiss, man, man, light skin tone");
        map.put("👨🏻‍❤️‍💋‍👨🏼", "kiss, man, man, light skin tone, medium-light skin tone");
        map.put("👨🏻‍❤️‍💋‍👨🏽", "kiss, man, man, light skin tone, medium skin tone");
        map.put("👨🏻‍❤️‍💋‍👨🏾", "kiss, man, man, light skin tone, medium-dark skin tone");
        map.put("👨🏻‍❤️‍💋‍👨🏿", "kiss, man, man, light skin tone, dark skin tone");
        map.put("👨🏻‍🤝‍👨🏼", "men holding hands, light skin tone, medium-light skin tone");
        map.put("👨🏻‍🤝‍👨🏽", "men holding hands, light skin tone, medium skin tone");
        map.put("👨🏻‍🤝‍👨🏾", "men holding hands, light skin tone, medium-dark skin tone");
        map.put("👨🏻‍🤝‍👨🏿", "men holding hands, light skin tone, dark skin tone");
        map.put("👨🏼‍❤️‍👨🏻", "couple with heart, man, man, medium-light skin tone, light skin tone");
        map.put("👨🏼‍❤️‍👨🏼", "couple with heart, man, man, medium-light skin tone");
        map.put("👨🏼‍❤️‍👨🏽", "couple with heart, man, man, medium-light skin tone, medium skin tone");
        map.put("👨🏼‍❤️‍👨🏾", "couple with heart, man, man, medium-light skin tone, medium-dark skin tone");
        map.put("👨🏼‍❤️‍👨🏿", "couple with heart, man, man, medium-light skin tone, dark skin tone");
        map.put("👨🏼‍❤️‍💋‍👨🏻", "kiss, man, man, medium-light skin tone, light skin tone");
        map.put("👨🏼‍❤️‍💋‍👨🏼", "kiss, man, man, medium-light skin tone");
        map.put("👨🏼‍❤️‍💋‍👨🏽", "kiss, man, man, medium-light skin tone, medium skin tone");
        map.put("👨🏼‍❤️‍💋‍👨🏾", "kiss, man, man, medium-light skin tone, medium-dark skin tone");
        map.put("👨🏼‍❤️‍💋‍👨🏿", "kiss, man, man, medium-light skin tone, dark skin tone");
        map.put("👨🏼‍🤝‍👨🏻", "men holding hands, medium-light skin tone, light skin tone");
        map.put("👨🏼‍🤝‍👨🏽", "men holding hands, medium-light skin tone, medium skin tone");
        map.put("👨🏼‍🤝‍👨🏾", "men holding hands, medium-light skin tone, medium-dark skin tone");
        map.put("👨🏼‍🤝‍👨🏿", "men holding hands, medium-light skin tone, dark skin tone");
        map.put("👨🏽‍❤️‍👨🏻", "couple with heart, man, man, medium skin tone, light skin tone");
        map.put("👨🏽‍❤️‍👨🏼", "couple with heart, man, man, medium skin tone, medium-light skin tone");
        map.put("👨🏽‍❤️‍👨🏽", "couple with heart, man, man, medium skin tone");
        map.put("👨🏽‍❤️‍👨🏾", "couple with heart, man, man, medium skin tone, medium-dark skin tone");
        map.put("👨🏽‍❤️‍👨🏿", "couple with heart, man, man, medium skin tone, dark skin tone");
        map.put("👨🏽‍❤️‍💋‍👨🏻", "kiss, man, man, medium skin tone, light skin tone");
        map.put("👨🏽‍❤️‍💋‍👨🏼", "kiss, man, man, medium skin tone, medium-light skin tone");
        map.put("👨🏽‍❤️‍💋‍👨🏽", "kiss, man, man, medium skin tone");
        map.put("👨🏽‍❤️‍💋‍👨🏾", "kiss, man, man, medium skin tone, medium-dark skin tone");
        map.put("👨🏽‍❤️‍💋‍👨🏿", "kiss, man, man, medium skin tone, dark skin tone");
        map.put("👨🏽‍🤝‍👨🏻", "men holding hands, medium skin tone, light skin tone");
        map.put("👨🏽‍🤝‍👨🏼", "men holding hands, medium skin tone, medium-light skin tone");
        map.put("👨🏽‍🤝‍👨🏾", "men holding hands, medium skin tone, medium-dark skin tone");
        map.put("👨🏽‍🤝‍👨🏿", "men holding hands, medium skin tone, dark skin tone");
        map.put("👨🏾‍❤️‍👨🏻", "couple with heart, man, man, medium-dark skin tone, light skin tone");
        map.put("👨🏾‍❤️‍👨🏼", "couple with heart, man, man, medium-dark skin tone, medium-light skin tone");
        map.put("👨🏾‍❤️‍👨🏽", "couple with heart, man, man, medium-dark skin tone, medium skin tone");
        map.put("👨🏾‍❤️‍👨🏾", "couple with heart, man, man, medium-dark skin tone");
        map.put("👨🏾‍❤️‍👨🏿", "couple with heart, man, man, medium-dark skin tone, dark skin tone");
        map.put("👨🏾‍❤️‍💋‍👨🏻", "kiss, man, man, medium-dark skin tone, light skin tone");
        map.put("👨🏾‍❤️‍💋‍👨🏼", "kiss, man, man, medium-dark skin tone, medium-light skin tone");
        map.put("👨🏾‍❤️‍💋‍👨🏽", "kiss, man, man, medium-dark skin tone, medium skin tone");
        map.put("👨🏾‍❤️‍💋‍👨🏾", "kiss, man, man, medium-dark skin tone");
        map.put("👨🏾‍❤️‍💋‍👨🏿", "kiss, man, man, medium-dark skin tone, dark skin tone");
        map.put("👨🏾‍🤝‍👨🏻", "men holding hands, medium-dark skin tone, light skin tone");
        map.put("👨🏾‍🤝‍👨🏼", "men holding hands, medium-dark skin tone, medium-light skin tone");
        map.put("👨🏾‍🤝‍👨🏽", "men holding hands, medium-dark skin tone, medium skin tone");
        map.put("👨🏾‍🤝‍👨🏿", "men holding hands, medium-dark skin tone, dark skin tone");
        map.put("👨🏿‍❤️‍👨🏻", "couple with heart, man, man, dark skin tone, light skin tone");
        map.put("👨🏿‍❤️‍👨🏼", "couple with heart, man, man, dark skin tone, medium-light skin tone");
        map.put("👨🏿‍❤️‍👨🏽", "couple with heart, man, man, dark skin tone, medium skin tone");
        map.put("👨🏿‍❤️‍👨🏾", "couple with heart, man, man, dark skin tone, medium-dark skin tone");
        map.put("👨🏿‍❤️‍👨🏿", "couple with heart, man, man, dark skin tone");
        map.put("👨🏿‍❤️‍💋‍👨🏻", "kiss, man, man, dark skin tone, light skin tone");
        map.put("👨🏿‍❤️‍💋‍👨🏼", "kiss, man, man, dark skin tone, medium-light skin tone");
        map.put("👨🏿‍❤️‍💋‍👨🏽", "kiss, man, man, dark skin tone, medium skin tone");
        map.put("👨🏿‍❤️‍💋‍👨🏾", "kiss, man, man, dark skin tone, medium-dark skin tone");
        map.put("👨🏿‍❤️‍💋‍👨🏿", "kiss, man, man, dark skin tone");
        map.put("👨🏿‍🤝‍👨🏻", "men holding hands, dark skin tone, light skin tone");
        map.put("👨🏿‍🤝‍👨🏼", "men holding hands, dark skin tone, medium-light skin tone");
        map.put("👨🏿‍🤝‍👨🏽", "men holding hands, dark skin tone, medium skin tone");
        map.put("👨🏿‍🤝‍👨🏾", "men holding hands, dark skin tone, medium-dark skin tone");
        map.put("👩‍❤️‍👨", "couple with heart, woman, man");
        map.put("👩‍❤️‍👩", "couple with heart, woman, woman");
        map.put("👩‍❤️‍💋‍👨", "kiss, woman, man");
        map.put("👩‍❤️‍💋‍👩", "kiss, woman, woman");
        map.put("👩‍👦", "family, woman, boy");
        map.put("👩‍👦‍👦", "family, woman, boy, boy");
        map.put("👩‍👧", "family, woman, girl");
        map.put("👩‍👧‍👦", "family, woman, girl, boy");
        map.put("👩‍👧‍👧", "family, woman, girl, girl");
        map.put("👩‍👩‍👦", "family, woman, woman, boy");
        map.put("👩‍👩‍👦‍👦", "family, woman, woman, boy, boy");
        map.put("👩‍👩‍👧", "family, woman, woman, girl");
        map.put("👩‍👩‍👧‍👦", "family, woman, woman, girl, boy");
        map.put("👩‍👩‍👧‍👧", "family, woman, woman, girl, girl");
        map.put("👩🏻‍❤️‍👨🏻", "couple with heart, woman, man, light skin tone");
        map.put("👩🏻‍❤️‍👨🏼", "couple with heart, woman, man, light skin tone, medium-light skin tone");
        map.put("👩🏻‍❤️‍👨🏽", "couple with heart, woman, man, light skin tone, medium skin tone");
        map.put("👩🏻‍❤️‍👨🏾", "couple with heart, woman, man, light skin tone, medium-dark skin tone");
        map.put("👩🏻‍❤️‍👨🏿", "couple with heart, woman, man, light skin tone, dark skin tone");
        map.put("👩🏻‍❤️‍👩🏻", "couple with heart, woman, woman, light skin tone");
        map.put("👩🏻‍❤️‍👩🏼", "couple with heart, woman, woman, light skin tone, medium-light skin tone");
        map.put("👩🏻‍❤️‍👩🏽", "couple with heart, woman, woman, light skin tone, medium skin tone");
        map.put("👩🏻‍❤️‍👩🏾", "couple with heart, woman, woman, light skin tone, medium-dark skin tone");
        map.put("👩🏻‍❤️‍👩🏿", "couple with heart, woman, woman, light skin tone, dark skin tone");
        map.put("👩🏻‍❤️‍💋‍👨🏻", "kiss, woman, man, light skin tone");
        map.put("👩🏻‍❤️‍💋‍👨🏼", "kiss, woman, man, light skin tone, medium-light skin tone");
        map.put("👩🏻‍❤️‍💋‍👨🏽", "kiss, woman, man, light skin tone, medium skin tone");
        map.put("👩🏻‍❤️‍💋‍👨🏾", "kiss, woman, man, light skin tone, medium-dark skin tone");
        map.put("👩🏻‍❤️‍💋‍👨🏿", "kiss, woman, man, light skin tone, dark skin tone");
        map.put("👩🏻‍❤️‍💋‍👩🏻", "kiss, woman, woman, light skin tone");
        map.put("👩🏻‍❤️‍💋‍👩🏼", "kiss, woman, woman, light skin tone, medium-light skin tone");
        map.put("👩🏻‍❤️‍💋‍👩🏽", "kiss, woman, woman, light skin tone, medium skin tone");
        map.put("👩🏻‍❤️‍💋‍👩🏾", "kiss, woman, woman, light skin tone, medium-dark skin tone");
        map.put("👩🏻‍❤️‍💋‍👩🏿", "kiss, woman, woman, light skin tone, dark skin tone");
        map.put("👩🏻‍🤝‍👨🏼", "woman and man holding hands, light skin tone, medium-light skin tone");
        map.put("👩🏻‍🤝‍👨🏽", "woman and man holding hands, light skin tone, medium skin tone");
        map.put("👩🏻‍🤝‍👨🏾", "woman and man holding hands, light skin tone, medium-dark skin tone");
        map.put("👩🏻‍🤝‍👨🏿", "woman and man holding hands, light skin tone, dark skin tone");
        map.put("👩🏻‍🤝‍👩🏼", "women holding hands, light skin tone, medium-light skin tone");
        map.put("👩🏻‍🤝‍👩🏽", "women holding hands, light skin tone, medium skin tone");
        map.put("👩🏻‍🤝‍👩🏾", "women holding hands, light skin tone, medium-dark skin tone");
        map.put("👩🏻‍🤝‍👩🏿", "women holding hands, light skin tone, dark skin tone");
        map.put("👩🏼‍❤️‍👨🏻", "couple with heart, woman, man, medium-light skin tone, light skin tone");
        map.put("👩🏼‍❤️‍👨🏼", "couple with heart, woman, man, medium-light skin tone");
        map.put("👩🏼‍❤️‍👨🏽", "couple with heart, woman, man, medium-light skin tone, medium skin tone");
        map.put("👩🏼‍❤️‍👨🏾", "couple with heart, woman, man, medium-light skin tone, medium-dark skin tone");
        map.put("👩🏼‍❤️‍👨🏿", "couple with heart, woman, man, medium-light skin tone, dark skin tone");
        map.put("👩🏼‍❤️‍👩🏻", "couple with heart, woman, woman, medium-light skin tone, light skin tone");
        map.put("👩🏼‍❤️‍👩🏼", "couple with heart, woman, woman, medium-light skin tone");
        map.put("👩🏼‍❤️‍👩🏽", "couple with heart, woman, woman, medium-light skin tone, medium skin tone");
        map.put("👩🏼‍❤️‍👩🏾", "couple with heart, woman, woman, medium-light skin tone, medium-dark skin tone");
        map.put("👩🏼‍❤️‍👩🏿", "couple with heart, woman, woman, medium-light skin tone, dark skin tone");
        map.put("👩🏼‍❤️‍💋‍👨🏻", "kiss, woman, man, medium-light skin tone, light skin tone");
        map.put("👩🏼‍❤️‍💋‍👨🏼", "kiss, woman, man, medium-light skin tone");
        map.put("👩🏼‍❤️‍💋‍👨🏽", "kiss, woman, man, medium-light skin tone, medium skin tone");
        map.put("👩🏼‍❤️‍💋‍👨🏾", "kiss, woman, man, medium-light skin tone, medium-dark skin tone");
        map.put("👩🏼‍❤️‍💋‍👨🏿", "kiss, woman, man, medium-light skin tone, dark skin tone");
        map.put("👩🏼‍❤️‍💋‍👩🏻", "kiss, woman, woman, medium-light skin tone, light skin tone");
        map.put("👩🏼‍❤️‍💋‍👩🏼", "kiss, woman, woman, medium-light skin tone");
        map.put("👩🏼‍❤️‍💋‍👩🏽", "kiss, woman, woman, medium-light skin tone, medium skin tone");
        map.put("👩🏼‍❤️‍💋‍👩🏾", "kiss, woman, woman, medium-light skin tone, medium-dark skin tone");
        map.put("👩🏼‍❤️‍💋‍👩🏿", "kiss, woman, woman, medium-light skin tone, dark skin tone");
        map.put("👩🏼‍🤝‍👨🏻", "woman and man holding hands, medium-light skin tone, light skin tone");
        map.put("👩🏼‍🤝‍👨🏽", "woman and man holding hands, medium-light skin tone, medium skin tone");
        map.put("👩🏼‍🤝‍👨🏾", "woman and man holding hands, medium-light skin tone, medium-dark skin tone");
        map.put("👩🏼‍🤝‍👨🏿", "woman and man holding hands, medium-light skin tone, dark skin tone");
        map.put("👩🏼‍🤝‍👩🏻", "women holding hands, medium-light skin tone, light skin tone");
        map.put("👩🏼‍🤝‍👩🏽", "women holding hands, medium-light skin tone, medium skin tone");
        map.put("👩🏼‍🤝‍👩🏾", "women holding hands, medium-light skin tone, medium-dark skin tone");
        map.put("👩🏼‍🤝‍👩🏿", "women holding hands, medium-light skin tone, dark skin tone");
        map.put("👩🏽‍❤️‍👨🏻", "couple with heart, woman, man, medium skin tone, light skin tone");
        map.put("👩🏽‍❤️‍👨🏼", "couple with heart, woman, man, medium skin tone, medium-light skin tone");
        map.put("👩🏽‍❤️‍👨🏽", "couple with heart, woman, man, medium skin tone");
        map.put("👩🏽‍❤️‍👨🏾", "couple with heart, woman, man, medium skin tone, medium-dark skin tone");
        map.put("👩🏽‍❤️‍👨🏿", "couple with heart, woman, man, medium skin tone, dark skin tone");
        map.put("👩🏽‍❤️‍👩🏻", "couple with heart, woman, woman, medium skin tone, light skin tone");
        map.put("👩🏽‍❤️‍👩🏼", "couple with heart, woman, woman, medium skin tone, medium-light skin tone");
        map.put("👩🏽‍❤️‍👩🏽", "couple with heart, woman, woman, medium skin tone");
        map.put("👩🏽‍❤️‍👩🏾", "couple with heart, woman, woman, medium skin tone, medium-dark skin tone");
        map.put("👩🏽‍❤️‍👩🏿", "couple with heart, woman, woman, medium skin tone, dark skin tone");
        map.put("👩🏽‍❤️‍💋‍👨🏻", "kiss, woman, man, medium skin tone, light skin tone");
        map.put("👩🏽‍❤️‍💋‍👨🏼", "kiss, woman, man, medium skin tone, medium-light skin tone");
        map.put("👩🏽‍❤️‍💋‍👨🏽", "kiss, woman, man, medium skin tone");
        map.put("👩🏽‍❤️‍💋‍👨🏾", "kiss, woman, man, medium skin tone, medium-dark skin tone");
        map.put("👩🏽‍❤️‍💋‍👨🏿", "kiss, woman, man, medium skin tone, dark skin tone");
        map.put("👩🏽‍❤️‍💋‍👩🏻", "kiss, woman, woman, medium skin tone, light skin tone");
        map.put("👩🏽‍❤️‍💋‍👩🏼", "kiss, woman, woman, medium skin tone, medium-light skin tone");
        map.put("👩🏽‍❤️‍💋‍👩🏽", "kiss, woman, woman, medium skin tone");
        map.put("👩🏽‍❤️‍💋‍👩🏾", "kiss, woman, woman, medium skin tone, medium-dark skin tone");
        map.put("👩🏽‍❤️‍💋‍👩🏿", "kiss, woman, woman, medium skin tone, dark skin tone");
        map.put("👩🏽‍🤝‍👨🏻", "woman and man holding hands, medium skin tone, light skin tone");
        map.put("👩🏽‍🤝‍👨🏼", "woman and man holding hands, medium skin tone, medium-light skin tone");
        map.put("👩🏽‍🤝‍👨🏾", "woman and man holding hands, medium skin tone, medium-dark skin tone");
        map.put("👩🏽‍🤝‍👨🏿", "woman and man holding hands, medium skin tone, dark skin tone");
        map.put("👩🏽‍🤝‍👩🏻", "women holding hands, medium skin tone, light skin tone");
        map.put("👩🏽‍🤝‍👩🏼", "women holding hands, medium skin tone, medium-light skin tone");
        map.put("👩🏽‍🤝‍👩🏾", "women holding hands, medium skin tone, medium-dark skin tone");
        map.put("👩🏽‍🤝‍👩🏿", "women holding hands, medium skin tone, dark skin tone");
        map.put("👩🏾‍❤️‍👨🏻", "couple with heart, woman, man, medium-dark skin tone, light skin tone");
        map.put("👩🏾‍❤️‍👨🏼", "couple with heart, woman, man, medium-dark skin tone, medium-light skin tone");
        map.put("👩🏾‍❤️‍👨🏽", "couple with heart, woman, man, medium-dark skin tone, medium skin tone");
        map.put("👩🏾‍❤️‍👨🏾", "couple with heart, woman, man, medium-dark skin tone");
        map.put("👩🏾‍❤️‍👨🏿", "couple with heart, woman, man, medium-dark skin tone, dark skin tone");
        map.put("👩🏾‍❤️‍👩🏻", "couple with heart, woman, woman, medium-dark skin tone, light skin tone");
        map.put("👩🏾‍❤️‍👩🏼", "couple with heart, woman, woman, medium-dark skin tone, medium-light skin tone");
        map.put("👩🏾‍❤️‍👩🏽", "couple with heart, woman, woman, medium-dark skin tone, medium skin tone");
        map.put("👩🏾‍❤️‍👩🏾", "couple with heart, woman, woman, medium-dark skin tone");
        map.put("👩🏾‍❤️‍👩🏿", "couple with heart, woman, woman, medium-dark skin tone, dark skin tone");
        map.put("👩🏾‍❤️‍💋‍👨🏻", "kiss, woman, man, medium-dark skin tone, light skin tone");
        map.put("👩🏾‍❤️‍💋‍👨🏼", "kiss, woman, man, medium-dark skin tone, medium-light skin tone");
        map.put("👩🏾‍❤️‍💋‍👨🏽", "kiss, woman, man, medium-dark skin tone, medium skin tone");
        map.put("👩🏾‍❤️‍💋‍👨🏾", "kiss, woman, man, medium-dark skin tone");
        map.put("👩🏾‍❤️‍💋‍👨🏿", "kiss, woman, man, medium-dark skin tone, dark skin tone");
        map.put("👩🏾‍❤️‍💋‍👩🏻", "kiss, woman, woman, medium-dark skin tone, light skin tone");
        map.put("👩🏾‍❤️‍💋‍👩🏼", "kiss, woman, woman, medium-dark skin tone, medium-light skin tone");
        map.put("👩🏾‍❤️‍💋‍👩🏽", "kiss, woman, woman, medium-dark skin tone, medium skin tone");
        map.put("👩🏾‍❤️‍💋‍👩🏾", "kiss, woman, woman, medium-dark skin tone");
        map.put("👩🏾‍❤️‍💋‍👩🏿", "kiss, woman, woman, medium-dark skin tone, dark skin tone");
        map.put("👩🏾‍🤝‍👨🏻", "woman and man holding hands, medium-dark skin tone, light skin tone");
        map.put("👩🏾‍🤝‍👨🏼", "woman and man holding hands, medium-dark skin tone, medium-light skin tone");
        map.put("👩🏾‍🤝‍👨🏽", "woman and man holding hands, medium-dark skin tone, medium skin tone");
        map.put("👩🏾‍🤝‍👨🏿", "woman and man holding hands, medium-dark skin tone, dark skin tone");
        map.put("👩🏾‍🤝‍👩🏻", "women holding hands, medium-dark skin tone, light skin tone");
        map.put("👩🏾‍🤝‍👩🏼", "women holding hands, medium-dark skin tone, medium-light skin tone");
        map.put("👩🏾‍🤝‍👩🏽", "women holding hands, medium-dark skin tone, medium skin tone");
        map.put("👩🏾‍🤝‍👩🏿", "women holding hands, medium-dark skin tone, dark skin tone");
        map.put("👩🏿‍❤️‍👨🏻", "couple with heart, woman, man, dark skin tone, light skin tone");
        map.put("👩🏿‍❤️‍👨🏼", "couple with heart, woman, man, dark skin tone, medium-light skin tone");
        map.put("👩🏿‍❤️‍👨🏽", "couple with heart, woman, man, dark skin tone, medium skin tone");
        map.put("👩🏿‍❤️‍👨🏾", "couple with heart, woman, man, dark skin tone, medium-dark skin tone");
        map.put("👩🏿‍❤️‍👨🏿", "couple with heart, woman, man, dark skin tone");
        map.put("👩🏿‍❤️‍👩🏻", "couple with heart, woman, woman, dark skin tone, light skin tone");
        map.put("👩🏿‍❤️‍👩🏼", "couple with heart, woman, woman, dark skin tone, medium-light skin tone");
        map.put("👩🏿‍❤️‍👩🏽", "couple with heart, woman, woman, dark skin tone, medium skin tone");
        map.put("👩🏿‍❤️‍👩🏾", "couple with heart, woman, woman, dark skin tone, medium-dark skin tone");
        map.put("👩🏿‍❤️‍👩🏿", "couple with heart, woman, woman, dark skin tone");
        map.put("👩🏿‍❤️‍💋‍👨🏻", "kiss, woman, man, dark skin tone, light skin tone");
        map.put("👩🏿‍❤️‍💋‍👨🏼", "kiss, woman, man, dark skin tone, medium-light skin tone");
        map.put("👩🏿‍❤️‍💋‍👨🏽", "kiss, woman, man, dark skin tone, medium skin tone");
        map.put("👩🏿‍❤️‍💋‍👨🏾", "kiss, woman, man, dark skin tone, medium-dark skin tone");
        map.put("👩🏿‍❤️‍💋‍👨🏿", "kiss, woman, man, dark skin tone");
        map.put("👩🏿‍❤️‍💋‍👩🏻", "kiss, woman, woman, dark skin tone, light skin tone");
        map.put("👩🏿‍❤️‍💋‍👩🏼", "kiss, woman, woman, dark skin tone, medium-light skin tone");
        map.put("👩🏿‍❤️‍💋‍👩🏽", "kiss, woman, woman, dark skin tone, medium skin tone");
        map.put("👩🏿‍❤️‍💋‍👩🏾", "kiss, woman, woman, dark skin tone, medium-dark skin tone");
        map.put("👩🏿‍❤️‍💋‍👩🏿", "kiss, woman, woman, dark skin tone");
        map.put("👩🏿‍🤝‍👨🏻", "woman and man holding hands, dark skin tone, light skin tone");
        map.put("👩🏿‍🤝‍👨🏼", "woman and man holding hands, dark skin tone, medium-light skin tone");
        map.put("👩🏿‍🤝‍👨🏽", "woman and man holding hands, dark skin tone, medium skin tone");
        map.put("👩🏿‍🤝‍👨🏾", "woman and man holding hands, dark skin tone, medium-dark skin tone");
        map.put("👩🏿‍🤝‍👩🏻", "women holding hands, dark skin tone, light skin tone");
        map.put("👩🏿‍🤝‍👩🏼", "women holding hands, dark skin tone, medium-light skin tone");
        map.put("👩🏿‍🤝‍👩🏽", "women holding hands, dark skin tone, medium skin tone");
        map.put("👩🏿‍🤝‍👩🏾", "women holding hands, dark skin tone, medium-dark skin tone");
        map.put("🧑‍🤝‍🧑", "people holding hands");
        map.put("🧑‍🧑‍🧒", "family, adult, adult, child");
        map.put("🧑‍🧑‍🧒‍🧒", "family, adult, adult, child, child");
        map.put("🧑‍🧒", "family, adult, child");
        map.put("🧑‍🧒‍🧒", "family, adult, child, child");
        map.put("🧑🏻‍❤️‍💋‍🧑🏼", "kiss, person, person, light skin tone, medium-light skin tone");
        map.put("🧑🏻‍❤️‍💋‍🧑🏽", "kiss, person, person, light skin tone, medium skin tone");
        map.put("🧑🏻‍❤️‍💋‍🧑🏾", "kiss, person, person, light skin tone, medium-dark skin tone");
        map.put("🧑🏻‍❤️‍💋‍🧑🏿", "kiss, person, person, light skin tone, dark skin tone");
        map.put("🧑🏻‍❤️‍🧑🏼", "couple with heart, person, person, light skin tone, medium-light skin tone");
        map.put("🧑🏻‍❤️‍🧑🏽", "couple with heart, person, person, light skin tone, medium skin tone");
        map.put("🧑🏻‍❤️‍🧑🏾", "couple with heart, person, person, light skin tone, medium-dark skin tone");
        map.put("🧑🏻‍❤️‍🧑🏿", "couple with heart, person, person, light skin tone, dark skin tone");
        map.put("🧑🏻‍🤝‍🧑🏻", "people holding hands, light skin tone");
        map.put("🧑🏻‍🤝‍🧑🏼", "people holding hands, light skin tone, medium-light skin tone");
        map.put("🧑🏻‍🤝‍🧑🏽", "people holding hands, light skin tone, medium skin tone");
        map.put("🧑🏻‍🤝‍🧑🏾", "people holding hands, light skin tone, medium-dark skin tone");
        map.put("🧑🏻‍🤝‍🧑🏿", "people holding hands, light skin tone, dark skin tone");
        map.put("🧑🏼‍❤️‍💋‍🧑🏻", "kiss, person, person, medium-light skin tone, light skin tone");
        map.put("🧑🏼‍❤️‍💋‍🧑🏽", "kiss, person, person, medium-light skin tone, medium skin tone");
        map.put("🧑🏼‍❤️‍💋‍🧑🏾", "kiss, person, person, medium-light skin tone, medium-dark skin tone");
        map.put("🧑🏼‍❤️‍💋‍🧑🏿", "kiss, person, person, medium-light skin tone, dark skin tone");
        map.put("🧑🏼‍❤️‍🧑🏻", "couple with heart, person, person, medium-light skin tone, light skin tone");
        map.put("🧑🏼‍❤️‍🧑🏽", "couple with heart, person, person, medium-light skin tone, medium skin tone");
        map.put("🧑🏼‍❤️‍🧑🏾", "couple with heart, person, person, medium-light skin tone, medium-dark skin tone");
        map.put("🧑🏼‍❤️‍🧑🏿", "couple with heart, person, person, medium-light skin tone, dark skin tone");
        map.put("🧑🏼‍🤝‍🧑🏻", "people holding hands, medium-light skin tone, light skin tone");
        map.put("🧑🏼‍🤝‍🧑🏼", "people holding hands, medium-light skin tone");
        map.put("🧑🏼‍🤝‍🧑🏽", "people holding hands, medium-light skin tone, medium skin tone");
        map.put("🧑🏼‍🤝‍🧑🏾", "people holding hands, medium-light skin tone, medium-dark skin tone");
        map.put("🧑🏼‍🤝‍🧑🏿", "people holding hands, medium-light skin tone, dark skin tone");
        map.put("🧑🏽‍❤️‍💋‍🧑🏻", "kiss, person, person, medium skin tone, light skin tone");
        map.put("🧑🏽‍❤️‍💋‍🧑🏼", "kiss, person, person, medium skin tone, medium-light skin tone");
        map.put("🧑🏽‍❤️‍💋‍🧑🏾", "kiss, person, person, medium skin tone, medium-dark skin tone");
        map.put("🧑🏽‍❤️‍💋‍🧑🏿", "kiss, person, person, medium skin tone, dark skin tone");
        map.put("🧑🏽‍❤️‍🧑🏻", "couple with heart, person, person, medium skin tone, light skin tone");
        map.put("🧑🏽‍❤️‍🧑🏼", "couple with heart, person, person, medium skin tone, medium-light skin tone");
        map.put("🧑🏽‍❤️‍🧑🏾", "couple with heart, person, person, medium skin tone, medium-dark skin tone");
        map.put("🧑🏽‍❤️‍🧑🏿", "couple with heart, person, person, medium skin tone, dark skin tone");
        map.put("🧑🏽‍🤝‍🧑🏻", "people holding hands, medium skin tone, light skin tone");
        map.put("🧑🏽‍🤝‍🧑🏼", "people holding hands, medium skin tone, medium-light skin tone");
        map.put("🧑🏽‍🤝‍🧑🏽", "people holding hands, medium skin tone");
        map.put("🧑🏽‍🤝‍🧑🏾", "people holding hands, medium skin tone, medium-dark skin tone");
        map.put("🧑🏽‍🤝‍🧑🏿", "people holding hands, medium skin tone, dark skin tone");
        map.put("🧑🏾‍❤️‍💋‍🧑🏻", "kiss, person, person, medium-dark skin tone, light skin tone");
        map.put("🧑🏾‍❤️‍💋‍🧑🏼", "kiss, person, person, medium-dark skin tone, medium-light skin tone");
        map.put("🧑🏾‍❤️‍💋‍🧑🏽", "kiss, person, person, medium-dark skin tone, medium skin tone");
        map.put("🧑🏾‍❤️‍💋‍🧑🏿", "kiss, person, person, medium-dark skin tone, dark skin tone");
        map.put("🧑🏾‍❤️‍🧑🏻", "couple with heart, person, person, medium-dark skin tone, light skin tone");
        map.put("🧑🏾‍❤️‍🧑🏼", "couple with heart, person, person, medium-dark skin tone, medium-light skin tone");
        map.put("🧑🏾‍❤️‍🧑🏽", "couple with heart, person, person, medium-dark skin tone, medium skin tone");
        map.put("🧑🏾‍❤️‍🧑🏿", "couple with heart, person, person, medium-dark skin tone, dark skin tone");
        map.put("🧑🏾‍🤝‍🧑🏻", "people holding hands, medium-dark skin tone, light skin tone");
        map.put("🧑🏾‍🤝‍🧑🏼", "people holding hands, medium-dark skin tone, medium-light skin tone");
        map.put("🧑🏾‍🤝‍🧑🏽", "people holding hands, medium-dark skin tone, medium skin tone");
        map.put("🧑🏾‍🤝‍🧑🏾", "people holding hands, medium-dark skin tone");
        map.put("🧑🏾‍🤝‍🧑🏿", "people holding hands, medium-dark skin tone, dark skin tone");
        map.put("🧑🏿‍❤️‍💋‍🧑🏻", "kiss, person, person, dark skin tone, light skin tone");
        map.put("🧑🏿‍❤️‍💋‍🧑🏼", "kiss, person, person, dark skin tone, medium-light skin tone");
        map.put("🧑🏿‍❤️‍💋‍🧑🏽", "kiss, person, person, dark skin tone, medium skin tone");
        map.put("🧑🏿‍❤️‍💋‍🧑🏾", "kiss, person, person, dark skin tone, medium-dark skin tone");
        map.put("🧑🏿‍❤️‍🧑🏻", "couple with heart, person, person, dark skin tone, light skin tone");
        map.put("🧑🏿‍❤️‍🧑🏼", "couple with heart, person, person, dark skin tone, medium-light skin tone");
        map.put("🧑🏿‍❤️‍🧑🏽", "couple with heart, person, person, dark skin tone, medium skin tone");
        map.put("🧑🏿‍❤️‍🧑🏾", "couple with heart, person, person, dark skin tone, medium-dark skin tone");
        map.put("🧑🏿‍🤝‍🧑🏻", "people holding hands, dark skin tone, light skin tone");
        map.put("🧑🏿‍🤝‍🧑🏼", "people holding hands, dark skin tone, medium-light skin tone");
        map.put("🧑🏿‍🤝‍🧑🏽", "people holding hands, dark skin tone, medium skin tone");
        map.put("🧑🏿‍🤝‍🧑🏾", "people holding hands, dark skin tone, medium-dark skin tone");
        map.put("🧑🏿‍🤝‍🧑🏿", "people holding hands, dark skin tone");
        map.put("🫱🏻‍🫲🏼", "handshake, light skin tone, medium-light skin tone");
        map.put("🫱🏻‍🫲🏽", "handshake, light skin tone, medium skin tone");
        map.put("🫱🏻‍🫲🏾", "handshake, light skin tone, medium-dark skin tone");
        map.put("🫱🏻‍🫲🏿", "handshake, light skin tone, dark skin tone");
        map.put("🫱🏼‍🫲🏻", "handshake, medium-light skin tone, light skin tone");
        map.put("🫱🏼‍🫲🏽", "handshake, medium-light skin tone, medium skin tone");
        map.put("🫱🏼‍🫲🏾", "handshake, medium-light skin tone, medium-dark skin tone");
        map.put("🫱🏼‍🫲🏿", "handshake, medium-light skin tone, dark skin tone");
        map.put("🫱🏽‍🫲🏻", "handshake, medium skin tone, light skin tone");
        map.put("🫱🏽‍🫲🏼", "handshake, medium skin tone, medium-light skin tone");
        map.put("🫱🏽‍🫲🏾", "handshake, medium skin tone, medium-dark skin tone");
        map.put("🫱🏽‍🫲🏿", "handshake, medium skin tone, dark skin tone");
        map.put("🫱🏾‍🫲🏻", "handshake, medium-dark skin tone, light skin tone");
        map.put("🫱🏾‍🫲🏼", "handshake, medium-dark skin tone, medium-light skin tone");
        map.put("🫱🏾‍🫲🏽", "handshake, medium-dark skin tone, medium skin tone");
        map.put("🫱🏾‍🫲🏿", "handshake, medium-dark skin tone, dark skin tone");
        map.put("🫱🏿‍🫲🏻", "handshake, dark skin tone, light skin tone");
        map.put("🫱🏿‍🫲🏼", "handshake, dark skin tone, medium-light skin tone");
        map.put("🫱🏿‍🫲🏽", "handshake, dark skin tone, medium skin tone");
        map.put("🫱🏿‍🫲🏾", "handshake, dark skin tone, medium-dark skin tone");
        map.put("🏃‍➡️", "person running facing right");
        map.put("🏃🏻‍➡️", "person running facing right, light skin tone");
        map.put("🏃🏼‍➡️", "person running facing right, medium-light skin tone");
        map.put("🏃🏽‍➡️", "person running facing right, medium skin tone");
        map.put("🏃🏾‍➡️", "person running facing right, medium-dark skin tone");
        map.put("🏃🏿‍➡️", "person running facing right, dark skin tone");
        map.put("👨‍⚕️", "man health worker");
        map.put("👨‍⚖️", "man judge");
        map.put("👨‍✈️", "man pilot");
        map.put("👨‍🌾", "man farmer");
        map.put("👨‍🍳", "man cook");
        map.put("👨‍🍼", "man feeding baby");
        map.put("👨‍🎓", "man student");
        map.put("👨‍🎤", "man singer");
        map.put("👨‍🎨", "man artist");
        map.put("👨‍🏫", "man teacher");
        map.put("👨‍🏭", "man factory worker");
        map.put("👨‍💻", "man technologist");
        map.put("👨‍💼", "man office worker");
        map.put("👨‍🔧", "man mechanic");
        map.put("👨‍🔬", "man scientist");
        map.put("👨‍🚀", "man astronaut");
        map.put("👨‍🚒", "man firefighter");
        map.put("👨‍🦯", "man with white cane");
        map.put("👨‍🦯‍➡️", "man with white cane facing right");
        map.put("👨‍🦼", "man in motorized wheelchair");
        map.put("👨‍🦼‍➡️", "man in motorized wheelchair facing right");
        map.put("👨‍🦽", "man in manual wheelchair");
        map.put("👨‍🦽‍➡️", "man in manual wheelchair facing right");
        map.put("👨🏻‍⚕️", "man health worker, light skin tone");
        map.put("👨🏻‍⚖️", "man judge, light skin tone");
        map.put("👨🏻‍✈️", "man pilot, light skin tone");
        map.put("👨🏻‍🌾", "man farmer, light skin tone");
        map.put("👨🏻‍🍳", "man cook, light skin tone");
        map.put("👨🏻‍🍼", "man feeding baby, light skin tone");
        map.put("👨🏻‍🎓", "man student, light skin tone");
        map.put("👨🏻‍🎤", "man singer, light skin tone");
        map.put("👨🏻‍🎨", "man artist, light skin tone");
        map.put("👨🏻‍🏫", "man teacher, light skin tone");
        map.put("👨🏻‍🏭", "man factory worker, light skin tone");
        map.put("👨🏻‍💻", "man technologist, light skin tone");
        map.put("👨🏻‍💼", "man office worker, light skin tone");
        map.put("👨🏻‍🔧", "man mechanic, light skin tone");
        map.put("👨🏻‍🔬", "man scientist, light skin tone");
        map.put("👨🏻‍🚀", "man astronaut, light skin tone");
        map.put("👨🏻‍🚒", "man firefighter, light skin tone");
        map.put("👨🏻‍🦯", "man with white cane, light skin tone");
        map.put("👨🏻‍🦯‍➡️", "man with white cane facing right, light skin tone");
        map.put("👨🏻‍🦼", "man in motorized wheelchair, light skin tone");
        map.put("👨🏻‍🦼‍➡️", "man in motorized wheelchair facing right, light skin tone");
        map.put("👨🏻‍🦽", "man in manual wheelchair, light skin tone");
        map.put("👨🏻‍🦽‍➡️", "man in manual wheelchair facing right, light skin tone");
        map.put("👨🏼‍⚕️", "man health worker, medium-light skin tone");
        map.put("👨🏼‍⚖️", "man judge, medium-light skin tone");
        map.put("👨🏼‍✈️", "man pilot, medium-light skin tone");
        map.put("👨🏼‍🌾", "man farmer, medium-light skin tone");
        map.put("👨🏼‍🍳", "man cook, medium-light skin tone");
        map.put("👨🏼‍🍼", "man feeding baby, medium-light skin tone");
        map.put("👨🏼‍🎓", "man student, medium-light skin tone");
        map.put("👨🏼‍🎤", "man singer, medium-light skin tone");
        map.put("👨🏼‍🎨", "man artist, medium-light skin tone");
        map.put("👨🏼‍🏫", "man teacher, medium-light skin tone");
        map.put("👨🏼‍🏭", "man factory worker, medium-light skin tone");
        map.put("👨🏼‍💻", "man technologist, medium-light skin tone");
        map.put("👨🏼‍💼", "man office worker, medium-light skin tone");
        map.put("👨🏼‍🔧", "man mechanic, medium-light skin tone");
        map.put("👨🏼‍🔬", "man scientist, medium-light skin tone");
        map.put("👨🏼‍🚀", "man astronaut, medium-light skin tone");
        map.put("👨🏼‍🚒", "man firefighter, medium-light skin tone");
        map.put("👨🏼‍🦯", "man with white cane, medium-light skin tone");
        map.put("👨🏼‍🦯‍➡️", "man with white cane facing right, medium-light skin tone");
        map.put("👨🏼‍🦼", "man in motorized wheelchair, medium-light skin tone");
        map.put("👨🏼‍🦼‍➡️", "man in motorized wheelchair facing right, medium-light skin tone");
        map.put("👨🏼‍🦽", "man in manual wheelchair, medium-light skin tone");
        map.put("👨🏼‍🦽‍➡️", "man in manual wheelchair facing right, medium-light skin tone");
        map.put("👨🏽‍⚕️", "man health worker, medium skin tone");
        map.put("👨🏽‍⚖️", "man judge, medium skin tone");
        map.put("👨🏽‍✈️", "man pilot, medium skin tone");
        map.put("👨🏽‍🌾", "man farmer, medium skin tone");
        map.put("👨🏽‍🍳", "man cook, medium skin tone");
        map.put("👨🏽‍🍼", "man feeding baby, medium skin tone");
        map.put("👨🏽‍🎓", "man student, medium skin tone");
        map.put("👨🏽‍🎤", "man singer, medium skin tone");
        map.put("👨🏽‍🎨", "man artist, medium skin tone");
        map.put("👨🏽‍🏫", "man teacher, medium skin tone");
        map.put("👨🏽‍🏭", "man factory worker, medium skin tone");
        map.put("👨🏽‍💻", "man technologist, medium skin tone");
        map.put("👨🏽‍💼", "man office worker, medium skin tone");
        map.put("👨🏽‍🔧", "man mechanic, medium skin tone");
        map.put("👨🏽‍🔬", "man scientist, medium skin tone");
        map.put("👨🏽‍🚀", "man astronaut, medium skin tone");
        map.put("👨🏽‍🚒", "man firefighter, medium skin tone");
        map.put("👨🏽‍🦯", "man with white cane, medium skin tone");
        map.put("👨🏽‍🦯‍➡️", "man with white cane facing right, medium skin tone");
        map.put("👨🏽‍🦼", "man in motorized wheelchair, medium skin tone");
        map.put("👨🏽‍🦼‍➡️", "man in motorized wheelchair facing right, medium skin tone");
        map.put("👨🏽‍🦽", "man in manual wheelchair, medium skin tone");
        map.put("👨🏽‍🦽‍➡️", "man in manual wheelchair facing right, medium skin tone");
        map.put("👨🏾‍⚕️", "man health worker, medium-dark skin tone");
        map.put("👨🏾‍⚖️", "man judge, medium-dark skin tone");
        map.put("👨🏾‍✈️", "man pilot, medium-dark skin tone");
        map.put("👨🏾‍🌾", "man farmer, medium-dark skin tone");
        map.put("👨🏾‍🍳", "man cook, medium-dark skin tone");
        map.put("👨🏾‍🍼", "man feeding baby, medium-dark skin tone");
        map.put("👨🏾‍🎓", "man student, medium-dark skin tone");
        map.put("👨🏾‍🎤", "man singer, medium-dark skin tone");
        map.put("👨🏾‍🎨", "man artist, medium-dark skin tone");
        map.put("👨🏾‍🏫", "man teacher, medium-dark skin tone");
        map.put("👨🏾‍🏭", "man factory worker, medium-dark skin tone");
        map.put("👨🏾‍💻", "man technologist, medium-dark skin tone");
        map.put("👨🏾‍💼", "man office worker, medium-dark skin tone");
        map.put("👨🏾‍🔧", "man mechanic, medium-dark skin tone");
        map.put("👨🏾‍🔬", "man scientist, medium-dark skin tone");
        map.put("👨🏾‍🚀", "man astronaut, medium-dark skin tone");
        map.put("👨🏾‍🚒", "man firefighter, medium-dark skin tone");
        map.put("👨🏾‍🦯", "man with white cane, medium-dark skin tone");
        map.put("👨🏾‍🦯‍➡️", "man with white cane facing right, medium-dark skin tone");
        map.put("👨🏾‍🦼", "man in motorized wheelchair, medium-dark skin tone");
        map.put("👨🏾‍🦼‍➡️", "man in motorized wheelchair facing right, medium-dark skin tone");
        map.put("👨🏾‍🦽", "man in manual wheelchair, medium-dark skin tone");
        map.put("👨🏾‍🦽‍➡️", "man in manual wheelchair facing right, medium-dark skin tone");
        map.put("👨🏿‍⚕️", "man health worker, dark skin tone");
        map.put("👨🏿‍⚖️", "man judge, dark skin tone");
        map.put("👨🏿‍✈️", "man pilot, dark skin tone");
        map.put("👨🏿‍🌾", "man farmer, dark skin tone");
        map.put("👨🏿‍🍳", "man cook, dark skin tone");
        map.put("👨🏿‍🍼", "man feeding baby, dark skin tone");
        map.put("👨🏿‍🎓", "man student, dark skin tone");
        map.put("👨🏿‍🎤", "man singer, dark skin tone");
        map.put("👨🏿‍🎨", "man artist, dark skin tone");
        map.put("👨🏿‍🏫", "man teacher, dark skin tone");
        map.put("👨🏿‍🏭", "man factory worker, dark skin tone");
        map.put("👨🏿‍💻", "man technologist, dark skin tone");
        map.put("👨🏿‍💼", "man office worker, dark skin tone");
        map.put("👨🏿‍🔧", "man mechanic, dark skin tone");
        map.put("👨🏿‍🔬", "man scientist, dark skin tone");
        map.put("👨🏿‍🚀", "man astronaut, dark skin tone");
        map.put("👨🏿‍🚒", "man firefighter, dark skin tone");
        map.put("👨🏿‍🦯", "man with white cane, dark skin tone");
        map.put("👨🏿‍🦯‍➡️", "man with white cane facing right, dark skin tone");
        map.put("👨🏿‍🦼", "man in motorized wheelchair, dark skin tone");
        map.put("👨🏿‍🦼‍➡️", "man in motorized wheelchair facing right, dark skin tone");
        map.put("👨🏿‍🦽", "man in manual wheelchair, dark skin tone");
        map.put("👨🏿‍🦽‍➡️", "man in manual wheelchair facing right, dark skin tone");
        map.put("👩‍⚕️", "woman health worker");
        map.put("👩‍⚖️", "woman judge");
        map.put("👩‍✈️", "woman pilot");
        map.put("👩‍🌾", "woman farmer");
        map.put("👩‍🍳", "woman cook");
        map.put("👩‍🍼", "woman feeding baby");
        map.put("👩‍🎓", "woman student");
        map.put("👩‍🎤", "woman singer");
        map.put("👩‍🎨", "woman artist");
        map.put("👩‍🏫", "woman teacher");
        map.put("👩‍🏭", "woman factory worker");
        map.put("👩‍💻", "woman technologist");
        map.put("👩‍💼", "woman office worker");
        map.put("👩‍🔧", "woman mechanic");
        map.put("👩‍🔬", "woman scientist");
        map.put("👩‍🚀", "woman astronaut");
        map.put("👩‍🚒", "woman firefighter");
        map.put("👩‍🦯", "woman with white cane");
        map.put("👩‍🦯‍➡️", "woman with white cane facing right");
        map.put("👩‍🦼", "woman in motorized wheelchair");
        map.put("👩‍🦼‍➡️", "woman in motorized wheelchair facing right");
        map.put("👩‍🦽", "woman in manual wheelchair");
        map.put("👩‍🦽‍➡️", "woman in manual wheelchair facing right");
        map.put("👩🏻‍⚕️", "woman health worker, light skin tone");
        map.put("👩🏻‍⚖️", "woman judge, light skin tone");
        map.put("👩🏻‍✈️", "woman pilot, light skin tone");
        map.put("👩🏻‍🌾", "woman farmer, light skin tone");
        map.put("👩🏻‍🍳", "woman cook, light skin tone");
        map.put("👩🏻‍🍼", "woman feeding baby, light skin tone");
        map.put("👩🏻‍🎓", "woman student, light skin tone");
        map.put("👩🏻‍🎤", "woman singer, light skin tone");
        map.put("👩🏻‍🎨", "woman artist, light skin tone");
        map.put("👩🏻‍🏫", "woman teacher, light skin tone");
        map.put("👩🏻‍🏭", "woman factory worker, light skin tone");
        map.put("👩🏻‍💻", "woman technologist, light skin tone");
        map.put("👩🏻‍💼", "woman office worker, light skin tone");
        map.put("👩🏻‍🔧", "woman mechanic, light skin tone");
        map.put("👩🏻‍🔬", "woman scientist, light skin tone");
        map.put("👩🏻‍🚀", "woman astronaut, light skin tone");
        map.put("👩🏻‍🚒", "woman firefighter, light skin tone");
        map.put("👩🏻‍🦯", "woman with white cane, light skin tone");
        map.put("👩🏻‍🦯‍➡️", "woman with white cane facing right, light skin tone");
        map.put("👩🏻‍🦼", "woman in motorized wheelchair, light skin tone");
        map.put("👩🏻‍🦼‍➡️", "woman in motorized wheelchair facing right, light skin tone");
        map.put("👩🏻‍🦽", "woman in manual wheelchair, light skin tone");
        map.put("👩🏻‍🦽‍➡️", "woman in manual wheelchair facing right, light skin tone");
        map.put("👩🏼‍⚕️", "woman health worker, medium-light skin tone");
        map.put("👩🏼‍⚖️", "woman judge, medium-light skin tone");
        map.put("👩🏼‍✈️", "woman pilot, medium-light skin tone");
        map.put("👩🏼‍🌾", "woman farmer, medium-light skin tone");
        map.put("👩🏼‍🍳", "woman cook, medium-light skin tone");
        map.put("👩🏼‍🍼", "woman feeding baby, medium-light skin tone");
        map.put("👩🏼‍🎓", "woman student, medium-light skin tone");
        map.put("👩🏼‍🎤", "woman singer, medium-light skin tone");
        map.put("👩🏼‍🎨", "woman artist, medium-light skin tone");
        map.put("👩🏼‍🏫", "woman teacher, medium-light skin tone");
        map.put("👩🏼‍🏭", "woman factory worker, medium-light skin tone");
        map.put("👩🏼‍💻", "woman technologist, medium-light skin tone");
        map.put("👩🏼‍💼", "woman office worker, medium-light skin tone");
        map.put("👩🏼‍🔧", "woman mechanic, medium-light skin tone");
        map.put("👩🏼‍🔬", "woman scientist, medium-light skin tone");
        map.put("👩🏼‍🚀", "woman astronaut, medium-light skin tone");
        map.put("👩🏼‍🚒", "woman firefighter, medium-light skin tone");
        map.put("👩🏼‍🦯", "woman with white cane, medium-light skin tone");
        map.put("👩🏼‍🦯‍➡️", "woman with white cane facing right, medium-light skin tone");
        map.put("👩🏼‍🦼", "woman in motorized wheelchair, medium-light skin tone");
        map.put("👩🏼‍🦼‍➡️", "woman in motorized wheelchair facing right, medium-light skin tone");
        map.put("👩🏼‍🦽", "woman in manual wheelchair, medium-light skin tone");
        map.put("👩🏼‍🦽‍➡️", "woman in manual wheelchair facing right, medium-light skin tone");
        map.put("👩🏽‍⚕️", "woman health worker, medium skin tone");
        map.put("👩🏽‍⚖️", "woman judge, medium skin tone");
        map.put("👩🏽‍✈️", "woman pilot, medium skin tone");
        map.put("👩🏽‍🌾", "woman farmer, medium skin tone");
        map.put("👩🏽‍🍳", "woman cook, medium skin tone");
        map.put("👩🏽‍🍼", "woman feeding baby, medium skin tone");
        map.put("👩🏽‍🎓", "woman student, medium skin tone");
        map.put("👩🏽‍🎤", "woman singer, medium skin tone");
        map.put("👩🏽‍🎨", "woman artist, medium skin tone");
        map.put("👩🏽‍🏫", "woman teacher, medium skin tone");
        map.put("👩🏽‍🏭", "woman factory worker, medium skin tone");
        map.put("👩🏽‍💻", "woman technologist, medium skin tone");
        map.put("👩🏽‍💼", "woman office worker, medium skin tone");
        map.put("👩🏽‍🔧", "woman mechanic, medium skin tone");
        map.put("👩🏽‍🔬", "woman scientist, medium skin tone");
        map.put("👩🏽‍🚀", "woman astronaut, medium skin tone");
        map.put("👩🏽‍🚒", "woman firefighter, medium skin tone");
        map.put("👩🏽‍🦯", "woman with white cane, medium skin tone");
        map.put("👩🏽‍🦯‍➡️", "woman with white cane facing right, medium skin tone");
        map.put("👩🏽‍🦼", "woman in motorized wheelchair, medium skin tone");
        map.put("👩🏽‍🦼‍➡️", "woman in motorized wheelchair facing right, medium skin tone");
        map.put("👩🏽‍🦽", "woman in manual wheelchair, medium skin tone");
        map.put("👩🏽‍🦽‍➡️", "woman in manual wheelchair facing right, medium skin tone");
        map.put("👩🏾‍⚕️", "woman health worker, medium-dark skin tone");
        map.put("👩🏾‍⚖️", "woman judge, medium-dark skin tone");
        map.put("👩🏾‍✈️", "woman pilot, medium-dark skin tone");
        map.put("👩🏾‍🌾", "woman farmer, medium-dark skin tone");
        map.put("👩🏾‍🍳", "woman cook, medium-dark skin tone");
        map.put("👩🏾‍🍼", "woman feeding baby, medium-dark skin tone");
        map.put("👩🏾‍🎓", "woman student, medium-dark skin tone");
        map.put("👩🏾‍🎤", "woman singer, medium-dark skin tone");
        map.put("👩🏾‍🎨", "woman artist, medium-dark skin tone");
        map.put("👩🏾‍🏫", "woman teacher, medium-dark skin tone");
        map.put("👩🏾‍🏭", "woman factory worker, medium-dark skin tone");
        map.put("👩🏾‍💻", "woman technologist, medium-dark skin tone");
        map.put("👩🏾‍💼", "woman office worker, medium-dark skin tone");
        map.put("👩🏾‍🔧", "woman mechanic, medium-dark skin tone");
        map.put("👩🏾‍🔬", "woman scientist, medium-dark skin tone");
        map.put("👩🏾‍🚀", "woman astronaut, medium-dark skin tone");
        map.put("👩🏾‍🚒", "woman firefighter, medium-dark skin tone");
        map.put("👩🏾‍🦯", "woman with white cane, medium-dark skin tone");
        map.put("👩🏾‍🦯‍➡️", "woman with white cane facing right, medium-dark skin tone");
        map.put("👩🏾‍🦼", "woman in motorized wheelchair, medium-dark skin tone");
        map.put("👩🏾‍🦼‍➡️", "woman in motorized wheelchair facing right, medium-dark skin tone");
        map.put("👩🏾‍🦽", "woman in manual wheelchair, medium-dark skin tone");
        map.put("👩🏾‍🦽‍➡️", "woman in manual wheelchair facing right, medium-dark skin tone");
        map.put("👩🏿‍⚕️", "woman health worker, dark skin tone");
        map.put("👩🏿‍⚖️", "woman judge, dark skin tone");
        map.put("👩🏿‍✈️", "woman pilot, dark skin tone");
        map.put("👩🏿‍🌾", "woman farmer, dark skin tone");
        map.put("👩🏿‍🍳", "woman cook, dark skin tone");
        map.put("👩🏿‍🍼", "woman feeding baby, dark skin tone");
        map.put("👩🏿‍🎓", "woman student, dark skin tone");
        map.put("👩🏿‍🎤", "woman singer, dark skin tone");
        map.put("👩🏿‍🎨", "woman artist, dark skin tone");
        map.put("👩🏿‍🏫", "woman teacher, dark skin tone");
        map.put("👩🏿‍🏭", "woman factory worker, dark skin tone");
        map.put("👩🏿‍💻", "woman technologist, dark skin tone");
        map.put("👩🏿‍💼", "woman office worker, dark skin tone");
        map.put("👩🏿‍🔧", "woman mechanic, dark skin tone");
        map.put("👩🏿‍🔬", "woman scientist, dark skin tone");
        map.put("👩🏿‍🚀", "woman astronaut, dark skin tone");
        map.put("👩🏿‍🚒", "woman firefighter, dark skin tone");
        map.put("👩🏿‍🦯", "woman with white cane, dark skin tone");
        map.put("👩🏿‍🦯‍➡️", "woman with white cane facing right, dark skin tone");
        map.put("👩🏿‍🦼", "woman in motorized wheelchair, dark skin tone");
        map.put("👩🏿‍🦼‍➡️", "woman in motorized wheelchair facing right, dark skin tone");
        map.put("👩🏿‍🦽", "woman in manual wheelchair, dark skin tone");
        map.put("👩🏿‍🦽‍➡️", "woman in manual wheelchair facing right, dark skin tone");
        map.put("🚶‍➡️", "person walking facing right");
        map.put("🚶🏻‍➡️", "person walking facing right, light skin tone");
        map.put("🚶🏼‍➡️", "person walking facing right, medium-light skin tone");
        map.put("🚶🏽‍➡️", "person walking facing right, medium skin tone");
        map.put("🚶🏾‍➡️", "person walking facing right, medium-dark skin tone");
        map.put("🚶🏿‍➡️", "person walking facing right, dark skin tone");
        map.put("🧎‍➡️", "person kneeling facing right");
        map.put("🧎🏻‍➡️", "person kneeling facing right, light skin tone");
        map.put("🧎🏼‍➡️", "person kneeling facing right, medium-light skin tone");
        map.put("🧎🏽‍➡️", "person kneeling facing right, medium skin tone");
        map.put("🧎🏾‍➡️", "person kneeling facing right, medium-dark skin tone");
        map.put("🧎🏿‍➡️", "person kneeling facing right, dark skin tone");
        map.put("🧑‍⚕️", "health worker");
        map.put("🧑‍⚖️", "judge");
        map.put("🧑‍✈️", "pilot");
        map.put("🧑‍🌾", "farmer");
        map.put("🧑‍🍳", "cook");
        map.put("🧑‍🍼", "person feeding baby");
        map.put("🧑‍🎄", "mx claus");
        map.put("🧑‍🎓", "student");
        map.put("🧑‍🎤", "singer");
        map.put("🧑‍🎨", "artist");
        map.put("🧑‍🏫", "teacher");
        map.put("🧑‍🏭", "factory worker");
        map.put("🧑‍💻", "technologist");
        map.put("🧑‍💼", "office worker");
        map.put("🧑‍🔧", "mechanic");
        map.put("🧑‍🔬", "scientist");
        map.put("🧑‍🚀", "astronaut");
        map.put("🧑‍🚒", "firefighter");
        map.put("🧑‍🦯", "person with white cane");
        map.put("🧑‍🦯‍➡️", "person with white cane facing right");
        map.put("🧑‍🦼", "person in motorized wheelchair");
        map.put("🧑‍🦼‍➡️", "person in motorized wheelchair facing right");
        map.put("🧑‍🦽", "person in manual wheelchair");
        map.put("🧑‍🦽‍➡️", "person in manual wheelchair facing right");
        map.put("🧑🏻‍⚕️", "health worker, light skin tone");
        map.put("🧑🏻‍⚖️", "judge, light skin tone");
        map.put("🧑🏻‍✈️", "pilot, light skin tone");
        map.put("🧑🏻‍🌾", "farmer, light skin tone");
        map.put("🧑🏻‍🍳", "cook, light skin tone");
        map.put("🧑🏻‍🍼", "person feeding baby, light skin tone");
        map.put("🧑🏻‍🎄", "mx claus, light skin tone");
        map.put("🧑🏻‍🎓", "student, light skin tone");
        map.put("🧑🏻‍🎤", "singer, light skin tone");
        map.put("🧑🏻‍🎨", "artist, light skin tone");
        map.put("🧑🏻‍🏫", "teacher, light skin tone");
        map.put("🧑🏻‍🏭", "factory worker, light skin tone");
        map.put("🧑🏻‍💻", "technologist, light skin tone");
        map.put("🧑🏻‍💼", "office worker, light skin tone");
        map.put("🧑🏻‍🔧", "mechanic, light skin tone");
        map.put("🧑🏻‍🔬", "scientist, light skin tone");
        map.put("🧑🏻‍🚀", "astronaut, light skin tone");
        map.put("🧑🏻‍🚒", "firefighter, light skin tone");
        map.put("🧑🏻‍🦯", "person with white cane, light skin tone");
        map.put("🧑🏻‍🦯‍➡️", "person with white cane facing right, light skin tone");
        map.put("🧑🏻‍🦼", "person in motorized wheelchair, light skin tone");
        map.put("🧑🏻‍🦼‍➡️", "person in motorized wheelchair facing right, light skin tone");
        map.put("🧑🏻‍🦽", "person in manual wheelchair, light skin tone");
        map.put("🧑🏻‍🦽‍➡️", "person in manual wheelchair facing right, light skin tone");
        map.put("🧑🏼‍⚕️", "health worker, medium-light skin tone");
        map.put("🧑🏼‍⚖️", "judge, medium-light skin tone");
        map.put("🧑🏼‍✈️", "pilot, medium-light skin tone");
        map.put("🧑🏼‍🌾", "farmer, medium-light skin tone");
        map.put("🧑🏼‍🍳", "cook, medium-light skin tone");
        map.put("🧑🏼‍🍼", "person feeding baby, medium-light skin tone");
        map.put("🧑🏼‍🎄", "mx claus, medium-light skin tone");
        map.put("🧑🏼‍🎓", "student, medium-light skin tone");
        map.put("🧑🏼‍🎤", "singer, medium-light skin tone");
        map.put("🧑🏼‍🎨", "artist, medium-light skin tone");
        map.put("🧑🏼‍🏫", "teacher, medium-light skin tone");
        map.put("🧑🏼‍🏭", "factory worker, medium-light skin tone");
        map.put("🧑🏼‍💻", "technologist, medium-light skin tone");
        map.put("🧑🏼‍💼", "office worker, medium-light skin tone");
        map.put("🧑🏼‍🔧", "mechanic, medium-light skin tone");
        map.put("🧑🏼‍🔬", "scientist, medium-light skin tone");
        map.put("🧑🏼‍🚀", "astronaut, medium-light skin tone");
        map.put("🧑🏼‍🚒", "firefighter, medium-light skin tone");
        map.put("🧑🏼‍🦯", "person with white cane, medium-light skin tone");
        map.put("🧑🏼‍🦯‍➡️", "person with white cane facing right, medium-light skin tone");
        map.put("🧑🏼‍🦼", "person in motorized wheelchair, medium-light skin tone");
        map.put("🧑🏼‍🦼‍➡️", "person in motorized wheelchair facing right, medium-light skin tone");
        map.put("🧑🏼‍🦽", "person in manual wheelchair, medium-light skin tone");
        map.put("🧑🏼‍🦽‍➡️", "person in manual wheelchair facing right, medium-light skin tone");
        map.put("🧑🏽‍⚕️", "health worker, medium skin tone");
        map.put("🧑🏽‍⚖️", "judge, medium skin tone");
        map.put("🧑🏽‍✈️", "pilot, medium skin tone");
        map.put("🧑🏽‍🌾", "farmer, medium skin tone");
        map.put("🧑🏽‍🍳", "cook, medium skin tone");
        map.put("🧑🏽‍🍼", "person feeding baby, medium skin tone");
        map.put("🧑🏽‍🎄", "mx claus, medium skin tone");
        map.put("🧑🏽‍🎓", "student, medium skin tone");
        map.put("🧑🏽‍🎤", "singer, medium skin tone");
        map.put("🧑🏽‍🎨", "artist, medium skin tone");
        map.put("🧑🏽‍🏫", "teacher, medium skin tone");
        map.put("🧑🏽‍🏭", "factory worker, medium skin tone");
        map.put("🧑🏽‍💻", "technologist, medium skin tone");
        map.put("🧑🏽‍💼", "office worker, medium skin tone");
        map.put("🧑🏽‍🔧", "mechanic, medium skin tone");
        map.put("🧑🏽‍🔬", "scientist, medium skin tone");
        map.put("🧑🏽‍🚀", "astronaut, medium skin tone");
        map.put("🧑🏽‍🚒", "firefighter, medium skin tone");
        map.put("🧑🏽‍🦯", "person with white cane, medium skin tone");
        map.put("🧑🏽‍🦯‍➡️", "person with white cane facing right, medium skin tone");
        map.put("🧑🏽‍🦼", "person in motorized wheelchair, medium skin tone");
        map.put("🧑🏽‍🦼‍➡️", "person in motorized wheelchair facing right, medium skin tone");
        map.put("🧑🏽‍🦽", "person in manual wheelchair, medium skin tone");
        map.put("🧑🏽‍🦽‍➡️", "person in manual wheelchair facing right, medium skin tone");
        map.put("🧑🏾‍⚕️", "health worker, medium-dark skin tone");
        map.put("🧑🏾‍⚖️", "judge, medium-dark skin tone");
        map.put("🧑🏾‍✈️", "pilot, medium-dark skin tone");
        map.put("🧑🏾‍🌾", "farmer, medium-dark skin tone");
        map.put("🧑🏾‍🍳", "cook, medium-dark skin tone");
        map.put("🧑🏾‍🍼", "person feeding baby, medium-dark skin tone");
        map.put("🧑🏾‍🎄", "mx claus, medium-dark skin tone");
        map.put("🧑🏾‍🎓", "student, medium-dark skin tone");
        map.put("🧑🏾‍🎤", "singer, medium-dark skin tone");
        map.put("🧑🏾‍🎨", "artist, medium-dark skin tone");
        map.put("🧑🏾‍🏫", "teacher, medium-dark skin tone");
        map.put("🧑🏾‍🏭", "factory worker, medium-dark skin tone");
        map.put("🧑🏾‍💻", "technologist, medium-dark skin tone");
        map.put("🧑🏾‍💼", "office worker, medium-dark skin tone");
        map.put("🧑🏾‍🔧", "mechanic, medium-dark skin tone");
        map.put("🧑🏾‍🔬", "scientist, medium-dark skin tone");
        map.put("🧑🏾‍🚀", "astronaut, medium-dark skin tone");
        map.put("🧑🏾‍🚒", "firefighter, medium-dark skin tone");
        map.put("🧑🏾‍🦯", "person with white cane, medium-dark skin tone");
        map.put("🧑🏾‍🦯‍➡️", "person with white cane facing right, medium-dark skin tone");
        map.put("🧑🏾‍🦼", "person in motorized wheelchair, medium-dark skin tone");
        map.put("🧑🏾‍🦼‍➡️", "person in motorized wheelchair facing right, medium-dark skin tone");
        map.put("🧑🏾‍🦽", "person in manual wheelchair, medium-dark skin tone");
        map.put("🧑🏾‍🦽‍➡️", "person in manual wheelchair facing right, medium-dark skin tone");
        map.put("🧑🏿‍⚕️", "health worker, dark skin tone");
        map.put("🧑🏿‍⚖️", "judge, dark skin tone");
        map.put("🧑🏿‍✈️", "pilot, dark skin tone");
        map.put("🧑🏿‍🌾", "farmer, dark skin tone");
        map.put("🧑🏿‍🍳", "cook, dark skin tone");
        map.put("🧑🏿‍🍼", "person feeding baby, dark skin tone");
        map.put("🧑🏿‍🎄", "mx claus, dark skin tone");
        map.put("🧑🏿‍🎓", "student, dark skin tone");
        map.put("🧑🏿‍🎤", "singer, dark skin tone");
        map.put("🧑🏿‍🎨", "artist, dark skin tone");
        map.put("🧑🏿‍🏫", "teacher, dark skin tone");
        map.put("🧑🏿‍🏭", "factory worker, dark skin tone");
        map.put("🧑🏿‍💻", "technologist, dark skin tone");
        map.put("🧑🏿‍💼", "office worker, dark skin tone");
        map.put("🧑🏿‍🔧", "mechanic, dark skin tone");
        map.put("🧑🏿‍🔬", "scientist, dark skin tone");
        map.put("🧑🏿‍🚀", "astronaut, dark skin tone");
        map.put("🧑🏿‍🚒", "firefighter, dark skin tone");
        map.put("🧑🏿‍🦯", "person with white cane, dark skin tone");
        map.put("🧑🏿‍🦯‍➡️", "person with white cane facing right, dark skin tone");
        map.put("🧑🏿‍🦼", "person in motorized wheelchair, dark skin tone");
        map.put("🧑🏿‍🦼‍➡️", "person in motorized wheelchair facing right, dark skin tone");
        map.put("🧑🏿‍🦽", "person in manual wheelchair, dark skin tone");
        map.put("🧑🏿‍🦽‍➡️", "person in manual wheelchair facing right, dark skin tone");
        map.put("⛹🏻‍♀️", "woman bouncing ball, light skin tone");
        map.put("⛹🏻‍♂️", "man bouncing ball, light skin tone");
        map.put("⛹🏼‍♀️", "woman bouncing ball, medium-light skin tone");
        map.put("⛹🏼‍♂️", "man bouncing ball, medium-light skin tone");
        map.put("⛹🏽‍♀️", "woman bouncing ball, medium skin tone");
        map.put("⛹🏽‍♂️", "man bouncing ball, medium skin tone");
        map.put("⛹🏾‍♀️", "woman bouncing ball, medium-dark skin tone");
        map.put("⛹🏾‍♂️", "man bouncing ball, medium-dark skin tone");
        map.put("⛹🏿‍♀️", "woman bouncing ball, dark skin tone");
        map.put("⛹🏿‍♂️", "man bouncing ball, dark skin tone");
        map.put("⛹️‍♀️", "woman bouncing ball");
        map.put("⛹️‍♂️", "man bouncing ball");
        map.put("🏃‍♀️", "woman running");
        map.put("🏃‍♀️‍➡️", "woman running facing right");
        map.put("🏃‍♂️", "man running");
        map.put("🏃‍♂️‍➡️", "man running facing right");
        map.put("🏃🏻‍♀️", "woman running, light skin tone");
        map.put("🏃🏻‍♀️‍➡️", "woman running facing right, light skin tone");
        map.put("🏃🏻‍♂️", "man running, light skin tone");
        map.put("🏃🏻‍♂️‍➡️", "man running facing right, light skin tone");
        map.put("🏃🏼‍♀️", "woman running, medium-light skin tone");
        map.put("🏃🏼‍♀️‍➡️", "woman running facing right, medium-light skin tone");
        map.put("🏃🏼‍♂️", "man running, medium-light skin tone");
        map.put("🏃🏼‍♂️‍➡️", "man running facing right, medium-light skin tone");
        map.put("🏃🏽‍♀️", "woman running, medium skin tone");
        map.put("🏃🏽‍♀️‍➡️", "woman running facing right, medium skin tone");
        map.put("🏃🏽‍♂️", "man running, medium skin tone");
        map.put("🏃🏽‍♂️‍➡️", "man running facing right, medium skin tone");
        map.put("🏃🏾‍♀️", "woman running, medium-dark skin tone");
        map.put("🏃🏾‍♀️‍➡️", "woman running facing right, medium-dark skin tone");
        map.put("🏃🏾‍♂️", "man running, medium-dark skin tone");
        map.put("🏃🏾‍♂️‍➡️", "man running facing right, medium-dark skin tone");
        map.put("🏃🏿‍♀️", "woman running, dark skin tone");
        map.put("🏃🏿‍♀️‍➡️", "woman running facing right, dark skin tone");
        map.put("🏃🏿‍♂️", "man running, dark skin tone");
        map.put("🏃🏿‍♂️‍➡️", "man running facing right, dark skin tone");
        map.put("🏄‍♀️", "woman surfing");
        map.put("🏄‍♂️", "man surfing");
        map.put("🏄🏻‍♀️", "woman surfing, light skin tone");
        map.put("🏄🏻‍♂️", "man surfing, light skin tone");
        map.put("🏄🏼‍♀️", "woman surfing, medium-light skin tone");
        map.put("🏄🏼‍♂️", "man surfing, medium-light skin tone");
        map.put("🏄🏽‍♀️", "woman surfing, medium skin tone");
        map.put("🏄🏽‍♂️", "man surfing, medium skin tone");
        map.put("🏄🏾‍♀️", "woman surfing, medium-dark skin tone");
        map.put("🏄🏾‍♂️", "man surfing, medium-dark skin tone");
        map.put("🏄🏿‍♀️", "woman surfing, dark skin tone");
        map.put("🏄🏿‍♂️", "man surfing, dark skin tone");
        map.put("🏊‍♀️", "woman swimming");
        map.put("🏊‍♂️", "man swimming");
        map.put("🏊🏻‍♀️", "woman swimming, light skin tone");
        map.put("🏊🏻‍♂️", "man swimming, light skin tone");
        map.put("🏊🏼‍♀️", "woman swimming, medium-light skin tone");
        map.put("🏊🏼‍♂️", "man swimming, medium-light skin tone");
        map.put("🏊🏽‍♀️", "woman swimming, medium skin tone");
        map.put("🏊🏽‍♂️", "man swimming, medium skin tone");
        map.put("🏊🏾‍♀️", "woman swimming, medium-dark skin tone");
        map.put("🏊🏾‍♂️", "man swimming, medium-dark skin tone");
        map.put("🏊🏿‍♀️", "woman swimming, dark skin tone");
        map.put("🏊🏿‍♂️", "man swimming, dark skin tone");
        map.put("🏋🏻‍♀️", "woman lifting weights, light skin tone");
        map.put("🏋🏻‍♂️", "man lifting weights, light skin tone");
        map.put("🏋🏼‍♀️", "woman lifting weights, medium-light skin tone");
        map.put("🏋🏼‍♂️", "man lifting weights, medium-light skin tone");
        map.put("🏋🏽‍♀️", "woman lifting weights, medium skin tone");
        map.put("🏋🏽‍♂️", "man lifting weights, medium skin tone");
        map.put("🏋🏾‍♀️", "woman lifting weights, medium-dark skin tone");
        map.put("🏋🏾‍♂️", "man lifting weights, medium-dark skin tone");
        map.put("🏋🏿‍♀️", "woman lifting weights, dark skin tone");
        map.put("🏋🏿‍♂️", "man lifting weights, dark skin tone");
        map.put("🏋️‍♀️", "woman lifting weights");
        map.put("🏋️‍♂️", "man lifting weights");
        map.put("🏌🏻‍♀️", "woman golfing, light skin tone");
        map.put("🏌🏻‍♂️", "man golfing, light skin tone");
        map.put("🏌🏼‍♀️", "woman golfing, medium-light skin tone");
        map.put("🏌🏼‍♂️", "man golfing, medium-light skin tone");
        map.put("🏌🏽‍♀️", "woman golfing, medium skin tone");
        map.put("🏌🏽‍♂️", "man golfing, medium skin tone");
        map.put("🏌🏾‍♀️", "woman golfing, medium-dark skin tone");
        map.put("🏌🏾‍♂️", "man golfing, medium-dark skin tone");
        map.put("🏌🏿‍♀️", "woman golfing, dark skin tone");
        map.put("🏌🏿‍♂️", "man golfing, dark skin tone");
        map.put("🏌️‍♀️", "woman golfing");
        map.put("🏌️‍♂️", "man golfing");
        map.put("👮‍♀️", "woman police officer");
        map.put("👮‍♂️", "man police officer");
        map.put("👮🏻‍♀️", "woman police officer, light skin tone");
        map.put("👮🏻‍♂️", "man police officer, light skin tone");
        map.put("👮🏼‍♀️", "woman police officer, medium-light skin tone");
        map.put("👮🏼‍♂️", "man police officer, medium-light skin tone");
        map.put("👮🏽‍♀️", "woman police officer, medium skin tone");
        map.put("👮🏽‍♂️", "man police officer, medium skin tone");
        map.put("👮🏾‍♀️", "woman police officer, medium-dark skin tone");
        map.put("👮🏾‍♂️", "man police officer, medium-dark skin tone");
        map.put("👮🏿‍♀️", "woman police officer, dark skin tone");
        map.put("👮🏿‍♂️", "man police officer, dark skin tone");
        map.put("👯‍♀️", "women with bunny ears");
        map.put("👯‍♂️", "men with bunny ears");
        map.put("👰‍♀️", "woman with veil");
        map.put("👰‍♂️", "man with veil");
        map.put("👰🏻‍♀️", "woman with veil, light skin tone");
        map.put("👰🏻‍♂️", "man with veil, light skin tone");
        map.put("👰🏼‍♀️", "woman with veil, medium-light skin tone");
        map.put("👰🏼‍♂️", "man with veil, medium-light skin tone");
        map.put("👰🏽‍♀️", "woman with veil, medium skin tone");
        map.put("👰🏽‍♂️", "man with veil, medium skin tone");
        map.put("👰🏾‍♀️", "woman with veil, medium-dark skin tone");
        map.put("👰🏾‍♂️", "man with veil, medium-dark skin tone");
        map.put("👰🏿‍♀️", "woman with veil, dark skin tone");
        map.put("👰🏿‍♂️", "man with veil, dark skin tone");
        map.put("👱‍♀️", "woman, blond hair");
        map.put("👱‍♂️", "man, blond hair");
        map.put("👱🏻‍♀️", "woman, light skin tone, blond hair");
        map.put("👱🏻‍♂️", "man, light skin tone, blond hair");
        map.put("👱🏼‍♀️", "woman, medium-light skin tone, blond hair");
        map.put("👱🏼‍♂️", "man, medium-light skin tone, blond hair");
        map.put("👱🏽‍♀️", "woman, medium skin tone, blond hair");
        map.put("👱🏽‍♂️", "man, medium skin tone, blond hair");
        map.put("👱🏾‍♀️", "woman, medium-dark skin tone, blond hair");
        map.put("👱🏾‍♂️", "man, medium-dark skin tone, blond hair");
        map.put("👱🏿‍♀️", "woman, dark skin tone, blond hair");
        map.put("👱🏿‍♂️", "man, dark skin tone, blond hair");
        map.put("👳‍♀️", "woman wearing turban");
        map.put("👳‍♂️", "man wearing turban");
        map.put("👳🏻‍♀️", "woman wearing turban, light skin tone");
        map.put("👳🏻‍♂️", "man wearing turban, light skin tone");
        map.put("👳🏼‍♀️", "woman wearing turban, medium-light skin tone");
        map.put("👳🏼‍♂️", "man wearing turban, medium-light skin tone");
        map.put("👳🏽‍♀️", "woman wearing turban, medium skin tone");
        map.put("👳🏽‍♂️", "man wearing turban, medium skin tone");
        map.put("👳🏾‍♀️", "woman wearing turban, medium-dark skin tone");
        map.put("👳🏾‍♂️", "man wearing turban, medium-dark skin tone");
        map.put("👳🏿‍♀️", "woman wearing turban, dark skin tone");
        map.put("👳🏿‍♂️", "man wearing turban, dark skin tone");
        map.put("👷‍♀️", "woman construction worker");
        map.put("👷‍♂️", "man construction worker");
        map.put("👷🏻‍♀️", "woman construction worker, light skin tone");
        map.put("👷🏻‍♂️", "man construction worker, light skin tone");
        map.put("👷🏼‍♀️", "woman construction worker, medium-light skin tone");
        map.put("👷🏼‍♂️", "man construction worker, medium-light skin tone");
        map.put("👷🏽‍♀️", "woman construction worker, medium skin tone");
        map.put("👷🏽‍♂️", "man construction worker, medium skin tone");
        map.put("👷🏾‍♀️", "woman construction worker, medium-dark skin tone");
        map.put("👷🏾‍♂️", "man construction worker, medium-dark skin tone");
        map.put("👷🏿‍♀️", "woman construction worker, dark skin tone");
        map.put("👷🏿‍♂️", "man construction worker, dark skin tone");
        map.put("💁‍♀️", "woman tipping hand");
        map.put("💁‍♂️", "man tipping hand");
        map.put("💁🏻‍♀️", "woman tipping hand, light skin tone");
        map.put("💁🏻‍♂️", "man tipping hand, light skin tone");
        map.put("💁🏼‍♀️", "woman tipping hand, medium-light skin tone");
        map.put("💁🏼‍♂️", "man tipping hand, medium-light skin tone");
        map.put("💁🏽‍♀️", "woman tipping hand, medium skin tone");
        map.put("💁🏽‍♂️", "man tipping hand, medium skin tone");
        map.put("💁🏾‍♀️", "woman tipping hand, medium-dark skin tone");
        map.put("💁🏾‍♂️", "man tipping hand, medium-dark skin tone");
        map.put("💁🏿‍♀️", "woman tipping hand, dark skin tone");
        map.put("💁🏿‍♂️", "man tipping hand, dark skin tone");
        map.put("💂‍♀️", "woman guard");
        map.put("💂‍♂️", "man guard");
        map.put("💂🏻‍♀️", "woman guard, light skin tone");
        map.put("💂🏻‍♂️", "man guard, light skin tone");
        map.put("💂🏼‍♀️", "woman guard, medium-light skin tone");
        map.put("💂🏼‍♂️", "man guard, medium-light skin tone");
        map.put("💂🏽‍♀️", "woman guard, medium skin tone");
        map.put("💂🏽‍♂️", "man guard, medium skin tone");
        map.put("💂🏾‍♀️", "woman guard, medium-dark skin tone");
        map.put("💂🏾‍♂️", "man guard, medium-dark skin tone");
        map.put("💂🏿‍♀️", "woman guard, dark skin tone");
        map.put("💂🏿‍♂️", "man guard, dark skin tone");
        map.put("💆‍♀️", "woman getting massage");
        map.put("💆‍♂️", "man getting massage");
        map.put("💆🏻‍♀️", "woman getting massage, light skin tone");
        map.put("💆🏻‍♂️", "man getting massage, light skin tone");
        map.put("💆🏼‍♀️", "woman getting massage, medium-light skin tone");
        map.put("💆🏼‍♂️", "man getting massage, medium-light skin tone");
        map.put("💆🏽‍♀️", "woman getting massage, medium skin tone");
        map.put("💆🏽‍♂️", "man getting massage, medium skin tone");
        map.put("💆🏾‍♀️", "woman getting massage, medium-dark skin tone");
        map.put("💆🏾‍♂️", "man getting massage, medium-dark skin tone");
        map.put("💆🏿‍♀️", "woman getting massage, dark skin tone");
        map.put("💆🏿‍♂️", "man getting massage, dark skin tone");
        map.put("💇‍♀️", "woman getting haircut");
        map.put("💇‍♂️", "man getting haircut");
        map.put("💇🏻‍♀️", "woman getting haircut, light skin tone");
        map.put("💇🏻‍♂️", "man getting haircut, light skin tone");
        map.put("💇🏼‍♀️", "woman getting haircut, medium-light skin tone");
        map.put("💇🏼‍♂️", "man getting haircut, medium-light skin tone");
        map.put("💇🏽‍♀️", "woman getting haircut, medium skin tone");
        map.put("💇🏽‍♂️", "man getting haircut, medium skin tone");
        map.put("💇🏾‍♀️", "woman getting haircut, medium-dark skin tone");
        map.put("💇🏾‍♂️", "man getting haircut, medium-dark skin tone");
        map.put("💇🏿‍♀️", "woman getting haircut, dark skin tone");
        map.put("💇🏿‍♂️", "man getting haircut, dark skin tone");
        map.put("🕵🏻‍♀️", "woman detective, light skin tone");
        map.put("🕵🏻‍♂️", "man detective, light skin tone");
        map.put("🕵🏼‍♀️", "woman detective, medium-light skin tone");
        map.put("🕵🏼‍♂️", "man detective, medium-light skin tone");
        map.put("🕵🏽‍♀️", "woman detective, medium skin tone");
        map.put("🕵🏽‍♂️", "man detective, medium skin tone");
        map.put("🕵🏾‍♀️", "woman detective, medium-dark skin tone");
        map.put("🕵🏾‍♂️", "man detective, medium-dark skin tone");
        map.put("🕵🏿‍♀️", "woman detective, dark skin tone");
        map.put("🕵🏿‍♂️", "man detective, dark skin tone");
        map.put("🕵️‍♀️", "woman detective");
        map.put("🕵️‍♂️", "man detective");
        map.put("🙅‍♀️", "woman gesturing NO");
        map.put("🙅‍♂️", "man gesturing NO");
        map.put("🙅🏻‍♀️", "woman gesturing NO, light skin tone");
        map.put("🙅🏻‍♂️", "man gesturing NO, light skin tone");
        map.put("🙅🏼‍♀️", "woman gesturing NO, medium-light skin tone");
        map.put("🙅🏼‍♂️", "man gesturing NO, medium-light skin tone");
        map.put("🙅🏽‍♀️", "woman gesturing NO, medium skin tone");
        map.put("🙅🏽‍♂️", "man gesturing NO, medium skin tone");
        map.put("🙅🏾‍♀️", "woman gesturing NO, medium-dark skin tone");
        map.put("🙅🏾‍♂️", "man gesturing NO, medium-dark skin tone");
        map.put("🙅🏿‍♀️", "woman gesturing NO, dark skin tone");
        map.put("🙅🏿‍♂️", "man gesturing NO, dark skin tone");
        map.put("🙆‍♀️", "woman gesturing OK");
        map.put("🙆‍♂️", "man gesturing OK");
        map.put("🙆🏻‍♀️", "woman gesturing OK, light skin tone");
        map.put("🙆🏻‍♂️", "man gesturing OK, light skin tone");
        map.put("🙆🏼‍♀️", "woman gesturing OK, medium-light skin tone");
        map.put("🙆🏼‍♂️", "man gesturing OK, medium-light skin tone");
        map.put("🙆🏽‍♀️", "woman gesturing OK, medium skin tone");
        map.put("🙆🏽‍♂️", "man gesturing OK, medium skin tone");
        map.put("🙆🏾‍♀️", "woman gesturing OK, medium-dark skin tone");
        map.put("🙆🏾‍♂️", "man gesturing OK, medium-dark skin tone");
        map.put("🙆🏿‍♀️", "woman gesturing OK, dark skin tone");
        map.put("🙆🏿‍♂️", "man gesturing OK, dark skin tone");
        map.put("🙇‍♀️", "woman bowing");
        map.put("🙇‍♂️", "man bowing");
        map.put("🙇🏻‍♀️", "woman bowing, light skin tone");
        map.put("🙇🏻‍♂️", "man bowing, light skin tone");
        map.put("🙇🏼‍♀️", "woman bowing, medium-light skin tone");
        map.put("🙇🏼‍♂️", "man bowing, medium-light skin tone");
        map.put("🙇🏽‍♀️", "woman bowing, medium skin tone");
        map.put("🙇🏽‍♂️", "man bowing, medium skin tone");
        map.put("🙇🏾‍♀️", "woman bowing, medium-dark skin tone");
        map.put("🙇🏾‍♂️", "man bowing, medium-dark skin tone");
        map.put("🙇🏿‍♀️", "woman bowing, dark skin tone");
        map.put("🙇🏿‍♂️", "man bowing, dark skin tone");
        map.put("🙋‍♀️", "woman raising hand");
        map.put("🙋‍♂️", "man raising hand");
        map.put("🙋🏻‍♀️", "woman raising hand, light skin tone");
        map.put("🙋🏻‍♂️", "man raising hand, light skin tone");
        map.put("🙋🏼‍♀️", "woman raising hand, medium-light skin tone");
        map.put("🙋🏼‍♂️", "man raising hand, medium-light skin tone");
        map.put("🙋🏽‍♀️", "woman raising hand, medium skin tone");
        map.put("🙋🏽‍♂️", "man raising hand, medium skin tone");
        map.put("🙋🏾‍♀️", "woman raising hand, medium-dark skin tone");
        map.put("🙋🏾‍♂️", "man raising hand, medium-dark skin tone");
        map.put("🙋🏿‍♀️", "woman raising hand, dark skin tone");
        map.put("🙋🏿‍♂️", "man raising hand, dark skin tone");
        map.put("🙍‍♀️", "woman frowning");
        map.put("🙍‍♂️", "man frowning");
        map.put("🙍🏻‍♀️", "woman frowning, light skin tone");
        map.put("🙍🏻‍♂️", "man frowning, light skin tone");
        map.put("🙍🏼‍♀️", "woman frowning, medium-light skin tone");
        map.put("🙍🏼‍♂️", "man frowning, medium-light skin tone");
        map.put("🙍🏽‍♀️", "woman frowning, medium skin tone");
        map.put("🙍🏽‍♂️", "man frowning, medium skin tone");
        map.put("🙍🏾‍♀️", "woman frowning, medium-dark skin tone");
        map.put("🙍🏾‍♂️", "man frowning, medium-dark skin tone");
        map.put("🙍🏿‍♀️", "woman frowning, dark skin tone");
        map.put("🙍🏿‍♂️", "man frowning, dark skin tone");
        map.put("🙎‍♀️", "woman pouting");
        map.put("🙎‍♂️", "man pouting");
        map.put("🙎🏻‍♀️", "woman pouting, light skin tone");
        map.put("🙎🏻‍♂️", "man pouting, light skin tone");
        map.put("🙎🏼‍♀️", "woman pouting, medium-light skin tone");
        map.put("🙎🏼‍♂️", "man pouting, medium-light skin tone");
        map.put("🙎🏽‍♀️", "woman pouting, medium skin tone");
        map.put("🙎🏽‍♂️", "man pouting, medium skin tone");
        map.put("🙎🏾‍♀️", "woman pouting, medium-dark skin tone");
        map.put("🙎🏾‍♂️", "man pouting, medium-dark skin tone");
        map.put("🙎🏿‍♀️", "woman pouting, dark skin tone");
        map.put("🙎🏿‍♂️", "man pouting, dark skin tone");
        map.put("🚣‍♀️", "woman rowing boat");
        map.put("🚣‍♂️", "man rowing boat");
        map.put("🚣🏻‍♀️", "woman rowing boat, light skin tone");
        map.put("🚣🏻‍♂️", "man rowing boat, light skin tone");
        map.put("🚣🏼‍♀️", "woman rowing boat, medium-light skin tone");
        map.put("🚣🏼‍♂️", "man rowing boat, medium-light skin tone");
        map.put("🚣🏽‍♀️", "woman rowing boat, medium skin tone");
        map.put("🚣🏽‍♂️", "man rowing boat, medium skin tone");
        map.put("🚣🏾‍♀️", "woman rowing boat, medium-dark skin tone");
        map.put("🚣🏾‍♂️", "man rowing boat, medium-dark skin tone");
        map.put("🚣🏿‍♀️", "woman rowing boat, dark skin tone");
        map.put("🚣🏿‍♂️", "man rowing boat, dark skin tone");
        map.put("🚴‍♀️", "woman biking");
        map.put("🚴‍♂️", "man biking");
        map.put("🚴🏻‍♀️", "woman biking, light skin tone");
        map.put("🚴🏻‍♂️", "man biking, light skin tone");
        map.put("🚴🏼‍♀️", "woman biking, medium-light skin tone");
        map.put("🚴🏼‍♂️", "man biking, medium-light skin tone");
        map.put("🚴🏽‍♀️", "woman biking, medium skin tone");
        map.put("🚴🏽‍♂️", "man biking, medium skin tone");
        map.put("🚴🏾‍♀️", "woman biking, medium-dark skin tone");
        map.put("🚴🏾‍♂️", "man biking, medium-dark skin tone");
        map.put("🚴🏿‍♀️", "woman biking, dark skin tone");
        map.put("🚴🏿‍♂️", "man biking, dark skin tone");
        map.put("🚵‍♀️", "woman mountain biking");
        map.put("🚵‍♂️", "man mountain biking");
        map.put("🚵🏻‍♀️", "woman mountain biking, light skin tone");
        map.put("🚵🏻‍♂️", "man mountain biking, light skin tone");
        map.put("🚵🏼‍♀️", "woman mountain biking, medium-light skin tone");
        map.put("🚵🏼‍♂️", "man mountain biking, medium-light skin tone");
        map.put("🚵🏽‍♀️", "woman mountain biking, medium skin tone");
        map.put("🚵🏽‍♂️", "man mountain biking, medium skin tone");
        map.put("🚵🏾‍♀️", "woman mountain biking, medium-dark skin tone");
        map.put("🚵🏾‍♂️", "man mountain biking, medium-dark skin tone");
        map.put("🚵🏿‍♀️", "woman mountain biking, dark skin tone");
        map.put("🚵🏿‍♂️", "man mountain biking, dark skin tone");
        map.put("🚶‍♀️", "woman walking");
        map.put("🚶‍♀️‍➡️", "woman walking facing right");
        map.put("🚶‍♂️", "man walking");
        map.put("🚶‍♂️‍➡️", "man walking facing right");
        map.put("🚶🏻‍♀️", "woman walking, light skin tone");
        map.put("🚶🏻‍♀️‍➡️", "woman walking facing right, light skin tone");
        map.put("🚶🏻‍♂️", "man walking, light skin tone");
        map.put("🚶🏻‍♂️‍➡️", "man walking facing right, light skin tone");
        map.put("🚶🏼‍♀️", "woman walking, medium-light skin tone");
        map.put("🚶🏼‍♀️‍➡️", "woman walking facing right, medium-light skin tone");
        map.put("🚶🏼‍♂️", "man walking, medium-light skin tone");
        map.put("🚶🏼‍♂️‍➡️", "man walking facing right, medium-light skin tone");
        map.put("🚶🏽‍♀️", "woman walking, medium skin tone");
        map.put("🚶🏽‍♀️‍➡️", "woman walking facing right, medium skin tone");
        map.put("🚶🏽‍♂️", "man walking, medium skin tone");
        map.put("🚶🏽‍♂️‍➡️", "man walking facing right, medium skin tone");
        map.put("🚶🏾‍♀️", "woman walking, medium-dark skin tone");
        map.put("🚶🏾‍♀️‍➡️", "woman walking facing right, medium-dark skin tone");
        map.put("🚶🏾‍♂️", "man walking, medium-dark skin tone");
        map.put("🚶🏾‍♂️‍➡️", "man walking facing right, medium-dark skin tone");
        map.put("🚶🏿‍♀️", "woman walking, dark skin tone");
        map.put("🚶🏿‍♀️‍➡️", "woman walking facing right, dark skin tone");
        map.put("🚶🏿‍♂️", "man walking, dark skin tone");
        map.put("🚶🏿‍♂️‍➡️", "man walking facing right, dark skin tone");
        map.put("🤦‍♀️", "woman facepalming");
        map.put("🤦‍♂️", "man facepalming");
        map.put("🤦🏻‍♀️", "woman facepalming, light skin tone");
        map.put("🤦🏻‍♂️", "man facepalming, light skin tone");
        map.put("🤦🏼‍♀️", "woman facepalming, medium-light skin tone");
        map.put("🤦🏼‍♂️", "man facepalming, medium-light skin tone");
        map.put("🤦🏽‍♀️", "woman facepalming, medium skin tone");
        map.put("🤦🏽‍♂️", "man facepalming, medium skin tone");
        map.put("🤦🏾‍♀️", "woman facepalming, medium-dark skin tone");
        map.put("🤦🏾‍♂️", "man facepalming, medium-dark skin tone");
        map.put("🤦🏿‍♀️", "woman facepalming, dark skin tone");
        map.put("🤦🏿‍♂️", "man facepalming, dark skin tone");
        map.put("🤵‍♀️", "woman in tuxedo");
        map.put("🤵‍♂️", "man in tuxedo");
        map.put("🤵🏻‍♀️", "woman in tuxedo, light skin tone");
        map.put("🤵🏻‍♂️", "man in tuxedo, light skin tone");
        map.put("🤵🏼‍♀️", "woman in tuxedo, medium-light skin tone");
        map.put("🤵🏼‍♂️", "man in tuxedo, medium-light skin tone");
        map.put("🤵🏽‍♀️", "woman in tuxedo, medium skin tone");
        map.put("🤵🏽‍♂️", "man in tuxedo, medium skin tone");
        map.put("🤵🏾‍♀️", "woman in tuxedo, medium-dark skin tone");
        map.put("🤵🏾‍♂️", "man in tuxedo, medium-dark skin tone");
        map.put("🤵🏿‍♀️", "woman in tuxedo, dark skin tone");
        map.put("🤵🏿‍♂️", "man in tuxedo, dark skin tone");
        map.put("🤷‍♀️", "woman shrugging");
        map.put("🤷‍♂️", "man shrugging");
        map.put("🤷🏻‍♀️", "woman shrugging, light skin tone");
        map.put("🤷🏻‍♂️", "man shrugging, light skin tone");
        map.put("🤷🏼‍♀️", "woman shrugging, medium-light skin tone");
        map.put("🤷🏼‍♂️", "man shrugging, medium-light skin tone");
        map.put("🤷🏽‍♀️", "woman shrugging, medium skin tone");
        map.put("🤷🏽‍♂️", "man shrugging, medium skin tone");
        map.put("🤷🏾‍♀️", "woman shrugging, medium-dark skin tone");
        map.put("🤷🏾‍♂️", "man shrugging, medium-dark skin tone");
        map.put("🤷🏿‍♀️", "woman shrugging, dark skin tone");
        map.put("🤷🏿‍♂️", "man shrugging, dark skin tone");
        map.put("🤸‍♀️", "woman cartwheeling");
        map.put("🤸‍♂️", "man cartwheeling");
        map.put("🤸🏻‍♀️", "woman cartwheeling, light skin tone");
        map.put("🤸🏻‍♂️", "man cartwheeling, light skin tone");
        map.put("🤸🏼‍♀️", "woman cartwheeling, medium-light skin tone");
        map.put("🤸🏼‍♂️", "man cartwheeling, medium-light skin tone");
        map.put("🤸🏽‍♀️", "woman cartwheeling, medium skin tone");
        map.put("🤸🏽‍♂️", "man cartwheeling, medium skin tone");
        map.put("🤸🏾‍♀️", "woman cartwheeling, medium-dark skin tone");
        map.put("🤸🏾‍♂️", "man cartwheeling, medium-dark skin tone");
        map.put("🤸🏿‍♀️", "woman cartwheeling, dark skin tone");
        map.put("🤸🏿‍♂️", "man cartwheeling, dark skin tone");
        map.put("🤹‍♀️", "woman juggling");
        map.put("🤹‍♂️", "man juggling");
        map.put("🤹🏻‍♀️", "woman juggling, light skin tone");
        map.put("🤹🏻‍♂️", "man juggling, light skin tone");
        map.put("🤹🏼‍♀️", "woman juggling, medium-light skin tone");
        map.put("🤹🏼‍♂️", "man juggling, medium-light skin tone");
        map.put("🤹🏽‍♀️", "woman juggling, medium skin tone");
        map.put("🤹🏽‍♂️", "man juggling, medium skin tone");
        map.put("🤹🏾‍♀️", "woman juggling, medium-dark skin tone");
        map.put("🤹🏾‍♂️", "man juggling, medium-dark skin tone");
        map.put("🤹🏿‍♀️", "woman juggling, dark skin tone");
        map.put("🤹🏿‍♂️", "man juggling, dark skin tone");
        map.put("🤼‍♀️", "women wrestling");
        map.put("🤼‍♂️", "men wrestling");
        map.put("🤽‍♀️", "woman playing water polo");
        map.put("🤽‍♂️", "man playing water polo");
        map.put("🤽🏻‍♀️", "woman playing water polo, light skin tone");
        map.put("🤽🏻‍♂️", "man playing water polo, light skin tone");
        map.put("🤽🏼‍♀️", "woman playing water polo, medium-light skin tone");
        map.put("🤽🏼‍♂️", "man playing water polo, medium-light skin tone");
        map.put("🤽🏽‍♀️", "woman playing water polo, medium skin tone");
        map.put("🤽🏽‍♂️", "man playing water polo, medium skin tone");
        map.put("🤽🏾‍♀️", "woman playing water polo, medium-dark skin tone");
        map.put("🤽🏾‍♂️", "man playing water polo, medium-dark skin tone");
        map.put("🤽🏿‍♀️", "woman playing water polo, dark skin tone");
        map.put("🤽🏿‍♂️", "man playing water polo, dark skin tone");
        map.put("🤾‍♀️", "woman playing handball");
        map.put("🤾‍♂️", "man playing handball");
        map.put("🤾🏻‍♀️", "woman playing handball, light skin tone");
        map.put("🤾🏻‍♂️", "man playing handball, light skin tone");
        map.put("🤾🏼‍♀️", "woman playing handball, medium-light skin tone");
        map.put("🤾🏼‍♂️", "man playing handball, medium-light skin tone");
        map.put("🤾🏽‍♀️", "woman playing handball, medium skin tone");
        map.put("🤾🏽‍♂️", "man playing handball, medium skin tone");
        map.put("🤾🏾‍♀️", "woman playing handball, medium-dark skin tone");
        map.put("🤾🏾‍♂️", "man playing handball, medium-dark skin tone");
        map.put("🤾🏿‍♀️", "woman playing handball, dark skin tone");
        map.put("🤾🏿‍♂️", "man playing handball, dark skin tone");
        map.put("🦸‍♀️", "woman superhero");
        map.put("🦸‍♂️", "man superhero");
        map.put("🦸🏻‍♀️", "woman superhero, light skin tone");
        map.put("🦸🏻‍♂️", "man superhero, light skin tone");
        map.put("🦸🏼‍♀️", "woman superhero, medium-light skin tone");
        map.put("🦸🏼‍♂️", "man superhero, medium-light skin tone");
        map.put("🦸🏽‍♀️", "woman superhero, medium skin tone");
        map.put("🦸🏽‍♂️", "man superhero, medium skin tone");
        map.put("🦸🏾‍♀️", "woman superhero, medium-dark skin tone");
        map.put("🦸🏾‍♂️", "man superhero, medium-dark skin tone");
        map.put("🦸🏿‍♀️", "woman superhero, dark skin tone");
        map.put("🦸🏿‍♂️", "man superhero, dark skin tone");
        map.put("🦹‍♀️", "woman supervillain");
        map.put("🦹‍♂️", "man supervillain");
        map.put("🦹🏻‍♀️", "woman supervillain, light skin tone");
        map.put("🦹🏻‍♂️", "man supervillain, light skin tone");
        map.put("🦹🏼‍♀️", "woman supervillain, medium-light skin tone");
        map.put("🦹🏼‍♂️", "man supervillain, medium-light skin tone");
        map.put("🦹🏽‍♀️", "woman supervillain, medium skin tone");
        map.put("🦹🏽‍♂️", "man supervillain, medium skin tone");
        map.put("🦹🏾‍♀️", "woman supervillain, medium-dark skin tone");
        map.put("🦹🏾‍♂️", "man supervillain, medium-dark skin tone");
        map.put("🦹🏿‍♀️", "woman supervillain, dark skin tone");
        map.put("🦹🏿‍♂️", "man supervillain, dark skin tone");
        map.put("🧍‍♀️", "woman standing");
        map.put("🧍‍♂️", "man standing");
        map.put("🧍🏻‍♀️", "woman standing, light skin tone");
        map.put("🧍🏻‍♂️", "man standing, light skin tone");
        map.put("🧍🏼‍♀️", "woman standing, medium-light skin tone");
        map.put("🧍🏼‍♂️", "man standing, medium-light skin tone");
        map.put("🧍🏽‍♀️", "woman standing, medium skin tone");
        map.put("🧍🏽‍♂️", "man standing, medium skin tone");
        map.put("🧍🏾‍♀️", "woman standing, medium-dark skin tone");
        map.put("🧍🏾‍♂️", "man standing, medium-dark skin tone");
        map.put("🧍🏿‍♀️", "woman standing, dark skin tone");
        map.put("🧍🏿‍♂️", "man standing, dark skin tone");
        map.put("🧎‍♀️", "woman kneeling");
        map.put("🧎‍♀️‍➡️", "woman kneeling facing right");
        map.put("🧎‍♂️", "man kneeling");
        map.put("🧎‍♂️‍➡️", "man kneeling facing right");
        map.put("🧎🏻‍♀️", "woman kneeling, light skin tone");
        map.put("🧎🏻‍♀️‍➡️", "woman kneeling facing right, light skin tone");
        map.put("🧎🏻‍♂️", "man kneeling, light skin tone");
        map.put("🧎🏻‍♂️‍➡️", "man kneeling facing right, light skin tone");
        map.put("🧎🏼‍♀️", "woman kneeling, medium-light skin tone");
        map.put("🧎🏼‍♀️‍➡️", "woman kneeling facing right, medium-light skin tone");
        map.put("🧎🏼‍♂️", "man kneeling, medium-light skin tone");
        map.put("🧎🏼‍♂️‍➡️", "man kneeling facing right, medium-light skin tone");
        map.put("🧎🏽‍♀️", "woman kneeling, medium skin tone");
        map.put("🧎🏽‍♀️‍➡️", "woman kneeling facing right, medium skin tone");
        map.put("🧎🏽‍♂️", "man kneeling, medium skin tone");
        map.put("🧎🏽‍♂️‍➡️", "man kneeling facing right, medium skin tone");
        map.put("🧎🏾‍♀️", "woman kneeling, medium-dark skin tone");
        map.put("🧎🏾‍♀️‍➡️", "woman kneeling facing right, medium-dark skin tone");
        map.put("🧎🏾‍♂️", "man kneeling, medium-dark skin tone");
        map.put("🧎🏾‍♂️‍➡️", "man kneeling facing right, medium-dark skin tone");
        map.put("🧎🏿‍♀️", "woman kneeling, dark skin tone");
        map.put("🧎🏿‍♀️‍➡️", "woman kneeling facing right, dark skin tone");
        map.put("🧎🏿‍♂️", "man kneeling, dark skin tone");
        map.put("🧎🏿‍♂️‍➡️", "man kneeling facing right, dark skin tone");
        map.put("🧏‍♀️", "deaf woman");
        map.put("🧏‍♂️", "deaf man");
        map.put("🧏🏻‍♀️", "deaf woman, light skin tone");
        map.put("🧏🏻‍♂️", "deaf man, light skin tone");
        map.put("🧏🏼‍♀️", "deaf woman, medium-light skin tone");
        map.put("🧏🏼‍♂️", "deaf man, medium-light skin tone");
        map.put("🧏🏽‍♀️", "deaf woman, medium skin tone");
        map.put("🧏🏽‍♂️", "deaf man, medium skin tone");
        map.put("🧏🏾‍♀️", "deaf woman, medium-dark skin tone");
        map.put("🧏🏾‍♂️", "deaf man, medium-dark skin tone");
        map.put("🧏🏿‍♀️", "deaf woman, dark skin tone");
        map.put("🧏🏿‍♂️", "deaf man, dark skin tone");
        map.put("🧔‍♀️", "woman, beard");
        map.put("🧔‍♂️", "man, beard");
        map.put("🧔🏻‍♀️", "woman, light skin tone, beard");
        map.put("🧔🏻‍♂️", "man, light skin tone, beard");
        map.put("🧔🏼‍♀️", "woman, medium-light skin tone, beard");
        map.put("🧔🏼‍♂️", "man, medium-light skin tone, beard");
        map.put("🧔🏽‍♀️", "woman, medium skin tone, beard");
        map.put("🧔🏽‍♂️", "man, medium skin tone, beard");
        map.put("🧔🏾‍♀️", "woman, medium-dark skin tone, beard");
        map.put("🧔🏾‍♂️", "man, medium-dark skin tone, beard");
        map.put("🧔🏿‍♀️", "woman, dark skin tone, beard");
        map.put("🧔🏿‍♂️", "man, dark skin tone, beard");
        map.put("🧖‍♀️", "woman in steamy room");
        map.put("🧖‍♂️", "man in steamy room");
        map.put("🧖🏻‍♀️", "woman in steamy room, light skin tone");
        map.put("🧖🏻‍♂️", "man in steamy room, light skin tone");
        map.put("🧖🏼‍♀️", "woman in steamy room, medium-light skin tone");
        map.put("🧖🏼‍♂️", "man in steamy room, medium-light skin tone");
        map.put("🧖🏽‍♀️", "woman in steamy room, medium skin tone");
        map.put("🧖🏽‍♂️", "man in steamy room, medium skin tone");
        map.put("🧖🏾‍♀️", "woman in steamy room, medium-dark skin tone");
        map.put("🧖🏾‍♂️", "man in steamy room, medium-dark skin tone");
        map.put("🧖🏿‍♀️", "woman in steamy room, dark skin tone");
        map.put("🧖🏿‍♂️", "man in steamy room, dark skin tone");
        map.put("🧗‍♀️", "woman climbing");
        map.put("🧗‍♂️", "man climbing");
        map.put("🧗🏻‍♀️", "woman climbing, light skin tone");
        map.put("🧗🏻‍♂️", "man climbing, light skin tone");
        map.put("🧗🏼‍♀️", "woman climbing, medium-light skin tone");
        map.put("🧗🏼‍♂️", "man climbing, medium-light skin tone");
        map.put("🧗🏽‍♀️", "woman climbing, medium skin tone");
        map.put("🧗🏽‍♂️", "man climbing, medium skin tone");
        map.put("🧗🏾‍♀️", "woman climbing, medium-dark skin tone");
        map.put("🧗🏾‍♂️", "man climbing, medium-dark skin tone");
        map.put("🧗🏿‍♀️", "woman climbing, dark skin tone");
        map.put("🧗🏿‍♂️", "man climbing, dark skin tone");
        map.put("🧘‍♀️", "woman in lotus position");
        map.put("🧘‍♂️", "man in lotus position");
        map.put("🧘🏻‍♀️", "woman in lotus position, light skin tone");
        map.put("🧘🏻‍♂️", "man in lotus position, light skin tone");
        map.put("🧘🏼‍♀️", "woman in lotus position, medium-light skin tone");
        map.put("🧘🏼‍♂️", "man in lotus position, medium-light skin tone");
        map.put("🧘🏽‍♀️", "woman in lotus position, medium skin tone");
        map.put("🧘🏽‍♂️", "man in lotus position, medium skin tone");
        map.put("🧘🏾‍♀️", "woman in lotus position, medium-dark skin tone");
        map.put("🧘🏾‍♂️", "man in lotus position, medium-dark skin tone");
        map.put("🧘🏿‍♀️", "woman in lotus position, dark skin tone");
        map.put("🧘🏿‍♂️", "man in lotus position, dark skin tone");
        map.put("🧙‍♀️", "woman mage");
        map.put("🧙‍♂️", "man mage");
        map.put("🧙🏻‍♀️", "woman mage, light skin tone");
        map.put("🧙🏻‍♂️", "man mage, light skin tone");
        map.put("🧙🏼‍♀️", "woman mage, medium-light skin tone");
        map.put("🧙🏼‍♂️", "man mage, medium-light skin tone");
        map.put("🧙🏽‍♀️", "woman mage, medium skin tone");
        map.put("🧙🏽‍♂️", "man mage, medium skin tone");
        map.put("🧙🏾‍♀️", "woman mage, medium-dark skin tone");
        map.put("🧙🏾‍♂️", "man mage, medium-dark skin tone");
        map.put("🧙🏿‍♀️", "woman mage, dark skin tone");
        map.put("🧙🏿‍♂️", "man mage, dark skin tone");
        map.put("🧚‍♀️", "woman fairy");
        map.put("🧚‍♂️", "man fairy");
        map.put("🧚🏻‍♀️", "woman fairy, light skin tone");
        map.put("🧚🏻‍♂️", "man fairy, light skin tone");
        map.put("🧚🏼‍♀️", "woman fairy, medium-light skin tone");
        map.put("🧚🏼‍♂️", "man fairy, medium-light skin tone");
        map.put("🧚🏽‍♀️", "woman fairy, medium skin tone");
        map.put("🧚🏽‍♂️", "man fairy, medium skin tone");
        map.put("🧚🏾‍♀️", "woman fairy, medium-dark skin tone");
        map.put("🧚🏾‍♂️", "man fairy, medium-dark skin tone");
        map.put("🧚🏿‍♀️", "woman fairy, dark skin tone");
        map.put("🧚🏿‍♂️", "man fairy, dark skin tone");
        map.put("🧛‍♀️", "woman vampire");
        map.put("🧛‍♂️", "man vampire");
        map.put("🧛🏻‍♀️", "woman vampire, light skin tone");
        map.put("🧛🏻‍♂️", "man vampire, light skin tone");
        map.put("🧛🏼‍♀️", "woman vampire, medium-light skin tone");
        map.put("🧛🏼‍♂️", "man vampire, medium-light skin tone");
        map.put("🧛🏽‍♀️", "woman vampire, medium skin tone");
        map.put("🧛🏽‍♂️", "man vampire, medium skin tone");
        map.put("🧛🏾‍♀️", "woman vampire, medium-dark skin tone");
        map.put("🧛🏾‍♂️", "man vampire, medium-dark skin tone");
        map.put("🧛🏿‍♀️", "woman vampire, dark skin tone");
        map.put("🧛🏿‍♂️", "man vampire, dark skin tone");
        map.put("🧜‍♀️", "mermaid");
        map.put("🧜‍♂️", "merman");
        map.put("🧜🏻‍♀️", "mermaid, light skin tone");
        map.put("🧜🏻‍♂️", "merman, light skin tone");
        map.put("🧜🏼‍♀️", "mermaid, medium-light skin tone");
        map.put("🧜🏼‍♂️", "merman, medium-light skin tone");
        map.put("🧜🏽‍♀️", "mermaid, medium skin tone");
        map.put("🧜🏽‍♂️", "merman, medium skin tone");
        map.put("🧜🏾‍♀️", "mermaid, medium-dark skin tone");
        map.put("🧜🏾‍♂️", "merman, medium-dark skin tone");
        map.put("🧜🏿‍♀️", "mermaid, dark skin tone");
        map.put("🧜🏿‍♂️", "merman, dark skin tone");
        map.put("🧝‍♀️", "woman elf");
        map.put("🧝‍♂️", "man elf");
        map.put("🧝🏻‍♀️", "woman elf, light skin tone");
        map.put("🧝🏻‍♂️", "man elf, light skin tone");
        map.put("🧝🏼‍♀️", "woman elf, medium-light skin tone");
        map.put("🧝🏼‍♂️", "man elf, medium-light skin tone");
        map.put("🧝🏽‍♀️", "woman elf, medium skin tone");
        map.put("🧝🏽‍♂️", "man elf, medium skin tone");
        map.put("🧝🏾‍♀️", "woman elf, medium-dark skin tone");
        map.put("🧝🏾‍♂️", "man elf, medium-dark skin tone");
        map.put("🧝🏿‍♀️", "woman elf, dark skin tone");
        map.put("🧝🏿‍♂️", "man elf, dark skin tone");
        map.put("🧞‍♀️", "woman genie");
        map.put("🧞‍♂️", "man genie");
        map.put("🧟‍♀️", "woman zombie");
        map.put("🧟‍♂️", "man zombie");
        map.put("👨‍🦰", "man, red hair");
        map.put("👨‍🦱", "man, curly hair");
        map.put("👨‍🦲", "man, bald");
        map.put("👨‍🦳", "man, white hair");
        map.put("👨🏻‍🦰", "man, light skin tone, red hair");
        map.put("👨🏻‍🦱", "man, light skin tone, curly hair");
        map.put("👨🏻‍🦲", "man, light skin tone, bald");
        map.put("👨🏻‍🦳", "man, light skin tone, white hair");
        map.put("👨🏼‍🦰", "man, medium-light skin tone, red hair");
        map.put("👨🏼‍🦱", "man, medium-light skin tone, curly hair");
        map.put("👨🏼‍🦲", "man, medium-light skin tone, bald");
        map.put("👨🏼‍🦳", "man, medium-light skin tone, white hair");
        map.put("👨🏽‍🦰", "man, medium skin tone, red hair");
        map.put("👨🏽‍🦱", "man, medium skin tone, curly hair");
        map.put("👨🏽‍🦲", "man, medium skin tone, bald");
        map.put("👨🏽‍🦳", "man, medium skin tone, white hair");
        map.put("👨🏾‍🦰", "man, medium-dark skin tone, red hair");
        map.put("👨🏾‍🦱", "man, medium-dark skin tone, curly hair");
        map.put("👨🏾‍🦲", "man, medium-dark skin tone, bald");
        map.put("👨🏾‍🦳", "man, medium-dark skin tone, white hair");
        map.put("👨🏿‍🦰", "man, dark skin tone, red hair");
        map.put("👨🏿‍🦱", "man, dark skin tone, curly hair");
        map.put("👨🏿‍🦲", "man, dark skin tone, bald");
        map.put("👨🏿‍🦳", "man, dark skin tone, white hair");
        map.put("👩‍🦰", "woman, red hair");
        map.put("👩‍🦱", "woman, curly hair");
        map.put("👩‍🦲", "woman, bald");
        map.put("👩‍🦳", "woman, white hair");
        map.put("👩🏻‍🦰", "woman, light skin tone, red hair");
        map.put("👩🏻‍🦱", "woman, light skin tone, curly hair");
        map.put("👩🏻‍🦲", "woman, light skin tone, bald");
        map.put("👩🏻‍🦳", "woman, light skin tone, white hair");
        map.put("👩🏼‍🦰", "woman, medium-light skin tone, red hair");
        map.put("👩🏼‍🦱", "woman, medium-light skin tone, curly hair");
        map.put("👩🏼‍🦲", "woman, medium-light skin tone, bald");
        map.put("👩🏼‍🦳", "woman, medium-light skin tone, white hair");
        map.put("👩🏽‍🦰", "woman, medium skin tone, red hair");
        map.put("👩🏽‍🦱", "woman, medium skin tone, curly hair");
        map.put("👩🏽‍🦲", "woman, medium skin tone, bald");
        map.put("👩🏽‍🦳", "woman, medium skin tone, white hair");
        map.put("👩🏾‍🦰", "woman, medium-dark skin tone, red hair");
        map.put("👩🏾‍🦱", "woman, medium-dark skin tone, curly hair");
        map.put("👩🏾‍🦲", "woman, medium-dark skin tone, bald");
        map.put("👩🏾‍🦳", "woman, medium-dark skin tone, white hair");
        map.put("👩🏿‍🦰", "woman, dark skin tone, red hair");
        map.put("👩🏿‍🦱", "woman, dark skin tone, curly hair");
        map.put("👩🏿‍🦲", "woman, dark skin tone, bald");
        map.put("👩🏿‍🦳", "woman, dark skin tone, white hair");
        map.put("🧑‍🦰", "person, red hair");
        map.put("🧑‍🦱", "person, curly hair");
        map.put("🧑‍🦲", "person, bald");
        map.put("🧑‍🦳", "person, white hair");
        map.put("🧑🏻‍🦰", "person, light skin tone, red hair");
        map.put("🧑🏻‍🦱", "person, light skin tone, curly hair");
        map.put("🧑🏻‍🦲", "person, light skin tone, bald");
        map.put("🧑🏻‍🦳", "person, light skin tone, white hair");
        map.put("🧑🏼‍🦰", "person, medium-light skin tone, red hair");
        map.put("🧑🏼‍🦱", "person, medium-light skin tone, curly hair");
        map.put("🧑🏼‍🦲", "person, medium-light skin tone, bald");
        map.put("🧑🏼‍🦳", "person, medium-light skin tone, white hair");
        map.put("🧑🏽‍🦰", "person, medium skin tone, red hair");
        map.put("🧑🏽‍🦱", "person, medium skin tone, curly hair");
        map.put("🧑🏽‍🦲", "person, medium skin tone, bald");
        map.put("🧑🏽‍🦳", "person, medium skin tone, white hair");
        map.put("🧑🏾‍🦰", "person, medium-dark skin tone, red hair");
        map.put("🧑🏾‍🦱", "person, medium-dark skin tone, curly hair");
        map.put("🧑🏾‍🦲", "person, medium-dark skin tone, bald");
        map.put("🧑🏾‍🦳", "person, medium-dark skin tone, white hair");
        map.put("🧑🏿‍🦰", "person, dark skin tone, red hair");
        map.put("🧑🏿‍🦱", "person, dark skin tone, curly hair");
        map.put("🧑🏿‍🦲", "person, dark skin tone, bald");
        map.put("🧑🏿‍🦳", "person, dark skin tone, white hair");
        map.put("⛓️‍💥", "broken chain");
        map.put("❤️‍🔥", "heart on fire");
        map.put("❤️‍🩹", "mending heart");
        map.put("🍄‍🟫", "brown mushroom");
        map.put("🍋‍🟩", "lime");
        map.put("🏳️‍⚧️", "transgender flag");
        map.put("🏳️‍🌈", "rainbow flag");
        map.put("🏴‍☠️", "pirate flag");
        map.put("🐈‍⬛", "black cat");
        map.put("🐕‍🦺", "service dog");
        map.put("🐦‍⬛", "black bird");
        map.put("🐦‍🔥", "phoenix");
        map.put("🐻‍❄️", "polar bear");
        map.put("👁️‍🗨️", "eye in speech bubble");
        map.put("😮‍💨", "face exhaling");
        map.put("😵‍💫", "face with spiral eyes");
        map.put("😶‍🌫️", "face in clouds");
        map.put("🙂‍↔️", "head shaking horizontally");
        map.put("🙂‍↕️", "head shaking vertically");

        return map;
    }

    private static final Pattern diacritics = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    /**
     * Removes accented characters from a string; if the "base" characters are non-English anyway then the result won't
     * be an ASCII string, but otherwise it probably will be.
     * <br>
     * This version can contain ligatures such as "æ" and "œ", but not with diacritics.
     * <br>
     * <a href="http://stackoverflow.com/a/1215117">Credit to StackOverflow user hashable</a>.
     *
     * @param str a string that may contain accented characters
     * @return a string with all accented characters replaced with their (possibly ASCII) counterparts
     */
    public static String removeAccents(String str) {
        String alteredString = Normalizer.normalize(str, Normalizer.Form.NFD);
        return diacritics.matcher(alteredString).replaceAll("");
    }

    public static String emojiToCodePoints(String emoji) {
        String name = emoji.codePoints()
                .mapToObj(pt -> String.format("%04x", pt))
//                .mapToObj(pt -> pt >= 0x10000 || pt == 0x200D ? String.format("%04x", pt) : String.format("%04x_FE0F", pt))
                .reduce("emoji_u", (a, b) -> a + b + "_");
        return name.substring(0, name.length()-1);
    }

    public static String stripFE0F(String str) {
        return str.replaceAll("_fe0f", "");
    }


    public static String codePointsToEmoji(String codePoints) {
        StringBuilder sb = new StringBuilder(8);
        String[] pts = codePoints.substring(7).split("_");
        for(String c : pts){
            int n = Integer.parseInt(c, 16);
            sb.appendCodePoint(n);
//            if(n < 0x10000 && n != 0x200D){
//                sb.appendCodePoint(0xFE0F);
//            }
        }
        return sb.toString();
    }

}