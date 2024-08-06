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
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectSet;
import com.github.tommyettinger.anim8.Dithered;
import com.github.tommyettinger.anim8.PNG8;
import com.github.tommyettinger.anim8.QualityPalette;

import java.text.Normalizer;
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
 */
public class Main extends ApplicationAdapter {
//    public static final String MODE = "MODIFY_JSON"; // run this first
    public static final String MODE = "EMOJI_LARGE"; // run this second
//    public static final String MODE = "EMOJI_SMALL";
//    public static final String MODE = "EMOJI_INOFFENSIVE"; // ugh, but needed
//    public static final String MODE = "EMOJI_HTML";
//    public static final String MODE = "FLAG";
//    public static final String MODE = "WRITE_INFO";
//    public static final String MODE = "ALTERNATE_PALETTES";

    public static final String TYPE = "color";
//    public static final String TYPE = "black";
    public static final String RAW_DIR = "noto-emoji-72x72-" + TYPE;

    public static final String JSON = "noto-emoji-cleaned.json";

    @Override
    public void create() {
        JsonReader reader = new JsonReader();
        if("MODIFY_JSON".equals(MODE)) {
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
                for(String s : new String[]{
                        "description","subgroups","tags",
                        "skintone","skintone_combination","skintone_base_emoji","skintone_base_hexcode",
                        "unicode","order","unicode_version","ios_version"}){
                    entry.remove(s);
                }
            }

            Gdx.files.local(JSON).writeString(json.toJson(JsonWriter.OutputType.json).replace("{", "\n{"), false);
        }
        else if("ALTERNATE_PALETTES".equals(MODE)) {
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
                Pixmap large = new Pixmap(Gdx.files.local("../../atlas/NotoEmoji"+TYPE+".png"));
                png.write(current.child("atlas/NotoEmoji"+TYPE+".png"), large, false, true);
                large.dispose();
                for (int i = 2; i <= 5; i++) {
                    Pixmap largeN = new Pixmap(Gdx.files.local("../../atlas/NotoEmoji"+TYPE+i+".png"));
                    png.write(current.child("atlas/NotoEmoji"+TYPE+i+".png"), largeN, false, true);
                    largeN.dispose();
                }
                Gdx.files.local("../../atlas/NotoEmoji.atlas").copyTo(current.child("atlas"));
                Pixmap mid = new Pixmap(Gdx.files.local("../../atlas-mid/NotoEmoji"+TYPE+".png"));
                png.write(current.child("atlas-mid/NotoEmoji"+TYPE+".png"), mid, false, true);
                mid.dispose();
                Gdx.files.local("../../atlas-mid/NotoEmoji"+TYPE+".atlas").copyTo(current.child("atlas-mid"));
                Pixmap small = new Pixmap(Gdx.files.local("../../atlas-small/NotoEmoji"+TYPE+".png"));
                png.write(current.child("atlas-small/NotoEmoji"+TYPE+".png"), small, false, true);
                small.dispose();
                Gdx.files.local("../../atlas-small/NotoEmoji"+TYPE+".atlas").copyTo(current.child("atlas-small"));
            }
        }
        else if("WRITE_INFO".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal(JSON));
            ObjectSet<String> used = new ObjectSet<>(json.size);
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String name = entry.getString("name");
                if(used.add(name)) {
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
        }
        else if("EMOJI_SMALL".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal(JSON));
            ObjectSet<String> used = new ObjectSet<>(json.size);
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String name = entry.getString("name");
                if(used.add(name)) {
                    name += ".png";
                    FileHandle original = Gdx.files.local("../../scaled-small-"+TYPE+"/name/" + name);
                    if (original.exists()) {
                        if(entry.has("emoji"))
                            original.copyTo(Gdx.files.local("../../renamed-small-"+TYPE+"/emoji/" + entry.getString("emoji") + ".png"));
                        original.copyTo(Gdx.files.local("../../renamed-small-"+TYPE+"/name/" + name));
                    }
                } else {
                    entry.remove();
                }
            }
        }
        else if("EMOJI_LARGE".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal(JSON));
            ObjectSet<String> used = new ObjectSet<>(json.size);
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String name = entry.getString("name");
                if(used.add(name)) {
                    String emoji = entry.getString("emoji"), codename = emojiToCodePoints(emoji);
                    name += ".png";
                    FileHandle original = Gdx.files.local("../../"+RAW_DIR+"/" + codename + ".png");
                    if (original.exists()) {
                        original.copyTo(Gdx.files.local("../../renamed-"+TYPE+"/emoji/" + emoji + ".png"));
                        original.copyTo(Gdx.files.local("../../renamed-"+TYPE+"/name/" + name));
                    }
                } else {
                    entry.remove();
                }
            }
        }
        else if("EMOJI_INOFFENSIVE".equals(MODE) || "EMOJI_INOFFENSIVE_MONO".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal(JSON));
            ObjectSet<String> used = new ObjectSet<>(json.size);
            String where = "EMOJI_INOFFENSIVE".equals(MODE) ? "/inoffensive-" : "/inoffensive-mono-";
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String name = entry.getString("name");
                if(name.endsWith("skin tone")) continue; // we're intending to make the images grayscale.
                if(name.contains("flag")) continue; // some false positives, but less politically sensitive stuff.
                if("star of David".equals(name)) continue;
                if("wheel of dharma".equals(name)) continue;
                if("yin yang".equals(name)) continue;
                if("latin cross".equals(name)) continue;
                if("orthodox cross".equals(name)) continue;
                if("star and crescent".equals(name)) continue;
                if("menorah".equals(name)) continue;
                if("dotted six-pointed star".equals(name)) continue;
                if("khanda".equals(name)) continue;
                if("red hair".equals(name)) continue;
                if("curly hair".equals(name)) continue;
                if("white hair".equals(name)) continue;
                if("bald".equals(name)) continue;
                if("no one under eighteen".equals(name)) continue;
                if("no smoking".equals(name)) continue;
                if("cigarette".equals(name)) continue;
                if("bomb".equals(name)) continue;
                if("church".equals(name)) continue;
                if("mosque".equals(name)) continue;
                if("hindu temple".equals(name)) continue;
                if("synagogue".equals(name)) continue;
                if("shinto shrine".equals(name)) continue;
                if("kaaba".equals(name)) continue;
                if("map of Japan".equals(name)) continue;
                if("wedding".equals(name)) continue;
                if("Tokyo tower".equals(name)) continue;
                if("Statue of Liberty".equals(name)) continue;
                if("sake".equals(name)) continue;
                if("love hotel".equals(name)) continue;
                if("breast-feeding".equals(name)) continue;
                if("eggplant".equals(name)) continue;
                if("peach".equals(name)) continue;
                if("bottle with popping cork".equals(name)) continue;
                if("wine glass".equals(name)) continue;
                if("cocktail glass".equals(name)) continue;
                if("tropical drink".equals(name)) continue;
                if("beer mug".equals(name)) continue;
                if("clinking beer mugs".equals(name)) continue;
                if("clinking glasses".equals(name)) continue;
                if("tumbler glass".equals(name)) continue;
                if("drunk person".equals(name)) continue;
                if("trump".equals(name)) continue;
                if("Greta Thunberg".equals(name)) continue;
                if("Twitter".equals(name)) continue;
                if("pinterest".equals(name)) continue;
                if("facebook".equals(name)) continue;
                if("instagram".equals(name)) continue;
                if("youtube".equals(name)) continue;
                if("github".equals(name)) continue;
                if("linkedin".equals(name)) continue;
                if("android".equals(name)) continue;
                if("musicbrainz".equals(name)) continue;
                if("openfoodfact".equals(name)) continue;
                if("openstreetmap".equals(name)) continue;
                if("wikidata".equals(name)) continue;
                if("Firefox".equals(name)) continue;
                if("Safari".equals(name)) continue;
                if("Opera".equals(name)) continue;
                if("Chromium".equals(name)) continue;
                if("Chrome".equals(name)) continue;
                if("Netscape Navigator".equals(name)) continue;
                if("Internet Explorer".equals(name)) continue;
                if("Edge".equals(name)) continue;
                if("iNaturalist".equals(name)) continue;
                if("gitlab".equals(name)) continue;
                if("mastodon".equals(name)) continue;
                if("peertube".equals(name)) continue;
                if("pixelfed".equals(name)) continue;
                if("signal".equals(name)) continue;
                if("element".equals(name)) continue;
                if("jellyfin".equals(name)) continue;
                if("reddit".equals(name)) continue;
                if("discord".equals(name)) continue;
                if("c".equals(name)) continue;
                if("cplusplus".equals(name)) continue;
                if("csharp".equals(name)) continue;
                if("chrome canary".equals(name)) continue;
                if("firefox developer".equals(name)) continue;
                if("firefox nightly".equals(name)) continue;
                if("javascript".equals(name)) continue;
                if("typescript".equals(name)) continue;
                if("webassembly".equals(name)) continue;
                if("svg".equals(name)) continue;
                if("markdown".equals(name)) continue;
                if("winrar".equals(name)) continue;
                if("ubuntu".equals(name)) continue;
                if("windows".equals(name)) continue;
                if("artstation".equals(name)) continue;
                if("apple".equals(name)) continue;
                if(name.startsWith("family")) continue;
                if(name.startsWith("couple")) continue;
                if(name.startsWith("kiss")) continue;
                if(name.startsWith("pregnant")) continue;
                if(name.contains("holding hands")) continue;
                if(used.add(name)) {
                    String codename = entry.getString("hexcode");
                    name += ".png";
                    FileHandle original = Gdx.files.local("../../"+RAW_DIR+"/" + codename + ".png");
                    if (original.exists()) {
                        if(entry.has("emoji"))
                            original.copyTo(Gdx.files.local("../.."+where+TYPE+"/emoji/" + entry.getString("emoji") + ".png"));
                        original.copyTo(Gdx.files.local("../.."+where+TYPE+"/name/" + name));
                    }
                } else {
                    entry.remove();
                }
            }
            Gdx.files.local("noto-emoji-info-" + ("EMOJI_INOFFENSIVE".equals(MODE) ? "inoffensive" : "inoffensive-mono") + ".json").writeString(json.toJson(JsonWriter.OutputType.json).replace("{", "\n{"), false);
        }
        else if("EMOJI_HTML".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal("noto-emoji-info.json"));
            StringBuilder sb = new StringBuilder(4096);
            sb.append("""
                    <!doctype html>
                    <html>
                    <head>
                    \t<title>NotoEmoji Preview</title>
                    \t<meta http-equiv="content-type" content="text/html; charset=UTF-8">
                    \t<meta id="gameViewport" name="viewport" content="width=device-width initial-scale=1">
                    \t<link href="styles.css" rel="stylesheet" type="text/css">
                    </head>
                    
                    """);
            sb.append("<body>\n");
            sb.append("<h1>NotoEmoji Preview</h1>\n");
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
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                String emojiChar = entry.getString("emoji", "");
                String name = entry.getString("name");
                String emojiFile = "name/" + name + ".png";
                sb.append("\t<div class=\"item\">\n" +
                                "\t\t<img src=\"").append(TYPE).append('/')
                        .append(emojiFile).append("\" alt=\"").append(name).append("\" />\n");
                if(!emojiChar.isEmpty()) sb.append("\t\t<p>").append(emojiChar).append("</p>\n");
                sb.append("\t\t<p>").append(name).append("</p>\n").append("\t</div>\n");
            }
            sb.append("</div>\n</body>\n");
            sb.append("</html>\n");
            Gdx.files.local(TYPE.equals("color") ? "index.html" : "black.html")
                    .writeString(sb.toString(), false, "UTF8");
        }
        else if("FLAG".equals(MODE)) {
            JsonValue json = reader.parse(Gdx.files.internal(JSON));
            char[] buffer = new char[2];
            for (JsonValue entry = json.child; entry != null; entry = entry.next) {
                if(!"Flags (country-flag)".equals(entry.getString("category"))) continue;

                String codename = entry.getString("hexcode") + ".png";
                String charString = entry.getString("emoji") + ".png";
                String name = entry.getString("name");
                String countryUnicode = entry.getString("emoji");
                buffer[0] = (char)(countryUnicode.codePointAt(1) - 56806 + 'A');
                buffer[1] = (char)(countryUnicode.codePointAt(3) - 56806 + 'A');
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
                .mapToObj(pt -> String.format("%04X", pt))
                .reduce("emoji_u", (a, b) -> a + b + "_");
        return name.substring(0, name.length()-1);
    }

}