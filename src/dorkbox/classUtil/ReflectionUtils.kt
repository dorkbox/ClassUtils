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
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method

/**
 * @author bennidi
 * Date: 2/16/12
 * Time: 12:14 PM
 * @author dorkbox
 * Date: 2/2/15
 */
object ReflectionUtils {
    /**
     * Gets the version number.
     */
    const val version = ClassHelper.version

    @Suppress("UNCHECKED_CAST")
    private val EMPTY_METHODS = arrayOfNulls<Method>(0) as Array<Method>

    /**
     * Get methods annotated with the specified annotation.
     *
     * @param target the class that you are looking for the methods on
     * @param annotationClass the annotations that define the method you are looking for
     * @param <A> the annotation type
     *
     * @return the array of methods that match the target + annotation
    </A> */
    fun <A : Annotation> getMethods(target: Class<*>, annotationClass: Class<A>): Array<Method?> {
        val methods = ArrayList<Method>()
        getMethods(target, annotationClass, methods)
        return methods.toArray(EMPTY_METHODS)
    }

    private fun <A : Annotation> getMethods(target: Class<*>, annotationClass: Class<A>, methods: ArrayList<Method>) {
        try {
            for (method in target.getDeclaredMethods()) {
                if (getAnnotation(method, annotationClass) != null) {
                    methods.add(method)
                }
            }
        }
        catch (ignored: Exception) {
        }

        // recursively go until root
        if (target != Any::class.java) {
            getMethods(target.superclass, annotationClass, methods)
        }
    }

    /**
     * Traverses the class hierarchy upwards, starting at the given subclass, looking
     * for an override of the given methods -> finds the bottom most override of the given
     * method if any exists
     */
    fun getOverridingMethod(overridingMethod: Method, subclass: Class<*>): Method? {
        var current = subclass
        while (current != overridingMethod.declaringClass) {
            current = try {
                return current.getDeclaredMethod(overridingMethod.name, *overridingMethod.parameterTypes)
            }
            catch (e: NoSuchMethodException) {
                current.superclass
            }
        }
        return null
    }

    fun containsOverridingMethod(allMethods: Array<Method>, methodToCheck: Method): Boolean {
        val length = allMethods.size
        var method: Method
        for (i in 0 until length) {
            method = allMethods[i]
            if (isOverriddenBy(methodToCheck, method)) {
                return true
            }
        }
        return false
    }

    /**
     * Searches for an Annotation of the given type on the class.  Supports meta annotations.
     *
     * @param from           AnnotatedElement (class, method...)
     * @param annotationType Annotation class to look for.
     * @param <A>            Class of annotation type
     *
     * @return Annotation instance or null
     */
    private fun <A : Annotation> getAnnotation(
        from: AnnotatedElement,
        annotationType: Class<A>,
        visited: IdentityMap<AnnotatedElement, Boolean>
    ): A? {

        if (visited.containsKey(from)) {
            return null
        }

        visited[from] = java.lang.Boolean.TRUE
        var ann: A? = from.getAnnotation(annotationType)
        if (ann != null) {
            return ann
        }

        for (metaAnn in from.annotations) {
            ann = getAnnotation<A>(metaAnn.annotationClass.java, annotationType, visited)
            if (ann != null) {
                return ann
            }
        }

        return null
    }

    fun <A : Annotation> getAnnotation(from: AnnotatedElement, annotationType: Class<A>): A? {
        return getAnnotation(from, annotationType, IdentityMap())
    }

    private fun isOverriddenBy(superclassMethod: Method, subclassMethod: Method): Boolean {
        // if the declaring classes are the same or the subclass method is not defined in the subclass
        // hierarchy of the given superclass method or the method names are not the same then
        // subclassMethod does not override superclassMethod
        if (superclassMethod.declaringClass == subclassMethod.declaringClass ||
            !superclassMethod.declaringClass.isAssignableFrom(subclassMethod.declaringClass) ||
            superclassMethod.name != subclassMethod.name
        ) {
            return false
        }

        val superClassMethodParameters = superclassMethod.parameterTypes
        val subClassMethodParameters = subclassMethod.parameterTypes

        // method must specify the same number of parameters
        //the parameters must occur in the exact same order
        for (i in subClassMethodParameters.indices) {
            if (superClassMethodParameters[i] != subClassMethodParameters[i]) {
                return false
            }
        }
        return true
    }
}
