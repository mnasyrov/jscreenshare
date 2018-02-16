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


/**
 * Server command line:
 * <pre>
 * java -jar jscreenshare -Djss.server=true [-Djss.serverPort=12345] [-Djss.rmiPort=12346]
 * </pre>
 * <p>
 * Client command line:
 * <pre>
 * java -jar jscreenshare -Djss.serverHost=HOSTNAME [-Djss.serverPort=12345] [-Djss.rmiPort=12346] [-Djss.refreshPeriod=5]
 * </pre>
 */
public class JScreenShare {
    private static final boolean isServer = Boolean.getBoolean("jss.server");
    private static final boolean isClient = !isServer;
    private static final String serverHost = System.getProperty("jss.serverHost", null);
    private static final int serverPort = Integer.getInteger("jss.serverPort", 12345);
    private static final int rmiPort = Integer.getInteger("jss.rmiPort", 0);
    private static final String rmiServerObjName = System.getProperty("jss.rmiServerObjName", "ScreenServer");
    private static final int refreshPeriod = Integer.getInteger("jss.refreshPeriod", 0); // seconds, 0 is disabled

    public static void main(String[] args) throws Exception {
        if (isServer) {
            ScreenServer screenServer = new ScreenServer();
            RemoteScreen stub = (RemoteScreen) UnicastRemoteObject.exportObject(screenServer, rmiPort);
            LocateRegistry.createRegistry(serverPort).bind(rmiServerObjName, stub);
        }

        if (isClient) {
            Registry registry = LocateRegistry.getRegistry(serverHost, serverPort);
            RemoteScreen stub = (RemoteScreen) registry.lookup(rmiServerObjName);
            RemoteScreen remoteScreen = (RemoteScreen) UnicastRemoteObject.exportObject(stub, rmiPort);

            ClientUi clientUi = new ClientUi(remoteScreen);
            clientUi.run();
        }

        System.out.println((isServer ? "Server" : "Client") + " is run.");
    }


    static class ClientUi {
        private ImageIcon imageIcon;
        private JFrame frame;
        private RemoteScreen remoteScreen;

        ClientUi(RemoteScreen remoteScreen) {
            this.remoteScreen = remoteScreen;
        }

        void run() throws ExecutionException, InterruptedException {
            imageIcon = new ImageIcon();
            frame = new JFrame("JScreenShare");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.getContentPane().setLayout(new FlowLayout());
            frame.getContentPane().add(new JLabel(imageIcon));
            frame.setMinimumSize(new Dimension(640, 480));

            frame.getContentPane().addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    showScreenShot();
                }
            });
            frame.pack();
            frame.setVisible(true);

            if (refreshPeriod > 0) {
                ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                        this::showScreenShot, 0, refreshPeriod, TimeUnit.SECONDS
                );
                future.get();
            }
        }

        private void showScreenShot() throws RuntimeException {
            BufferedImage screenShot;
            try {
                byte[] pngData = remoteScreen.takeScreenShotPng();
                screenShot = ImageSerialization.fromPng(pngData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            boolean initFrame = imageIcon.getImage() == null;
            imageIcon.setImage(screenShot);
            if (initFrame) {
                frame.pack();
                frame.setVisible(true);
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
