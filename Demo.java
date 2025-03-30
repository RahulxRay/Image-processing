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
    
    // Log area (set by the main frame)
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
                // ROI selection only applies in the processedImage area (right panel)
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
                }
            }
        });
    }
    
    // Update ROI based on current mouse point (relative to processed image)
    private void updateROI(Point currentPoint) {
        int newX = Math.min(roiStart.x, currentPoint.x);
        int newY = Math.min(roiStart.y, currentPoint.y);
        int newW = Math.abs(currentPoint.x - roiStart.x);
        int newH = Math.abs(currentPoint.y - roiStart.y);
        roi = new Rectangle(newX, newY, newW, newH);
    }
    
    // Helper: make a deep copy of a BufferedImage.
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
            
            // Create an array to hold convolution results for each channel (r,g,b).
            float[][][] conv = new float[width][height][3]; // channels: 0=r, 1=g, 2=b
            
            // For each pixel and each channel, compute the convolution.
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int c = 1; c <= 3; c++) { // channels 1,2,3 correspond to r,g,b.
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
            
            // Optionally normalize each channel separately.
            if (normalize) {
                float[] minVal = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
                float[] maxVal = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int c = 0; c < 3; c++) {
                            if (conv[x][y][c] < minVal[c]) {
                                minVal[c] = conv[x][y][c];
                            }
                            if (conv[x][y][c] > maxVal[c]) {
                                maxVal[c] = conv[x][y][c];
                            }
                        }
                    }
                }
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        for (int c = 0; c < 3; c++) {
                            float range = maxVal[c] - minVal[c];
                            if (range == 0) {
                                range = 1;
                            }
                            conv[x][y][c] = (conv[x][y][c] - minVal[c]) * 255 / range;
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
        
        private BufferedImage adaptiveThreshold(BufferedImage img, int blockSize, int varianceThreshold) {
            int width = img.getWidth(), height = img.getHeight();
            BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y += blockSize) {
                for (int x = 0; x < width; x += blockSize) {
                    int w = Math.min(blockSize, width - x);
                    int h = Math.min(blockSize, height - y);
                    int[] hist = new int[256];
                    int total = w * h;
                    int sum = 0;
                    for (int j = 0; j < h; j++){
                        for (int i = 0; i < w; i++){
                            int rgb = img.getRGB(x+i, y+j);
                            int r = (rgb >> 16) & 0xff;
                            int g = (rgb >> 8) & 0xff;
                            int b = rgb & 0xff;
                            int gray = (r+g+b)/3;
                            hist[gray]++;
                            sum += gray;
                        }
                    }
                    double mean = sum / (double) total;
                    double var = 0;
                    for (int i = 0; i < 256; i++){
                        var += hist[i] * Math.pow(i - mean, 2);
                    }
                    var /= total;
                    double std = Math.sqrt(var);
                    int T = (int)mean;
                    if (std > varianceThreshold) {
                        int sum1 = 0, sum2 = 0, count1 = 0, count2 = 0;
                        for (int j = 0; j < h; j++){
                            for (int i = 0; i < w; i++){
                                int rgb = img.getRGB(x+i, y+j);
                                int r = (rgb >> 16) & 0xff;
                                int g = (rgb >> 8) & 0xff;
                                int b = rgb & 0xff;
                                int grayVal = (r+g+b)/3;
                                if(grayVal < T) { sum1 += grayVal; count1++; }
                                else { sum2 += grayVal; count2++; }
                            }
                        }
                        if(count1 > 0 && count2 > 0)
                            T = (int)Math.round((sum1/count1 + sum2/count2)/2.0);
                    }
                    for (int j = 0; j < h; j++){
                        for (int i = 0; i < w; i++){
                            int rgb = img.getRGB(x+i, y+j);
                            int r = (rgb >> 16) & 0xff;
                            int g = (rgb >> 8) & 0xff;
                            int b = rgb & 0xff;
                            int grayVal = (r+g+b)/3;
                            int val = (grayVal >= T) ? 255 : 0;
                            int newRgb = (255 << 24) | (val << 16) | (val << 8) | val;
                            out.setRGB(x+i, y+j, newRgb);
                        }
                    }
                }
            }
            return out;
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
    }
    
    // ==================== Painting ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int x = 0;
        // Draw left image (originalImage)
        if (originalImage != null) {
            g.drawImage(originalImage, x, 0, this);
            x += originalImage.getWidth() + gap;
        }
        // Draw middle image (secondImage); if null, draw a placeholder border.
        if (secondImage != null) {
            g.drawImage(secondImage, x, 0, this);
        } else {
            g.setColor(Color.GRAY);
            g.drawRect(x, 0, originalImage.getWidth(), originalImage.getHeight());
            g.drawString("Load second image", x + 10, 20);
        }
        x += getMiddleImageWidth() + gap;
        // Draw right image (processedImage)
        if (processedImage != null) {
            g.drawImage(processedImage, x, 0, this);
        }
        // Draw ROI rectangle (relative to processedImage area)
        if (roi != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.RED);
            int offsetX = getLeftImageWidth() + getMiddleImageWidth() + 2 * gap;
            g2d.drawRect(offsetX + roi.x, roi.y, roi.width, roi.height);
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
                    processedImage = applyArithmeticOperation(processedImage, secondImage, op);
                    repaint();
                }
        } else if (cmd.equals("Bitwise NOT")) {
            backupForUndo();
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> applyBitwiseNot(img))
            : applyBitwiseNot(processedImage);
            repaint();
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
                    processedImage = applyBitwiseOperation(processedImage, secondImage, op);
                    repaint();
                }
        }        // ----- Lab4 Operations -----
        else if (cmd.equals("Point Negative")) {
            backupForUndo();
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> applyPointNegative(img))
            : applyPointNegative(processedImage);
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
                        processedImage = (roi != null)
                        ? applyOnROI(processedImage, (img) -> applyPowerLawTransform(img, p))
                        : applyPowerLawTransform(processedImage, p);
                                            repaint();
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
                    // useAbs = true;
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
                    // Using a 2x2 Roberts cross operator.
                    kernel = new float[][] {
                        {0, 0, 0},
                        {0, 0, -1},
                        {0, 1, 0}
                    };
                    useAbs = true;
                    break;
                    case "Roberts2":
                    // Using a 2x2 Roberts cross operator.
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
                backupForUndo();
                // Always normalize the convolution result.
                processedImage = applyConvolution(processedImage, kernel, useAbs, true);
                repaint();
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
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid filter size.");
                }
            }
        }
        // Lab8 Operations: Thresholding
        else if (cmd.equals("Mean & Std")) {
            double[] ms = computeMeanStd(processedImage);
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
        } else if (cmd.equals("Adaptive Threshold")) {
            String input1 = JOptionPane.showInputDialog(this, "Enter block size (odd integer):", "16");
            String input2 = JOptionPane.showInputDialog(this, "Enter variance threshold:", "100");
            if (input1 != null && input2 != null) {
                try {
                    int blockSize = Integer.parseInt(input1);
                    int varThresh = Integer.parseInt(input2);
                    backupForUndo();
                    processedImage = (roi != null)
                    ? applyOnROI(processedImage, (img) -> adaptiveThreshold(img, blockSize, varThresh))
                    : adaptiveThreshold(processedImage, blockSize, varThresh);
                                    repaint();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input for adaptive threshold.");
                }
            }
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
            BufferedImage histImg = displayHistogram(processedImage);
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
        JMenuItem openOriginalItem = new JMenuItem("Open Original");
        openOriginalItem.setActionCommand("Open Original");
        openOriginalItem.addActionListener(demo);
        fileMenu.add(openOriginalItem);
        JMenuItem loadSecondItem = new JMenuItem("Load Second Image");
        loadSecondItem.setActionCommand("Load Second Image");
        loadSecondItem.addActionListener(demo);
        fileMenu.add(loadSecondItem);
        JMenuItem saveItem = new JMenuItem("Save Processed");
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
        JMenuItem selectROIItem = new JMenuItem("Select ROI");
        selectROIItem.setActionCommand("Select ROI");
        selectROIItem.addActionListener(demo);
        editMenu.add(selectROIItem);
        JMenuItem clearROIItem = new JMenuItem("Clear ROI");
        clearROIItem.setActionCommand("Clear ROI");
        clearROIItem.addActionListener(demo);
        editMenu.add(clearROIItem);
        JMenuItem resetItem = new JMenuItem("Reset");
        resetItem.setActionCommand("Reset");
        resetItem.addActionListener(demo);
        editMenu.add(resetItem);
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
        String[] lab8Ops = {"Mean & Std", "Simple Threshold", "Automated Threshold", "Adaptive Threshold"};
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