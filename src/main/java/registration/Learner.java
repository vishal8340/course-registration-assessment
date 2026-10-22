package registration;

import java.util.Objects;

public class Learner {
    private final String id;
    private final String name;
    private volatile boolean prerequisiteMet;

    public Learner(String id, String name, boolean prerequisiteMet) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.prerequisiteMet = prerequisiteMet;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isPrerequisiteMet() { return prerequisiteMet; }

    /** Allows a learner to become eligible later, e.g., completes prerequisite mid-term. */
    public void setPrerequisiteMet(boolean prerequisiteMet) {
        this.prerequisiteMet = prerequisiteMet;
    }

    @Override
    public String toString() {
        return "Learner{id='" + id + "', name='" + name + "', prerequisiteMet=" + prerequisiteMet + '}';
    }
}