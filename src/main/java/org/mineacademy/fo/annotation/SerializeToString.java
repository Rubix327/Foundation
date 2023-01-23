package org.mineacademy.fo.annotation;

import org.mineacademy.fo.model.ConfigSerializable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used on classes implementing {@link ConfigSerializable}
 * to show that the class must be serialized as a String.<br><br>
 * A class must have two methods to accomplish this:<br>
 * <ol>
 *     <li>public String serializeToString()</li>
 *     <li>public static YourClass deserialize(String obj)</li>
 * </ol>
 * You should implement both methods by yourself.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SerializeToString {

}
