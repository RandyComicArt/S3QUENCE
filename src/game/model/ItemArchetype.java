package game.model;

public enum ItemArchetype {
    POISON("POISON");

    private final String label;

    ItemArchetype(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
