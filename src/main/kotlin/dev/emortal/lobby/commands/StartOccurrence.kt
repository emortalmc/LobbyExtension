package dev.emortal.lobby.commands

import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameManager.game
import dev.emortal.lobby.games.LobbyGame
import dev.emortal.lobby.occurrences.ChatOccurrence
import dev.emortal.lobby.util.showFireworkWithDuration
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player
import net.minestom.server.item.firework.FireworkEffect
import net.minestom.server.item.firework.FireworkEffectType
import world.cepi.kstom.command.kommand.Kommand
import java.awt.Color
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

object StartOccurrence : Kommand({

    onlyPlayers

    default {
        if (!player.instance!!.getTag(GameManager.gameNameTag).contentEquals("lobby", true)) {
            player.sendActionBar(Component.text("Not in a lobby!", NamedTextColor.RED))
            return@default
        }

        if (player.username != "emortaldev") return@default

        val lobbyGame = (player.game ?: return@default) as? LobbyGame ?: return@default
        val occurrence = ChatOccurrence()
        occurrence.start(lobbyGame)

        lobbyGame.occurrenceStopTask = lobbyGame.instance.scheduler().buildTask {
            lobbyGame.currentOccurrence?.stop(lobbyGame)
        }.delay(Duration.ofSeconds(40)).schedule()




    }

}, "startoccurrence")