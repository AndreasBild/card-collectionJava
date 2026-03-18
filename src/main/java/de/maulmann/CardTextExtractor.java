package de.maulmann;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CardTextExtractor {

    private static final String CACHE_DIR = "content/ocr-cache";
    private static final Region REGION = Region.EU_CENTRAL_1; // Ensure this matches your AWS Region

    public static String getBackText(String imagePath) {
        File imageFile = new File(imagePath);

        // If the image doesn't exist locally yet, we can't extract text
        if (!imageFile.exists()) {
            return "";
        }

        // Create a cache filename based on the image name (e.g., card-back.webp -> card-back.txt)
        String cacheFileName = imageFile.getName().replace(".webp", ".txt").replace(".jpg", ".txt");
        Path cacheFilePath = Paths.get(CACHE_DIR, cacheFileName);

        // --- CACHE CHECK: If we already extracted this card, return the saved text instantly ---
        if (Files.exists(cacheFilePath)) {
            try {
                return Files.readString(cacheFilePath, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("Failed to read OCR cache for " + imageFile.getName());
            }
        }

        // --- OCR PROCESSING: If it's a new card, send it to AWS Textract ---
        System.out.println("    -> Running AI Text Extraction on new image: " + imageFile.getName());
        String extractedText = extractTextWithAWS(imageFile);

        // Save the result to the cache folder so we NEVER have to process it again
        if (!extractedText.isEmpty()) {
            try {
                File cacheDirFile = new File(CACHE_DIR);
                if (!cacheDirFile.exists()) cacheDirFile.mkdirs();
                Files.writeString(cacheFilePath, extractedText, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("Failed to save OCR cache for " + imageFile.getName());
            }
        }

        return extractedText;
    }

    private static String extractTextWithAWS(File imageFile) {
        StringBuilder result = new StringBuilder();

        try (TextractClient textractClient = TextractClient.builder()
                .region(REGION)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            Document document = Document.builder()
                    .bytes(SdkBytes.fromByteArray(imageBytes))
                    .build();

            DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                    .document(document)
                    .build();

            DetectDocumentTextResponse response = textractClient.detectDocumentText(request);

            // Textract returns lines and words. We just want the consolidated lines of text.
            for (Block block : response.blocks()) {
                if (block.blockType() == BlockType.LINE) {
                    result.append(block.text()).append("\n");
                }
            }

        } catch (Exception e) {
            System.err.println("    -> AWS Textract failed for " + imageFile.getName() + ": " + e.getMessage());
        }

        return result.toString().trim();
    }
}