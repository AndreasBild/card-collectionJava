package de.maulmann;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class FileGenerator {


    // constants: base paths for input and output
    private static final String pathSource = "../card-CollectionJava/content/";
    private static final String pathOutput = "../card-CollectionJava/output/";
    private static final String generatedFileLocation = pathOutput + "index.html";
    private static final String[] nameOfInputFile = getFileNamesFromDirectory();

    // constants for static page parts
    public static final String footer = """
            <h3>Other Collections</h3>
             <ul class="actions-list">
                <li>
                 <a href="index.html" title="Juwan Howard Cards Collection" class="modern-button">
                   Juwan Howard Basketball Cards Collection</a>
                </li>
                <li>
                <a href=Wantlist.html title="Juwan Howard Collection Wantlist" class="modern-button">
                Juwan Howard Basketball Cards Wantlist</a>
                </li>
                <li>
                <a href=Baseball.html title="Upper Deck Baseball Cards" class="modern-button">
                Upper Deck Baseball Cards</a>
                </li>
                <li>
                <a href=Flawless.html title="2008 Upper Deck Flawless Basketball" class="modern-button">
                2008 Upper Deck Flawless Basketball</a></li>
                <li>
                <a href=Panini.html title="2012-13 Panini Flawless Basketball"class="modern-button">
                2012-13 Panini Flawless Basketball</a></li>
                </ul>
            """;
    private static final String templateBegin =
            """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                    <script async src="https://www.googletagmanager.com/gtag/js?id=G-535TKYRZTR"></script>
                                    <script>
                                      window.dataLayer = window.dataLayer || [];
                                      function gtag(){dataLayer.push(arguments);}
                                      gtag('js', new Date());
                                      gtag('config', 'G-535TKYRZTR');
                                    </script>
                          <meta name="google-site-verification" content="Ev1ZxTPJs2GMFNQ6FyItlCYAKUWscL3jDFS_mVXH6IQ">
                          <title>Juwan Howard Basketball Trading Card Collection</title>
                          <meta charset="UTF-8">
                          <meta http-equiv="X-UA-Compatible" content="IE=edge">
                          <meta name="theme-color" content="#317EFB">
                          <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
                          <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
                          <link rel="icon" type="image/png" sizes="194x194" href="/favicon-194x194.png">
                          <link rel="icon" type="image/png" sizes="192x192" href="/android-chrome-192x192.png">
                          <link rel="icon" type="image/png" sizes="16x16" href="/favicon-16x16.png">
                          <link rel="manifest" href="/site.webmanifest">
                          <link rel="mask-icon" href="/safari-pinned-tab.svg" color="#317EFB">
                          <meta name="apple-mobile-web-app-title" content="Maulmann.de">
                          <meta name="application-name" content="Maulmann.de">
                          <meta name="msapplication-TileColor" content="#317EFB">
                          <link href="/favicon.ico" rel="icon"  sizes="32x32">
                          <link href="/apple-touch-icon.png" rel="apple-touch-icon">
                          <meta name="description" content="Private Collection of Juwan Howard Basketball Trading Cards. Containing many 1/1 and rare Trading Cards from companies like: Panini, Fleer, Topps and Upper Deck. Including Super rare cards like Precious Metal Gems from the 90s">
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                          <link href="../css/main.css" rel="stylesheet" type="text/css">
                          <meta name="robots" content="index,follow">
                    </head>
                    <body>
                    <h1><a id="top" title='Top of the list'>List of Juwan Howard Basketball Trading Cards</a></h1>
                    """;

    private static final String tableHead = "<table>";
    private static final String templateEnd = footer + "List Created: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()) + "</body></html>";


    public static void main(String[] args) throws IOException {

        //formatFile();

        // create a new file or use existing file with the same name
        createTargetFile(generatedFileLocation);
        // add document header as first part of the file content
        addTemplateComponent(templateBegin, false);

        int counterAll = 0;
        for (final String fileName : nameOfInputFile) {
            System.out.println("Filename: " + fileName);
            // iterate over all file names in the given directory
            final String sourceFile = pathSource + fileName + ".html";
            System.out.println("Source: " + sourceFile);
            counterAll = appendFileContent(sourceFile, fileName, counterAll);

        }
        addTemplateComponent(templateEnd, true);
    }

    /**
     * creates an internal anchor in the
     *
     * @return String an anchor element with all internal anchors
     */
    private static String createAnchorList() {

        final StringBuilder internalAnchorList = new StringBuilder();

        for (final String fileName : nameOfInputFile) {
            internalAnchorList.append(" | <a href=").append('#').append(fileName).append(" title='Juwan Howard Trading Cards from Season: ").append(fileName).append("'>").append(fileName).append("</a> ");
        }
        internalAnchorList.append(" |");


        return internalAnchorList.toString();

    }

    /**
     * formats a list of files in a given directory by removing whitespaces.
     * Writes the formatted files in a separate directory
     */

    private static void formatFile() {

        final File folder = new File(pathSource);
        final File[] listOfFilesInDirectory = folder.listFiles();

        assert listOfFilesInDirectory != null;
        Arrays.sort(listOfFilesInDirectory);
        for (File aListOfFilesInDirectory : listOfFilesInDirectory) {
            if (aListOfFilesInDirectory.isFile()) {
                final String fileName = aListOfFilesInDirectory.getName();
                System.out.println("FileName: " + fileName);
                try {
                    createTargetFile(pathOutput + fileName);
                    formatFileContent(pathSource + fileName, pathOutput + fileName);
                } catch (IOException e) {
                    String message = e.getCause().getMessage();
                    System.out.println(message);
                }


            }
        }

    }


    /**
     * @return array of strings containing the names of files/subdirectories in the given base path.
     */
    private static String[] getFileNamesFromDirectory() {
        File directory = new File("./");
        System.out.println("################ AbsolutePath: " + directory.getAbsolutePath());

        final File folder = new File(pathSource);
        final File[] listOfFilesInDirectory = folder.listFiles();


        final List<String> result = new ArrayList<>() {
        };

        assert listOfFilesInDirectory != null;
        Arrays.sort(listOfFilesInDirectory);
        for (File aListOfFilesInDirectory : listOfFilesInDirectory) {
            if (aListOfFilesInDirectory.isFile()) {
                System.out.println("File in Directory : " + aListOfFilesInDirectory.getName().substring(0, aListOfFilesInDirectory.getName().lastIndexOf('.')));
                result.add(aListOfFilesInDirectory.getName().substring(0, aListOfFilesInDirectory.getName().lastIndexOf('.')));
            } else if (aListOfFilesInDirectory.isDirectory()) {
                System.out.println("Subdirectory in Directory: " + aListOfFilesInDirectory.getName());
            }
        }
        System.out.println("result " + result.size());
        return result.toArray(new String[0]);
    }


    /**
     * Method to create ar re-use a file under the given target location (path in the files System)
     *
     * @throws IOException Java IO Exception
     */
    private static void createTargetFile(String fileName) throws IOException {
        final File myFile = new File(fileName);

        if (myFile.createNewFile()) {
            System.out.println("A new File under: " + fileName + " was created ");
        } else {
            System.out.println("An existing File under: " + fileName + " was replaced ");
        }
    }

    /**
     * Method for adding content to a given file. First a header with doctype definition is added. In a later step the rest of the content is appended at the end
     *
     * @param templateBegin  String, that contains the basic file header template
     * @param appendAtTheEnd boolean, that indicated if the provided content shall be added at the end or the beginning of the file
     * @throws IOException, thrown if a file operation did not work out as planned
     */
    private static void addTemplateComponent(String templateBegin, boolean appendAtTheEnd) throws IOException {
        PrintWriter printWriter = new PrintWriter(new java.io.FileWriter(FileGenerator.generatedFileLocation, appendAtTheEnd));

        final BufferedWriter out = new BufferedWriter(printWriter);

        out.append(templateBegin);
        if (!appendAtTheEnd) out.append(createAnchorList());
        out.flush();
        out.close();
    }

    private static int appendFileContent(final String source, final String name, int counterIn) throws IOException {
        final String anchorName = "<a title='Juwan Howard Trading Cards for Season " + name + "' id=" + name + ">" + name + "</a>";
        final StringBuffer result = new StringBuffer("<h2>").append(anchorName).append("</h2>").append('\n').append(tableHead);


        try (BufferedReader inputStream = new BufferedReader(new FileReader(source)); PrintWriter outputStream = new PrintWriter(new FileWriter(FileGenerator.generatedFileLocation, true))) {


            final BufferedWriter out = new BufferedWriter(outputStream);


            String line;
            int counterAll = counterIn; // keep track of all rows
            int counter = -1; // needs to be -1 because of the table header, we count only the content rows

            while ((line = inputStream.readLine()) != null) {
                //every line that is a row or column, goes to the resulting file
                if (line.contains("</t") || line.contains("<td") || line.contains("<tr") || line.contains("<th")) {
                    result.append(line.trim());
                    // we simply count the rows, but do not consider the first one as it is the table header
                    if (line.contains("<tr>")) {
                        ++counter;
                    }
                }
            }
            counterAll = counterAll + counter;


            //System.out.println(result);

            final int offset = result.lastIndexOf("</h2>");

            result.replace(offset, offset + 5, " [This Season: " + counter + " | Total: " + counterAll + "]</h2><a href=\"#top\" title='Back to the top of the list'>top</a>");


            out.append(result.append('\n'));
            out.flush();
            return counterAll;

        }
    }


    private static void formatFileContent(final String source, String target) throws IOException {
        final StringBuffer result = new StringBuffer();


        try (BufferedReader inputStream = new BufferedReader(new FileReader(source)); PrintWriter outputStream = new PrintWriter(new FileWriter(target, false))) {


            final BufferedWriter out = new BufferedWriter(outputStream);


            String line;
            int counter = -1; // needs to be -1 because of the table header, we count only the content rows

            while ((line = inputStream.readLine()) != null) {
                if (line.isEmpty()) {
                    return;
                } else if (line.contains("<table>") || line.contains("</table>") || line.contains("<td>") || line.contains("</td>") || line.contains("<tr>") || line.contains("<th>")) {
                    result.append(line.trim()).append('\n');
                } else if (line.contains("</tr>")) {
                    result.append(line.trim()).append('\n');
                    ++counter;
                } else {
                    result.append(line.trim());
                }

            }


            System.out.println(result);
            final int offset;

            if (result.lastIndexOf("]</h2>") != -1) {
                offset = result.lastIndexOf("]</h2>");
                result.replace(offset, offset + 6, "]</h2>" + "\n");
            } else {

                offset = result.lastIndexOf("</h2>");
                result.replace(offset, offset + 5, " [Total: " + counter + "]</h2>" + "\n");
            }
            out.append(result.append('\n'));
            out.flush();


        }
    }
}


