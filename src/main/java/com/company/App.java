package com.company;

import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriteParam;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageWriter;

public class App {

    public static void compressTiffInPlace(String filePath) throws IOException {
        // Read the original TIFF file
        File originalFile = new File(filePath);
        BufferedImage image = ImageIO.read(originalFile);

        // Convert to black-and-white (1-bit) image
        

        // Get an ImageWriter for the TIFF format
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No TIFF ImageWriter found!");
        }
        ImageWriter writer = writers.next();

        // Configure the compression parameters for Group 4
        TIFFImageWriteParam writeParam = (TIFFImageWriteParam) writer.getDefaultWriteParam();
        writeParam.setCompressionMode(TIFFImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionType("CCITT T.6"); // Group 4 compression

        // Create a temporary file for the compressed image
        File tempFile = File.createTempFile("compressed", ".tiff");
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(tempFile)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), writeParam);
        }

        // Clean up the writer
        writer.dispose();

        // Delete the original file and rename the compressed file to the original file name
        if (originalFile.delete()) {
            if (!tempFile.renameTo(originalFile)) {
                throw new IOException("Failed to rename temporary file to the original file name.");
            }
            System.out.println("Compressed: " + filePath);
        } else {
            throw new IOException("Failed to delete the original file.");
        }
    }


    // Recursively compress all TIFF files in the given directory and subdirectories
    public static void compressTiffFilesInDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Recurse into subdirectories
                    compressTiffFilesInDirectory(file);
                } else if (file.getName().toLowerCase().endsWith(".tif") || file.getName().toLowerCase().endsWith(".tiff")) {
                    // Compress TIFF file
                    try {
                        compressTiffInPlace(file.getAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("Error compressing file: " + file.getAbsolutePath());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            String rootDirectoryPath = "E:\\test\\S231123"; // Change to your root directory
            File rootDirectory = new File(rootDirectoryPath);
            if (rootDirectory.exists() && rootDirectory.isDirectory()) {
                compressTiffFilesInDirectory(rootDirectory);
                System.out.println("Compression of all TIFF files completed!");
            } else {
                System.out.println("The provided path is not a valid directory.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
