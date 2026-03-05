package game.model;

public enum TimerStyle {
    BORDER_RING("BORDER"),
    BACKDROP_HUE("BACKDROP HUE");

    private final String label;

    TimerStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
