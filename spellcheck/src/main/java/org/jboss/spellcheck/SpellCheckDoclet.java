/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.spellcheck;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SourcePosition;
import com.swabunga.spell.engine.Configuration;
import com.swabunga.spell.engine.SpellDictionary;
import com.swabunga.spell.engine.SpellDictionaryHashMap;
import com.swabunga.spell.event.SpellCheckEvent;
import com.swabunga.spell.event.SpellCheckListener;
import com.swabunga.spell.event.SpellChecker;
import com.swabunga.spell.event.StringWordTokenizer;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;


/**
 * This is a clone of softframworks' spellCheck doclet with 2 additions to the
 * input options.
 *      -reportfile <filename>    Write the results to the specified file.  When no file
 *                                is specified results are written to standard out.
 *      -unknownwords <filename>  A file of words not found in the dictionaries specified.
 *
 *
 * softframworks' SpellCheckDoclet code can be found here
 * http://www.softframeworks.com/download/download.php and its documentation can be found here
 * http://www.softframeworks.com/etc/spellcheck/SpellCheckDoclet.html
 *
 * Both this doclets are dependent on the jazzy-0.5.2 API.   Jazzy's project page is here
 * http://sourceforge.net/projects/jazzy/
 *
 * User: rsearls
 * Date: 11/20/15
 */
public class SpellCheckDoclet {

    private static HashMap<String, String> inputOptionList = new HashMap<String, String>();

    static {
        // SpellCheck's input options and number of args of each
        inputOptionList.put("-dictionary", "2");
        inputOptionList.put("-ignore", "2");
        inputOptionList.put("-ignorecontaining", "2");
        inputOptionList.put("-ignorefile", "2");
        inputOptionList.put("-echo", "2");
        inputOptionList.put("-reportfile", "2");
        inputOptionList.put("-unknownwords", "2");
        inputOptionList.put("-withsuggestions", "1");
        inputOptionList.put("-checkhtmlfiles", "1");
    }

    // default behavior write to standard OUT.
    private static PrintStream outputStream = System.out;
    private static PrintStream unknowWordsOutputStream = null;
    private static boolean isfirstOption = true;
    public static final String TITLE = "SpellCheck Results";
    private static final HtmlFileFilter HTML_FILE_FILTER = new HtmlFileFilter();
    private static final SpellChecker spellChecker = configureSpellChecker();
    private static final HashSet<String> ignoreContainingList = new HashSet<String>();
    private static final HashSet<String> unknownWords = new HashSet<String>();

    // The package currently being spell-checked.
    private static PackageDoc currentPackage = null;

    // The package for which the most recently header was written.
    private static PackageDoc lastPackageHeaderDoc = null;

    // The file currently being spell-checked.
    private static File currentFile = null;

    // The file for which the most recently header was written.
    private static File lastHeaderFile = null;

    // The class currently being spell-checked.
    private static ClassDoc currentClass = null;

    // The class for which the most recently header was written.
    private static ClassDoc lastClassHeaderDoc = null;

    // The class member (field, constructor, or method) currently being spell-checked.
    private static MemberDoc currentMember = null;

    // The class member (field, constructor, or method) for which the most recently
    // header was written.
    private static MemberDoc lastMemberHeaderDoc = null;

    // At least one dictionary must be specified.
    private static boolean isAddedDictionary = false;

    // The inputs specified by the parameters can optionally be echoed to the console.
    private static boolean echoInputs = true;

    // Correct spelling suggestions can optionally be included.
    private static boolean withSuggestions = false;

    // Documentation HTML files can optionally be checked.
    private static boolean checkHtmlFiles = false;

    private static String outputReportFile = "Unknow reportFile";

    /**
     * Lookup the count input arguments for each input option
     * @param theOption option key word
     * @return number of args
     */
    public static int optionLength(String theOption) {

        int argCnt = 0;
        String value = inputOptionList.get(theOption.toLowerCase());
        if (value != null) {
            argCnt = Integer.parseInt(value);
        }
        return argCnt;
    }

    /**
     * Entry point to the spell-check process.  The Root Doc provides access to the results
     * of the JavaDoc analysis of the Java source files.  This Root Doc is used to obtain
     * access to the Javadoc comments whose content is spell-checked.
     *
     * @NOTE This method is invoked by JavaDoc.
     *
     * @param theRootDoc
     * @return
     */
    public static boolean start(RootDoc theRootDoc) {
        // Make sure that a dictionary was specified.
        if (!isAddedDictionary) {
            System.out.println("\n*** ERROR: At least one dictionary must be specified.");
            return false;
        }

        // Print the Herald.
        outputStream.println("**************************************************************************************");
        outputStream.println(TITLE);
        outputStream.println(new Date());
        outputStream.println("**************************************************************************************");

        // Spell check the API.
        spellCheckApi(theRootDoc);

        outputStream.println("\n*********************************************\n   SpellCheck Results Complete \n*********************************************\n");

        writeUnknownWords();

        return true;
    }


    //@Override
    public static boolean validOptions(String theOptionList[][],
                                       DocErrorReporter theErrorReporter) {

        // Display the Herald to the console.
        if (isfirstOption) {
            System.out.println("\n" + TITLE +
                "\n==========================================\n");
            isfirstOption = false;
        }


        for (int ii = 0; ii < theOptionList.length; ii++) {
            String[] theOption = theOptionList[ii];
            theOption[0] = theOption[0].toLowerCase();

            if (theOption[0].equals("-dictionary")) {
                if (echoInputs) {
                    System.out.println("  ::: Dictionary: " + theOption[1]);
                }

                if (theOption[1] == null) {
                    System.out.println("*** ERROR: -dictionary filename is missing");
                    System.exit(-1);
                } else {
                    addDictionary(theOption[1]);
                }

            } else if (theOption[0].equals("-reportfile")) {
                if (theOption[1] == null) {
                    System.out.println("*** ERROR: -reportfile filename is missing");
                } else {
                    try {
                        outputStream = new PrintStream(theOption[1]);
                        outputReportFile = theOption[1];
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

            } else if (theOption[0].equals("-unknownwords")) {

                if (theOption[1] == null) {
                    System.out.println("*** ERROR: -unknownwords filename is missing");
                } else {
                    try {
                        unknowWordsOutputStream = new PrintStream(theOption[1]);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

            } else if (theOption[0].equals("-ignore")) {
                if (echoInputs) {
                    System.out.println("  >>> Ignore: " + theOption[1]);
                }
                spellChecker.ignoreAll(theOption[1]);

            } else if (theOption[0].equals("-ignorefile")) {
                if (theOption[1] == null) {
                    System.out.println("*** ERROR: -ignorefile filename is missing");
                } else {
                    processIgnoreFile(theOption[1]);
                }

            } else if (theOption[0].equals("-ignorecontaining")) {
                if (echoInputs) {
                    System.out.println("  >>> Ignore Containing: " + theOption[1]);
                }
                ignoreContainingList.add(theOption[1]);

            } else if (theOption[0].equals("-echo")) {

                if (theOption[1].equalsIgnoreCase("off")) {
                    echoInputs = false;
                } else if (theOption[1].equalsIgnoreCase("on")) {
                    echoInputs = true;
                } else {
                    System.out.println("\n*** ERROR: Invalid ECHO value: " + theOption[1] +
                        "\n           Must be 'ON' or 'OFF'.");
                    return false;
                }
            }

            if (theOption[0].equals("-withsuggestions")) {
                withSuggestions = true;
            } else if (theOption[0].equals("-checkhtmlfiles")) {
                checkHtmlFiles = true;
            }
        }

        return true;
    }


    /**
     * Spell-checks the API identified by the given Root Doc.
     */
    private static void spellCheckApi(RootDoc theRootDoc) {
        ClassDoc[] theClassList = theRootDoc.classes();

        if (outputStream == System.out) {
            for (int ii = 0; ii < theClassList.length; ii++) {
                spellCheckClass(theClassList[ii]);
            }
        } else {
            for (int ii = 0; ii < theClassList.length; ii++) {
                spellCheckClass(theClassList[ii]);
            }
            System.out.println("\n********************************************************************\n"
               + "   SpellCheck results written to file " + outputReportFile
               + "\n********************************************************************\n");
        }
    }


    /**
     * Spell-checks the Javadoc for the given class.
     */
    private static void spellCheckClass(ClassDoc theClassDoc) {
        PackageDoc theContainingPackage = theClassDoc.containingPackage();
        MemberDoc[] theMemberList = null;

        // If a new Package is being started, then spell check the HTML files associated
        // with this package before the class is checked.
        if (checkHtmlFiles && (theContainingPackage != currentPackage)) {
            currentPackage = theContainingPackage;
            spellCheckHtmlFiles(theContainingPackage);
        }

        // Record the current package, class, and member.
        // NOTE: The Current Member must be set to NULL to indicate that the class comment
        //       is being processed.
        currentPackage = theContainingPackage;
        currentClass = theClassDoc;
        currentMember = null;

        // Check the class comment.
        spellCheckString(theClassDoc.commentText());

        // Check the fields.
        theMemberList = theClassDoc.fields();
        for (int ii = 0; ii < theMemberList.length; ii++) {
            spellCheckMember(theMemberList[ii]);
        }

        // Check the constructors.
        theMemberList = theClassDoc.constructors();
        for (int ii = 0; ii < theMemberList.length; ii++) {
            spellCheckMember(theMemberList[ii]);
        }

        // Check the methods.
        theMemberList = theClassDoc.methods();
        for (int ii = 0; ii < theMemberList.length; ii++) {
            spellCheckMember(theMemberList[ii]);
        }
    }


    /**
     * Spell-checks the Javadoc for the the given class member (field, constructor, or method).
     */
    private static void spellCheckMember(MemberDoc theMemberDoc) {
        currentMember = theMemberDoc;

        // Check the member comment.
        spellCheckString(theMemberDoc.commentText());
    }

    /**
     * Spell-checks the given string.
     */
    private static void spellCheckString(String theString) {
        try {
            spellChecker.checkSpelling(new StringWordTokenizer(theString));
        } catch (Exception theEx) {
            theEx.printStackTrace();
        }
    }


    /**
     * Spell-checks the HTML files associated with for the given package.
     */
    private static void spellCheckHtmlFiles(PackageDoc thePackageDoc) {
        SourcePosition theFilePosition = thePackageDoc.position();
        File theDirectory = null;
        String theSubdirectory = null;
        String theMsg = null;

        // Display the package name.
        System.out.print("\n  >>> " + thePackageDoc);

        // Check for NULL File Position.  This indicates that the package.html file
        // is not present.
        if (theFilePosition == null) {
            theMsg = "  ****WARNING: The package.html file is not present for this package.\n" +
                "               The HTML files cannot be spellchecked until this file " +
                "is added.";
            System.out.println("\n\n" + theMsg);
            return;
        }

        // Get the directory associated with the given Package.
        theDirectory = theFilePosition.file();
        if (!theDirectory.isDirectory()) {
            theDirectory = theDirectory.getParentFile();
        }

        // Spell-check the HTML files in this directory.
        spellCheckHtmlFiles(theDirectory);

        // Spell-check the HTML files in the doc-files subdirectory (if it exists).
        theSubdirectory = theDirectory.toString() + File.separatorChar + "doc-files";
        theDirectory = new File(theSubdirectory);
        if (theDirectory.exists()) {
            spellCheckHtmlFiles(theDirectory);
        }
    }


    /**
     * Spell-checks the HTML files in the given directory.
     */
    private static void spellCheckHtmlFiles(File theDirectory) {
        File[] theHtmlFileList = theDirectory.listFiles(HTML_FILE_FILTER);

        // Check the HTML files in the given directory.
        for (int ii = 0; ii < theHtmlFileList.length; ii++) {
            spellCheckHtmlFile(theHtmlFileList[ii]);
        }
    }


    /**
     * Spell-checks the contents of the given HTML file.
     */
    private static void spellCheckHtmlFile(File theFile) {
        StringBuffer theFileContentBuffer = null;
        String theFileContent = null;
        int theBodyStartIndex = 0;

        try {
            currentFile = theFile;

            // Get the content of the file.
            theFileContentBuffer = getFileContentString(theFile);

            // Get the portion of the file starting from the <BODY> element.  This is the
            // portion of the file that is spell checked.
            // NOTE: The code assumes that the BODY element appears in either all upper-case
            //       or all lower-case.
            theBodyStartIndex = theFileContentBuffer.indexOf("<BODY");
            if (theBodyStartIndex < 0) {
                theBodyStartIndex = theFileContentBuffer.indexOf("<body");
            }

            if (theBodyStartIndex > 0) {
                theFileContent = theFileContentBuffer.substring(theBodyStartIndex);
            } else {
                theFileContent = theFileContentBuffer.toString();
            }

            // Spell check this portion of the file.
            spellCheckString(theFileContent);

            currentFile = null;
        } catch (Throwable theEx) {
            System.out.println("*** ERROR encountered reading file: " + theFile);
            theEx.printStackTrace();
            System.exit(-1);
        }
    }


    /**
     * Gets the content of the given file as a string.  An exception is thrown if the file
     * does not exist or is a directory.
     *
     * @NOTE The length of the file is obtained as a long value and cast to an int value.  This
     * means that this method cannot be used for files longer than the value that can be
     * contained in an int variable.
     */
    private static StringBuffer getFileContentString(File theFile)
        throws Exception {
        BufferedInputStream theInputFile = null;
        StringBuffer theBuffer = null;
        String theMsg = null;
        int theByte = 0;

        // Make sure that the input file exists and is not a directory.
        if (!theFile.exists()) {
            theMsg = "The specified input file does not exist: " + theFile.toString();
            throw new Exception(theMsg);
        }

        if (theFile.isDirectory()) {
            theMsg = "The input file cannot be a directory: " + theFile.toString();
            throw new Exception(theMsg);
        }

        // Allocate the String Buffer to hold the contents of the file.
        theBuffer = new StringBuffer((int) theFile.length());

        // Open the Input file.
        theInputFile = new BufferedInputStream(new FileInputStream(theFile));

        // Read the contents of the Input File into the String Buffer.
        while ((theByte = theInputFile.read()) >= 0) {
            theBuffer.append((char) theByte);
        }

        // Close the Input file.
        theInputFile.close();

        return theBuffer;
    }

    /****************************************************************
     *
     ****************************************************************/

    /**
     * Builds the Spell Checker.
     */
    private static SpellChecker configureSpellChecker() {

        SpellChecker theSpellChecker = null;

        try {
            // Create the Spell Checker.
            theSpellChecker = new SpellChecker();

            // Configure the Spell Checker.
            theSpellChecker.getConfiguration().setBoolean(
                Configuration.SPELL_IGNOREMIXEDCASE, true);

            // Specify the words to be ignored.
            // NOTE: Ignore commonly abbreviations: e.g and i.e.
            theSpellChecker.ignoreAll("e.g");
            theSpellChecker.ignoreAll("i.e");

            // Add the Spell Check Listener.
            theSpellChecker.addSpellCheckListener(new SpellErrorHandler());
        } catch (Exception theEx) {
            theEx.printStackTrace();
            System.exit(-1);
        }

        return theSpellChecker;
    }

    /**
     * Adds a dictionary to the Spell Checker.
     */
    private static void addDictionary(String theWordListFile) {

        try {
            InputStream inStream = SpellCheckDoclet.class.getClassLoader().getResourceAsStream(theWordListFile);

            if (inStream == null) {
                // file on local disk
                SpellDictionary theDictionary = new SpellDictionaryHashMap(
                    new File(theWordListFile));
                spellChecker.addDictionary(theDictionary);

                if (echoInputs) {
                    File f = new File(theWordListFile);
                    System.out.println("Loaded dictionary: " + f.getCanonicalPath());
                }

            } else {
                // file in archive
                SpellDictionary theDictionary = new SpellDictionaryHashMap(
                   new InputStreamReader(inStream));
                spellChecker.addDictionary(theDictionary);
                inStream.close();

                if (echoInputs) {
                    URL tmpFile = SpellCheckDoclet.class.getClassLoader().getResource(theWordListFile);
                    System.out.println("Loaded dictionary: " + tmpFile.toString());
                }
            }
            isAddedDictionary = true;

        } catch (Exception e) {
            System.out.println("*** ERROR encountered adding dictionary: "
                + theWordListFile);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * Processes the given Ignore File.
     */
    private static void processIgnoreFile(String theFileName) {

        try {
            // Create the file reader.
            LineNumberReader theLineReader = new LineNumberReader(
                new FileReader(theFileName));
            String theIgnoreWord = null;

            // Read the file one line at a time.
            while ((theIgnoreWord = theLineReader.readLine()) != null) {
                // Add the word contained on this line to the ignored words.
                theIgnoreWord = theIgnoreWord.trim();
                if (theIgnoreWord.length() > 0) {
                    if (echoInputs) {
                        System.out.println("  >>> Ignore: " + theIgnoreWord);
                    }
                    spellChecker.ignoreAll(theIgnoreWord);
                }
            }
        } catch (Exception e) {
            System.out.println("*** ERROR encountered processing Ignore File: " + theFileName);
            e.printStackTrace();
            System.exit(-1);
        }
    }


    /**
     * Writes the Package Header.
     */
    private static void writePackageHeader(PackageDoc thePackageDoc) {
        outputStream.println("\n*********************************************" +
            "\nPackage: " + thePackageDoc +
            "\n*********************************************");
        lastPackageHeaderDoc = thePackageDoc;
    }


    /**
     * Writes the File Header.
     */
    private static void writeFileHeader(File theFile) {
        outputStream.println("  File: " + theFile +
            "\n  ================================================");

        lastHeaderFile = theFile;
    }


    /**
     * Writes the Class Header.
     */
    private static void writeClassHeader(ClassDoc theClassDoc) {
        outputStream.println("\n  Class: " + theClassDoc +
            "\n  ================================================");

        lastClassHeaderDoc = theClassDoc;
    }


    /**
     * Writes the Member (field, constructor, or method) Header.
     */
    private static void writeMemberHeader(MemberDoc theMemberDoc) {
        if (theMemberDoc instanceof MethodDoc) {
            outputStream.println("    Method: " + theMemberDoc.name() +
                "\n    --------------------------------------");
        } else if (theMemberDoc instanceof ConstructorDoc) {
            outputStream.println("    Constructor: " + theMemberDoc.name() +
                "\n    --------------------------------------");
        } else if (theMemberDoc instanceof FieldDoc) {
            outputStream.println("    Field: " + theMemberDoc.name() +
                "\n    --------------------------------------");
        }

        lastMemberHeaderDoc = theMemberDoc;
    }


    private static void writeUnknownWords() {

        if (unknowWordsOutputStream != null && !unknownWords.isEmpty()) {
            try {
                for (String s : unknownWords) {
                    unknowWordsOutputStream.println(s);
                }

            } catch (Exception e) {
                System.out.println("*** ERROR writing unknown words to file.");
            } finally {
                unknowWordsOutputStream.close();
            }
        }
    }


    /****************************************************************
     *
     ****************************************************************/

    /**
     * SpellErrorHandler.
     */
    private static class SpellErrorHandler implements SpellCheckListener {
        /**
         * Handles spelling error events.
         * SpellCheckListener interface implementation.
         */
        public void spellingError(SpellCheckEvent theEvent) {
            String theInvalidWord = theEvent.getInvalidWord();

            // Certain spelling errors are ignored.
            // NOTE: This does not pertain to Ignore Words, since Ignore Words do not raise a
            //       Spell Check Evant.  Rather it pertains to Ignore Containing strings.
            //if (isToIgnore(theInvalidWord)) return; // todo
            if (!ignoreContainingList.contains(theInvalidWord)) {

                // Write the Package Header (if not already written).
                if (currentPackage != lastPackageHeaderDoc) {
                    writePackageHeader(currentPackage);
                }

                // If a file is being processed...
                if (currentFile != null) {
                    // Write the File Header (if not already written).
                    if (currentFile != lastHeaderFile) {
                        writeFileHeader(currentFile);
                        outputStream.print("\t");
                    }
                } else {
                    // Write the Class Header (if not already written).
                    if (currentClass != lastClassHeaderDoc) {
                        writeClassHeader(currentClass);
                        outputStream.print("\t");
                    }

                    // Write the Member Header (if not already written).
                    if ((currentMember != null) && (currentMember != lastMemberHeaderDoc)) {
                        writeMemberHeader(currentMember);
                        outputStream.print("\t");
                    }
                }

                unknownWords.add(theInvalidWord);
                // Write the invalid word.
                outputStream.println(theInvalidWord);

                // Write the suggests.
                if (withSuggestions) {
                    outputStream.print(": ");

                    List theSuggestions = theEvent.getSuggestions();
                    if (theSuggestions.size() > 0) {
                        for (Iterator theSuggestedWord = theSuggestions.iterator(); theSuggestedWord.hasNext(); ) {
                            outputStream.print(theSuggestedWord.next() + " ");
                        }
                    } else {
                        outputStream.print("<No suggestions>");
                    }
                    outputStream.print("\n\t");
                }
            }
        }

    }

    /**
     * HtmlFileFilter.
     */
    private static class HtmlFileFilter implements FileFilter, Serializable {
        public boolean accept(File theFile) {
            if (theFile.isDirectory()) {
                return false;
            }

            return theFile.getName().toLowerCase().endsWith(".html");
        }
    }

}
