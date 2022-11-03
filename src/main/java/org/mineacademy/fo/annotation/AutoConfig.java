package org.mineacademy.fo.annotation;

import com.google.common.base.CaseFormat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation automatically saves and loads fields between a file and your custom
 * class extending {@link org.mineacademy.fo.settings.YamlConfig}.<br><br>
 * <b>On class</b>:
 * <ul>
 * <li>Saves and loads all non-static class fields</li>
 * <li>Saves and loads those static fields that have separately specified this annotation above themselves</li>
 * <li>But skips fields that have disabled this feature by <i>@AutoConfig(false)</i></li>
 * </ul>
 * When using on class, if you want to prevent one specific field from auto-loading and auto-saving,
 * use <i>@AutoConfig(false)</i> on that field.<br>
 * <br>
 * <b>On field</b>:
 * <ul>
 * <li>Saves and loads field if annotation is in enabled state</li>
 * <li>Skips field saving if <i>@AutoConfig(autoSave = false)</i></li>
 * <li>Skips field loading if <i>@AutoConfig(autoLoad = false)</i></li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoConfig {

    /**
     * When false, automatic loading and saving does not work for class or field above which is set.
     */
    boolean value() default true;

    /**
     * When false, automatic loading does not work for class or field above which is set.<br>
     * You may manually get the disabled fields in <i>onLoad</i> method if you want.
     */
    boolean autoLoad() default true;

    /**
     * When false, automatic saving does not work for class or field above which is set.<br>
     * You may manually set the disabled fields in <i>onSave</i> method if you want.
     */
    boolean autoSave() default true;

    /**
     * In what format should we convert your fields to the file.<br>
     * Only usable if set on class. You can only set one format for one class.<br>
     * Default: lower_underscore.
     */
    CaseFormat format() default CaseFormat.LOWER_UNDERSCORE;

}