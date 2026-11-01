package com.fairin.example;

import com.fairin.api.FairinMod;
import com.fairin.api.OnInit;
import com.fairin.api.OnTick;

/** Sample Fairin mod: put the built example jar into .minecraft/mods/ */
@FairinMod(id = "example", name = "Fairin Example", version = "0.1.0")
public class ExampleMod {
    private int ticks;

    @OnInit
    public void init() {
        System.out.println("[ExampleMod] init on Fairin loader!");
    }

    @OnTick
    public void tick() {
        if (++ticks % 200 == 0) { // every 10 seconds
            System.out.println("[ExampleMod] alive, tick " + ticks);
        }
    }
}
