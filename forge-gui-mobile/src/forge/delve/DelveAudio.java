package forge.delve;

import forge.sound.MusicPlaylist;
import forge.sound.SoundEffectType;
import forge.sound.SoundSystem;

/**
 * Music and sound for Delve, using Forge's Adventure soundtrack (town, cave,
 * castle, boss) and its sound effects. Respects the player's Forge volume settings.
 */
final class DelveAudio {
    private DelveAudio() {}

    /** Switch the background music unless that playlist is already playing. */
    static void music(MusicPlaylist playlist) {
        try {
            if (SoundSystem.instance.getCurrentPlaylist() != playlist)
                SoundSystem.instance.setBackgroundMusic(playlist);
        } catch (Exception e) {
            e.printStackTrace(); // audio must never break the game
        }
    }

    static void sfx(SoundEffectType type) {
        try {
            SoundSystem.instance.play(type, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void town() { music(MusicPlaylist.TOWN); }
    static void dungeon() { music(MusicPlaylist.CAVE); }
    static void castle() { music(MusicPlaylist.CASTLE); }
    static void menus() { music(MusicPlaylist.MENUS); }

    static void coins() { sfx(SoundEffectType.CoinsDrop); }
    static void heal() { sfx(SoundEffectType.LifeGain); }
    static void hurt() { sfx(SoundEffectType.LifeLoss); }
    static void shuffle() { sfx(SoundEffectType.Shuffle); }
    static void flip() { sfx(SoundEffectType.FlipCard); }
    static void day() { sfx(SoundEffectType.Daytime); }
    static void night() { sfx(SoundEffectType.Nighttime); }
}
