package game.model;

public class CoinPickup {
    private final int x;
    private final int y;
    private final int value;

    public CoinPickup(int x, int y, int value) {
        this.x = x;
        this.y = y;
        this.value = value;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getValue() {
        return value;
    }
}
