package com.noticemc.noticeconnect.velocity.commands.command

import revxrsal.commands.command.CommandActor
import revxrsal.commands.command.ExecutableCommand
import revxrsal.commands.process.ValueResolver

abstract class ValueParser<T : Any> : ValueResolver<T> {

    abstract fun suggestions(args: List<String>, sender: CommandActor, command: ExecutableCommand): Set<String>

    abstract override fun resolve(context: ValueResolver.ValueResolverContext): T
}
