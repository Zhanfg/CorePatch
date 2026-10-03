package org.lsposed.corepatch.hook

import org.lsposed.corepatch.XposedHelper.log
import java.lang.reflect.Method

object HookResolver {
    fun findMethod(
        owner: Class<*>,
        name: String,
        predicate: (Method) -> Boolean = { true },
    ): Method? {
        val method = owner.declaredMethods
            .asSequence()
            .filter { it.name == name }
            .firstOrNull(predicate)

        if (method == null) {
            log("[HookResolver] method not found: ${owner.name}#$name")
            return null
        }

        runCatching { method.isAccessible = true }
        return method
    }

    fun findMethods(
        owner: Class<*>,
        name: String,
        predicate: (Method) -> Boolean = { true },
    ): List<Method> {
        val methods = owner.declaredMethods
            .asSequence()
            .filter { it.name == name }
            .filter(predicate)
            .onEach { runCatching { it.isAccessible = true } }
            .toList()

        if (methods.isEmpty()) {
            log("[HookResolver] methods not found: ${owner.name}#$name")
        }
        return methods
    }
}
