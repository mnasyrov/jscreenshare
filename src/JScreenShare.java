import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;


public class JScreenShare {
    private static final boolean isServer = Boolean.getBoolean("jss.server");
    private static final boolean isClient = !isServer;
    private static final String serverHost = System.getProperty("jss.host", "127.0.0.1");
    private static final int serverPort = Integer.getInteger("jss.port", 12345);
    private static final int rmiPort = Integer.getInteger("jss.rmiPort", 0);
    private static final String rmiServerObjName = System.getProperty("jss.rmiServerObjName", "ScreenServer");
    private static final int refreshPeriod = Integer.getInteger("jss.refreshPeriod", 0); // seconds, 0 is disabled
    private static final boolean imageScale = Boolean.getBoolean("jss.imageScale");

    public static void main(String[] args) throws Exception {
        if (isServer) {
            ScreenServer screenServer = new ScreenServer();
            System.setProperty("java.rmi.server.hostname", serverHost);
            RemoteScreen stub = (RemoteScreen) UnicastRemoteObject.exportObject(screenServer, rmiPort);
            LocateRegistry.createRegistry(serverPort).bind(rmiServerObjName, stub);
        }

        if (isClient) {
            Registry registry = LocateRegistry.getRegistry(serverHost, serverPort);
            RemoteScreen stub = (RemoteScreen) registry.lookup(rmiServerObjName);
            RemoteScreen remoteScreen = (RemoteScreen) UnicastRemoteObject.exportObject(stub, rmiPort);
            new ClientUi(remoteScreen).run();
        }

        System.out.println((isServer ? "Server" : "Client") + " is run.");
    }


    static class ClientUi {
        private final RemoteScreen remoteScreen;
        private ImageIcon imageIcon;
        private JFrame frame;
        private Container contentPane;
        private BufferedImage screenShot;

        ClientUi(RemoteScreen remoteScreen) {
            this.remoteScreen = remoteScreen;
        }

        void run() throws ExecutionException, InterruptedException {
            imageIcon = new ImageIcon();
            frame = new JFrame("JScreenShare");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setMinimumSize(new Dimension(640, 480));
            contentPane = frame.getContentPane();
            JLabel label = new JLabel(imageIcon);
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    showScreenShot();
                }
            });
            if (imageScale) {
                contentPane.add(label);
            } else {
                contentPane.add(new JScrollPane(label, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED));
            }
            frame.setVisible(true);

            if (refreshPeriod > 0) {
                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                        this::showScreenShot, 0, refreshPeriod, TimeUnit.SECONDS
                );
                future.get();
            } else {
                showScreenShot();
            }
        }

        private void showScreenShot() throws RuntimeException {
            boolean initialFrame = screenShot == null;

            try {
                byte[] pngData = remoteScreen.takeScreenShotPng();
                screenShot = ImageSerialization.fromPng(pngData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Image image = screenShot;
            if (imageScale && !initialFrame) {
                image = screenShot.getScaledInstance(contentPane.getWidth(), contentPane.getHeight(), Image.SCALE_AREA_AVERAGING);
            }
            imageIcon.setImage(image);

            if (initialFrame) {
                frame.pack();
            }
            frame.repaint();
        }
    }


    public interface RemoteScreen extends Remote {
        byte[] takeScreenShotPng() throws IOException;
    }


    public static class ScreenServer implements RemoteScreen {
        private Robot robot;

        public ScreenServer() throws AWTException {
            robot = new Robot();
        }

        public byte[] takeScreenShotPng() throws IOException {
            BufferedImage image = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
            return ImageSerialization.toPng(image);
        }
    }


    static class ImageSerialization {
        static byte[] toPng(RenderedImage image) throws IOException {
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                output.flush();
                return output.toByteArray();
            }
        }

        static BufferedImage fromPng(byte[] pngData) throws IOException {
            try (ByteArrayInputStream input = new ByteArrayInputStream(pngData)) {
                return ImageIO.read(input);
            }
        }
    }
}
