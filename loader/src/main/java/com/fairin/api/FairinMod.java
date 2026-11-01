package com.fairin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a class as a Fairin mod. Exactly one class in a mod jar must have it. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FairinMod {
    /** Unique mod id, lowercase letters/digits/underscore. */
    String id();
    /** Human readable name. */
    String name() default "";
    /** Mod version string. */
    String version() default "1.0";
}
