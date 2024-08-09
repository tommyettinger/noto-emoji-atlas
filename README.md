# noto-emoji-atlas
Texture atlases for the [Noto-Emoji](https://fonts.google.com/noto/specimen/Noto+Color+Emoji) emoji set;
[libGDX](https://libgdx.com/)-compatible.

**[Preview of all emoji, their names, and their aliases here](https://tommyettinger.github.io/noto-emoji-atlas/).**

# What is it?

Texture atlases are a convenient and efficient way of accessing any of a large number of images without incurring
performance penalties due to texture swaps. In libGDX games, texture atlases tend to be used heavily, but creating
them can be a hassle (especially for large atlases). The [Noto Emoji](https://fonts.google.com/noto/specimen/Noto+Color+Emoji)
are a nicely-designed set of detailed, high-quality emoji with wide coverage for Unicode (including the latest standard at
the time of writing, 15.1).

This project exists to put the Noto Emoji into texture atlasees so games can use them more easily. This includes
games that use [TextraTypist](https://github.com/tommyettinger/textratypist/), which can load emoji atlases as a main
feature. This project also does some work to try to resize the initially 72x72 or 32x32 Noto Emoji images to 24x24.
This involved sharpening blur on resize, and should produce more legible emoji at small sizes
compared to naively scaling down with a default filter.

# But what about...

This is a very similar project to [twemoji-atlas](https://github.com/tommyettinger/twemoji-atlas) and
[openmoji-atlas](https://github.com/tommyettinger/openmoji-atlas). The main reason you might prefer Noto Emoji is simply
that it looks really nice, especially at larger sizes. It also has excellent coverage of Emoji 15.1 . You might prefer Twemoji
if you want your emoji to match how they look on other platforms that also use Twemoji, such as Discord. You might prefer
OpenMoji if you want monochrome glyphs to be available, or want some of its extended non-Unicode emoji. This project has a lot
of extra aliases ("shortcodes", in emoji parlance) for glyphs, but they are mostly different from the aliases used in the other
two projects. They have to be different at this point because Twemoji hasn't been updated to Emoji 15.1 to my knowledge, and
I'm not sure about OpenMoji.

# How do I get it?

Atlases are available for 24x24 (small) and 32x32 (mid) sizes of Noto Emoji. You can choose from
[atlas-small-color](atlas-small-color/), and [atlas-mid-color](atlas-mid-color/); both of these use a 2048x2048 texture,
so the higher-quality atlas-mid-color is probably better for most usage. There is also a larger, non-power-of-two-sized
atlas for the 72x72 size in [atlas-large-color](atlas-large-color/); this is only meant to be used on desktop builds
because it uses one huge 5254x4000 texture. This texture could be packed into a 8192x8192 larger atlas, such as one with
a font in it and other assets. A 16384x16384 texture isn't out of the question for desktop apps/games, either.

# License

[OFL 1.1](LICENSE.txt).

This project uses data made available under the MIT license by [EmojiBase](https://github.com/milesj/emojibase/tree/master).
