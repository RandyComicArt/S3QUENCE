package game.model;

public class EncounterNode {
    private int x;
    private int y;
    private final EncounterEnemy enemy;

    public EncounterNode(int maxHealth) {
        this.enemy = new EncounterEnemy(maxHealth);
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

    public boolean isCleared() {
        return enemy.isDefeated();
    }
}
