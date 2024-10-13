package com.company;

import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageWriteParam;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class App {

   
	public static void compressTiffInPlace(String filePath) throws IOException {
	    File originalFile = new File(filePath);
	    BufferedImage image;
	    IIOMetadata imageMetadata;

	    // Read the image and its metadata
	    try (ImageInputStream inputStream = ImageIO.createImageInputStream(originalFile)) {
	        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("tiff");
	        if (!readers.hasNext()) {
	            throw new IllegalStateException("No TIFF ImageReader found!");
	        }
	        ImageReader reader = readers.next();
	        reader.setInput(inputStream, true);
	        
	        ImageReadParam param = reader.getDefaultReadParam();
	        image = reader.read(0, param);
	        
	        // Get the image metadata
	        imageMetadata = reader.getImageMetadata(0);
	        reader.dispose();
	    }
	    if (image.getColorModel().getPixelSize() != 1) {
	        System.out.println("Skipping file (not 1-bit): " + filePath);
	        return; // Skip files that are not 1-bit
	    }
	    // Create a new writer for the compressed image
	    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
	    if (!writers.hasNext()) {
	        throw new IllegalStateException("No TIFF ImageWriter found!");
	    }
	    ImageWriter writer = writers.next();

	    TIFFImageWriteParam writeParam = (TIFFImageWriteParam) writer.getDefaultWriteParam();
	    writeParam.setCompressionMode(TIFFImageWriteParam.MODE_EXPLICIT);
	    writeParam.setCompressionType("CCITT T.6");

	    // Transfer metadata (including DPI) to the new file
	    File tempFile = File.createTempFile("compressed", ".tiff");
	    try (ImageOutputStream ios = ImageIO.createImageOutputStream(tempFile)) {
	        writer.setOutput(ios);

	        // Write the compressed image with the original metadata
	        writer.write(null, new javax.imageio.IIOImage(image, null, imageMetadata), writeParam);
	    }

	    writer.dispose();

	    // Replace the original file with the compressed file
	    if (originalFile.delete()) {
	        if (!tempFile.renameTo(originalFile)) {
	            throw new IOException("Failed to rename temporary file to the original file name.");
	        }
	        System.out.println("Compressed successffully " + filePath);
	    } else {
	        throw new IOException("Failed to delete the original file.");
	    }
	}
    // Recursively compress TIFF files in the provided directory
    public static void compressTiffFilesInDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    compressTiffFilesInDirectory(file); // Go deeper into the subdirectories
                } else if (file.getName().toLowerCase().endsWith(".tif") || file.getName().toLowerCase().endsWith(".tiff")) {
                    try {
                        compressTiffInPlace(file.getAbsolutePath()); // Compress the TIFF file
                    } catch (IOException e) {
                        System.err.println("Error compressing file: " + file.getAbsolutePath());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // Compress a directory (after TIFF compression) into a ZIP file
    public static void compressDirectoryToZip(File dir) throws IOException {
        String zipFileName = dir.getAbsolutePath() + ".zip";
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFileName))) {
            Path dirPath = dir.toPath();
            Files.walk(dirPath).forEach(path -> {
                File file = path.toFile();
                try {
                    if (file.isFile()) {
                        String zipEntryName = dirPath.relativize(path).toString();
                        zos.putNextEntry(new ZipEntry(zipEntryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                } catch (IOException e) {
                    System.err.println("Error adding file to ZIP: " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            });
        }
        System.out.println("Directory compressed to ZIP: " + zipFileName);
    }

    // Delete a directory and all of its contents
    public static void deleteDirectory(File dir) throws IOException {
        Path dirPath = dir.toPath();
        Files.walk(dirPath)
                .sorted(Comparator.reverseOrder()) // Ensure files and subdirectories are deleted first
                .map(Path::toFile)
                .forEach(File::delete);
        System.out.println("Deleted original directory: " + dir.getAbsolutePath());
    }

    // Process all directories starting with 'S', then '2', and finally 'BCN'
    public static void compressTiffAndThenZipDirectories(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && file.getName().startsWith("S")) {
                    File[] subFiles = file.listFiles();
                    if (subFiles != null) {
                        for (File subFile : subFiles) {
                            if (subFile.isDirectory() && subFile.getName().startsWith("2")) {
                                // Compress all TIFF files in the "2" subdirectories (including the BCN subdirectories)
                                compressTiffFilesInDirectory(subFile);

                                // After compressing TIFFs, compress the "2" directory itself into a ZIP file
                                try {
                                    compressDirectoryToZip(subFile);

                                    // After compression, delete the original "2" directory
                                    deleteDirectory(subFile);
                                } catch (IOException e) {
                                    System.err.println("Error compressing or deleting directory: " + subFile.getAbsolutePath());
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Read the root directory path from an XML configuration file
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

    // Main method to drive the compression process
    public static void main(String[] args) {
        try {
            String xmlFilePath = "config.xml"; // Path to your XML config file
            String rootDirectoryPath = readPathFromXml(xmlFilePath);
            if (rootDirectoryPath != null) {
                File rootDirectory = new File(rootDirectoryPath);
                if (rootDirectory.exists() && rootDirectory.isDirectory()) {
                    compressTiffAndThenZipDirectories(rootDirectory);
                    System.out.println("Compression and deletion of original directories completed!");
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
