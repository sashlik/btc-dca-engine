package com.hillariousstartups.btcdca

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.request.SendMessage

class TgBot(
    botToken: String,
    private val chatId: Long,
) {
    private val bot = TelegramBot(botToken)

    fun send(text: String) {
        val resp = bot.execute(SendMessage(chatId, text))
        require(resp.isOk) { "Telegram send failed: ${resp.errorCode()} ${resp.description()}" }
    }
}