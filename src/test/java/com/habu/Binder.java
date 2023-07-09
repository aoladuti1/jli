package com.habu;

public class Tester {

    public static class EV {
        public static void c() {
        }
    }

    public Tester() {
    }

    public Tester(int x) {
        System.out.println("__");
    }

    public Tester(char x) {
        System.out.println("___");

    }

    public static boolean ob(Object x) {
        return false;
    }

    public static boolean ob(int x) {
        return true;
    }
 
}
