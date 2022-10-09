package dev.emortal.lobby.games

import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.LobbyGame
import dev.emortal.immortal.luckperms.PermissionUtils.hasLuckPermission
import dev.emortal.immortal.npc.MultilineHologram
import dev.emortal.lobby.LobbyExtension
import dev.emortal.lobby.LobbyExtension.Companion.npcs
import dev.emortal.lobby.commands.MountCommand.mountMap
import dev.emortal.lobby.occurrences.Occurrence
import dev.emortal.lobby.util.showFireworkWithDuration
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.entity.EntityPotionAddEvent
import net.minestom.server.event.entity.EntityPotionRemoveEvent
import net.minestom.server.event.entity.EntityTickEvent
import net.minestom.server.event.inventory.InventoryPreClickEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.*
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.firework.FireworkEffect
import net.minestom.server.item.firework.FireworkEffectType
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket
import net.minestom.server.network.packet.client.play.ClientSteerVehiclePacket
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.noItalic
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.asPos
import world.cepi.kstom.util.playSound
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadLocalRandom

class LobbyExtensionGame(gameOptions: GameOptions) : LobbyGame(gameOptions) {

    val armourStandSeatMap = ConcurrentHashMap<Entity, Point>()

    override var spawnPosition = Pos(0.5, 65.0, -0.5, 180f, 0f)

    var currentOccurrence: Occurrence? = null
    var occurrenceStopTask: Task? = null

    val holograms = ConcurrentHashMap<String, MultilineHologram>()

    override fun gameStarted() {
        instance.get()?.enableAutoChunkLoad(false)

        val radius = 5
        val diameter = radius * 2
        val chunks = (diameter + 1) * (diameter + 1)
        val countDownLatch = CountDownLatch((diameter + 1) * (diameter + 1))
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                instance.get()?.loadChunk(x, z)?.thenRun {
                    countDownLatch.countDown()
                }
            }
        }

        npcs.forEach {
            val hologram = MultilineHologram(it.hologramLines.toMutableList())
            holograms[it.gameName] = hologram
            hologram.setInstance(it.position.add(0.0, (it.entityType.height() + 0.2) / 2.0, 0.0), instance.get()!!)

            val playerCountCache = LobbyExtension.playerCountCache[it.gameName]
            if (playerCountCache != -1) {
                hologram.setLine(it.hologramLines.size - 1, Component.text("${playerCountCache ?: 0} online", NamedTextColor.GRAY))
            } else {
                hologram.setLine(it.hologramLines.size - 1, Component.text("Game unavailable", TextColor.color(217, 54, 54)))
            }


        }


    }

    override fun gameDestroyed() {
        holograms.clear()
        armourStandSeatMap.clear()
    }

    override fun playerJoin(player: Player) {
        npcs.forEach {
            it.addViewer(player)
        }

        val compassItemStack = ItemStack.builder(Material.COMPASS)
            .displayName(Component.text("Game Selector", NamedTextColor.GOLD).noItalic())
            .build()

        //player.inventory.helmet = ItemStack.of(Material.GOLDEN_HELMET)
        //player.sendMessage("o7")

        player.inventory.setItemStack(4, compassItemStack)

        //player.isAllowFlying = player.hasLuckPermission("lobby.fly")

        if (player.hasLuckPermission("lobby.fireworks")) {
            val fireworkItemstack = ItemStack.builder(Material.FIREWORK_ROCKET)
                .displayName(Component.text("Launch a firework", NamedTextColor.LIGHT_PURPLE).noItalic())
                .build()

            player.inventory.setItemStack(8, fireworkItemstack)
        }

//        if (player.hasLuckPermission("lobby.doot")) {
//            val trumpetItemstack = ItemStack.builder(Material.PHANTOM_MEMBRANE)
//                .displayName(Component.text("Trumpet", NamedTextColor.YELLOW).noItalic())
//                .meta {
//                    it.customModelData(1)
//                }
//                .build()
//
//            player.inventory.setItemStack(0, trumpetItemstack)
//        }
    }

    override fun playerLeave(player: Player) {
        npcs.forEach {
            it.removeViewer(player)
        }
    }

    override fun registerEvents() {

        eventNode.listenOnly<ItemDropEvent> {
            isCancelled = true
        }
        eventNode.listenOnly<InventoryPreClickEvent> {
            isCancelled = true
        }
        eventNode.listenOnly<PlayerSwapItemEvent> {
            isCancelled = true
        }

//        val reenableFlyTag = Tag.Boolean("reenableFly")
//        eventNode.listenOnly<PlayerStartFlyingEvent> {
//            if (player.gameMode != GameMode.ADVENTURE) return@listenOnly
//
//            player.isFlying = false
//            player.isAllowFlying = false
//            player.setTag(reenableFlyTag, true)
//
//            val launchDir = player.position.direction().apply { x, y, z -> Vec(x * 30.0, 20.0, z * 30.0) }
//            player.velocity = launchDir
//            player.playSound(Sound.sound(SoundEvent.ENTITY_GENERIC_EXPLODE, Sound.Source.MASTER, 0.6f, 1.5f))
//            player.showParticle(
//                Particle.particle(
//                    type = ParticleType.EXPLOSION,
//                    data = OffsetAndSpeed(),
//                    count = 1
//                ), player.position.asVec()
//            )
//        }

        eventNode.listenOnly<PlayerMoveEvent> {
            if (newPosition.y < 50) {
                player.teleport(spawnPosition)
                return@listenOnly
            }

//            if (isOnGround && player.hasTag(reenableFlyTag) && !player.isAllowFlying) {
//                player.isAllowFlying = true
//            }

            /*if (newPosition.y < 62) {
                // BACKROOMS
                player.leaveGame()
                val out = ByteBufOutputStream(ByteBufAllocator.DEFAULT.buffer())
                out.writeUTF("Connect")
                out.writeUTF("backrooms")
                val buffer = out.buffer()
                val bytes = ByteArray(buffer.readableBytes())
                buffer.duplicate().readBytes(bytes)
                out.flush()
                player.sendPacket(PluginMessagePacket("BungeeCord", bytes))
            }*/

            val blockUnder = instance.getBlock(newPosition.sub(0.0, 1.0, 0.0))
            val blockIn = instance.getBlock(newPosition)

            if (blockUnder.compare(Block.SLIME_BLOCK)) {
                player.addEffect(Potion(PotionEffect.JUMP_BOOST, 10, 10, 0))
            }

//            if (newPosition.x > 19 && newPosition.x < 31 && newPosition.y < 70 && newPosition.y >= 65.0 && newPosition.z < 9 && newPosition.z > -13) {
//                player.setTag(doNotTeleportTag, true)
//                player.joinGameOrNew("connect4")
//            }
        }

        eventNode.listenOnly<PlayerEntityInteractEvent> {
            if (!player.itemInMainHand.isAir) return@listenOnly
            val interactedPlayer = target as? Player ?: return@listenOnly
            if (player.hasLuckPermission("lobby.pickupplayer")) {
                player.addPassenger(interactedPlayer)
                if (interactedPlayer.vehicle != null && interactedPlayer.vehicle !is Player) {
                    interactedPlayer.vehicle?.remove()
                }
            }
        }
        eventNode.listenOnly<PlayerStopSneakingEvent> {
            player.passengers.forEach {
                player.removePassenger(it)
                it.velocity = this.player.position.direction().mul(30.0)
            }
        }

        eventNode.listenOnly<EntityTickEvent> {
            if (entity.entityType == EntityType.DOLPHIN) {
                val pass = entity.passengers.firstOrNull()?.position
                if (pass != null) this.entity.setView(pass.yaw, pass.pitch)
            }
        }

        eventNode.listenOnly<PlayerUseItemEvent> {
            if (itemStack.material() == Material.COMPASS) {
                player.openInventory(LobbyExtension.gameSelectorGUI.inventory)
                player.playSound(Sound.sound(SoundEvent.UI_TOAST_IN, Sound.Source.MASTER, 1f, 1f))
            }

            if (itemStack.material() == Material.PHANTOM_MEMBRANE) {
                val rand = ThreadLocalRandom.current()
                instance.playSound(Sound.sound(Key.key("item.trumpet.doot"), Sound.Source.MASTER, 1f, rand.nextFloat(0.8f, 1.2f)), player.position)
                instance.getNearbyEntities(player.position, 8.0).filter { it.entityType == EntityType.PLAYER && it != player }.forEach {
                    it.velocity = it.position.sub(player.position).asVec().normalize().mul(60.0).withY { 17.0 }
                }
            }
        }

        eventNode.listenOnly<EntityPotionAddEvent> {
            if (potion.effect == PotionEffect.GLOWING) {
                entity.isGlowing = true
            }
        }
        eventNode.listenOnly<EntityPotionRemoveEvent> {
            if (potion.effect == PotionEffect.GLOWING) {
                entity.isGlowing = false
            }
        }

        eventNode.listenOnly<PlayerPacketEvent> {
            if (packet is ClientSteerVehiclePacket) {
                val steerPacket = packet as ClientSteerVehiclePacket
                if (steerPacket.flags.toInt() == 2) {
                    if (player.vehicle != null && player.vehicle !is Player) {
                        val entity = player.vehicle!!
                        entity.removePassenger(player)

                        mountMap[player.uuid]?.destroy()
                        mountMap.remove(player.uuid)

                        if (armourStandSeatMap.containsKey(entity)) {
                            armourStandSeatMap.remove(entity)
                            entity.remove()
                            player.velocity = Vec(0.0, 10.0, 0.0)
                        }
                    }
                    return@listenOnly
                }

                val mount = mountMap[player.uuid] ?: return@listenOnly
                mount.move(player, steerPacket.forward, steerPacket.sideways)

            }

            if (packet is ClientPlayerBlockPlacementPacket) {
                val placePacket = packet as ClientPlayerBlockPlacementPacket

                if (player.itemInMainHand.material() == Material.FIREWORK_ROCKET && placePacket.hand == Player.Hand.MAIN) {

                    val random = ThreadLocalRandom.current()
                    val effects = mutableListOf(
                        FireworkEffect(
                            random.nextBoolean(),
                            random.nextBoolean(),
                            FireworkEffectType.values().random(),
                            listOf(net.minestom.server.color.Color(Color.HSBtoRGB(random.nextFloat(), 1f, 1f))),
                            listOf(net.minestom.server.color.Color(Color.HSBtoRGB(random.nextFloat(), 1f, 1f)))
                        )
                    )
                    players.showFireworkWithDuration(instance, placePacket.blockPosition.add(placePacket.cursorPositionX.toDouble(), placePacket.cursorPositionY.toDouble(), placePacket.cursorPositionZ.toDouble()).asPos(), 20 + random.nextInt(0, 11), effects)
                }

            }
        }

        eventNode.listenOnly<PlayerBlockInteractEvent> {

            if (block.name().contains("stair", true)) {
                if (player.vehicle != null) return@listenOnly
                if (armourStandSeatMap.values.contains(blockPosition)) return@listenOnly
                if (block.getProperty("half") == "top") return@listenOnly

                val armourStand = Entity(EntityType.ARMOR_STAND)
                val armourStandMeta = armourStand.entityMeta as ArmorStandMeta
                armourStandMeta.setNotifyAboutChanges(false)
                armourStandMeta.isSmall = true
                armourStandMeta.isHasNoBasePlate = true
                armourStandMeta.isMarker = true
                armourStandMeta.isInvisible = true
                armourStandMeta.setNotifyAboutChanges(true)
                armourStand.setNoGravity(true)

                val spawnPos = blockPosition.add(0.5, 0.3, 0.5)
                val yaw = when (block.getProperty("facing")) {
                    "east" -> 90f
                    "south" -> 180f
                    "west" -> -90f
                    else -> 0f
                }

                armourStand.setInstance(instance, Pos(spawnPos, yaw, 0f))
                    .thenRun {
                        armourStand.addPassenger(player)
                    }

                armourStandSeatMap[armourStand] = blockPosition
            }
        }
    }

    fun refreshHolo(gameName: String, players: Int) {
        val gameListing = LobbyExtension.gameListingConfig.gameListings[gameName] ?: return
        if (!gameListing.itemVisible) return

        val hologram = holograms[gameName] ?: return

        if (players == -1) {
            hologram.setLine(hologram.components.size - 1, Component.text("Game unavailable", TextColor.color(217, 54, 54)))
        } else {
            hologram.setLine(hologram.components.size - 1, Component.text("$players online", NamedTextColor.GRAY))
        }
    }

    override fun instanceCreate(): Instance {
        val newInstance = Manager.instance.createInstanceContainer()
        newInstance.chunkLoader = LobbyExtension.sharedLoader
        newInstance.timeRate = 0
        newInstance.timeUpdate = null
        newInstance.setTag(Tag.Boolean("doNotAutoUnloadChunk"), true)

        return newInstance
    }

}