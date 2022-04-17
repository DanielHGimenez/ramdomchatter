package com.randomchat

import io.javalin.Javalin
import io.javalin.core.util.FileUtil
import io.javalin.http.Context
import io.javalin.http.Cookie
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap
import kotlin.text.StringBuilder

val MINUTES_TO_SESSION_EXPIRE = 30L

fun main(args: Array<String>) {
    val port = System.getenv("PORT") ?: "80"
    val app = Javalin.create().start(Integer.parseInt(port))

    val sessions = HashMap<String, LocalDateTime>()
    val chatQueue = PriorityQueue<String>()
    val chats = HashMap<String, Chat>()

    app.before("/**") { ctx ->
        checkSession(sessions, ctx)
    }

    app.get("/**") { ctx ->
        val userId = getUserId(ctx)!!
        val command = toCommand(ctx.path())

        if (command != "") {
            executeCommand(command, userId, chatQueue, chats)
            ctx.redirect("/")
        }

        val chat = chats[userId]
        val pageRendered = renderPage(userId, chat)

        ctx.html(pageRendered)
    }
}

fun checkSession(sessions: Map<String, LocalDateTime>, ctx: Context) {
    var userId = getUserId(ctx)

    if (null == userId) {
        userId = getNewUserId(sessions)
        ctx.cookie(getNewSessionCookie(userId))
    }
    else
        ctx.cookie(getNewSessionCookie(userId!!))
}

fun getUserId(ctx: Context) = ctx.cookie("id")

fun getNewSessionCookie(userId: String): Cookie = Cookie("id", userId, maxAge = getNewExpirationTimestamp())

fun getNewExpirationTimestamp() = Timestamp.valueOf(LocalDateTime.now().plusMinutes(MINUTES_TO_SESSION_EXPIRE)).nanos

fun getNewUserId(sessions: Map<String, *>): String {
    var userId: String

    do {
        userId = UUID.randomUUID().toString()
    } while (sessions.containsKey(userId))

    return userId
}

fun renderPage(userId: String, chat: Chat?): String {
    val page = StringBuilder()
    page.append(FileUtil.readResource("/index.html"))

    val chatMarker = "<!-- CHAT -->"
    val startOfChatMarker = page.indexOf(chatMarker)
    page.replace(startOfChatMarker, startOfChatMarker + chatMarker.length, chat?.render(userId) ?: "")

    val idMarker = "<!-- ID -->"
    val startOfIdMarker = page.indexOf(idMarker)
    page.replace(startOfIdMarker, startOfIdMarker + idMarker.length, userId)

    val chatStatus =
        if(chat != null)
            "<span style=\"background-color:MediumSeaGreen;\">YES</span>"
        else
            "<span style=\"background-color:Tomato;\">NO</span>"
    val chatStatusMarker = "<!-- CHATSTATUS -->"
    val startOfChatStatusMarker = page.indexOf(chatStatusMarker)
    page.replace(startOfChatStatusMarker, startOfChatStatusMarker + chatStatusMarker.length, chatStatus)

    return page.toString()
}

fun toCommand(path: String) = path.replaceFirst("/", "").replace("%20", " ")

fun executeCommand(command: String, userId: String, chatQueue: PriorityQueue<String>, chats: HashMap<String, Chat>) {
    val arguments = getCommandArguments(command)
    when (getCommandExecutor(command)) {
        "message" -> sendMessage(userId, chats, arguments)
        "newChat" -> joinNewChat(userId, chatQueue, chats)
    }
}

fun getCommandExecutor(command: String) = command.split(Regex("\\s")).first()

fun getCommandArguments(command: String): List<String> {
    val words = command.split(Regex("\\s"))
    return if (words.size > 1) words.subList(1, words.size) else LinkedList()
}

@Synchronized
fun joinNewChat(userId: String, chatQueue: PriorityQueue<String>, chats: HashMap<String, Chat>) {
    if (chatQueue.isEmpty())
        chatQueue.add(userId)
    else if (!chatQueue.contains(userId)) {
        val chat = Chat()
        chats[chatQueue.poll()] = chat
        chats[userId] = chat
    }
}

fun sendMessage(userId: String, chats: Map<String, Chat>, arguments: List<String>) =
    chats.get(userId)?.putNewMessage(userId, arguments.joinToString(" "))

class Chat(
    private val messages: LinkedList<Message> = LinkedList<Message>()
) {

    fun putNewMessage(userId: String, message: String) = messages.add(Message(userId, message))

    fun render(userId: String): String {
        val builder = StringBuilder()

        messages.forEach { message ->
            val messageRendered =
                if (userId == message.userId)
                    "<b style=\"background-color:DodgerBlue;\">you: </b>${message.message}<br>"
                else
                    "<b style=\"background-color:Tomato;\">other: </b>${message.message}<br>"
            builder.append(messageRendered)
        }

        return builder.toString()
    }

}

data class Message(
    val userId: String,
    val message: String
)
