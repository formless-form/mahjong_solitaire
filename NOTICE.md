# Notices & Asset Attribution

> **Dark Mahjong** is a modified fork of [PcmciaKai/mahjong_solitaire](https://github.com/PcmciaKai/mahjong_solitaire)
> (Copyright (C) 2026 PcmciaKai), changed from 2026-10-25 by Gareth (github.com/formless-form):
> rebranded, portrait-only, dark theme, stats/timer/support dialog removed, new title screen,
> win celebration, tilted-camera table with shader-lit tiles and tabletops. The licences below cover the original assets and those added by the fork.

This repository uses a multi-license model to clearly separate source code permissions from media asset rights and third-party attributions.

* **Source Code:** Licensed under the [GNU General Public License v3.0 (GPLv3)](LICENSE) with an [App Store Exception](#1-source-code-license), and an [additional term](#additional-term-names-and-logos-gplv3-section-7e) withholding the Dark Mahjong names and logos.
* **Graphics:** Dedicated to the [Public Domain (CC0 1.0)](#2-graphics-assets).
* **Sound Effects:** Dedicated to the [Public Domain (CC0 1.0)](#3-audio-assets).
* **Background Music:** [AI-generated](#background-music) with Suno, dedicated to the Public Domain (CC0 1.0).
* **Fonts:** Licensed under the [SIL Open Font License 1.1](#4-font-assets).

---

## 1. Source Code License

Copyright (C) 2026 PcmciaKai
Dark Mahjong's changes: Copyright (C) 2026 Grevillea Labs

The source code is licensed under the GNU General Public License, version 3. The full license text is available in the [LICENSE](LICENSE) file.

### App Store Exception (Additional Permission under GPLv3 Section 7)

As a special exception to the GNU General Public License version 3.0, the copyright holders of this software grant permission to distribute compiled binaries of this program (and derivative works built from it) on the Apple App Store or other digital distribution channels that apply Terms of Service or DRM constraints. All source code modifications must still be made available under the terms of the GNU General Public License v3.0.

### Additional term: names and logos (GPLv3 Section 7(e))

For the material Dark Mahjong adds, this licence does not grant any right to use the names **"Dark Mahjong"** and **"暗夜麻將"**, the name seal, the launcher icon, or the name **"Grevillea Labs"**, under trademark law or otherwise. A modified version that you distribute must go by a different name and icon, and must not suggest that it is Dark Mahjong or comes from its makers. Saying truthfully that your work is based on Dark Mahjong is fine. (The original project's own name is PcmciaKai's and is not covered here.)

### A request

Dark Mahjong is made to be free of ads and tracking, with nothing to buy, offline and open source, and it will stay so. We ask anyone who builds on it to keep it the same: no ads, no in-app purchases, no tracking, and not sold for profit. This is a request rather than a licence term (GPLv3 permits commercial use), and we trust it will be honoured in the spirit the game is given.

---

## 2. Graphics Assets

### Mahjong Tiles

* **Author:** samoheen
* **Source:** [samoheen/mahjong-tiles (GitHub)](https://github.com/samoheen/mahjong-tiles)
* **License:** Public Domain / CC0 1.0 Universal
* **Details:** All tile illustrations (PNG/SVG) are free to copy, modify, and distribute for any purpose without restrictions.

---

## 3. Audio Assets

### Match sound

* **Files:** `app/src/main/res/raw/tile_match_1.ogg` to `tile_match_4.ogg`, built by `tools/make-match-sounds.py`: a tile clack from `sounds/tile_clack-original.wav` (mono, trimmed, levelled, sped up 1.4×, four slightly different pitches) over synthesised stardust (pentatonic chimes and a glitter fizz)
* **License:** [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) (public domain dedication); no attribution

### Tile tap

* **Files:** `app/src/main/res/raw/tile_tap_1.ogg` to `tile_tap_4.ogg`, built by `tools/make-tap-sounds.py` (mono, trimmed, levelled, two slightly different pitches each) from `impactWood_light_000.ogg` and `impactWood_light_002.ogg` in `sounds/kenney/`
* **Source:** [Impact Sounds](https://kenney.nl/assets/impact-sounds) by **Kenney** (www.kenney.nl)
* **License:** [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/); see `sounds/kenney/License.txt`

### Fireplace

* **File:** `app/src/main/res/raw/fireplace.ogg`, built by `tools/make-fire-sound.py` (mono, the first 200 seconds with their ends crossfaded into a seamless loop, softened, levelled, Opus 80 kbps) from `sounds/freesound/447818__courter__crackeling-fireplace.mp3`
* **Source:** [Crackeling Fireplace](https://freesound.org/people/courter/sounds/447818/) by **Christopher C. Courter** (courter), Freesound
* **License:** [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)

### Win fireworks

* **File:** `app/src/main/res/raw/win_fireworks.ogg`, the first 5.5 seconds mixed to mono, levelled and faded by `tools/make-win-sound.py` from `sounds/freesound/769030__greysound__fireworks-annapolis-royal-ns-excerpt.flac` (the recording's first 10 seconds)
* **Source:** [Fireworks - Annapolis Royal, NS](https://freesound.org/people/greysound/sounds/769030/) by **greysound**, Freesound
* **License:** [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/)

### Background Music

* **Files:** `zen_mountain_mist.opus`, `zen_mountain_stream.opus`, `ink_cloud_meditation.opus`, `ink_cloud_study.opus`, `jade_rain_study.opus`
* **Origin:** AI-generated with [Suno](https://suno.com) on a paid (Pro) plan, which grants ownership of the output to the subscriber
* **License:** [Public Domain / CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/)
* **Note:** These tracks were not composed or performed by a human. To the extent any copyright or related rights exist in them, PcmciaKai waives them under CC0 1.0. You may copy, modify, and distribute them for any purpose, including commercially, without asking permission.

---

## 4. Font Assets

### Google Sans Flex

* **Author:** The Google Sans Flex Project Authors — [googlefonts/googlesans-flex](https://github.com/googlefonts/googlesans-flex), via [Google Fonts](https://fonts.google.com/specimen/Google+Sans+Flex)
* **File:** `app/src/main/res/font/google_sans_flex.ttf` (all text except the title screen)
* **Changes:** pinned to upright, normal width and no rounding (the weight and optical-size axes are kept) and subset to Latin, using fontTools. No Reserved Font Names apply.
* **License:** SIL Open Font License, Version 1.1 — see [LICENSES/OFL-1.1-GoogleSansFlex.txt](LICENSES/OFL-1.1-GoogleSansFlex.txt). "Google Sans Flex" is a trademark of Google LLC; its use here is only to identify the font and implies no affiliation with or endorsement by Google.
* **Replaced:** upstream's Tomorrow font, no longer bundled.

---

## 5. Assets added by Dark Mahjong

### Layouts from Mah

* **Source:** [Mah](https://github.com/ffalt/mah) by **ffalt**, an HTML5 mahjong solitaire game: 29 of the 84 boards in `src/assets/data/boards.json` (commit `43c3174`): those that suit a phone held upright and whose tiles all rest fully on the layer below
* **File:** `app/src/main/java/com/vortessence/mahjong/data/repository/MahLayouts.kt`, generated by `tools/import-mah-layouts.py` (board positions kept in Mah's own format; "Teotihucan" respelled "Teotihuacan")
* **License:** MIT, Copyright (c) 2016 ffalt — see [LICENSES/MIT-Mah.txt](LICENSES/MIT-Mah.txt)

### Title-screen photograph

* **File:** `app/src/main/res/drawable-nodpi/title_mountains.webp` (portrait crop)
* **Original:** [Sanqing Mountain is surrounded by clouds and mists](https://commons.wikimedia.org/wiki/File:Sanqing_Mountain_is_surrounded_by_clouds_and_mists1.jpg) by **Huangdan2060**, via Wikimedia Commons
* **License:** [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) (public domain dedication)
* **Changes:** cropped to a tall portrait frame without distortion, darkened and colour-graded. No attribution is required, so none is shown in the app.
* **Original:** kept at full resolution in `tools/sources/title-sanqing-mountain-commons.jpg`; `tools/make-title-photo.py` rebuilds the title image from it, or crops new frames with the same grade

### Thai straw mat

* Drawn procedurally by the table shader (no image is included). Its proportions, palette and motif bands were measured from, and modelled on, [Cambodian mat motifs](https://commons.wikimedia.org/wiki/File:Cambodian_mat_motifs.jpg) by **Willuconquer** (CC BY-SA 4.0, Wikimedia Commons), a woven sedge mat of the kind made across Cambodia and Thailand. Thanks to the photographer.

### Name seal (seal script)

* **File:** `app/src/main/res/font/seal_script_name.ttf` (subset to the four characters 暗夜麻將)
* **Source:** 全字庫說文解字 TrueType font (`ebas927.ttf`) from CNS11643 中文標準交換碼全字庫, **Ministry of Digital Affairs**, Taiwan — https://www.cns11643.gov.tw
* **License:** [Open Government Data License, version 1.0](https://data.gov.tw/license) (the CNS11643 data may alternatively be used under OFL-1.1). Subset by the Dark Mahjong project.

### Title font

* **Cinzel** by Natanael Gama — [Google Fonts](https://fonts.google.com/specimen/Cinzel)
* **File:** `app/src/main/res/font/cinzel.ttf`
* **License:** SIL Open Font License 1.1 — see [LICENSES/OFL-1.1-Cinzel.txt](LICENSES/OFL-1.1-Cinzel.txt)
