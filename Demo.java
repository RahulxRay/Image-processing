import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.Stack;
import java.util.Random;
import javax.imageio.*;
import javax.swing.*;

public class Demo extends JPanel implements ActionListener {
    // Original image loaded at startup.
    private BufferedImage originalImage;
    // Processed image (result of operations).
    private BufferedImage processedImage;
    // Undo stack to store previous states.
    private Stack<BufferedImage> undoStack = new Stack<>();
    // Gap (in pixels) between images when displayed.
    private final int gap = 10;

    // Constructor: load the image from a file.
    public Demo(File imageFile) {
        try {
            originalImage = ImageIO.read(imageFile);
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
            processedImage = copyImage(originalImage);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Image could not be read.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // Create a deep copy of a BufferedImage.
    private BufferedImage copyImage(BufferedImage img) {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics g = copy.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copy;
    }

    // Preferred size of the panel to accommodate two images side by side.
    @Override
    public Dimension getPreferredSize() {
        int width = originalImage.getWidth() + gap + processedImage.getWidth();
        int height = Math.max(originalImage.getHeight(), processedImage.getHeight());
        return new Dimension(width, height);
    }

    // Clamp a value to the range [0,255].
    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // Convert a BufferedImage to a 3D int array [width][height][4]
    // where index 0 is alpha, 1 is red, 2 is green, 3 is blue.
    private int[][][] convertToArray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][][] arr = new int[width][height][4];
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                int p = image.getRGB(x, y);
                arr[x][y][0] = (p >> 24) & 0xff;
                arr[x][y][1] = (p >> 16) & 0xff;
                arr[x][y][2] = (p >> 8) & 0xff;
                arr[x][y][3] = p & 0xff;
            }
        }
        return arr;
    }

    // Convert a 3D int array back to a BufferedImage.
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

    // ==================== LAB 1 & LAB 2 Operations ====================

    // Negative (linear transform): s = 255 - r.
    private BufferedImage applyNegative(BufferedImage img) {
        int width = img.getWidth(), height = img.getHeight();
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

    // Rescale intensities: s = round(r * factor), clamped to [0,255].
    private BufferedImage applyRescale(BufferedImage img, float factor) {
        int width = img.getWidth(), height = img.getHeight();
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

    // Shift intensities: s = r + shift, clamped to [0,255].
    private BufferedImage applyShift(BufferedImage img, int shift) {
        int width = img.getWidth(), height = img.getHeight();
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

    // Shift+Rescale: add a random value (between -50 and 50) and then linearly rescale.
    private BufferedImage applyShiftAndRescale(BufferedImage img) {
        int width = img.getWidth(), height = img.getHeight();
        int[][][] arr = convertToArray(img);
        Random rand = new Random();
        int[] minChannel = {255, 255, 255};
        int[] maxChannel = {0, 0, 0};
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int c = 1; c <= 3; c++){
                    int randomVal = rand.nextInt(101) - 50;
                    arr[x][y][c] += randomVal;
                    minChannel[c-1] = Math.min(minChannel[c-1], arr[x][y][c]);
                    maxChannel[c-1] = Math.max(maxChannel[c-1], arr[x][y][c]);
                }
            }
        }
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int c = 1; c <= 3; c++){
                    if (maxChannel[c-1] != minChannel[c-1]) {
                        int newVal = (arr[x][y][c] - minChannel[c-1]) * 255 / (maxChannel[c-1] - minChannel[c-1]);
                        arr[x][y][c] = clamp(newVal);
                    } else {
                        arr[x][y][c] = clamp(arr[x][y][c]);
                    }
                }
            }
        }
        return convertToBimage(arr);
    }

    // ==================== LAB 3 Operations ====================

    // Arithmetic operations: perform "add", "subtract", "multiply", or "divide" on two images.
    private BufferedImage applyArithmeticOperation(BufferedImage img1, BufferedImage img2, String op) {
        int width = Math.min(img1.getWidth(), img2.getWidth());
        int height = Math.min(img1.getHeight(), img2.getHeight());
        int[][][] arr1 = convertToArray(img1);
        int[][][] arr2 = convertToArray(img2);
        int[][][] res = new int[width][height][4];
        int[] minChannel = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] maxChannel = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                res[x][y][0] = arr1[x][y][0]; // alpha from first image
                for (int c = 1; c <= 3; c++){
                    int v = 0;
                    switch(op) {
                        case "add": v = arr1[x][y][c] + arr2[x][y][c]; break;
                        case "subtract": v = arr1[x][y][c] - arr2[x][y][c]; break;
                        case "multiply": v = arr1[x][y][c] * arr2[x][y][c]; break;
                        case "divide": v = (arr2[x][y][c] == 0) ? 0 : arr1[x][y][c] / arr2[x][y][c]; break;
                    }
                    res[x][y][c] = v;
                    minChannel[c-1] = Math.min(minChannel[c-1], v);
                    maxChannel[c-1] = Math.max(maxChannel[c-1], v);
                }
            }
        }
        // Rescale result channels to [0,255]
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int c = 1; c <= 3; c++){
                    if (maxChannel[c-1] != minChannel[c-1]) {
                        int newVal = (res[x][y][c] - minChannel[c-1]) * 255 / (maxChannel[c-1] - minChannel[c-1]);
                        res[x][y][c] = clamp(newVal);
                    } else {
                        res[x][y][c] = clamp(res[x][y][c]);
                    }
                }
            }
        }
        return convertToBimage(res);
    }

    // Bitwise Boolean operations: "and", "or", "xor" on two images.
    private BufferedImage applyBitwiseOperation(BufferedImage img1, BufferedImage img2, String op) {
        int width = Math.min(img1.getWidth(), img2.getWidth());
        int height = Math.min(img1.getHeight(), img2.getHeight());
        int[][][] arr1 = convertToArray(img1);
        int[][][] arr2 = convertToArray(img2);
        int[][][] res = new int[width][height][4];
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                res[x][y][0] = arr1[x][y][0];
                for (int c = 1; c <= 3; c++){
                    int v = 0;
                    switch(op) {
                        case "and": v = arr1[x][y][c] & arr2[x][y][c]; break;
                        case "or":  v = arr1[x][y][c] | arr2[x][y][c]; break;
                        case "xor": v = arr1[x][y][c] ^ arr2[x][y][c]; break;
                    }
                    res[x][y][c] = clamp(v);
                }
            }
        }
        return convertToBimage(res);
    }

    // Bitwise NOT: apply bitwise complement on each color channel.
    private BufferedImage applyBitwiseNot(BufferedImage img) {
        int width = img.getWidth(), height = img.getHeight();
        int[][][] arr = convertToArray(img);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int c = 1; c <= 3; c++){
                    arr[x][y][c] = ~arr[x][y][c] & 0xff;
                }
            }
        }
        return convertToBimage(arr);
    }

    // ROI-based negative: use a B/W mask image to select the region (if mask pixel > 128, apply negative).
    private BufferedImage applyROINegative(BufferedImage img, BufferedImage mask) {
        int width = Math.min(img.getWidth(), mask.getWidth());
        int height = Math.min(img.getHeight(), mask.getHeight());
        int[][][] arrImg = convertToArray(img);
        int[][][] arrMask = convertToArray(mask);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                int maskVal = arrMask[x][y][1];
                if (maskVal > 128) {
                    arrImg[x][y][1] = 255 - arrImg[x][y][1];
                    arrImg[x][y][2] = 255 - arrImg[x][y][2];
                    arrImg[x][y][3] = 255 - arrImg[x][y][3];
                }
            }
        }
        return convertToBimage(arrImg);
    }

    // ==================== LAB 4 Operations ====================

    // (1) Negative Linear Transform – same as our negative filter.
    private BufferedImage applyPointNegative(BufferedImage img) {
        return applyNegative(img);
    }

    // (2) Logarithmic Transform: s = c * log(1 + r).
    // If user does not provide a value for c, compute automatically as c = 255 / ln(256).
    private BufferedImage applyLogTransform(BufferedImage img, Float userC) {
        int width = img.getWidth(), height = img.getHeight();
        int[][][] arr = convertToArray(img);
        // Automatic computation:
        float c = (userC != null) ? userC : (255f / (float)Math.log(256));
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int channel = 1; channel <= 3; channel++){
                    int r = arr[x][y][channel];
                    int s = clamp(Math.round(c * (float)Math.log(1 + r)));
                    arr[x][y][channel] = s;
                }
            }
        }
        return convertToBimage(arr);
    }

    // (3) Power-Law Transform: s = c * (r^p).
    // Automatically compute c as 255 / (255^p) so that 255 maps to 255.
    private BufferedImage applyPowerLawTransform(BufferedImage img, float p) {
        int width = img.getWidth(), height = img.getHeight();
        int[][][] arr = convertToArray(img);
        float c = 255f / (float)Math.pow(255, p);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int channel = 1; channel <= 3; channel++){
                    int r = arr[x][y][channel];
                    int s = clamp(Math.round(c * (float)Math.pow(r, p)));
                    arr[x][y][channel] = s;
                }
            }
        }
        return convertToBimage(arr);
    }

    // (4) Random Look-up Table Transform: generate a LUT of 256 random values and apply.
    private BufferedImage applyRandomLUT(BufferedImage img) {
        int[] lut = new int[256];
        Random rand = new Random();
        for (int i = 0; i < 256; i++){
            lut[i] = rand.nextInt(256);
        }
        int width = img.getWidth(), height = img.getHeight();
        int[][][] arr = convertToArray(img);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                for (int channel = 1; channel <= 3; channel++){
                    int r = arr[x][y][channel];
                    arr[x][y][channel] = lut[r];
                }
            }
        }
        return convertToBimage(arr);
    }

    // (5) Bit-Plane Slicing: convert the image to grayscale, then extract the specified bit plane.
    private BufferedImage applyBitPlaneSlicing(BufferedImage img, int bit) {
        int width = img.getWidth(), height = img.getHeight();
        // Create a new grayscale image.
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                // Compute grayscale value (average of R, G, B).
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int gray = (r + g + b) / 3;
                // Extract the bit.
                int bitVal = ((gray >> bit) & 1) == 1 ? 255 : 0;
                int newPixel = (255 << 24) | (bitVal << 16) | (bitVal << 8) | bitVal;
                result.setRGB(x, y, newPixel);
            }
        }
        return result;
    }

    // ==================== Undo & Reset ====================

    // Backup the current processed image.
    private void backupForUndo() {
        if (processedImage != null)
            undoStack.push(copyImage(processedImage));
    }

    // Undo the last operation.
    private void undo() {
        if (!undoStack.isEmpty()) {
            processedImage = undoStack.pop();
            repaint();
        } else {
            JOptionPane.showMessageDialog(this, "Nothing to undo!");
        }
    }

    // Reset processed image to the original and clear the undo stack.
    private void resetToOriginal() {
        processedImage = copyImage(originalImage);
        undoStack.clear();
        repaint();
    }

    // ==================== Painting ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (originalImage != null)
            g.drawImage(originalImage, 0, 0, this);
        if (processedImage != null)
            g.drawImage(processedImage, originalImage.getWidth() + gap, 0, this);
    }

    // ==================== Action Handling ====================

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        // ----- File Operations -----
        if (cmd.equals("Open")) {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
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
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    ImageIO.write(processedImage, "png", file);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else if (cmd.equals("Undo")) {
            undo();
        }
        // ----- Lab1/Lab2 Operations -----
        else if (cmd.equals("Original")) {
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
                    if (factor < 0 || factor > 2)
                        JOptionPane.showMessageDialog(this, "Scaling factor must be between 0 and 2.");
                    else {
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
        // ----- Lab3 Operations -----
        else if (cmd.equals("Arithmetic Add") ||
                 cmd.equals("Arithmetic Subtract") ||
                 cmd.equals("Arithmetic Multiply") ||
                 cmd.equals("Arithmetic Divide")) {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    BufferedImage secondImage = ImageIO.read(file);
                    if (secondImage.getType() != BufferedImage.TYPE_INT_RGB) {
                        BufferedImage temp = new BufferedImage(
                            secondImage.getWidth(), secondImage.getHeight(), BufferedImage.TYPE_INT_RGB
                        );
                        Graphics g = temp.getGraphics();
                        g.drawImage(secondImage, 0, 0, null);
                        g.dispose();
                        secondImage = temp;
                    }
                    backupForUndo();
                    String op = "";
                    if (cmd.equals("Arithmetic Add")) op = "add";
                    else if (cmd.equals("Arithmetic Subtract")) op = "subtract";
                    else if (cmd.equals("Arithmetic Multiply")) op = "multiply";
                    else if (cmd.equals("Arithmetic Divide")) op = "divide";
                    processedImage = applyArithmeticOperation(processedImage, secondImage, op);
                    repaint();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else if (cmd.equals("Bitwise NOT")) {
            backupForUndo();
            processedImage = applyBitwiseNot(processedImage);
            repaint();
        } else if (cmd.equals("Bitwise AND") ||
                   cmd.equals("Bitwise OR") ||
                   cmd.equals("Bitwise XOR")) {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    BufferedImage secondImage = ImageIO.read(file);
                    if (secondImage.getType() != BufferedImage.TYPE_INT_RGB) {
                        BufferedImage temp = new BufferedImage(
                            secondImage.getWidth(), secondImage.getHeight(), BufferedImage.TYPE_INT_RGB
                        );
                        Graphics g = temp.getGraphics();
                        g.drawImage(secondImage, 0, 0, null);
                        g.dispose();
                        secondImage = temp;
                    }
                    backupForUndo();
                    String op = "";
                    if (cmd.equals("Bitwise AND")) op = "and";
                    else if (cmd.equals("Bitwise OR")) op = "or";
                    else if (cmd.equals("Bitwise XOR")) op = "xor";
                    processedImage = applyBitwiseOperation(processedImage, secondImage, op);
                    repaint();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else if (cmd.equals("ROI Negative")) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Mask Image (Black & White)");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    BufferedImage maskImage = ImageIO.read(file);
                    backupForUndo();
                    processedImage = applyROINegative(processedImage, maskImage);
                    repaint();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        // ----- Lab4 Operations -----
        else if (cmd.equals("Point Negative")) {
            backupForUndo();
            processedImage = applyPointNegative(processedImage);
            repaint();
        } else if (cmd.equals("Logarithmic Transform")) {
            String input = JOptionPane.showInputDialog(this, "Enter constant c (or leave blank for automatic):", "");
            Float cVal = null;
            if (input != null && !input.trim().isEmpty()) {
                try {
                    cVal = Float.parseFloat(input);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for constant c. Using automatic value.");
                }
            }
            backupForUndo();
            processedImage = applyLogTransform(processedImage, cVal);
            repaint();
        } else if (cmd.equals("Power-Law Transform")) {
            String input = JOptionPane.showInputDialog(this, "Enter power (p, from 0.01 to 25):", "1.0");
            if (input != null) {
                try {
                    float p = Float.parseFloat(input);
                    if (p < 0.01f || p > 25f) {
                        JOptionPane.showMessageDialog(this, "Power must be between 0.01 and 25.");
                    } else {
                        backupForUndo();
                        processedImage = applyPowerLawTransform(processedImage, p);
                        repaint();
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for power.");
                }
            }
        } else if (cmd.equals("Random LUT Transform")) {
            backupForUndo();
            processedImage = applyRandomLUT(processedImage);
            repaint();
        } else if (cmd.equals("Bit-Plane Slicing")) {
            String input = JOptionPane.showInputDialog(this, "Enter bit plane (0-7):", "0");
            if (input != null) {
                try {
                    int bit = Integer.parseInt(input);
                    if (bit < 0 || bit > 7) {
                        JOptionPane.showMessageDialog(this, "Bit must be between 0 and 7.");
                    } else {
                        backupForUndo();
                        processedImage = applyBitPlaneSlicing(processedImage, bit);
                        repaint();
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for bit.");
                }
            }
        }
    }

    // ==================== Main Method ====================
    public static void main(String[] args) {
        // Prompt the user to select an image file at startup.
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
            System.exit(0);
        File selectedFile = fileChooser.getSelectedFile();

        Demo demo = new Demo(selectedFile);
        JFrame frame = new JFrame("Image Processing Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(demo, BorderLayout.CENTER);

        // Build the menu bar.
        JMenuBar menuBar = new JMenuBar();

        // File menu.
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

        // Edit menu.
        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setActionCommand("Undo");
        undoItem.addActionListener(demo);
        editMenu.add(undoItem);
        menuBar.add(editMenu);

        // Lab1 & Lab2 Operations.
        JMenu opMenu = new JMenu("Operations");
        String[] ops = {"Original", "Negative", "Rescale", "Shift", "Shift+Rescale"};
        for (String op : ops) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            opMenu.add(item);
        }
        menuBar.add(opMenu);

        // Lab3 Operations.
        JMenu lab3Menu = new JMenu("Lab3 Operations");
        String[] lab3Ops = {"Arithmetic Add", "Arithmetic Subtract", "Arithmetic Multiply", "Arithmetic Divide",
                            "Bitwise NOT", "Bitwise AND", "Bitwise OR", "Bitwise XOR", "ROI Negative"};
        for (String op : lab3Ops) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            lab3Menu.add(item);
        }
        menuBar.add(lab3Menu);

        // Lab4 Operations.
        JMenu lab4Menu = new JMenu("Lab4 Operations");
        String[] lab4Ops = {"Point Negative", "Logarithmic Transform", "Power-Law Transform", "Random LUT Transform", "Bit-Plane Slicing"};
        for (String op : lab4Ops) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            lab4Menu.add(item);
        }
        menuBar.add(lab4Menu);

        frame.setJMenuBar(menuBar);
        frame.pack();
        frame.setVisible(true);
    }
}
