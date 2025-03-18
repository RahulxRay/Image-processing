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
    // Original image loaded at startup.
    private BufferedImage originalImage;
    // Processed image (result of operations).
    private BufferedImage processedImage;
    // Undo stack to store previous states.
    private Stack<BufferedImage> undoStack = new Stack<>();
    // Gap (in pixels) between images when displayed.
    private final int gap = 10;
    
    // ROI selection fields.
    private boolean selectingROI = false;
    private Point roiStart = null;
    private Rectangle roi = null; // ROI relative to the processed image area

    // Constructor: load the image from file.
    public Demo(File imageFile) {
        try {
            originalImage = ImageIO.read(imageFile);
            if (originalImage.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage temp = new BufferedImage(
                    originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB
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
        
        // Add mouse listeners for ROI selection.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int offsetX = originalImage.getWidth() + gap;
                // Only allow ROI selection if the click is in the processed image area.
                if (selectingROI && e.getX() >= offsetX) {
                    // Get the point relative to the processed image.
                    roiStart = new Point(e.getX() - offsetX, e.getY());
                    roi = new Rectangle(roiStart);
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (selectingROI && roiStart != null) {
                    int offsetX = originalImage.getWidth() + gap;
                    Point currentPoint = new Point(e.getX() - offsetX, e.getY());
                    updateROI(currentPoint);
                    selectingROI = false;
                    repaint();
                }
            }
        });
        

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (selectingROI && roiStart != null) {
                    int offsetX = originalImage.getWidth() + gap;
                    Point currentPoint = new Point(e.getX() - offsetX, e.getY());
                    updateROI(currentPoint);
                    repaint();
                }
            }
        });
    }
    
    // And update the updateROI method as follows:
    private void updateROI(Point currentPoint) {
        int newX = Math.min(roiStart.x, currentPoint.x);
        int newY = Math.min(roiStart.y, currentPoint.y);
        int newW = Math.abs(currentPoint.x - roiStart.x);
        int newH = Math.abs(currentPoint.y - roiStart.y);
        roi = new Rectangle(newX, newY, newW, newH);
    }

    // Create a deep copy of a BufferedImage.
    private BufferedImage copyImage(BufferedImage img) {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
        Graphics g = copy.getGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return copy;
    }
    
    // Preferred size: wide enough for two images side by side.
    @Override
    public Dimension getPreferredSize() {
        int width = originalImage.getWidth() + gap + processedImage.getWidth();
        int height = Math.max(originalImage.getHeight(), processedImage.getHeight());
        return new Dimension(width, height);
    }
    
    // Clamp a value to [0,255].
    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
    
    // ------------------- Utility Methods -------------------

    // Convert image to 3D array [width][height][4]: [alpha, red, green, blue].
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
        // ... (Other Lab1/Lab2 methods omitted for brevity)
    
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
            
            // Convert the image to grayscale (using simple average)
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
            
            // Convolve: create an array to hold convolution values.
            float[][] conv = new float[width][height];
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    float sum = 0;
                    for (int m = 0; m < kRows; m++){
                        for (int n = 0; n < kCols; n++){
                            int ix = x + n - kCenterX;
                            int iy = y + m - kCenterY;
                            if (ix >= 0 && ix < width && iy >= 0 && iy < height) {
                                sum += kernel[m][n] * gray[ix][iy];
                            }
                        }
                    }
                    conv[x][y] = sum;
                }
            }
            
            // Optionally convert to absolute values.
            if (useAbsolute) {
                for (int y = 0; y < height; y++){
                    for (int x = 0; x < width; x++){
                        conv[x][y] = Math.abs(conv[x][y]);
                    }
                }
            }
            
            // Optionally normalize to [0,255].
            if (normalize) {
                float minVal = Float.MAX_VALUE, maxVal = -Float.MAX_VALUE;
                for (int y = 0; y < height; y++){
                    for (int x = 0; x < width; x++){
                        if (conv[x][y] < minVal) minVal = conv[x][y];
                        if (conv[x][y] > maxVal) maxVal = conv[x][y];
                    }
                }
                float range = maxVal - minVal;
                if (range == 0) range = 1;
                for (int y = 0; y < height; y++){
                    for (int x = 0; x < width; x++){
                        conv[x][y] = (conv[x][y] - minVal) * 255 / range;
                    }
                }
            }
            
            BufferedImage outImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++){
                for (int x = 0; x < width; x++){
                    int val = clamp(Math.round(conv[x][y]));
                    int rgb = (255 << 24) | (val << 16) | (val << 8) | val;
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
    // Apply a processing function only on the ROI (if selected).
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
        // Draw original image on left.
        if (originalImage != null)
            g.drawImage(originalImage, 0, 0, this);
        // Draw processed image on right.
        if (processedImage != null)
            g.drawImage(processedImage, originalImage.getWidth() + gap, 0, this);
        // If ROI is selected, draw a red rectangle on top of the processed image.
        if (roi != null) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.RED);
            int offsetX = originalImage.getWidth() + gap;
            g2d.drawRect(offsetX + roi.x, roi.y, roi.width, roi.height);
        }
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
        // ROI selection commands.
        else if (cmd.equals("Select ROI")) {
            // Enter ROI selection mode.
            selectingROI = true;
            roi = null;  // Clear any previous ROI.
            JOptionPane.showMessageDialog(this, "Click and drag on the processed image to select ROI.");
        } else if (cmd.equals("Clear ROI")) {
            roi = null;
            repaint();
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
            processedImage = (roi != null)
            ? applyOnROI(processedImage, (img) -> applyBitwiseNot(img))
            : applyBitwiseNot(processedImage);
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
                    processedImage = (roi != null)
                        ? applyOnROI(processedImage, (img) -> applyROINegative(img, maskImage))
                        : applyROINegative(processedImage, maskImage);
                    repaint();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }        
        // ----- Lab4 Operations -----
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
                        {0, 1, 0},
                        {1, -4, 1},
                        {0, 1, 0}
                    };
                    useAbs = true;
                    break;
                case "8-Neighbour Laplacian":
                    kernel = new float[][] {
                        {1, 1, 1},
                        {1, -8, 1},
                        {1, 1, 1}
                    };
                    useAbs = true;
                    break;
                case "4-Neighbour Laplacian Enhancement":
                    kernel = new float[][] {
                        {0, 1, 0},
                        {1, 5, 1},
                        {0, 1, 0}
                    };
                    break;
                case "8-Neighbour Laplacian Enhancement":
                    kernel = new float[][] {
                        {1, 1, 1},
                        {1, 9, 1},
                        {1, 1, 1}
                    };
                    break;
                case "Roberts":
                    // Using a 2x2 Roberts cross operator.
                    kernel = new float[][] {
                        {0, 1},
                        {-1, 0}
                    };
                    useAbs = true;
                    break;
                case "Sobel X":
                    kernel = new float[][] {
                        {1, 0, -1},
                        {2, 0, -2},
                        {1, 0, -1}
                    };
                    useAbs = true;
                    break;
                case "Sobel Y":
                    kernel = new float[][] {
                        {1, 2, 1},
                        {0, 0, 0},
                        {-1, -2, -1}
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
                        {0, 0, 1, 0, 0},
                        {0, 1, 2, 1, 0},
                        {1, 2, -16, 2, 1},
                        {0, 1, 2, 1, 0},
                        {0, 0, 1, 0, 0}
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
        }
        // (Other processing functions can be similarly wrapped with applyOnROI.)
        // ... (Other menu commands for Lab3..Lab8 would be here)
    }
    
    // ------------------- Main Method -------------------
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
        JMenuItem selectROIItem = new JMenuItem("Select ROI");
        selectROIItem.setActionCommand("Select ROI");
        selectROIItem.addActionListener(demo);
        editMenu.add(selectROIItem);
        JMenuItem clearROIItem = new JMenuItem("Clear ROI");
        clearROIItem.setActionCommand("Clear ROI");
        clearROIItem.addActionListener(demo);
        editMenu.add(clearROIItem);
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
        
        frame.setJMenuBar(menuBar);
        frame.pack();
        frame.setVisible(true);
    }
}
