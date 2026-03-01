package com.noticemc.noticeconnect.velocity.commands.command

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Suggestion(val value: String)
