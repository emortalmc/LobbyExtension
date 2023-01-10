package dev.emortal.lobby.mount

import dev.emortal.lobby.games.SeatEntity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.timer.TaskSchedule

sealed class DolphinMount : Mount(Component.text("Dolphin", NamedTextColor.AQUA)) {

    override fun spawn(instance: Instance, player: Player) {
        val entity = SeatEntity(physics = true, entityType = EntityType.DOLPHIN) {
            destroy()
        }

        entity.scheduler().buildTask {
            tick(player)
        }.repeat(TaskSchedule.nextTick()).schedule()

        entity.setInstance(instance, player.position).thenRun {
            entity.addPassenger(player)
        }

        entities.add(entity)
    }

}