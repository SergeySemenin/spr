package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlin.time.Duration.Companion.seconds

data class Player(val id: String, val name: String)
data class PokerTable(val id: String, val players: MutableMap<String, String> = mutableMapOf()) // id игрока -> место

val tables = mutableListOf<PokerTable>()  // Список столов
val connectedPlayers = mutableMapOf<String, DefaultWebSocketSession>()  // Подключенные игроки

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/ws") { // websocketSession
            val playerId = "player_${System.currentTimeMillis()}"
            val player = Player(playerId, "Player $playerId")
            connectedPlayers[playerId] = this

            send("Welcome $playerId! Use 'join <tableId>' to join a table.")

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = frame.readText()
                        when {
                            message.startsWith("join") -> {
                                val tableId = message.substringAfter("join").trim()
                                joinTable(player, tableId, this)
                            }
                            else -> {
                                send("Unknown command.")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error: ${e.message}")
            }
        }
    }
}

suspend fun joinTable(player: Player, tableId: String, session: DefaultWebSocketSession) {
    // Ищем таблицу по ID
    var table = tables.find { it.id == tableId }

    // Если таблица не найдена, создаем новую
    if (table == null) {
        table = createTable(tableId)
        tables.add(table) // Добавляем новую таблицу в список
    }

    // Садим игрока за стол
    val seat = getAvailableSeat(table)
    if (seat != null) {
        table.players[player.id] = seat
        notifyAllPlayers(table)
    } else {
        session.send("Table is full!")
    }
}

// Получить доступное место за столом
fun getAvailableSeat(table: PokerTable): String? {
    val allSeats = listOf("seat1", "seat2", "seat3", "seat4", "seat5", "seat6")
    return allSeats.firstOrNull { !table.players.containsValue(it) }
}

// Функция для создания новой таблицы
fun createTable(tableId: String): PokerTable {
    return PokerTable(tableId)
}

// Уведомить всех игроков о текущем состоянии стола
suspend fun notifyAllPlayers(table: PokerTable) {
    val state = table.players.map { "${it.key} is sitting at ${it.value}" }.joinToString(", ")
    table.players.keys.forEach { playerId ->
        connectedPlayers[playerId]?.send("Table state: $state")
    }
}

