package com.techducat.coreader

interface NotificationInterface {
    fun notify(message: String)
    fun onSessionsUpdated(sessions: List<Session>)

    fun onSessionsUpdated(count: Int, page: Int, pageHtml: String)

    fun onSessionsUpdated(count: Int)
}