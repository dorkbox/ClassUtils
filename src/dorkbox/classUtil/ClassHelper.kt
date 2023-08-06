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

import net.jodah.typetools.TypeResolver
import java.lang.reflect.*
import java.lang.reflect.Array
import java.util.*

object ClassHelper {
    /**
     * Gets the version number.
     */
    const val version = "1.2"

    init {
        // Add this project to the updates system, which verifies this class + UUID + version information
        dorkbox.updates.Updates.add(ClassHelper::class.java, "2cd6b7f69d2a4fdb82285cbde74349a6", version)
    }


    /**
     * Retrieves the generic type parameter for the PARENT (super) class of the specified class or lambda expression.
     *
     * Because of how type erasure works in java, this will work on lambda expressions and ONLY parent/super classes.
     *
     * @param genericTypeClass  this class is what you are looking for
     * @param classToCheck      class to actually get the parameter from
     * @param genericParameterToGet 0-based index of parameter as class to get
     *
     * @return null if the generic type could not be found.
     */
    fun getGenericParameterAsClassForSuperClass(
        genericTypeClass: Class<*>?,
        classToCheck: Class<*>,
        genericParameterToGet: Int
    ): Class<*>? {
        var loopClassCheck = classToCheck

        // this will ALWAYS return something, if it is unknown, it will return TypeResolver.Unknown.class
        var classes = TypeResolver.resolveRawArguments(genericTypeClass, loopClassCheck)
        if (classes.size > genericParameterToGet && classes[genericParameterToGet] != TypeResolver.Unknown::class.java) {
            return classes[genericParameterToGet]
        }

        // case of multiple inheritance, we are trying to get the first available generic info
        // don't check for Object.class (this is where superclass is null)
        while (loopClassCheck != Any::class.java) {
            // check to see if we have what we are looking for on our CURRENT class
            val superClassGeneric = loopClassCheck.getGenericSuperclass()
            classes = TypeResolver.resolveRawArguments(superClassGeneric, loopClassCheck)
            if (classes.size > genericParameterToGet) {
                val aClass = classes[genericParameterToGet]
                if (aClass != TypeResolver.Unknown::class.java) {
                    return classes[genericParameterToGet]
                }
            }

            // NO MATCH, so walk up.
            loopClassCheck = loopClassCheck.superclass
        }

        // NOTHING! now check interfaces!
        loopClassCheck = classToCheck
        while (loopClassCheck != Any::class.java) {
            // check to see if we have what we are looking for on our CURRENT class interfaces
            val genericInterfaces = loopClassCheck.getGenericInterfaces()
            for (genericInterface in genericInterfaces) {
                classes = TypeResolver.resolveRawArguments(genericInterface, loopClassCheck)
                if (classes.size > genericParameterToGet) {
                    val aClass = classes[genericParameterToGet]
                    if (aClass != TypeResolver.Unknown::class.java) {
                        return aClass
                    }
                }
            }

            // NO MATCH, so walk up.
            loopClassCheck = loopClassCheck.superclass
        }

        // couldn't find it.
        return null
    }

    // from: https://github.com/square/retrofit/blob/108fe23964b986107aed352ba467cd2007d15208/retrofit/src/main/java/retrofit2/Utils.java
    fun getRawType(type: Type): Class<*> {
        Objects.requireNonNull(type, "type == null")
        if (type is Class<*>) {
            // Type is a normal class.
            return type
        }
        if (type is ParameterizedType) {

            // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either but
            // suspects some pathological case related to nested classes exists.
            val rawType = type.rawType
            require(rawType is Class<*>)
            return rawType
        }
        if (type is GenericArrayType) {
            val componentType = type.genericComponentType
            return Array.newInstance(getRawType(componentType), 0).javaClass
        }
        if (type is TypeVariable<*>) {
            // We could use the variable's bounds, but that won't work if there are multiple. Having a raw
            // type that's more general than necessary is okay.
            return Any::class.java
        }
        if (type is WildcardType) {
            return getRawType(type.upperBounds[0])
        }
        throw IllegalArgumentException(
            "Expected a Class, ParameterizedType, or " + "GenericArrayType, but <" + type + "> is of type " + type.javaClass.getName()
        )
    }

    /**
     * Check to see if clazz or interface directly has one of the interfaces defined by requiredClass
     *
     *
     * If the class DOES NOT directly have the interface it will fail.
     */
    fun hasInterface(requiredClass: Class<*>, clazz: Class<*>?): Boolean {
        if (clazz == null) {
            return false
        }
        if (requiredClass == clazz) {
            return true
        }
        val interfaces = clazz.getInterfaces()
        for (iface in interfaces) {
            if (iface == requiredClass) {
                return true
            }
        }
        // now walk up to see if we can find it.
        for (iface in interfaces) {
            val b = hasInterface(requiredClass, iface)
            if (b) {
                return b
            }
        }

        // nothing, so now we check the PARENT of this class
        var superClass = clazz.superclass

        // case of multiple inheritance, we are trying to get the first available generic info
        // don't check for Object.class (this is where superclass is null)
        while (superClass != null && superClass != Any::class.java) {
            // check to see if we have what we are looking for on our CURRENT class
            if (hasInterface(requiredClass, superClass)) {
                return true
            }

            // NO MATCH, so walk up.
            superClass = superClass.superclass
        }

        // if we don't find it.
        return false
    }

    /**
     * Checks to see if the clazz is a subclass of a parent class.
     */
    fun hasParentClass(parentClazz: Class<*>, clazz: Class<*>): Boolean {
        val superClass = clazz.superclass
        if (parentClazz == superClass) {
            return true
        }
        return if (superClass != null && superClass != Any::class.java) {
            hasParentClass(parentClazz, superClass)
        }
        else false
    }
}
