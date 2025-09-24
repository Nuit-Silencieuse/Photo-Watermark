package com.watermarker;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import picocli.CommandLine;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "watermarker", mixinStandardHelpOptions = true, version = "Photo Watermarker 1.0",
        description = "Adds a date watermark to photos based on EXIF data.")
public class Main implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "The path to the directory containing images.")
    private File inputDirectory;

    @CommandLine.Option(names = {"-s", "--font-size"}, description = "Font size of the watermark.", defaultValue = "36")
    private int fontSize;

    @CommandLine.Option(names = {"-c", "--color"}, description = "Color of the watermark (e.g., 'WHITE', 'RED', or hex #RRGGBB).", defaultValue = "WHITE")
    private String color;

    @CommandLine.Option(names = {"-p", "--position"}, description = "Position of the watermark (TOP_LEFT, CENTER, BOTTOM_RIGHT).", defaultValue = "BOTTOM_RIGHT")
    private Position position;

    private enum Position {
        TOP_LEFT,
        CENTER,
        BOTTOM_RIGHT
    }

    @Override
    public Integer call() throws Exception {
        if (!inputDirectory.isDirectory()) {
            System.err.println("Error: Provided path is not a directory.");
            return 1;
        }

        File outputDirectory = new File(inputDirectory, inputDirectory.getName() + "_watermark");
        if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdirs()) {
                System.err.println("Error: Could not create output directory.");
                return 1;
            }
        }

        System.out.println("Processing files in: " + inputDirectory.getAbsolutePath());
        System.out.println("Saving watermarked files to: " + outputDirectory.getAbsolutePath());

        File[] files = inputDirectory.listFiles();
        if (files == null) {
            System.err.println("Error: Could not list files in the directory.");
            return 1;
        }

        for (File file : files) {
            if (file.isFile()) {
                try {
                    // 1. Read EXIF metadata to get the shooting date
                    Metadata metadata = ImageMetadataReader.readMetadata(file);
                    ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                    Date date = directory != null ? directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL) : null;

                    if (date == null) {
                        System.out.println("Skipping (no date info): " + file.getName());
                        continue;
                    }
                    String watermarkText = new SimpleDateFormat("yyyy-MM-dd").format(date);

                    // 2. Read the image
                    BufferedImage image = ImageIO.read(file);
                    if (image == null) {
                        System.out.println("Skipping (not a supported image): " + file.getName());
                        continue;
                    }
                    Graphics2D g2d = image.createGraphics();

                    // 3. Configure graphics for watermarking
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
                    g2d.setColor(parseColor(color));

                    // 4. Calculate position and draw the watermark
                    FontMetrics fm = g2d.getFontMetrics();
                    int textWidth = fm.stringWidth(watermarkText);
                    int textHeight = fm.getAscent();

                    int x, y;
                    switch (position) {
                        case TOP_LEFT:
                            x = 20;
                            y = textHeight + 20;
                            break;
                        case CENTER:
                            x = (image.getWidth() - textWidth) / 2;
                            y = (image.getHeight() + textHeight) / 2;
                            break;
                        case BOTTOM_RIGHT:
                        default:
                            x = image.getWidth() - textWidth - 20;
                            y = image.getHeight() - 20;
                            break;
                    }

                    g2d.drawString(watermarkText, x, y);
                    g2d.dispose();

                    // 5. Save the new image
                    String outputFileName = file.getName();
                    String formatName = outputFileName.substring(outputFileName.lastIndexOf(".") + 1);
                    File outputFile = new File(outputDirectory, outputFileName);
                    ImageIO.write(image, formatName, outputFile);

                    System.out.println("Watermarked: " + file.getName());

                } catch (Exception e) {
                    System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        System.out.println("Processing complete.");
        return 0;
    }

    private Color parseColor(String colorStr) {
        try {
            // Try parsing standard color names
            return (Color) Color.class.getField(colorStr.toUpperCase()).get(null);
        } catch (Exception e) {
            // Fallback to decoding hex color
            return Color.decode(colorStr);
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
