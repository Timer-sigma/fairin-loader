package com.fairin.loader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class hierarchy of the obfuscated jar (built in a cheap pre-pass over class headers).
 * Lets the remapper resolve INHERITED members: a call "owner.name(desc)" is mapped via
 * the class that actually declares the member (mappings only list declarations).
 */
public final class Hierarchy {
    /** obf internal name -> super internal name */
    static final Map<String, String> superMap = new HashMap<String, String>();
    /** obf internal name -> directly implemented interfaces */
    static final Map<String, List<String>> itfMap = new HashMap<String, List<String>>();
    /** obf internal name -> obf methods declared, key: name + descriptor */
    static final Map<String, Set<String>> methods = new HashMap<String, Set<String>>();
    /** obf internal name -> obf fields declared, key: name + descriptor */
    static final Map<String, Set<String>> fields = new HashMap<String, Set<String>>();

    private Hierarchy() {
    }

    public static void learn(String internal, String superName, String[] interfaces,
                             Set<String> clsMethods, Set<String> clsFields) {
        if (superName != null) superMap.put(internal, superName);
        if (interfaces != null && interfaces.length > 0) {
            List<String> list = new ArrayList<String>(interfaces.length);
            for (String itf : interfaces) if (itf != null) list.add(itf);
            if (!list.isEmpty()) itfMap.put(internal, list);
        }
        // NOTE: sets are filled AFTER learn() returns (visit() precedes visitMethod),
        // so always store the reference itself - it will be populated later.
        methods.put(internal, clsMethods != null ? clsMethods : new HashSet<String>());
        fields.put(internal, clsFields != null ? clsFields : new HashSet<String>());
    }

    /** BFS up the hierarchy (supers AND interfaces) from owner: first class declaring name+desc. */
    static String declaringClass(String owner, String name, String desc, boolean isMethod) {
        Deque<String> queue = new ArrayDeque<String>();
        Set<String> visited = new HashSet<String>();
        queue.add(owner);
        visited.add(owner);
        Map<String, Set<String>> members = isMethod ? methods : fields;
        String key = name + desc;
        while (!queue.isEmpty()) {
            String cls = queue.poll();
            Set<String> m = members.get(cls);
            if (m != null && m.contains(key)) return cls;
            String sup = superMap.get(cls);
            if (sup != null && visited.add(sup)) queue.add(sup);
            List<String> itfs = itfMap.get(cls);
            if (itfs != null) {
                for (String itf : itfs) {
                    if (visited.add(itf)) queue.add(itf);
                }
            }
        }
        return null;
    }
}
