package game.model;

public class EncounterEnemy {
    private final int maxHealth;
    private int health;

    public EncounterEnemy(int maxHealth) {
        this.maxHealth = Math.max(1, maxHealth);
        this.health = this.maxHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getHealth() {
        return health;
    }

    public boolean isDefeated() {
        return health <= 0;
    }

    public boolean applyDamage(int damage) {
        int applied = Math.max(0, damage);
        health = Math.max(0, health - applied);
        return isDefeated();
    }

    public double getHealthRatio() {
        return health / (double) maxHealth;
    }
}
