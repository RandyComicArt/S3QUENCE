package game.model;

public enum ShopOption {
    HEAL("PATCH HEART", 18, "RESTORE 1 HEART"),
    SHIELD("MISTAKE GUARD", 24, "BLOCK 1 WRONG HIT"),
    TIMER("TIME CACHE", 20, "+1200MS NEXT FIGHT");

    private final String label;
    private final int cost;
    private final String description;

    ShopOption(String label, int cost, String description) {
        this.label = label;
        this.cost = cost;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public int getCost() {
        return cost;
    }

    public String getDescription() {
        return description;
    }
}
