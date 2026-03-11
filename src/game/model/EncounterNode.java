package game.model;

public class EncounterNode {
    private int x;
    private int y;
    private final RoomNodeType type;
    private final EncounterEnemy enemy;

    private EncounterNode(RoomNodeType type, EncounterEnemy enemy) {
        this.type = type;
        this.enemy = enemy;
    }

    public static EncounterNode createEncounter(int maxHealth, EnemyArchetype archetype) {
        return new EncounterNode(RoomNodeType.ENCOUNTER, new EncounterEnemy(maxHealth, archetype));
    }

    public static EncounterNode createShop() {
        return new EncounterNode(RoomNodeType.SHOP, null);
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public EncounterEnemy getEnemy() {
        return enemy;
    }

    public RoomNodeType getType() {
        return type;
    }

    public boolean isEncounter() {
        return type == RoomNodeType.ENCOUNTER;
    }

    public boolean isShop() {
        return type == RoomNodeType.SHOP;
    }

    public boolean isCleared() {
        return isEncounter() && enemy.isDefeated();
    }
}
