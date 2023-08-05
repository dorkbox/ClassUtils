/*
 * Copyright 2023 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.classUtil

object ClassResolver {
    private const val CALL_CONTEXT_OFFSET = 3 // may need to change if this class is redesigned
    private val CALLER_RESOLVER: CallerResolver

    init {
        try {
            // This can fail if the current SecurityManager does not allow
            // RuntimePermission ("createSecurityManager"):
            CALLER_RESOLVER = CallerResolver()
        }
        catch (se: SecurityException) {
            throw RuntimeException("ClassLoaderResolver: could not create CallerResolver: $se")
        }
    }

    /**
     * Indexes into the current method call context with a given offset.
     */
    fun getCallerClass(callerOffset: Int): Class<*> {
        return CALLER_RESOLVER.classContext[CALL_CONTEXT_OFFSET + callerOffset]
    }

    /**
     * A helper class to get the call context. It subclasses SecurityManager to make getClassContext() accessible. An instance of
     * CallerResolver only needs to be created, not installed as an actual security manager.
     */
    private class CallerResolver : SecurityManager() {
        public override fun getClassContext(): Array<Class<*>> {
            return super.getClassContext()
        }
    }
}
