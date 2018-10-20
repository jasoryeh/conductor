package net.vectormc.conductor;

public class ConductorMain {

    public static void main(String[] args) {
        Conductor conductorInstance = new Conductor();
        Conductor.setInstance(conductorInstance);
    }

}
