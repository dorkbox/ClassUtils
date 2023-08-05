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

import dorkbox.collections.IdentityMap
import java.util.concurrent.atomic.*

/**
 * @author dorkbox
 * Date: 4/1/15
 */
class ClassHierarchy(loadFactor: Float) {
    @Volatile
    private var arrayCache: IdentityMap<Class<*>, Class<*>>

    @Volatile
    private var superClassesCache: IdentityMap<Class<*>, Array<Class<*>>>

    /**
     * These data structures are never reset because the class hierarchy doesn't change at runtime. This class uses the "single writer
     * principle" for storing data, EVEN THOUGH it's not accessed by a single writer. This DOES NOT MATTER because duplicates DO NOT matter
     */
    init {
        arrayCache = IdentityMap(32, loadFactor)
        superClassesCache = IdentityMap(32, loadFactor)
    }

    /**
     * will return the class + parent classes as an array.
     * if parameter clazz is of type array, then the super classes are of array type as well
     *
     *
     * race conditions will result in DUPLICATE answers, which we don't care if happens
     * never returns null
     * never reset (class hierarchy never changes during runtime)
     */
    @Suppress("UNCHECKED_CAST")
    fun getClassAndSuperClasses(clazz: Class<*>): Array<Class<*>> {
        // access a snapshot of the subscriptions (single-writer-principle)
        val cache = superClassesREF[this] as IdentityMap<Class<*>, Array<Class<*>>>
        var classes = cache[clazz]

        // duplicates DO NOT MATTER
        if (classes == null) {
            // publish all super types of class
            val superTypesIterator = getSuperTypes(clazz)
            val newList = ArrayList<Class<*>>(16)
            var c: Class<*>
            val isArray = clazz.isArray

            // have to add the original class to the front of the list
            newList.add(clazz)
            if (isArray) {
                // super-types for an array ALSO must be an array.
                while (superTypesIterator.hasNext()) {
                    c = superTypesIterator.next()
                    c = getArrayClass(c)
                    if (c != clazz) {
                        newList.add(c)
                    }
                }
            }
            else {
                while (superTypesIterator.hasNext()) {
                    c = superTypesIterator.next()
                    if (c != clazz) {
                        newList.add(c)
                    }
                }
            }

            classes = arrayOfNulls<Class<*>>(newList.size) as Array<Class<*>>
            newList.toArray(classes)
            cache[clazz] = classes

            // save this snapshot back to the original (single writer principle)
            superClassesREF.lazySet(this, cache)
        }
        return classes
    }

    /**
     * race conditions will result in DUPLICATE answers, which we don't care if happens
     * never returns null
     * never resets (class hierarchy never changes during runtime)
     *
     * https://bugs.openjdk.java.net/browse/JDK-6525802  (fixed this in 2007, so Array.newInstance is just as fast (via intrinsics) new [])
     * Cache is in place to keep GC down.
     */
    fun getArrayClass(c: Class<*>): Class<*> {
        // access a snapshot of the subscriptions (single-writer-principle)
        val cache = cast<IdentityMap<Class<*>, Class<*>>>(arrayREF[this])
        var clazz = cache[c]

        if (clazz == null) {
            // messy, but the ONLY way to do it. Array super types are also arrays
            val newInstance = java.lang.reflect.Array.newInstance(c, 0) as Array<Any>
            clazz = newInstance.javaClass
            cache[c] = clazz

            // save this snapshot back to the original (single writer principle)
            arrayREF.lazySet(this, cache)
        }

        return clazz
    }

    /**
     * Clears the caches, should only be called on shutdown
     */
    fun shutdown() {
        arrayCache.clear()
        superClassesCache.clear()
    }

    companion object {
        /**
         * Gets the version number.
         */
        const val version = ClassHelper.version

        // Recommended for best performance while adhering to the "single writer principle". Must be static-final
        private val arrayREF = AtomicReferenceFieldUpdater.newUpdater(
            ClassHierarchy::class.java, IdentityMap::class.java, "arrayCache"
        )

        private val superClassesREF = AtomicReferenceFieldUpdater.newUpdater(
            ClassHierarchy::class.java, IdentityMap::class.java, "superClassesCache"
        )

        /**
         * Collect all directly and indirectly related super types (classes and interfaces) of a given class.
         *
         * @param from The root class to start with
         * @return An array of classes, each representing a super type of the root class
         */
        fun getSuperTypes(from: Class<*>): Iterator<Class<*>> {
            // This must be a 'set' because there can be duplicates, depending on the object hierarchy
            @Suppress("NAME_SHADOWING")
            var from = from
            val superclasses = IdentityMap<Class<*>, Boolean>()
            collectInterfaces(from, superclasses)

            while (from != Any::class.java && !from.isInterface) {
                superclasses[from.superclass] = java.lang.Boolean.TRUE
                from = from.superclass
                collectInterfaces(from, superclasses)
            }

            return superclasses.keys()
        }

        private fun collectInterfaces(from: Class<*>, accumulator: IdentityMap<Class<*>, Boolean>) {
            for (iface in from.getInterfaces()) {
                accumulator[iface] = java.lang.Boolean.TRUE
                collectInterfaces(iface, accumulator)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> cast(obj: Any): T {
            return obj as T
        }
    }
}
