import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    private static Robot robot;
    private static ImageIcon imageIcon;
    private static JFrame frame;


    public static void main(String[] args) throws AWTException {
        robot = new Robot();

        imageIcon = new ImageIcon();
        frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new FlowLayout());
        frame.getContentPane().add(new JLabel(imageIcon));
        frame.setMinimumSize(new Dimension(640, 480));

        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
            BufferedImage screenShot = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));

            boolean initFrame = imageIcon.getImage() == null;
            imageIcon.setImage(screenShot);
            if (initFrame) {
                frame.pack();
                frame.setVisible(true);
            }

            frame.repaint();
        }, 0, 1, TimeUnit.SECONDS);
    }
}
