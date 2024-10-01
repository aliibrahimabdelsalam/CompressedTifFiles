package com.company;

import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriteParam;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageWriter;
import org.w3c.dom.Document; // Correct import
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class App {

    public static void compressTiffInPlace(String filePath) throws IOException {
        // Read the original TIFF file
        File originalFile = new File(filePath);
        BufferedImage image = ImageIO.read(originalFile);
        
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

    // Method to read the directory path from the XML file
    public static String readPathFromXml(String xmlFilePath) {
        String path = null;
        try {
            File xmlFile = new File(xmlFilePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("rootDirectoryPath");
            if (nodeList.getLength() > 0) {
                path = nodeList.item(0).getTextContent();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

    public static void main(String[] args) {
        try {
            String xmlFilePath = "config.xml"; // Path to your XML config file in the same directory
            String rootDirectoryPath = readPathFromXml(xmlFilePath);
            if (rootDirectoryPath != null) {
                File rootDirectory = new File(rootDirectoryPath); // Create a File object from the path
                if (rootDirectory.exists() && rootDirectory.isDirectory()) {
                    compressTiffFilesInDirectory(rootDirectory); // Pass File object
                    System.out.println("Compression of all TIFF files completed!");
                } else {
                    System.out.println("The provided path is not a valid directory.");
                }
            } else {
                System.out.println("Failed to read directory path from XML.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
