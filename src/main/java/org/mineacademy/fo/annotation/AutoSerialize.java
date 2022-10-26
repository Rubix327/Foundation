package org.mineacademy.fo.annotation;

import com.google.common.base.CaseFormat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation automatically serializes and deserializes fields for your
 * custom class implementing {@link org.mineacademy.fo.model.ConfigSerializable}.<br><br>
 * <b>On class</b>:
 * <ul>
 * <li>Serializes and deserializes all non-static class fields</li>
 * <li>But skips fields that have disabled this feature by <i>@AutoSerialize(false)</i></li>
 * </ul>
 * When using on class, if you want to prevent one specific field from auto-serializing and auto-deserializing,
 * use <i>@AutoSerialize(false)</i> on that field.<br>
 * <br>
 * <b>On field</b>:
 * <ul>
 * <li>Serializes and deserializes field if annotation is in enabled state</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoSerialize {

    /**
     * When false, automatic serializing and deserializing does not work for class or field above which is set.
     */
    boolean value() default true;

    /**
     * In what format should we convert your fields to SerializedMap.<br>
     * Only usable if set on class. You can only set one format for one class.<br>
     * Default: lower_underscore.
     */
    CaseFormat format() default CaseFormat.LOWER_UNDERSCORE;

}
