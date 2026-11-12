package me.biquaternions.carpet.script.external;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Cached reflection helpers.</p>
 *
 * <p>fabric-carpet accesses private members of Minecraft classes through mixin accessors. Paper plugins can't use
 * mixins, so the same members are reached through reflection instead. Paper runs with Mojang mappings, so the names
 * are the same ones fabric-carpet uses.</p>
 */
final class Reflect {

    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private Reflect() {
    }

    static Field field(Class<?> owner, String name) {
        return FIELDS.computeIfAbsent(owner.getName() + "#" + name, k -> {
            for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
                try {
                    Field field = c.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new IllegalStateException("No field " + name + " in " + owner.getName());
        });
    }

    static Method method(Class<?> owner, String name, Class<?>... parameters) {
        return METHODS.computeIfAbsent(owner.getName() + "#" + name + Arrays.toString(parameters), k -> {
            for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
                try {
                    Method method = c.getDeclaredMethod(name, parameters);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            throw new IllegalStateException("No method " + name + " in " + owner.getName());
        });
    }

    @SuppressWarnings("unchecked")
    static <T> T get(Class<?> owner, Object instance, String name) {
        try {
            return (T) field(owner, name).get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void set(Class<?> owner, Object instance, String name, Object value) {
        try {
            field(owner, name).set(instance, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T invoke(Method method, Object instance, Object... args) {
        try {
            return (T) method.invoke(instance, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

}
