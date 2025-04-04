import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.Stack;
import java.util.Random;
import java.util.function.Function;
import javax.imageio.*;
import javax.swing.*;

public class Demo extends JPanel implements ActionListener {
    // Three image slots.
    private BufferedImage originalImage;    // Left image (first image)
    private BufferedImage secondImage;      // Middle image (second image)
    private BufferedImage processedImage;   // Right image (result of processing)
    
    // Undo stack for the processedImage.
    private Stack<BufferedImage> undoStack = new Stack<>();
    // Gap between images.
    private final int gap = 10;
    
    // ROI selection fields (applies to processedImage).
    private boolean selectingROI = false;
    private Point roiStart = null;
    private Rectangle roi = null; // ROI relative to the processedImage
    
    // Log area for displaying messages.
    private JTextArea logArea;
    
    // Constructor: load the original image from file.
    public Demo(File imageFile, JTextArea logArea) {
        this.logArea = logArea;
        try {
            BufferedImage img = null;
            // Check if the file is RAW (by its extension)
            if (imageFile.getName().toLowerCase().endsWith(".raw")) {
                String wStr = JOptionPane.showInputDialog(this, "Enter width for RAW image (leave blank to guess):", "");
                String hStr = JOptionPane.showInputDialog(this, "Enter height for RAW image (leave blank to guess):", "");
                int w, h;
                if (wStr == null || hStr == null || wStr.trim().isEmpty() || hStr.trim().isEmpty()) {
                    Dimension d = guessDimensions(imageFile);
                    w = d.width;
                    h = d.height;
                    log("Guessed RAW dimensions: " + w + " x " + h);
                } else {
                    w = Integer.parseInt(wStr);
                    h = Integer.parseInt(hStr);
                }
                img = readRawImage(imageFile, w, h);
            } else {
                img = ImageIO.read(imageFile);
                if (img.getType() != BufferedImage.TYPE_INT_RGB) {
                    BufferedImage temp = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics g = temp.getGraphics();
                    g.drawImage(img, 0, 0, null);
                    g.dispose();
                    img = temp;
                }
            }
            // Force resize to exactly 512 x 512
            originalImage = resizeImage(img);
            processedImage = copyImage(originalImage);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Original image could not be read.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        
        // Add mouse listeners for ROI selection on the processed image.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
            // ROI selection only applies in the processedImage area (right one)
            int offsetX = getLeftImageWidth() + getMiddleImageWidth() + 2 * gap;
            if (selectingROI && e.getX() >= offsetX) {
                roiStart = new Point(e.getX() - offsetX, e.getY());
                roi = new Rectangle(roiStart);
            }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
            if (selectingROI && roiStart != null) {
                int offsetX = getLeftImageWidth() + getMiddleImageWidth() + 2 * gap;
                Point currentPoint = new Point(e.getX() - offsetX, e.getY());
                updateROI(currentPoint);
                selectingROI = false;
                repaint();
                log("ROI selected: " + roi);
            }
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
            if (selectingROI && roiStart != null) {
                int offsetX = getLeftImageWidth() + getMiddleImageWidth() + 2 * gap;
                Point currentPoint = new Point(e.getX() - offsetX, e.getY());
                updateROI(currentPoint);
                repaint();
                log("ROI updated: " + roi);
            }
            }
        });
        }
    private void updateROI(Point currentPoint) {
        int newX = Math.min(roiStart.x, currentPoint.x);
        int newY = Math.min(roiStart.y, currentPoint.y);
        int newW = Math.abs(currentPoint.x - roiStart.x);
        int newH = Math.abs(currentPoint.y - roiStart.y);
        roi = new Rectangle(newX, newY, newW, newH);
    }
    
    // Helper: make a copy of a BufferedImage.
    private BufferedImage copyImage(BufferedImage img) {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics g = copy.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copy;
    }
    
    // Methods to get widths of left and middle images (for layout calculations).
    private int getLeftImageWidth() {
        return originalImage != null ? originalImage.getWidth() : 0;
    }
    private int getMiddleImageWidth() {
        // If secondImage is not loaded, use a placeholder width equal to originalImage.
        return secondImage != null ? secondImage.getWidth() : (originalImage != null ? originalImage.getWidth() : 0);
    }
    
    // Preferred size: width = sum of widths of three images + 2 gaps, height = max of heights.
    @Override
    public Dimension getPreferredSize() {
        int w1 = getLeftImageWidth();
        int w2 = getMiddleImageWidth();
        int w3 = processedImage != null ? processedImage.getWidth() : 0;
        int totalWidth = w1 + gap + w2 + gap + w3;
        int h1 = originalImage != null ? originalImage.getHeight() : 0;
        int h2 = secondImage != null ? secondImage.getHeight() : 0;
        int h3 = processedImage != null ? processedImage.getHeight() : 0;
        int maxHeight = Math.max(h1, Math.max(h2, h3));
        return new Dimension(totalWidth, maxHeight);
    }
    
    // Clamp value.
    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
    
    // ------------------- Utility Methods (Image conversion) -------------------
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
    // If no dimensions are provided, try to guess dimensions from the file size.
    // This method assumes the image is square if possible; if not, it uses the floor of sqrt(fileSize) as width.
    private Dimension guessDimensions(File file) {
        long size = file.length();
        int dim = (int) Math.round(Math.sqrt(size));
        // If the file size is a perfect square, assume a square image.
        if (dim * dim == size) {
            return new Dimension(dim, dim);
        } else if (size % dim == 0) {
            // Otherwise, assume width = dim and height = file size / dim
            return new Dimension(dim, (int) (size / dim));
        } else {
            // Fallback: assume square (user may need to adjust)
            return new Dimension(dim, dim);
        }
    }

    // Always resize any image to exactly 512 x 512.
    private BufferedImage resizeImage(BufferedImage img) {
        int newWidth = 512;
        int newHeight = 512;
        Image tmp = img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    private BufferedImage readRawImage(File file, int width, int height) throws IOException {
        int size = width * height;
        byte[] bytes = new byte[size];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(bytes);
            if (read != size) {
                throw new IOException("Raw file size does not match expected dimensions.");
            }
        }
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int val = bytes[y * width + x] & 0xff;
                int rgb = (255 << 24) | (val << 16) | (val << 8) | val;
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }

    // Returns an array of supported image formats.
    private String[] getFormats() {
        String[] formats = {"bmp", "gif", "jpeg", "jpg", "png"};
        java.util.TreeSet<String> formatSet = new java.util.TreeSet<>();
        for (String s : formats) {
            formatSet.add(s.toLowerCase());
        }
        return formatSet.toArray(new String[0]);
    }

    // New "Save As" method.
    private void saveAs() {
        // Let the user choose the format (default "png")
        String format = (String) JOptionPane.showInputDialog(
                this,
                "Choose file format:",
                "Save As",
                JOptionPane.PLAIN_MESSAGE,
                null,
                getFormats(),
                "png");
        if (format != null) {
            JFileChooser chooser = new JFileChooser();
            // Suggest a default file name using the chosen format.
            chooser.setSelectedFile(new File("savedimage." + format));
            int rval = chooser.showSaveDialog(this);
            if (rval == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    ImageIO.write(processedImage, format, file);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Error saving file: " + ex.getMessage());
                }
            }
        }
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
    
        // Shift+Rescale: Add one random value (between -50 and 50) per pixel (applied to all channels)
        // then rescale each channel independently to the range [0,255].
        private BufferedImage applyShiftAndRescale(BufferedImage img) {
            int width = img.getWidth(), height = img.getHeight();
            int[][][] arr = convertToArray(img);
            Random rand = new Random();
            
            // Arrays to track min and max per channel.
            int[] minChannel = {255, 255, 255};
            int[] maxChannel = {0, 0, 0};
            
            // For each pixel, generate a single random shift value and add it to each channel.
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int randomVal = rand.nextInt(101) - 50; // one random value between -50 and 50 per pixel
                    for (int c = 1; c <= 3; c++){
                        arr[x][y][c] += randomVal;
                        minChannel[c - 1] = Math.min(minChannel[c - 1], arr[x][y][c]);
                        maxChannel[c - 1] = Math.max(maxChannel[c - 1], arr[x][y][c]);
                    }
                }
            }
            
            // Rescale each channel independently.
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
    
        // ==================== LAB 3 Operations ====================
    
        // Arithmetic operations: perform "add", "subtract", "multiply", or "divide" on two images.
        private BufferedImage applyArithmeticOperation(BufferedImage img1, BufferedImage img2, String op) {
            int width = Math.min(img1.getWidth(), img2.getWidth());
            int height = Math.min(img1.getHeight(), img2.getHeight());
            int[][][] arr1 = convertToArray(img1);
            int[][][] arr2 = convertToArray(img2);
            int[][][] res = new int[width][height][4];
           int minChannel = Integer.MAX_VALUE;
            int maxChannel = Integer.MIN_VALUE;
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    res[x][y][0] = arr1[x][y][0]; // alpha from first image
                    for (int c = 1; c <= 3; c++){
                        //int v = 0;
                        switch(op) {
                            case "add": res[x][y][c] = arr1[x][y][c] + arr2[x][y][c]; break;
                            case "subtract": res[x][y][c] = arr1[x][y][c] - arr2[x][y][c]; break;
                            case "multiply": res[x][y][c] = arr1[x][y][c] * arr2[x][y][c]; break;
                            case "divide": res[x][y][c] = (arr2[x][y][c] == 0) ? 0 : arr1[x][y][c] / arr2[x][y][c]; break;
                        }
                        
                        minChannel = Math.min(minChannel, res[x][y][c]);
                        maxChannel = Math.max(maxChannel, res[x][y][c]);
                    }
                }
            }
            // Rescale result channels to [0,255]
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    for (int c = 1; c <= 3; c++){
                            res[x][y][c] = (res[x][y][c] - minChannel) * 255 / (maxChannel - minChannel);
                            
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
            log("Logarithmic transform: c = " + c);
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
    
        // ------------------- LAB 6: Convolution -------------------
        /**
         * Applies convolution to the given image using the provided kernel.
         * The image is first converted to grayscale. If useAbsolute is true, the result
         * is converted to absolute values. If normalize is true, the result is linearly
         * mapped to the range [0,255].
         *
         * @param img         the input image (will be converted to grayscale)
         * @param kernel      the convolution kernel (2D float array)
         * @param useAbsolute if true, take the absolute value of each convolution result
         * @param normalize   if true, linearly map results to [0,255]
         * @return a new BufferedImage containing the convolution result in grayscale
         */
        private BufferedImage applyConvolution(BufferedImage img, float[][] kernel, boolean useAbsolute, boolean normalize) {
            int width = img.getWidth();
            int height = img.getHeight();
            int kRows = kernel.length;
            int kCols = kernel[0].length;
            int kCenterX = kCols / 2;
            int kCenterY = kRows / 2;
            
            // Get the RGB values from the image.
            int[][][] arr = convertToArray(img);
            
            // Create an array to hold convolution results for each channel (r, g, b).
            float[][][] conv = new float[width][height][3]; // channels: 0=r, 1=g, 2=b
            
            // For each pixel and each channel, compute the convolution.
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int c = 1; c <= 3; c++) { // channels 1,2,3 correspond to r, g, b.
                        float sum = 0;
                        for (int m = 0; m < kRows; m++) {
                            for (int n = 0; n < kCols; n++) {
                                int ix = x + n - kCenterX;
                                int iy = y + m - kCenterY;
                                if (ix >= 0 && ix < width && iy >= 0 && iy < height) {
                                    sum += kernel[m][n] * arr[ix][iy][c];
                                }
                            }
                        }
                        conv[x][y][c - 1] = sum;
                    }
                }
            }
            
            // Optionally take the absolute value.
            if (useAbsolute) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int c = 0; c < 3; c++) {
                            conv[x][y][c] = Math.abs(conv[x][y][c]);
                        }
                    }
                }
            }
            
            // Optionally normalize the entire image using a global min and max.
            if (normalize) {
                float globalMin = Float.MAX_VALUE;
                float globalMax = -Float.MAX_VALUE;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int c = 0; c < 3; c++) {
                            globalMin = Math.min(globalMin, conv[x][y][c]);
                            globalMax = Math.max(globalMax, conv[x][y][c]);
                        }
                    }
                }
                float range = globalMax - globalMin;
                if (range == 0) {
                    range = 1;
                }
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int c = 0; c < 3; c++) {
                            conv[x][y][c] = (conv[x][y][c] - globalMin) * 255 / range;
                        }
                    }
                }
            }
            
            // Build the output image from the convolved channels.
            BufferedImage outImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = clamp(Math.round(conv[x][y][0]));
                    int g = clamp(Math.round(conv[x][y][1]));
                    int b = clamp(Math.round(conv[x][y][2]));
                    int rgb = (255 << 24) | (r << 16) | (g << 8) | b;
                    outImg.setRGB(x, y, rgb);
                }
            }
            return outImg;
        }
        // ------------------- LAB 7: Order-statistics Filtering -------------------
        // Helper: extract grayscale values from an image.
        private int[][] getGrayMatrix(BufferedImage img) {
            int width = img.getWidth();
            int height = img.getHeight();
            int[][] gray = new int[width][height];
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    gray[x][y] = (r + g + b) / 3;
                }
            }
            return gray;
        }
        
        // Helper: create a grayscale image from a 2D array.
        private BufferedImage createGrayImage(int[][] gray) {
            int width = gray.length;
            int height = gray[0].length;
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int val = clamp(gray[x][y]);
                    int rgb = (255 << 24) | (val << 16) | (val << 8) | val;
                    img.setRGB(x, y, rgb);
                }
            }
            return img;
        }
        
        // Salt-and-Pepper Noise: randomly set some pixels to 0 or 255.
        private BufferedImage addSaltAndPepperNoise(BufferedImage img, double noiseProb) {
            int width = img.getWidth(), height = img.getHeight();
            BufferedImage out = copyImage(img);
            Random rand = new Random();
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    if (rand.nextDouble() < noiseProb) {
                        boolean salt = rand.nextBoolean();
                        int val = salt ? 255 : 0;
                        int rgb = (255 << 24) | (val << 16) | (val << 8) | val;
                        out.setRGB(x, y, rgb);
                    }
                }
            }
            return out;
        }
        
        // Min Filter: replace each pixel with the minimum value in its neighborhood.
        private BufferedImage applyMinFilter(BufferedImage img, int filterSize) {
            int[][] gray = getGrayMatrix(img);
            int width = img.getWidth(), height = img.getHeight();
            int offset = filterSize / 2;
            int[][] out = new int[width][height];
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int minVal = 255;
                    for (int j = -offset; j <= offset; j++){
                        for (int i = -offset; i <= offset; i++){
                            int xx = x + i, yy = y + j;
                            if (xx >= 0 && xx < width && yy >= 0 && yy < height)
                                minVal = Math.min(minVal, gray[xx][yy]);
                        }
                    }
                    out[x][y] = minVal;
                }
            }
            return createGrayImage(out);
        }
        
        // Max Filter: replace each pixel with the maximum value in its neighborhood.
        private BufferedImage applyMaxFilter(BufferedImage img, int filterSize) {
            int[][] gray = getGrayMatrix(img);
            int width = img.getWidth(), height = img.getHeight();
            int offset = filterSize / 2;
            int[][] out = new int[width][height];
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int maxVal = 0;
                    for (int j = -offset; j <= offset; j++){
                        for (int i = -offset; i <= offset; i++){
                            int xx = x + i, yy = y + j;
                            if (xx >= 0 && xx < width && yy >= 0 && yy < height)
                                maxVal = Math.max(maxVal, gray[xx][yy]);
                        }
                    }
                    out[x][y] = maxVal;
                }
            }
            return createGrayImage(out);
        }
        
        // Midpoint Filter: replace each pixel with (min + max)/2 from its neighborhood.
        private BufferedImage applyMidpointFilter(BufferedImage img, int filterSize) {
            int[][] gray = getGrayMatrix(img);
            int width = img.getWidth(), height = img.getHeight();
            int offset = filterSize / 2;
            int[][] out = new int[width][height];
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int minVal = 255, maxVal = 0;
                    for (int j = -offset; j <= offset; j++){
                        for (int i = -offset; i <= offset; i++){
                            int xx = x + i, yy = y + j;
                            if (xx >= 0 && xx < width && yy >= 0 && yy < height) {
                                int v = gray[xx][yy];
                                minVal = Math.min(minVal, v);
                                maxVal = Math.max(maxVal, v);
                            }
                        }
                    }
                    out[x][y] = (minVal + maxVal) / 2;
                }
            }
            return createGrayImage(out);
        }
        
        // Median Filter: replace each pixel with the median of its neighborhood.
        private BufferedImage applyMedianFilter(BufferedImage img, int filterSize) {
            int[][] gray = getGrayMatrix(img);
            int width = img.getWidth(), height = img.getHeight();
            int offset = filterSize / 2;
            int[][] out = new int[width][height];
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int n = filterSize * filterSize;
                    int[] vals = new int[n];
                    int count = 0;
                    for (int j = -offset; j <= offset; j++){
                        for (int i = -offset; i <= offset; i++){
                            int xx = x + i, yy = y + j;
                            if (xx >= 0 && xx < width && yy >= 0 && yy < height)
                                vals[count++] = gray[xx][yy];
                        }
                    }
                    java.util.Arrays.sort(vals, 0, count);
                    out[x][y] = vals[count / 2];
                }
            }
            return createGrayImage(out);
        }
        // ------------------- Lab 5: Histogram & Histogram Equalisation -------------------
        private int[] computeHistogram(BufferedImage img) {
            int width = img.getWidth(), height = img.getHeight();
            int[] hist = new int[256];
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    int gray = (r + g + b) / 3;
                    hist[gray]++;
                }
            }
            return hist;
        }
        
        private BufferedImage histogramEqualisation(BufferedImage img) {
            int width = img.getWidth(), height = img.getHeight();
            int total = width * height;
            int[] hist = computeHistogram(img);
            int[] cdf = new int[256];
            cdf[0] = hist[0];
            for (int i = 1; i < 256; i++){
                cdf[i] = cdf[i-1] + hist[i];
            }
            int cdf_min = 0;
            for (int i = 0; i < 256; i++){
                if (cdf[i] != 0) { cdf_min = cdf[i]; break; }
            }
            int[] transform = new int[256];
            for (int i = 0; i < 256; i++){
                transform[i] = Math.round((cdf[i] - cdf_min) * 255f / (total - cdf_min));
                transform[i] = clamp(transform[i]);
            }
            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    int gray = (r + g + b) / 3;
                    int newVal = transform[gray];
                    int newRgb = (255 << 24) | (newVal << 16) | (newVal << 8) | newVal;
                    out.setRGB(x, y, newRgb);
                }
            }
            return out;
        }
        
        // Display the histogram as a 256x256 image.
        private BufferedImage displayHistogram(BufferedImage img) {
            int[] hist = computeHistogram(img);
            int max = 0;
            for (int i = 0; i < 256; i++){
                if (hist[i] > max) max = hist[i];
            }
            BufferedImage histImg = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = histImg.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 256, 256);
            g2d.setColor(Color.BLACK);
            for (int i = 0; i < 256; i++){
                int heightBar = Math.round(hist[i] * 255f / max);
                g2d.drawLine(i, 255, i, 255 - heightBar);
            }
            g2d.dispose();
            return histImg;
        }
        
        // ------------------- Lab 8: Thresholding -------------------
        private double[] computeMeanStd(BufferedImage img) {
            int[] hist = computeHistogram(img);
            int total = img.getWidth() * img.getHeight();
            double mean = 0;
            for (int i = 0; i < 256; i++){
                mean += i * hist[i];
            }
            mean /= total;
            double variance = 0;
            for (int i = 0; i < 256; i++){
                variance += hist[i] * Math.pow(i - mean, 2);
            }
            variance /= total;
            double std = Math.sqrt(variance);
            return new double[]{mean, std};
        }
        
        private BufferedImage simpleThreshold(BufferedImage img, int thresh) {
            int width = img.getWidth(), height = img.getHeight();
            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    int gray = (r + g + b) / 3;
                    int val = (gray >= thresh) ? 255 : 0;
                    int newRgb = (255 << 24) | (val << 16) | (val << 8) | val;
                    out.setRGB(x, y, newRgb);
                }
            }
            return out;
        }
        
        private int automatedThreshold(BufferedImage img) {
            int width = img.getWidth(), height = img.getHeight();
            int[][] gray = new int[width][height];
            int sum = 0;
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xff;
                    int g = (rgb >> 8) & 0xff;
                    int b = rgb & 0xff;
                    int gVal = (r + g + b) / 3;
                    gray[x][y] = gVal;
                    sum += gVal;
                }
            }
            int T = sum / (width * height);
            boolean converged = false;
            while (!converged) {
                long sum1 = 0, sum2 = 0;
                int count1 = 0, count2 = 0;
                for (int y = 0; y < height; y++){
                    for (int x = 0; x < width; x++){
                        if (gray[x][y] < T) {
                            sum1 += gray[x][y];
                            count1++;
                        } else {
                            sum2 += gray[x][y];
                            count2++;
                        }
                    }
                }
                int T_new = T;
                if (count1 > 0 && count2 > 0) {
                    T_new = (int)Math.round((sum1 / (double)count1 + sum2 / (double)count2) / 2.0);
                }
                if (Math.abs(T_new - T) < 1)
                    converged = true;
                T = T_new;
            }
            return T;
        }

    
    // ------------------- ROI Wrapper -------------------
    // Applies a function to the ROI subimage if ROI is defined; otherwise, applies to full image.
    private BufferedImage applyOnROI(BufferedImage img, Function<BufferedImage, BufferedImage> func) {
        if (roi == null) {
            return func.apply(img);
        } else {
            // Extract ROI subimage (note: roi is relative to the processed image).
            BufferedImage sub = img.getSubimage(roi.x, roi.y, roi.width, roi.height);
            BufferedImage processedSub = func.apply(sub);
            // Create a copy of the full image.
            BufferedImage result = copyImage(img);
            Graphics g = result.getGraphics();
            g.drawImage(processedSub, roi.x, roi.y, null);
            g.dispose();
            return result;
        }
    }
    // A helper that applies a two–image operation only on the ROI region of the first image.
    private BufferedImage applyOnROI2(BufferedImage img1, BufferedImage img2, java.util.function.BiFunction<BufferedImage, BufferedImage, BufferedImage> func) {
        if (roi == null) {
            return func.apply(img1, img2);
        } else {
            // Extract ROI from both images (assuming both are the same size)
            BufferedImage roi1 = img1.getSubimage(roi.x, roi.y, roi.width, roi.height);
            BufferedImage roi2 = img2.getSubimage(roi.x, roi.y, roi.width, roi.height);
            BufferedImage roiResult = func.apply(roi1, roi2);
            // Paste the ROI result back into a copy of img1.
            BufferedImage result = copyImage(img1);
            Graphics g = result.getGraphics();
            g.drawImage(roiResult, roi.x, roi.y, null);
            g.dispose();
            return result;
        }
    }


    // ------------------- Logging -------------------
    private void log(String message) {
        if (logArea != null) {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }
    
    // ------------------- Undo & Reset -------------------
    private void backupForUndo() {
        if (processedImage != null)
            undoStack.push(copyImage(processedImage));
    }
    
    // Undo the last operation.
    private void undo() {
        if (!undoStack.isEmpty()) {
            processedImage = undoStack.pop();
            repaint();
            log("Undo performed.");
        } else {
            JOptionPane.showMessageDialog(this, "Nothing to undo!");
        }
    }
    
    // Reset processed image to the original and clear the undo stack.
    private void resetToOriginal() {
        processedImage = copyImage(originalImage);
        undoStack.clear();
        roi = null;  // also clear ROI
        repaint();
        log("Reset to original image.");
        logArea.setText("");  // clear log area
    }
    
    // ==================== Painting ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int labelHeight = 20; // vertical space reserved for labels
        int x = 0;
        
        // Draw left image (originalImage) with label "Original"
        if (originalImage != null) {
            // Draw label above the image
            g.setColor(Color.BLACK);
            g.drawString("Original Image", x + 10, 15);
            // Draw image below the label
            g.drawImage(originalImage, x, labelHeight, this);
            x += originalImage.getWidth() + gap;
        }
        
        // Draw middle image (secondImage) with label "Second"
        if (secondImage != null) {
            g.setColor(Color.BLACK);
            g.drawString("Second Image", x + 10, 15);
            g.drawImage(secondImage, x, labelHeight, this);
        } else {
            // Draw placeholder border and label if second image not loaded
            g.setColor(Color.GRAY);
            g.drawRect(x, labelHeight, originalImage.getWidth(), originalImage.getHeight());
            g.drawString("Load second image", x + 10, labelHeight + 20);
            g.setColor(Color.BLACK);
            g.drawString("Second", x + 10, 15);
        }
        x += getMiddleImageWidth() + gap;
        
        // Draw right image (processedImage) with label "Processed"
        if (processedImage != null) {
            g.setColor(Color.BLACK);
            g.drawString("Processed Image", x + 10, 15);
            g.drawImage(processedImage, x, labelHeight, this);
        }
        
        // Draw ROI rectangle (relative to processed image area)
        if (roi != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.RED);
            // Calculate offset based on the positions of the first two images and the gap
            int offsetX = getLeftImageWidth() + getMiddleImageWidth() + 2 * gap;
            g2d.drawRect(offsetX + roi.x, roi.y + labelHeight, roi.width, roi.height);
        }
    }
    
    // ------------------- Action Handling -------------------
    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("Open Original")) {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    BufferedImage img = null;
                    // Check if the file is RAW (by extension)
                    if (file.getName().toLowerCase().endsWith(".raw")) {
                        String wStr = JOptionPane.showInputDialog(this, "Enter width for RAW image (leave blank to guess):", "");
                        String hStr = JOptionPane.showInputDialog(this, "Enter height for RAW image (leave blank to guess):", "");
                        int w, h;
                        if (wStr.trim().isEmpty() || hStr.trim().isEmpty()) {
                            Dimension d = guessDimensions(file);
                            w = d.width;
                            h = d.height;
                            log("Guessed RAW dimensions: " + w + " x " + h);
                        } else {
                            w = Integer.parseInt(wStr);
                            h = Integer.parseInt(hStr);
                        }
                        img = readRawImage(file, w, h);
                    }else {
                        img = ImageIO.read(file);
                        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
                            BufferedImage temp = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                            Graphics g = temp.getGraphics();
                            g.drawImage(img, 0, 0, null);
                            g.dispose();
                            img = temp;
                        }
                    }
                    // Force resize to 512 x 512
                    img = resizeImage(img);
                    originalImage = img;
                    resetToOriginal();
                    revalidate();
                    repaint();
                    log("Loaded original image.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error loading image: " + ex.getMessage());
                }
            }
        } else if (cmd.equals("Save")) {
            saveAs();
        } else if (cmd.equals("Undo")) {
            undo();
        }
        // ROI selection commands.
        else if (cmd.equals("Select ROI")) {
            // Enter ROI selection mode.
            selectingROI = true;
            roi = null;  // Clear any previous ROI.
            JOptionPane.showMessageDialog(this, "Click and drag on the processed image to select ROI.");
            log("ROI selection mode entered.");
        } else if (cmd.equals("Clear ROI")) {
            roi = null;
            repaint();
            log("ROI cleared.");
        }
        else if (cmd.equals("Original")) {
            backupForUndo();
            resetToOriginal();
        } else if (cmd.equals("Negative")) {
            backupForUndo();
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> applyNegative(img))
            : applyNegative(processedImage);
            repaint();
            log("Applied negative operation.");
        } else if (cmd.equals("Rescale")) {
            String input = JOptionPane.showInputDialog(this, "Enter scaling factor (0 to 2):", "1.0");
            if (input != null) {
                try {
                    float factor = Float.parseFloat(input);
                    if (factor < 0 || factor > 2)
                        JOptionPane.showMessageDialog(this, "Scaling factor must be between 0 and 2.");
                    else {
                        backupForUndo();
                        processedImage = (roi != null)
                        ? applyOnROI(processedImage, (img) -> applyRescale(img, factor))
                        : applyRescale(processedImage, factor);
                        repaint();
                        log("Applied rescale operation with factor " + factor);
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
                    processedImage = (roi != null)
                    ? applyOnROI(processedImage, (img) -> applyShift(img, shiftVal))
                    : applyShift(processedImage, shiftVal);
                    repaint();
                    log("Applied shift operation with value " + shiftVal);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for shift value.");
                }
            }
        } else if (cmd.equals("Shift+Rescale")) {
            backupForUndo();
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> applyShiftAndRescale(img))
            : applyShiftAndRescale(processedImage);
            repaint();
            log("Applied shift and rescale operation.");
        }
        // ----- Lab3 Operations -----
        else if (cmd.equals("Arithmetic Add") ||
                    cmd.equals("Arithmetic Subtract") ||
                    cmd.equals("Arithmetic Multiply") ||
                    cmd.equals("Arithmetic Divide")) {
                if (secondImage == null) {
                    JOptionPane.showMessageDialog(this, "Please load a second image first.");
                } else {
                    backupForUndo();
                    String op = "";
                    if (cmd.equals("Arithmetic Add")) op = "add";
                    else if (cmd.equals("Arithmetic Subtract")) op = "subtract";
                    else if (cmd.equals("Arithmetic Multiply")) op = "multiply";
                    else if (cmd.equals("Arithmetic Divide")) op = "divide";
                    final String opFinal = op;
                    processedImage = applyOnROI2(processedImage, secondImage,
                    (imgA, imgB) -> applyArithmeticOperation(imgA, imgB, opFinal));
          repaint();
          log("Applied arithmetic operation: " + opFinal);
            }
        } else if (cmd.equals("Bitwise NOT")) {
            backupForUndo();
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> applyBitwiseNot(img))
            : applyBitwiseNot(processedImage);
            repaint();
            log("Applied bitwise NOT operation.");
        } else if (cmd.equals("Bitwise AND") ||
            cmd.equals("Bitwise OR") ||
            cmd.equals("Bitwise XOR")) {
        if (secondImage == null) {
            JOptionPane.showMessageDialog(this, "Please load a second image first.");
        } else {
            backupForUndo();
            String op = "";
            if (cmd.equals("Bitwise AND")) op = "and";
            else if (cmd.equals("Bitwise OR")) op = "or";
            else if (cmd.equals("Bitwise XOR")) op = "xor";
            final String opFinal = op;
            processedImage = applyOnROI2(processedImage, secondImage,
            (imgA, imgB) -> applyBitwiseOperation(imgA, imgB, opFinal));
            repaint();
            log("Applied bitwise operation: " + opFinal);
                }
        }        // ----- Lab4 Operations -----
        else if (cmd.equals("Point Negative")) {
            backupForUndo();
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> applyPointNegative(img))
            : applyPointNegative(processedImage);
            repaint();
            log("Applied point negative operation.");
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
            final Float cValFinal = cVal;
            processedImage = (roi != null) 
            ? applyOnROI(processedImage, (img) -> applyLogTransform(img, cValFinal))
            : applyLogTransform(processedImage, cVal);
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
                        processedImage = (roi != null)
                        ? applyOnROI(processedImage, (img) -> applyPowerLawTransform(img, p))
                        : applyPowerLawTransform(processedImage, p);
                        repaint();
                        log("Applied power-law transform with p = " + p);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for power.");
                }
            }
        } else if (cmd.equals("Random LUT Transform")) {
            backupForUndo();
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> applyRandomLUT(img))
            : applyRandomLUT(processedImage);
            repaint();
            log("Applied random LUT transform.");
        } else if (cmd.equals("Bit-Plane Slicing")) {
            String input = JOptionPane.showInputDialog(this, "Enter bit plane (0-7):", "0");
            if (input != null) {
                try {
                    int bit = Integer.parseInt(input);
                    if (bit < 0 || bit > 7) {
                        JOptionPane.showMessageDialog(this, "Bit must be between 0 and 7.");
                    } else {
                        backupForUndo();
                        processedImage = (roi != null)
                        ? applyOnROI(processedImage, (img) -> applyBitPlaneSlicing(img, bit))
                        : applyBitPlaneSlicing(processedImage, bit);
                        repaint();
                        log("Applied bit-plane slicing for bit " + bit);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for bit.");
                }
            }
        }else if (cmd.equals("Convolution")) {
            // Present a list of sample masks for selection.
            String[] options = { 
                "Averaging 3x3", 
                "Weighted Averaging 3x3", 
                "4-Neighbour Laplacian", 
                "8-Neighbour Laplacian", 
                "4-Neighbour Laplacian Enhancement", 
                "8-Neighbour Laplacian Enhancement", 
                "Roberts", 
                "Roberts2",
                "Sobel X", 
                "Sobel Y", 
                "Gaussian 5x5", 
                "Laplacian of Gaussian 5x5" 
            };
            String selection = (String) JOptionPane.showInputDialog(
                this, 
                "Select a convolution mask:", 
                "Convolution", 
                JOptionPane.PLAIN_MESSAGE, 
                null, 
                options, 
                options[0]
            );
            if (selection != null) {
                float[][] kernel = null;
                boolean useAbs = false;
                switch(selection) {
                    case "Averaging 3x3":
                        kernel = new float[][] {
                            {1/9f, 1/9f, 1/9f},
                            {1/9f, 1/9f, 1/9f},
                            {1/9f, 1/9f, 1/9f}
                        };
                        break;
                    case "Weighted Averaging 3x3":
                        kernel = new float[][] {
                            {1/16f, 2/16f, 1/16f},
                            {2/16f, 4/16f, 2/16f},
                            {1/16f, 2/16f, 1/16f}
                        };
                        break;
                    case "4-Neighbour Laplacian":
                        kernel = new float[][] {
                            {0, -1, 0},
                            {-1, 4, -1},
                            {0, -1, 0}
                        };
                        break;
                    case "8-Neighbour Laplacian":
                        kernel = new float[][] {
                            {-1, -1, -1},
                            {-1, 8, -1},
                            {-1, -1, -1}
                        };
                        useAbs = true;
                        break;
                    case "4-Neighbour Laplacian Enhancement":
                        kernel = new float[][] {
                            {0, -1, 0},
                            {-1, 5, -1},
                            {0, -1, 0}
                        };
                        break;
                    case "8-Neighbour Laplacian Enhancement":
                        kernel = new float[][] {
                            {-1, -1, -1},
                            {-1, 9, -1},
                            {-1, -1, -1}
                        };
                        break;
                    case "Roberts":
                        kernel = new float[][] {
                            {0, 0, 0},
                            {0, 0, -1},
                            {0, 1, 0}
                        };
                        useAbs = true;
                        break;
                    case "Roberts2":
                        kernel = new float[][] {
                            {0, 0, 0},
                            {0, -1, 0},
                            {0, 0, 1}
                        };
                        useAbs = true;
                        break;
                    case "Sobel X":
                        kernel = new float[][] {
                            {-1, 0, 1},
                            {-2, 0, 2},
                            {-1, 0, 1}
                        };
                        useAbs = true;
                        break;
                    case "Sobel Y":
                        kernel = new float[][] {
                            {-1, -2, -1},
                            {0, 0, 0},
                            {1, 2, 1}
                        };
                        useAbs = true;
                        break;
                    case "Gaussian 5x5":
                        kernel = new float[][] {
                            {1/273f, 4/273f, 7/273f, 4/273f, 1/273f},
                            {4/273f, 16/273f,26/273f,16/273f,4/273f},
                            {7/273f,26/273f,41/273f,26/273f,7/273f},
                            {4/273f,16/273f,26/273f,16/273f,4/273f},
                            {1/273f, 4/273f, 7/273f, 4/273f, 1/273f}
                        };
                        break;
                    case "Laplacian of Gaussian 5x5":
                        kernel = new float[][] {
                            {0, 0, -1, 0, 0},
                            {0, -1, -2, -1, 0},
                            {-1, -2, 16, -2, -1},
                            {0, -1, -2, -1, 0},
                            {0, 0, -1, 0, 0}
                        };
                        useAbs = true;
                        break;
                }
                if (kernel != null) {
                    // Prompt user: Do you want to normalize (rescale) the output or just clamp?
                    int rescaleChoice = JOptionPane.showConfirmDialog(
                        this, 
                        "Rescale convolution to [0–255]? (Yes = normalize and clamp, No = clamp only)", 
                        "Rescale Output?", 
                        JOptionPane.YES_NO_OPTION
                    );
                    boolean normalize = (rescaleChoice == JOptionPane.YES_OPTION);
                    
                    backupForUndo();
                    final float[][] kernelFinal = kernel;
                    final boolean useAbsFinal = useAbs;
                    processedImage = (roi != null)
                        ? applyOnROI(processedImage, (img) -> applyConvolution(img, kernelFinal, useAbsFinal, normalize))
                        : applyConvolution(processedImage, kernel, useAbs, normalize);
                    repaint();
                    log("Applied convolution with" + selection + " kernel." + (normalize ? " Rescaled output." : " Clamped output."));
                }
            }
        }
        // ----- Lab7 Operations -----
        else if (cmd.equals("Salt-and-Pepper Noise")) {
            String input = JOptionPane.showInputDialog(this, "Enter noise probability (0-1):", "0.05");
            if (input != null) {
                try {
                    double noiseProb = Double.parseDouble(input);
                    backupForUndo();
                    processedImage = (roi != null)
                    ? applyOnROI(processedImage, (img) -> addSaltAndPepperNoise(img, noiseProb))
                    : addSaltAndPepperNoise(processedImage, noiseProb);
                    repaint();
                    log("Applied salt-and-pepper noise with probability " + noiseProb);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid noise probability.");
                }
            }
        } else if (cmd.equals("Min Filter")) {
            String input = JOptionPane.showInputDialog(this, "Enter filter size (odd integer):", "3");
            if (input != null) {
                try {
                    int size = Integer.parseInt(input);
                    backupForUndo();
                    processedImage = (roi != null)
                    ? applyOnROI(processedImage, (img) -> applyMinFilter(img, size))
                    : applyMinFilter(processedImage, size);
                    repaint();
                    log("Applied min filter with size " + size);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid filter size.");
                }
            }
        } else if (cmd.equals("Max Filter")) {
            String input = JOptionPane.showInputDialog(this, "Enter filter size (odd integer):", "3");
            if (input != null) {
                try {
                    int size = Integer.parseInt(input);
                    backupForUndo();
                    processedImage = (roi != null)
                    ? applyOnROI(processedImage, (img) -> applyMaxFilter(img, size))
                    : applyMaxFilter(processedImage, size);
                    repaint();
                    log("Applied max filter with size " + size);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid filter size.");
                }
            }
        } else if (cmd.equals("Midpoint Filter")) {
            String input = JOptionPane.showInputDialog(this, "Enter filter size (odd integer):", "3");
            if (input != null) {
                try {
                    int size = Integer.parseInt(input);
                    backupForUndo();
                    processedImage = (roi != null)
                    ? applyOnROI(processedImage, (img) -> applyMidpointFilter(img, size))
                    : applyMidpointFilter(processedImage, size);
                    repaint();
                    log("Applied midpoint filter with size " + size);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid filter size.");
                }
            }
        } else if (cmd.equals("Median Filter")) {
            String input = JOptionPane.showInputDialog(this, "Enter filter size (odd integer):", "3");
            if (input != null) {
                try {
                    int size = Integer.parseInt(input);
                    backupForUndo();
                    processedImage = (roi != null)
                    ? applyOnROI(processedImage, (img) -> applyMedianFilter(img, size))
                    : applyMedianFilter(processedImage, size);
                    repaint();
                    log("Applied median filter with size " + size);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid filter size.");
                }
            }
        }
        // Lab8 Operations: Thresholding
        else if (cmd.equals("Mean & Std")) {
            BufferedImage target = (roi != null) 
            ? processedImage.getSubimage(roi.x, roi.y, roi.width, roi.height)
            : processedImage;
            double[] ms = computeMeanStd(target);
            log("Mean: " + ms[0] + ", Std Dev: " + ms[1]);
            JOptionPane.showMessageDialog(this, "Mean: " + ms[0] + "\nStd Dev: " + ms[1]);
        } else if (cmd.equals("Simple Threshold")) {
            String input = JOptionPane.showInputDialog(this, "Enter threshold (0-255):", "128");
            if (input != null) {
                try {
                    int thresh = Integer.parseInt(input);
                    backupForUndo();
                    processedImage = (roi != null)
                    ? applyOnROI(processedImage, (img) -> simpleThreshold(img, thresh))
                    : simpleThreshold(processedImage, thresh);
                    repaint();
                    log("Applied simple threshold with value " + thresh);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid threshold value.");
                }
            }
        } else if (cmd.equals("Automated Threshold")) {
            backupForUndo();
            int T = automatedThreshold(processedImage);
            JOptionPane.showMessageDialog(this, "Automated threshold: " + T);
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> simpleThreshold(img, T))
            : simpleThreshold(processedImage, T);
            repaint();
            log("Applied automated threshold with value " + T);
        }
        // Lab5 Operations: Histogram & Histogram Equalisation
        else if (cmd.equals("Histogram Equalisation")) {
            backupForUndo();
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> histogramEqualisation(img))
            : histogramEqualisation(processedImage);
            repaint();
            log("Applied histogram equalisation.");
        } else if (cmd.equals("Display Histogram")) {
            BufferedImage target = (roi != null) 
            ? processedImage.getSubimage(roi.x, roi.y, roi.width, roi.height)
            : processedImage;
            BufferedImage histImg = displayHistogram(target);
            JFrame histFrame = new JFrame("Histogram");
            histFrame.getContentPane().add(new JLabel(new ImageIcon(histImg)));
            histFrame.pack();
            histFrame.setVisible(true);
        }
        else if (cmd.equals("Negative")) {
            backupForUndo();
            // If ROI is selected, apply function only to ROI.
            processedImage = (roi != null) ? applyOnROI(processedImage, (img) -> applyNegative(img)) : applyNegative(processedImage);
            repaint();
            log("Applied negative operation.");
        }else if (cmd.equals("Load Second Image")) {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try {
                    BufferedImage img = null;
                    if (file.getName().toLowerCase().endsWith(".raw")) {
                        String wStr = JOptionPane.showInputDialog(this, "Enter width for RAW image (leave blank to guess):", "");
                        String hStr = JOptionPane.showInputDialog(this, "Enter height for RAW image (leave blank to guess):", "");
                        int w, h;
                        if (wStr.trim().isEmpty() || hStr.trim().isEmpty()) {
                            Dimension d = guessDimensions(file);
                            w = d.width;
                            h = d.height;
                            log("Guessed RAW dimensions: " + w + " x " + h);
                        } else {
                            w = Integer.parseInt(wStr);
                            h = Integer.parseInt(hStr);
                        }
                        img = readRawImage(file, w, h);
                    } else {
                        img = ImageIO.read(file);
                        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
                            BufferedImage temp = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                            Graphics g = temp.getGraphics();
                            g.drawImage(img, 0, 0, null);
                            g.dispose();
                            img = temp;
                        }
                    }
                    // Resize second image as well.
                    img = resizeImage(img);
                    secondImage = img;
                    repaint();
                    log("Loaded second image.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error loading image: " + ex.getMessage());
                }
            }
        }
    }
    // ------------------- Main Method -------------------
    public static void main(String[] args) {
        // Create a log area.
        JTextArea logArea = new JTextArea(5, 40);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        
        // Prompt for the original image.
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
            System.exit(0);
        File selectedFile = fileChooser.getSelectedFile();
        
        Demo demo = new Demo(selectedFile, logArea);
        JFrame frame = new JFrame("Image Processing Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(demo, BorderLayout.CENTER);
        
        // Build the menu bar.
        JMenuBar menuBar = new JMenuBar();
        
        // File menu.
        JMenu fileMenu = new JMenu("File");
        String[] fileOps = {"Open Original", "Load Second Image", "Save"};
        for (String op : fileOps) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            fileMenu.add(item);
        }
        menuBar.add(fileMenu);

        // Edit menu.
        JMenu editMenu = new JMenu("Edit");
        String[] editOps = {"Undo", "Select ROI", "Clear ROI"};
        for (String op : editOps) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            editMenu.add(item);
        }
        menuBar.add(editMenu);

        // Lab1 & Lab2 Operations.
        JMenu lab1and2Menu = new JMenu("Lab 1&2 Operations");
        String[] ops = {"Original", "Negative", "Rescale", "Shift", "Shift+Rescale"};
        for (String op : ops) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            lab1and2Menu.add(item);
        }
        menuBar.add(lab1and2Menu);
        
        // Lab3 Operations.
        JMenu lab3Menu = new JMenu("Lab3 Operations");
        String[] lab3Ops = {"Arithmetic Add", "Arithmetic Subtract", "Arithmetic Multiply", "Arithmetic Divide",
                            "Bitwise NOT", "Bitwise AND", "Bitwise OR", "Bitwise XOR"};
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
        
        // Lab5 Operations.
        JMenu lab5Menu = new JMenu("Lab5 Operations");
        String[] lab5Ops = {"Histogram Equalisation", "Display Histogram"};
        for (String op : lab5Ops) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            lab5Menu.add(item);
        }
        menuBar.add(lab5Menu);
        
        // Lab6 Operations.
        JMenu lab6Menu = new JMenu("Lab6 Operations");
        JMenuItem convItem = new JMenuItem("Convolution");
        convItem.setActionCommand("Convolution");
        convItem.addActionListener(demo);
        lab6Menu.add(convItem);
        menuBar.add(lab6Menu);
        
        // Lab7 Operations.
        JMenu lab7Menu = new JMenu("Lab7 Operations");
        String[] lab7Ops = {"Salt-and-Pepper Noise", "Min Filter", "Max Filter", "Midpoint Filter", "Median Filter"};
        for (String op : lab7Ops) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            lab7Menu.add(item);
        }
        menuBar.add(lab7Menu);
        
        // Lab8 Operations.
        JMenu lab8Menu = new JMenu("Lab8 Operations");
        String[] lab8Ops = {"Mean & Std", "Simple Threshold", "Automated Threshold"};
        for (String op : lab8Ops) {
            JMenuItem item = new JMenuItem(op);
            item.setActionCommand(op);
            item.addActionListener(demo);
            lab8Menu.add(item);
        }
        menuBar.add(lab8Menu);
        
        // Build the main frame.
        // JFrame frame = new JFrame("Image Processing Demo (3-Image + Log)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Use a BorderLayout: center for images, south for log.
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(demo, BorderLayout.CENTER);
        mainPanel.add(logScroll, BorderLayout.SOUTH);
        frame.setJMenuBar(menuBar);
        frame.getContentPane().add(mainPanel);
        frame.pack();
        frame.setVisible(true);
    }
}