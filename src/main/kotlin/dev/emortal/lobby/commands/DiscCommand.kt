package dev.emortal.lobby.commands

import dev.emortal.lobby.LobbyExtension
import dev.emortal.lobby.commands.DiscCommand.nbsSongs
import dev.emortal.lobby.commands.DiscCommand.playingDiscTag
import dev.emortal.lobby.commands.DiscCommand.stopPlayingTaskMap
import dev.emortal.lobby.commands.DiscCommand.suggestions
import dev.emortal.lobby.util.MusicDisc
import dev.emortal.nbstom.NBS
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.SoundStop
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.asMini
import world.cepi.kstom.command.arguments.literal
import world.cepi.kstom.command.arguments.suggest
import world.cepi.kstom.command.kommand.Kommand
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension
import kotlin.math.roundToLong

object DiscCommand : Kommand({

    onlyPlayers()

    // If no arguments given, open inventory
    default {
        val musicPlayerInventory = LobbyExtension.playerMusicInvMap[player] ?: return@default

        player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2f))
        player.openInventory(musicPlayerInventory)
    }

    val refresh by literal
    val stop by literal

    val discArgument = ArgumentType.StringArray("disc").suggest {
        suggestions
    }


    syntax(stop) {
        val discValues = MusicDisc.values()
        val playingDisc = player.getTag(playingDiscTag)?.let { discValues[it] }

        playingDisc?.sound?.let {
            player.stopSound(SoundStop.named(it))
            player.removeTag(playingDiscTag)
        }

        stopPlayingTaskMap[player]?.cancel()
        NBS.stopPlaying(player)
    }

    syntax(refresh) {
        DiscCommand.refreshSongs()
    }

    syntax(discArgument) {
        val disc = context.get(discArgument).joinToString(separator = " ")
        val discValues = MusicDisc.values()
        val playingDisc = player.getTag(playingDiscTag)?.let { discValues[it] }

        playingDisc?.sound?.let {
            player.stopSound(SoundStop.named(it))
            player.removeTag(playingDiscTag)
        }

        stopPlayingTaskMap[player]?.cancel()
        NBS.stopPlaying(player)

        var discName: String
        try {
            val nowPlayingDisc = MusicDisc.valueOf("MUSIC_DISC_${disc.uppercase()}")

            discName = nowPlayingDisc.description

            player.setTag(playingDiscTag, discValues.indexOf(nowPlayingDisc))
            player.playSound(Sound.sound(nowPlayingDisc.sound, Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())

            stopPlayingTaskMap[player] = Manager.scheduler.buildTask {

                if (player.hasTag(LoopCommand.loopTag)) {
                    Manager.command.execute(sender, "disc ${nowPlayingDisc.shortName}")
                } else {
                    Manager.command.execute(sender, "disc stop")
                }

            }.delay(Duration.ofSeconds(nowPlayingDisc.length.toLong())).schedule()
        } catch (e: IllegalArgumentException) {
            if (!nbsSongs.contains(disc)) {
                sender.sendMessage(Component.text("Invalid song", NamedTextColor.RED))
                return@syntax
            }

            val nbs = NBS(Path.of("./nbs/${disc}.nbs"))
            NBS.playWithParticles(nbs, player)

            if (disc == "DJ Got Us Fallin' in Love") {
                Manager.scheduler.buildTask {
                    player.sendMessage("Creeper?")
                }.delay(Duration.ofMillis(3850)).schedule()
                Manager.scheduler.buildTask {
                    player.sendMessage("Aww man")
                }.delay(Duration.ofMillis(5900)).schedule()
            }

            stopPlayingTaskMap[player] = Manager.scheduler.buildTask {

                if (player.hasTag(LoopCommand.loopTag)) {
                    Manager.command.execute(sender, "disc ${disc}")
                } else {
                    Manager.command.execute(sender, "disc stop")
                }

            }.delay(Duration.ofSeconds((nbs.length / nbs.tps).roundToLong())).schedule()

            discName = "${nbs.originalAuthor.ifEmpty { nbs.author }} - ${nbs.songName}"
        }

        player.sendActionBar("<gray>Playing: <aqua>${discName}</aqua>".asMini())


    }
}, "disc", "music") {

    val stopPlayingTaskMap = HashMap<Player, Task>()
    private val playingDiscTag = Tag.Integer("playingDisc")

    private var nbsSongs: List<String> = listOf()
    private var suggestions: List<String> = listOf()

    fun refreshSongs() {
        try {
            nbsSongs = Files.list(Path.of("./nbs/")).collect(Collectors.toUnmodifiableList()).map { it.nameWithoutExtension }
            suggestions = MusicDisc.values().map { it.shortName } + nbsSongs
        } catch (e: Exception) {
            e.printStackTrace()
            nbsSongs = listOf()
        }
    }

}