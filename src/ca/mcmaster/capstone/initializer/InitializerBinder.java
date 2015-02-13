package ca.mcmaster.capstone.initializer;

import android.os.Binder;

public class InitializerBinder extends Binder {
    private final Initializer initializer;

    public InitializerBinder(final Initializer initializer) {
        this.initializer = initializer;
    }

    public Initializer getInitializer() {
        return initializer;
    }
}