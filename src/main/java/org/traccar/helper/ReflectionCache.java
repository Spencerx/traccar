/*
 * Copyright 2025 - 2026 Anton Tananaev (anton@traccar.org)
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
package org.traccar.helper;

import org.traccar.storage.QueryIgnore;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionCache {

    private ReflectionCache() {
    }

    private record Key(Class<?> clazz, String type) {
    }

    public record PropertyMethod(Method method, boolean queryIgnore, String lowerCaseName) {
    }

    private static final Map<Key, Map<String, PropertyMethod>> CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Constructor<?>> CONSTRUCTORS = new ConcurrentHashMap<>();

    public static Map<String, PropertyMethod> getProperties(Class<?> clazz, String type) {
        return CACHE.computeIfAbsent(new Key(clazz, type), key -> {
            Map<String, PropertyMethod> properties = new HashMap<>();
            for (Method method : key.clazz().getMethods()) {
                int parameterCount = key.type().equals("set") ? 1 : 0;
                var parameters = method.getParameterTypes();
                if (method.getName().startsWith(key.type()) && parameters.length == parameterCount
                        && !method.getName().equals("getClass")) {
                    String name = Introspector.decapitalize(method.getName().substring(3));
                    properties.put(name, new PropertyMethod(
                            method, method.isAnnotationPresent(QueryIgnore.class),
                            name.toLowerCase(Locale.ROOT)));
                }
            }
            return properties;
        });
    }

    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> getConstructor(Class<T> clazz) {
        return (Constructor<T>) CONSTRUCTORS.computeIfAbsent(clazz, key -> {
            try {
                return key.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        });
    }

}
