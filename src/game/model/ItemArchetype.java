package game.model;

public enum ItemArchetype {
    POISON("POISON"),
    INITIAL_SURGE("INITIAL SURGE");

    private final String label;

    ItemArchetype(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
