package org.liuyehcf.chat.server;

/**
 * Created by Liuye on 2017/6/7.
 */
public class DeadSingleton {
    private static final class LazyInitialization {
        private static DeadSingleton deadSingleton = new DeadSingleton();
    }

    public static DeadSingleton getDeadSingleton() {
        return LazyInitialization.deadSingleton;
    }

    private DeadSingleton() {
        dependency=new Dependency();
    }

    private Dependency dependency;


    public static void main(String[] args){
        System.out.println(DeadSingleton.getDeadSingleton().dependency.deadSingleton);
    }
}

class Dependency {
    DeadSingleton deadSingleton = DeadSingleton.getDeadSingleton();

    Dependency() {

    }
}
