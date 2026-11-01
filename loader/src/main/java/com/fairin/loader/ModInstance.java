package com.fairin.loader;

/** Metadata + live instance of a discovered mod. */
public final class ModInstance {
    public final String id;
    public final String name;
    public final String version;
    public final String className;
    public final String jarName;
    public Object instance;

    public ModInstance(String id, String name, String version, String className, String jarName) {
        this.id = id;
        this.name = name == null || name.isEmpty() ? id : name;
        this.version = version;
        this.className = className;
        this.jarName = jarName;
    }

    @Override
    public String toString() {
        return name + " " + version + " (" + jarName + ")";
    }
}

