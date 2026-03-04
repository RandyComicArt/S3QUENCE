package game;

import game.audio.AudioManager;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        AudioManager.startBackgroundLoop("Loop_drum.wav");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("S3QUENCE");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);

            GamePanel gamePanel = new GamePanel();
            frame.add(gamePanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            gamePanel.requestFocusInWindow();
        });
    }
}
