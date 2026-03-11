package game.model;

public class EncounterEnemy {
    private final int maxHealth;
    private final EnemyArchetype archetype;
    private int health;

    public EncounterEnemy(int maxHealth, EnemyArchetype archetype) {
        this.maxHealth = Math.max(1, maxHealth);
        this.archetype = archetype == null ? EnemyArchetype.NORMAL : archetype;
        this.health = this.maxHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public int getHealth() {
        return health;
    }

    public EnemyArchetype getArchetype() {
        return archetype;
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
