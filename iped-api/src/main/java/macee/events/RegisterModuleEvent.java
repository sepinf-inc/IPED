package macee.events;

import macee.ForensicModule;

public class RegisterModuleEvent {

    private final ForensicModule module;

    public RegisterModuleEvent(ForensicModule m) {
        this.module = m;
    }

    public ForensicModule getModule() {
        return module;
    }

}
