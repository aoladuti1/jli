package com.habu;

public class Binder {

    public static class EV {
        public static void c() {
        }
    }

    public Binder() {
    }

    public Binder(int x) {
        System.out.println("__");
    }

    public Binder(char x) {
        System.out.println("___");

    }

    public static boolean ob(Object x) {
        return false;
    }

    public static boolean ob(int x) {
        return true;
    }
 
}
