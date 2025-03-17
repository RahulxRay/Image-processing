import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.Stack;
import java.util.Random;
import javax.imageio.*;
import javax.swing.*;

public class Demo extends JPanel implements ActionListener {
    // The original image chosen by the user.
    private BufferedImage originalImage;
    // The processed image that is modified by operations.
    private BufferedImage processedImage;
    // Stack for multi-level undo functionality.
    private Stack<BufferedImage> undoStack = new Stack<>();
    // Gap between the original and processed images when drawn.
    private final int gap = 10;

    // Constructor: load the image from the provided file.
    public Demo(File imageFile) {
        try {
            originalImage = ImageIO.read(imageFile);
            // Ensure the image is in TYPE_INT_RGB.
            if (originalImage.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage temp = new BufferedImage(
                    originalImage.getWidth(), originalImage.getHeight(), 
                    BufferedImage.TYPE_INT_RGB
                );
                Graphics g = temp.getGraphics();
                g.drawImage(originalImage, 0, 0, null);
                g.dispose();
                originalImage = temp;
            }
            // Initially, processedImage is a copy of originalImage.
            processedImage = copyImage(originalImage);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Image could not be read.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // Utility: create a deep copy of a BufferedImage.
    private BufferedImage copyImage(BufferedImage img) {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics g = copy.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copy;
    }

    // Panel size: wide enough to hold both images side by side.
    @Override
    public Dimension getPreferredSize() {
        int width = originalImage.getWidth() + gap + processedImage.getWidth();
        int height = Math.max(originalImage.getHeight(), processedImage.getHeight());
        return new Dimension(width, height);
    }

    // Clamp a value to the range [0, 255].
    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // Convert a BufferedImage to a 3D int array [width][height][4] where indices 1-3 are red, green, blue.
    private int[][][] convertToArray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][][] arr = new int[width][height][4];
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                int p = image.getRGB(x, y);
                arr[x][y][0] = (p >> 24) & 0xff; // alpha
                arr[x][y][1] = (p >> 16) & 0xff; // red
                arr[x][y][2] = (p >> 8) & 0xff;  // green
                arr[x][y][3] = p & 0xff;         // blue
            }
        }
        return arr;
    }

    // Convert a 3D array back to a BufferedImage.
    private BufferedImage convertToBimage(int[][][] arr) {
        int width = arr.length;
        int height = arr[0].length;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                int a = arr[x][y][0];
                int r = arr[x][y][1];
                int g = arr[x][y][2];
                int b = arr[x][y][3];
                int p = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, y, p);
            }
        }
        return img;
    }

    // ---------- Image Processing Operations ----------

    // Negative filter: invert each color channel.
    private BufferedImage applyNegative(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[][][] arr = convertToArray(img);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                arr[x][y][1] = 255 - arr[x][y][1];
                arr[x][y][2] = 255 - arr[x][y][2];
                arr[x][y][3] = 255 - arr[x][y][3];
            }
        }
        return convertToBimage(arr);
    }

    // Exercise 1: Rescale intensities.
    // Multiply each pixel value by the factor, round, and clamp between 0 and 255.
    private BufferedImage applyRescale(BufferedImage img, float factor) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[][][] arr = convertToArray(img);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                arr[x][y][1] = clamp(Math.round(arr[x][y][1] * factor));
                arr[x][y][2] = clamp(Math.round(arr[x][y][2] * factor));
                arr[x][y][3] = clamp(Math.round(arr[x][y][3] * factor));
            }
        }
        return convertToBimage(arr);
    }

    // Exercise 2: Shift intensities.
    // Add the provided integer shift to each pixel value and clamp the result.
    private BufferedImage applyShift(BufferedImage img, int shift) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[][][] arr = convertToArray(img);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                arr[x][y][1] = clamp(arr[x][y][1] + shift);
                arr[x][y][2] = clamp(arr[x][y][2] + shift);
                arr[x][y][3] = clamp(arr[x][y][3] + shift);
            }
        }
        return convertToBimage(arr);
    }

    // Exercise 3: Shift and Rescale.
    // For each pixel, add a random integer (here, chosen between -50 and 50),
    // then determine the minimum and maximum pixel values for each channel,
    // and linearly rescale the values to be in the range [0, 255].
    private BufferedImage applyShiftAndRescale(BufferedImage img) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[][][] arr = convertToArray(img);
        Random rand = new Random();
        // For channels red, green, blue (indices 1, 2, 3)
        int[] minChannel = {255, 255, 255};
        int[] maxChannel = {0, 0, 0};

        // Add a random value to each pixel and compute min/max.
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int c = 1; c <= 3; c++){
                    int randomVal = rand.nextInt(101) - 50; // random integer between -50 and 50
                    arr[x][y][c] += randomVal;
                    if (arr[x][y][c] < minChannel[c - 1])
                        minChannel[c - 1] = arr[x][y][c];
                    if (arr[x][y][c] > maxChannel[c - 1])
                        maxChannel[c - 1] = arr[x][y][c];
                }
            }
        }
        // Rescale each channel.
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int c = 1; c <= 3; c++){
                    if (maxChannel[c - 1] != minChannel[c - 1]) {
                        int newVal = (arr[x][y][c] - minChannel[c - 1]) * 255 / (maxChannel[c - 1] - minChannel[c - 1]);
                        arr[x][y][c] = clamp(newVal);
                    } else {
                        arr[x][y][c] = clamp(arr[x][y][c]);
                    }
                }
            }
        }
        return convertToBimage(arr);
    }

    // ---------- Undo and Reset Methods ----------

    // Push the current processed image onto the undo stack.
    private void backupForUndo() {
        if (processedImage != null) {
            undoStack.push(copyImage(processedImage));
        }
    }

    // Undo the last operation by popping the previous state from the stack.
    private void undo() {
        if (!undoStack.isEmpty()) {
            processedImage = undoStack.pop();
            repaint();
        } else {
            JOptionPane.showMessageDialog(this, "Nothing to undo!");
        }
    }

    // Reset the processed image to the original image and clear the undo stack.
    private void resetToOriginal() {
        processedImage = copyImage(originalImage);
        undoStack.clear();
        repaint();
    }

    // ---------- Painting: Draw both images side by side ----------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (originalImage != null) {
            g.drawImage(originalImage, 0, 0, this);
        }
        if (processedImage != null) {
            g.drawImage(processedImage, originalImage.getWidth() + gap, 0, this);
        }
    }

    // ---------- Menu Action Handling ----------

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("Open")) {
            JFileChooser chooser = new JFileChooser();
            int rval = chooser.showOpenDialog(this);
            if (rval == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    BufferedImage img = ImageIO.read(file);
                    if (img.getType() != BufferedImage.TYPE_INT_RGB) {
                        BufferedImage temp = new BufferedImage(
                            img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB
                        );
                        Graphics g = temp.getGraphics();
                        g.drawImage(img, 0, 0, null);
                        g.dispose();
                        img = temp;
                    }
                    originalImage = img;
                    resetToOriginal();
                    revalidate();
                    repaint();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else if (cmd.equals("Save")) {
            JFileChooser chooser = new JFileChooser();
            int rval = chooser.showSaveDialog(this);
            if (rval == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    ImageIO.write(processedImage, "png", file);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else if (cmd.equals("Undo")) {
            undo();
        } else if (cmd.equals("Original")) {
            backupForUndo();
            resetToOriginal();
        } else if (cmd.equals("Negative")) {
            backupForUndo();
            processedImage = applyNegative(processedImage);
            repaint();
        } else if (cmd.equals("Rescale")) {
            String input = JOptionPane.showInputDialog(this, "Enter scaling factor (0 to 2):", "1.0");
            if (input != null) {
                try {
                    float factor = Float.parseFloat(input);
                    if (factor < 0 || factor > 2) {
                        JOptionPane.showMessageDialog(this, "Scaling factor must be between 0 and 2.");
                    } else {
                        backupForUndo();
                        processedImage = applyRescale(processedImage, factor);
                        repaint();
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for scaling factor.");
                }
            }
        } else if (cmd.equals("Shift")) {
            String input = JOptionPane.showInputDialog(this, "Enter shift value (integer):", "0");
            if (input != null) {
                try {
                    int shiftVal = Integer.parseInt(input);
                    backupForUndo();
                    processedImage = applyShift(processedImage, shiftVal);
                    repaint();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for shift value.");
                }
            }
        } else if (cmd.equals("Shift+Rescale")) {
            backupForUndo();
            processedImage = applyShiftAndRescale(processedImage);
            repaint();
        }
    }

    // ---------- Main Method: Prompt user for file then set up the GUI ----------

    public static void main(String[] args) {
        // Prompt the user to select an image file at startup.
        JFileChooser fileChooser = new JFileChooser();
        int returnVal = fileChooser.showOpenDialog(null);
        if(returnVal != JFileChooser.APPROVE_OPTION) {
            System.exit(0);
        }
        File selectedFile = fileChooser.getSelectedFile();

        // Create the Demo panel using the selected file.
        Demo demo = new Demo(selectedFile);
        JFrame frame = new JFrame("Image Processing Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(demo, BorderLayout.CENTER);

        // Build the menu bar.
        JMenuBar menuBar = new JMenuBar();

        // File menu: Open and Save.
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open");
        openItem.setActionCommand("Open");
        openItem.addActionListener(demo);
        fileMenu.add(openItem);
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setActionCommand("Save");
        saveItem.addActionListener(demo);
        fileMenu.add(saveItem);
        menuBar.add(fileMenu);

        // Edit menu: Undo.
        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setActionCommand("Undo");
        undoItem.addActionListener(demo);
        editMenu.add(undoItem);
        menuBar.add(editMenu);

        // Operations menu.
        JMenu opMenu = new JMenu("Operations");
        String[] ops = {"Original", "Negative", "Rescale", "Shift", "Shift+Rescale"};
        for (String op : ops) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            opMenu.add(item);
        }
        menuBar.add(opMenu);

        frame.setJMenuBar(menuBar);
        frame.pack();
        frame.setVisible(true);
    }
}
