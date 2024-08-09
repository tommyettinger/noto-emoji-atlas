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

# How do I get it?

Atlases are available for 24x24 (small) and 32x32 (mid) sizes of Noto Emoji. You can choose from
[atlas-small-color](atlas-small-color/), and [atlas-mid-color](atlas-mid-color/).

# License

[OFL 1.1](LICENSE.txt).