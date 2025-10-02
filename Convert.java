import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

public class Convert {
    public static void main(String[] args) {
        // Ensure input/output directories exist
        createDirectoryIfNotExists("original");
        createDirectoryIfNotExists("input");
        createDirectoryIfNotExists("output");

        // Convert own images into a .bmp
        saveAsBmp("original\\test1.jpg", "input\\test1.bmp");
        saveAsBmp(".\\original/test2.jpg", ".\\input\\test2.bmp");
        saveAsBmp(".\\original/test3.jpg", ".\\input\\test3.bmp");

        // Run JPEG compression on bitmap images
        runCompression("input\\test1.bmp", "output\\test1.jpg", 20, JpegEncoder.Subsampling.YUV_422);
        runCompression("input\\test2.bmp", "output\\test2.jpg", 20, JpegEncoder.Subsampling.YUV_422);
        runCompression("input\\test3.bmp", "output\\test3.jpg", 20, JpegEncoder.Subsampling.YUV_422);
    }

    public static void runCompression(String uncompressedFilename, String comprFilename, int quality, JpegEncoder.Subsampling subsampling) {
        // quality: variable between 1-100. The smaller (= worse) the quality, the greater the compression
        // subsampling: sampling factor. options: YUV_420, YUV_422, YUV_444 (also default)
        System.out.println("-------------------------------------------------------------------");
        System.out.println("Compressing file: " + uncompressedFilename + ", Saving into file: " + comprFilename);
        try {
            File output = new File(comprFilename);
            BufferedOutputStream bufStream = new BufferedOutputStream(new FileOutputStream(output));

            // load in an image to be compressed
            Image uncompressed = ImageIO.read(new File(uncompressedFilename));
            JpegEncoder coder = new JpegEncoder(uncompressed, quality, bufStream, subsampling);

            // start clock
            Instant start = Instant.now();

            // try to compress image
            coder.compress();

            // end clock
            Instant end = Instant.now();

            // calculate time it takes to compress the image
            Duration timeTaken = Duration.between(start, end);

            Path uncomprPath = Paths.get(uncompressedFilename);
            Path comprPath = Paths.get(comprFilename);
            System.out.println("Time taken to compress image: " + timeTaken.toMillis() + " ms");
            System.out.println("Uncompressed file size: " + Files.size(uncomprPath) + " bytes");
            System.out.println("Compressed file size: " + Files.size(comprPath) + " bytes");
            System.out.println("Ratio of compression: " + ((1.0 * Files.size(uncomprPath)) / Files.size(comprPath)));

        } catch (IOException err) {
            System.err.println(err);
        }
    }

    /*
     * Takes in filename, loads in file and saves file as a BMP image with
     * newFilename
     * Used to obtain BMP images from online JPEG images
     */
    public static void saveAsBmp(String filename, String newFilename) {
        // load in image
        BufferedImage bmpImg = null;
        try {
            File file = new File(filename);
            if (!file.exists()) {
                System.err.println("Could not find image: " + filename);
                return;
            }
            bmpImg = ImageIO.read(file);
        } catch (IOException e) {
            System.err.println("Could not read in image: " + filename);
            e.printStackTrace();
            return;
        }

        // save image as BMP
        try {
            File output = new File(newFilename);
            ImageIO.write(bmpImg, "bmp", output);
        } catch (IOException err) {
            System.out.println("Error in saving image");
        }
    }

    // Create directory if missing
    private static void createDirectoryIfNotExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("Created directory: " + dirPath);
            } else {
                System.err.println("Failed to create directory: " + dirPath);
            }
        }
    }
}
